package com.rumdl.intellij.lsp

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.rumdl.intellij.Rumdl
import org.eclipse.lsp4j.ApplyWorkspaceEditParams
import org.eclipse.lsp4j.ApplyWorkspaceEditResponse
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ConfigurationParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.FormattingOptions
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializedParams
import org.eclipse.lsp4j.LogTraceParams
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.ProgressParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.RegistrationParams
import org.eclipse.lsp4j.ShowDocumentParams
import org.eclipse.lsp4j.ShowDocumentResult
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentIdentifier
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.UnregistrationParams
import org.eclipse.lsp4j.WorkDoneProgressCreateParams
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClient
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * End-to-end contract test for the part of issue #2 that actually breaks for
 * users: whether the `rumdl` binary the plugin resolves speaks LSP
 * `textDocument/formatting` and returns edits that fix a Markdown violation.
 *
 * This drives the **exact production command** ([Rumdl.createLspCommandLine],
 * the same call [RumdlLspServerDescriptor.createCommandLine] makes) against a
 * real `rumdl server` subprocess over stdio, then asserts the formatted result.
 *
 * Scope: this proves the rumdl server formatting contract. It deliberately does
 * NOT exercise IntelliJ's "Reformat Code" action wiring - the IntelliJ LSP API
 * ships no supported in-IDE harness for running a server, and that glue is
 * covered structurally by [RumdlLspServerDescriptorTest]. Keeping the boundary
 * explicit here avoids faking coverage we do not have.
 *
 * Fails hard when `rumdl` is absent: a green run must mean a real server
 * actually formatted the document.
 */
class RumdlLspFormattingContractTest : BasePlatformTestCase() {

    private companion object {
        const val LSP_TIMEOUT_SECONDS = 30L

        // A multi-sentence paragraph on one line. With MD013 reflow in
        // semantic-line-breaks mode, rumdl splits it to one sentence per line.
        val SOURCE = buildString {
            append("# Title\n")
            append("\n")
            append(
                "This is a sentence. This is another sentence on the same line " +
                    "which makes it quite long indeed yes. And a third clause here.\n"
            )
        }

        val EXPECTED = buildString {
            append("# Title\n")
            append("\n")
            append("This is a sentence.\n")
            append("This is another sentence on the same line which makes it quite long indeed yes.\n")
            append("And a third clause here.\n")
        }

        val RUMDL_CONFIG = """
            [MD013]
            line-length = 80
            reflow = true
            reflow-mode = "semantic-line-breaks"
        """.trimIndent() + "\n"
    }

    // rootUri is deprecated in the LSP spec in favour of workspaceFolders, but
    // rumdl resolves .rumdl.toml from it, so it remains the correct field here.
    @Suppress("DEPRECATION")
    fun `test rumdl server formats Markdown via textDocument formatting`() {
        val workDir = createTempProject()
        val mdFile = File(workDir, "doc.md").apply { writeText(SOURCE) }

        val commandLine = Rumdl.createLspCommandLine(project, workDir)
        assertNotNull(
            "rumdl executable not found. This contract test must run against a real " +
                "rumdl server (install rumdl and ensure it is on PATH or configured).",
            commandLine,
        )

        val process = commandLine!!.createProcess()
        val stderrDrain = drainStream(process)
        try {
            val client = SilentLanguageClient()
            val launcher = LSPLauncher.createClientLauncher(
                client,
                process.inputStream,
                process.outputStream,
            )
            val server = launcher.remoteProxy
            launcher.startListening()

            val initParams = InitializeParams().apply {
                processId = ProcessHandle.current().pid().toInt()
                rootUri = workDir.toURI().toString()
                // rumdl's server deserializes InitializeParams with serde and
                // requires `capabilities` (LSP marks it mandatory); omitting it
                // makes initialize fail with "missing field `capabilities`".
                capabilities = ClientCapabilities()
            }
            val initResult = server.initialize(initParams).await("initialize")

            val formattingProvider = initResult.capabilities.documentFormattingProvider
            assertNotNull(
                "rumdl server did not advertise documentFormattingProvider; this rumdl " +
                    "is too old for Reformat Code to ever work (see issue #2).",
                formattingProvider,
            )

            server.initialized(InitializedParams())

            val uri = mdFile.toURI().toString()
            server.textDocumentService.didOpen(
                DidOpenTextDocumentParams(
                    TextDocumentItem(uri, "markdown", 1, SOURCE),
                ),
            )

            val edits: List<TextEdit>? = server.textDocumentService.formatting(
                DocumentFormattingParams(
                    TextDocumentIdentifier(uri),
                    FormattingOptions(4, true),
                ),
            ).await("textDocument/formatting")

            assertNotNull("formatting returned null instead of a list of edits", edits)
            assertFalse(
                "rumdl returned zero edits for a document with a known MD013 " +
                    "semantic-line-breaks violation; Reformat Code would be a no-op (issue #2).",
                edits!!.isEmpty(),
            )

            assertEquals(
                "Applying the server's edits did not produce the expected " +
                    "semantic-line-breaks reflow.",
                EXPECTED,
                applyEdits(SOURCE, edits),
            )

            runCatching { server.shutdown().get(LSP_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
            runCatching { server.exit() }
        } finally {
            process.destroy()
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly()
            }
            stderrDrain.join(2_000)
        }
    }

    private fun createTempProject(): File {
        val dir = createTempDirectory()
        File(dir, ".rumdl.toml").writeText(RUMDL_CONFIG)
        return dir
    }

    private fun createTempDirectory(): File {
        val dir = File.createTempFile("rumdl-lsp-contract", "").apply {
            delete()
            mkdirs()
        }
        dir.deleteOnExit()
        return dir
    }

    private fun <T> CompletableFuture<T>.await(what: String): T =
        try {
            get(LSP_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        } catch (e: Exception) {
            throw AssertionError("LSP request '$what' did not complete", e)
        }

    /** Drains the process stderr so a chatty server cannot deadlock on a full pipe. */
    private fun drainStream(process: Process): Thread =
        Thread {
            runCatching { process.errorStream.bufferedReader().forEachLine { } }
        }.apply {
            isDaemon = true
            start()
        }

    /**
     * Applies LSP [TextEdit]s to [text]. Edits are applied from the end of the
     * document backwards so earlier offsets stay valid.
     */
    private fun applyEdits(text: String, edits: List<TextEdit>): String {
        val lineStartOffsets = buildList {
            add(0)
            text.forEachIndexed { index, c -> if (c == '\n') add(index + 1) }
        }
        fun offsetOf(line: Int, character: Int): Int = lineStartOffsets[line] + character

        val sorted = edits.sortedWith(
            compareByDescending<TextEdit> { it.range.start.line }
                .thenByDescending { it.range.start.character },
        )
        val sb = StringBuilder(text)
        for (edit in sorted) {
            val start = offsetOf(edit.range.start.line, edit.range.start.character)
            val end = offsetOf(edit.range.end.line, edit.range.end.character)
            sb.replace(start, end, edit.newText)
        }
        return sb.toString()
    }

    /**
     * A [LanguageClient] that answers every server-initiated callback with a
     * benign response. In lsp4j 0.21.1 (the pinned version) all `default`
     * callbacks throw [UnsupportedOperationException], so an unhandled callback
     * from rumdl (`client/registerCapability`, `window/workDoneProgress/create`,
     * `workspace/configuration`, `$/progress`, ...) would surface as a SEVERE
     * JSON-RPC error and could make the contract test flaky as the server
     * evolves. Implementing the full surface keeps the session clean.
     */
    private class SilentLanguageClient : LanguageClient {
        override fun telemetryEvent(`object`: Any?) {}
        override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {}
        override fun showMessage(messageParams: MessageParams?) {}
        override fun showMessageRequest(
            requestParams: ShowMessageRequestParams?,
        ): CompletableFuture<MessageActionItem> = CompletableFuture.completedFuture(null)
        override fun logMessage(message: MessageParams?) {}

        override fun applyEdit(
            params: ApplyWorkspaceEditParams?,
        ): CompletableFuture<ApplyWorkspaceEditResponse> =
            CompletableFuture.completedFuture(ApplyWorkspaceEditResponse(false))

        override fun registerCapability(params: RegistrationParams?): CompletableFuture<Void> =
            CompletableFuture.completedFuture(null)

        override fun unregisterCapability(params: UnregistrationParams?): CompletableFuture<Void> =
            CompletableFuture.completedFuture(null)

        override fun workspaceFolders(): CompletableFuture<List<WorkspaceFolder>> =
            CompletableFuture.completedFuture(emptyList())

        // One result per requested item, as the LSP spec requires; nulls mean
        // "no configuration", which is correct here since the server resolves
        // .rumdl.toml from rootUri.
        override fun configuration(
            params: ConfigurationParams?,
        ): CompletableFuture<List<Any?>> =
            CompletableFuture.completedFuture(arrayOfNulls<Any?>(params?.items?.size ?: 0).toList())

        override fun createProgress(
            params: WorkDoneProgressCreateParams?,
        ): CompletableFuture<Void> = CompletableFuture.completedFuture(null)

        override fun notifyProgress(params: ProgressParams?) {}

        override fun logTrace(params: LogTraceParams?) {}

        override fun showDocument(
            params: ShowDocumentParams?,
        ): CompletableFuture<ShowDocumentResult> =
            CompletableFuture.completedFuture(ShowDocumentResult(true))

        override fun refreshSemanticTokens(): CompletableFuture<Void> =
            CompletableFuture.completedFuture(null)

        override fun refreshCodeLenses(): CompletableFuture<Void> =
            CompletableFuture.completedFuture(null)

        override fun refreshInlayHints(): CompletableFuture<Void> =
            CompletableFuture.completedFuture(null)

        override fun refreshInlineValues(): CompletableFuture<Void> =
            CompletableFuture.completedFuture(null)

        override fun refreshDiagnostics(): CompletableFuture<Void> =
            CompletableFuture.completedFuture(null)
    }
}
