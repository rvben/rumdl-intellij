@file:Suppress("UnstableApiUsage")

package com.rumdl.intellij.lsp

import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.platform.lsp.api.LspServer
import com.intellij.platform.lsp.api.LspServerManager
import com.intellij.platform.lsp.api.LspServerState
import com.intellij.psi.PsiManager
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.rumdl.intellij.Rumdl
import com.rumdl.intellij.RumdlConfigService
import java.io.File

/**
 * End-to-end test for the actual user-facing half of issue #2: invoking the
 * IDE's **"Reformat Code"** on a Markdown file must route through the running
 * rumdl LSP server and rewrite the buffer. The existing
 * [RumdlLspFormattingContractTest] only proves the raw server speaks
 * `textDocument/formatting`; it never exercises the IDE action wiring, which is
 * exactly where "Reformat Code does nothing" lives.
 *
 * This drives the real platform path:
 *   1. start the rumdl server via [LspServerManager] for a real on-disk project,
 *   2. assert the platform's LSP formatting service claims the file
 *      (`canFormat` == true) - i.e. Reformat Code is routed to rumdl, and
 *   3. run Reformat Code and assert the document was reflowed.
 *
 * Uses the user's exact config shape from the issue: `pyproject.toml` with
 * `flavor = "mkdocs"` and MD013 semantic-line-breaks reflow.
 *
 * Runs against a real `rumdl` subprocess, so it is gated into `integrationTest`
 * (the `*ContractTest` filter) alongside the pinned-binary install.
 */
class RumdlReformatActionContractTest : BasePlatformTestCase() {

    private companion object {
        // A cold CI runner may still be indexing the downloaded IDE when the
        // LSP process starts. Bound the wait, but leave enough room for that
        // supported slow path before declaring formatter routing broken.
        const val WAIT_TIMEOUT_SECONDS = 60
        const val LSP_SERVICE_FQN = "com.intellij.platform.lsp.impl.formatter.LspFormattingService"

        val PYPROJECT = """
            [tool.rumdl]
            flavor = "mkdocs"
            line_length = 120

            [tool.rumdl.MD013]
            reflow = true
            reflow-mode = "semantic-line-breaks"
        """.trimIndent() + "\n"

        // Verbatim reproducer from issue #2. The command-line formatter joins
        // each sentence into one physical line under semantic-line-breaks.
        val SOURCE = buildString {
            append("Before creating a task and sending it to the background, validate that all\n")
            append("required resources exist. We want to fail early if we now, that e.g a\n")
            append("workbook with a passed `workbook_id` does not exist.\n")
        }
        val EXPECTED = buildString {
            append("Before creating a task and sending it to the background, validate that all required resources exist.\n")
            append("We want to fail early if we now, that e.g a workbook with a passed `workbook_id` does not exist.\n")
        }
    }

    fun `test Reformat Code routes Markdown through rumdl and reflows the buffer`() {
        // canonicalFile resolves the macOS /var -> /private/var symlink so the
        // path we whitelist below matches the one the VFS actually validates.
        val workDir = File.createTempFile("rumdl-reformat-action", "").apply {
            delete()
            mkdirs()
            deleteOnExit()
        }.canonicalFile
        File(workDir, "pyproject.toml").writeText(PYPROJECT)
        val mdIoFile = File(workDir, "doc.md").apply { writeText(SOURCE) }

        // The test VFS only permits registered roots; allow our real on-disk
        // project dir (the rumdl subprocess needs real files, not the temp:// VFS).
        VfsRootAccess.allowRootAccess(testRootDisposable, workDir.path)

        val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(mdIoFile)
            ?: error("could not load ${mdIoFile.path} into the VFS")
        val workVDir = vFile.parent ?: error("no parent VFS dir for ${vFile.path}")

        // The platform only starts an LSP server for an open file that is inside
        // the project's content (ProjectFileIndex.isInContent). Register the
        // on-disk project dir as a content root so the server can start.
        val module = ModuleManager.getInstance(project).modules.first()
        PsiTestUtil.addContentRoot(module, workVDir)

        // The descriptor launches `rumdl server` with project.basePath as its
        // working directory. Light test projects reuse a basePath whose temp dir
        // may have been reaped by a previous test, which makes process launch fail
        // with WorkingDirectoryNotFoundException (server -> ShutdownUnexpectedly).
        // Ensure it exists so this test is order-independent.
        project.basePath?.let { File(it).mkdirs() }

        val manager = LspServerManager.getInstance(project)
        val config = RumdlConfigService.getInstance(project)
        val previousState = config.state.copy()
        try {
            config.state.enableLsp = true
            // An invalid pin must fail here instead of silently using another installation.
            System.getProperty("rumdl.test.binary")?.let { bin ->
                val binary = File(bin)
                assertTrue("Pinned rumdl is not an executable file: $bin", binary.isFile && binary.canExecute())
                config.state.rumdlPath = bin
            }
            val detected = Rumdl.detectExecutable(project)
            assertNotNull("No rumdl executable found for the Reformat Code contract test", detected)

            // Opening the file triggers the provider's fileOpened; also ask the
            // manager explicitly so the server starts even without editor events.
            myFixture.openFileInEditor(vFile)
            manager.startServersIfNeeded(RumdlLspServerSupportProvider::class.java)

            val server = waitForRunningServer(manager, detected!!)
            assertNotNull(
                "rumdl server did not advertise documentFormattingProvider.",
                server.initializeResult?.capabilities?.documentFormattingProvider,
            )

            val psiFile = PsiManager.getInstance(project).findFile(vFile)
                ?: error("no PSI for ${vFile.path}")

            // The crux of issue #2: the platform's LSP formatting service must
            // claim the Markdown file, otherwise Reformat Code is a silent no-op.
            val lspFormattingService = FormattingService.EP_NAME.extensionList
                .firstOrNull { it::class.java.name == LSP_SERVICE_FQN }
                ?: error("platform LSP formatting service not registered")

            waitFor("LSP formatting service claims the Markdown file") {
                lspFormattingService.canFormat(psiFile)
            }
            assertTrue(
                "Platform LSP formatting service does not claim the Markdown file, so " +
                    "Reformat Code never reaches rumdl (issue #2).",
                lspFormattingService.canFormat(psiFile),
            )

            // Full end-to-end: invoke the registered Reformat Code action with
            // the same editor/project data the keyboard shortcut supplies. A
            // direct CodeStyleManager.reformat call skips FileInEditorProcessor,
            // so it can pass even when Ctrl+Alt+L itself is not wired correctly.
            val reformatAction = ActionManager.getInstance().getAction("ReformatCode")
                ?: error("IDE ReformatCode action is not registered")
            val dataContext = SimpleDataContext.builder()
                .add(CommonDataKeys.PROJECT, project)
                .add(CommonDataKeys.EDITOR, myFixture.editor)
                .add(CommonDataKeys.VIRTUAL_FILE, vFile)
                .build()
            reformatAction.actionPerformed(TestActionEvent.createTestEvent(reformatAction, dataContext))

            val document = FileDocumentManager.getInstance().getDocument(vFile)
                ?: error("no document for ${vFile.path}")
            waitFor(
                "document reflowed by Reformat Code",
                diagnostics = { "Expected buffer:\n$EXPECTED\nActual buffer:\n${document.text}" },
            ) { document.text == EXPECTED }

            assertEquals(
                "Reformat Code did not reflow the Markdown buffer via rumdl (issue #2).",
                EXPECTED,
                document.text,
            )
        } finally {
            runCatching { manager.stopServers(RumdlLspServerSupportProvider::class.java) }
            // Light-project modules are reused across tests; remove the content
            // root we added so we don't leak it into sibling tests.
            runCatching { PsiTestUtil.removeContentEntry(module, workVDir) }
            config.loadState(previousState)
        }
    }

    private fun waitForRunningServer(manager: LspServerManager, binary: File): LspServer {
        var running: LspServer? = null
        val started = System.nanoTime()
        val transitions = mutableListOf<String>()
        var previousSnapshot: String? = null
        waitFor(
            "rumdl server reaches Running state",
            diagnostics = {
                "binary=${binary.absolutePath}, executable=${binary.isFile && binary.canExecute()}, " +
                    "workingDirectory=${project.basePath}, " +
                    "workingDirectoryExists=${project.basePath?.let { File(it).isDirectory }}, " +
                    "projectInitialized=${project.isInitialized}, projectDisposed=${project.isDisposed}, " +
                    "indexing=${DumbService.isDumb(project)}, states=$transitions, " +
                    "ideLogDirectory=${System.getProperty("idea.log.path")}"
            },
        ) {
            val servers = manager.getServersForProvider(RumdlLspServerSupportProvider::class.java)
            val snapshot = servers.joinToString(prefix = "[", postfix = "]") { "${it.state}" }
            if (snapshot != previousSnapshot) {
                if (transitions.size < 64) {
                    transitions.add("${(System.nanoTime() - started) / 1_000_000}ms: $snapshot")
                }
                previousSnapshot = snapshot
            }
            running = servers.firstOrNull { it.state == LspServerState.Running }
            running != null
        }
        return checkNotNull(running)
    }

    /** Dispatch all IDE events while waiting; never discard events needed by LSP startup. */
    private fun waitFor(
        what: String,
        diagnostics: () -> String = { "ideLogDirectory=${System.getProperty("idea.log.path")}" },
        condition: () -> Boolean,
    ) {
        PlatformTestUtil.waitWithEventsDispatching(
            java.util.function.Supplier { "Timed out after ${WAIT_TIMEOUT_SECONDS}s waiting for $what. ${diagnostics()}" },
            java.util.function.BooleanSupplier { condition() },
            WAIT_TIMEOUT_SECONDS,
        )
    }
}
