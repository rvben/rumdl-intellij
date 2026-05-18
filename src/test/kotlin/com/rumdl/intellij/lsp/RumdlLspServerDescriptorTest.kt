@file:Suppress("UnstableApiUsage")

package com.rumdl.intellij.lsp

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Tests for [RumdlLspServerDescriptor], focused on the IDE integration points:
 * supported-file detection and the "Reformat Code" (LSP formatting) hook-up.
 */
class RumdlLspServerDescriptorTest : BasePlatformTestCase() {

    private lateinit var descriptor: RumdlLspServerDescriptor

    override fun setUp() {
        super.setUp()
        descriptor = RumdlLspServerDescriptor(project)
    }

    fun `test isMarkdownFile accepts all canonical extensions`() {
        for (ext in listOf("md", "markdown", "mdown", "mkd")) {
            assertTrue(".$ext should be recognised as Markdown", isMarkdownFile(file("README.$ext")))
        }
    }

    fun `test isMarkdownFile is case-insensitive`() {
        assertTrue(isMarkdownFile(file("README.MD")))
        assertTrue(isMarkdownFile(file("notes.Markdown")))
        assertTrue(isMarkdownFile(file("doc.MkD")))
    }

    fun `test isMarkdownFile rejects unrelated extensions and missing extensions`() {
        for (name in listOf("notes.txt", "config.yaml", "script.sh", "no-extension", "image.png", "")) {
            assertFalse("$name should not be recognised as Markdown", isMarkdownFile(file(name)))
        }
    }

    fun `test isSupportedFile mirrors isMarkdownFile`() {
        assertTrue(descriptor.isSupportedFile(file("README.md")))
        assertTrue(descriptor.isSupportedFile(file("README.MD")))
        assertFalse(descriptor.isSupportedFile(file("notes.txt")))
    }

    fun `test lspCustomization wires Reformat Code through the rumdl server`() {
        // Without an LspFormattingSupport on the descriptor's customization, IntelliJ's
        // "Reformat Code" action (Ctrl+Alt+L / Cmd+Opt+L) silently ignores rumdl. See issue #2.
        val customizer = descriptor.lspCustomization.formattingCustomizer
        assertTrue(
            "formattingCustomizer must be an LspFormattingSupport so reformat-code reaches the server, " +
                "but was: ${customizer::class.qualifiedName}",
            customizer is LspFormattingSupport
        )
    }

    fun `test markdown files are always formatted exclusively by the rumdl server`() {
        val support = descriptor.formattingSupport()

        // rumdl is the authoritative Markdown formatter, so exclusivity must hold
        // for every combination of the IDE/server hint flags - none of them should
        // be able to divert a Markdown file away from rumdl.
        for (ideCanFormat in listOf(true, false)) {
            for (serverWants in listOf(true, false)) {
                assertTrue(
                    "rumdl must claim exclusive formatting for Markdown " +
                        "(ideCanFormat=$ideCanFormat, serverWants=$serverWants)",
                    support.shouldFormatThisFileExclusivelyByServer(
                        file("doc.md"),
                        ideCanFormatThisFileItself = ideCanFormat,
                        serverExplicitlyWantsToFormatThisFile = serverWants,
                    )
                )
            }
        }
    }

    fun `test markdown is still formatted before LSP capability negotiation settles`() {
        // Regression guard for issue #2: the IDE reports it can format Markdown
        // itself and has not yet observed the server's formatting capability
        // (serverWants=false). Gating on that flag here made "Reformat Code" a
        // silent no-op. rumdl always advertises documentFormattingProvider and
        // the IDE has no useful Markdown fallback, so exclusivity must still hold.
        val support = descriptor.formattingSupport()

        assertTrue(
            "rumdl must remain the exclusive Markdown formatter even when the IDE " +
                "has not yet seen the server's formatting capability (issue #2)",
            support.shouldFormatThisFileExclusivelyByServer(
                file("doc.md"),
                ideCanFormatThisFileItself = true,
                serverExplicitlyWantsToFormatThisFile = false,
            )
        )
    }

    fun `test non-markdown files are never formatted by rumdl`() {
        val support = descriptor.formattingSupport()

        for (ideCanFormat in listOf(true, false)) {
            for (serverWants in listOf(true, false)) {
                assertFalse(
                    "rumdl must not claim exclusivity over non-Markdown files " +
                        "(ideCanFormat=$ideCanFormat, serverWants=$serverWants)",
                    support.shouldFormatThisFileExclusivelyByServer(
                        file("script.sh"),
                        ideCanFormatThisFileItself = ideCanFormat,
                        serverExplicitlyWantsToFormatThisFile = serverWants,
                    )
                )
            }
        }
    }

    fun `test all markdown extensions are formatted by rumdl`() {
        val support = descriptor.formattingSupport()
        for (ext in listOf("md", "markdown", "mdown", "mkd", "MD", "Markdown")) {
            assertTrue(
                "rumdl should format .$ext files",
                support.shouldFormatThisFileExclusivelyByServer(
                    file("doc.$ext"),
                    ideCanFormatThisFileItself = true,
                    serverExplicitlyWantsToFormatThisFile = false,
                )
            )
        }
    }

    private fun RumdlLspServerDescriptor.formattingSupport(): LspFormattingSupport =
        lspCustomization.formattingCustomizer as LspFormattingSupport

    private fun file(name: String): VirtualFile = LightVirtualFile(name, "")
}
