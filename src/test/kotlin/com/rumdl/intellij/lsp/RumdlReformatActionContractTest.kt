@file:Suppress("UnstableApiUsage")

package com.rumdl.intellij.lsp

import com.intellij.formatting.service.FormattingService
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.module.ModuleManager
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
        const val WAIT_TIMEOUT_MS = 30_000L
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

        // Make detection deterministic if a binary path is provided; otherwise
        // fall back to PATH detection (governed by the pinned rumdl via the Makefile).
        System.getProperty("rumdl.test.binary")?.let { bin ->
            RumdlConfigService.getInstance(project).state.rumdlPath = bin
        }

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
        try {
            // Opening the file triggers the provider's fileOpened; also ask the
            // manager explicitly so the server starts even without editor events.
            myFixture.openFileInEditor(vFile)
            manager.startServersIfNeeded(RumdlLspServerSupportProvider::class.java)

            val detected = Rumdl.detectExecutable(project)?.absolutePath
            val server = waitForRunningServer(manager)
            if (server == null) {
                val servers = manager.getServersForProvider(RumdlLspServerSupportProvider::class.java)
                val states = servers.joinToString(", ") { "${it.state}" }
                fail(
                    "rumdl LSP server never reached Running state; cannot test Reformat Code. " +
                        "detectedBinary=$detected, serverCount=${servers.size}, states=[$states]",
                )
            }
            assertNotNull(
                "rumdl server did not advertise documentFormattingProvider.",
                server!!.initializeResult?.capabilities?.documentFormattingProvider,
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
            waitFor("document reflowed by Reformat Code") { document.text == EXPECTED }

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
        }
    }

    private fun waitForRunningServer(manager: LspServerManager): LspServer? {
        var running: LspServer? = null
        waitFor("rumdl server reaches Running state") {
            val servers = manager.getServersForProvider(RumdlLspServerSupportProvider::class.java)
            running = servers.firstOrNull { it.state == LspServerState.Running }
            running != null
        }
        return running
    }

    /** Pump the IDE event queue until [condition] holds or the timeout elapses. */
    private fun waitFor(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + WAIT_TIMEOUT_MS * 1_000_000
        while (System.nanoTime() < deadline) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
            if (condition()) return
            Thread.sleep(50)
        }
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
        // Caller asserts the post-condition; this only bounds the wait.
    }
}
