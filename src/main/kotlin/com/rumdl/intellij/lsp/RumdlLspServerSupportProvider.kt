@file:Suppress("UnstableApiUsage")

package com.rumdl.intellij.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.LspServerSupportProvider.LspServerStarter
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.intellij.platform.lsp.api.customization.LspCustomization
import com.intellij.platform.lsp.api.customization.LspFormattingSupport
import com.rumdl.intellij.Rumdl
import com.rumdl.intellij.RumdlConfigService
import com.rumdl.intellij.RumdlNotifications

private val MARKDOWN_EXTENSIONS = setOf("md", "markdown", "mdown", "mkd")

internal fun isMarkdownFile(file: VirtualFile): Boolean =
    file.extension?.lowercase() in MARKDOWN_EXTENSIONS

/**
 * Provides LSP server support for Markdown files using rumdl.
 */
class RumdlLspServerSupportProvider : LspServerSupportProvider {

    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerStarter
    ) {
        if (!RumdlConfigService.getInstance(project).state.enableLsp) return
        if (!isMarkdownFile(file)) return

        if (Rumdl.detectExecutable(project) == null) {
            RumdlNotifications.notifyRumdlNotFound(project)
            return
        }

        serverStarter.ensureServerStarted(RumdlLspServerDescriptor(project))
    }
}

/**
 * Descriptor for the rumdl LSP server.
 */
class RumdlLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "rumdl") {

    override fun isSupportedFile(file: VirtualFile): Boolean = isMarkdownFile(file)

    override fun createCommandLine(): GeneralCommandLine {
        val workingDir = project.basePath?.let { java.io.File(it) }
        return Rumdl.createLspCommandLine(project, workingDir)
            ?: throw IllegalStateException("rumdl executable not found")
    }

    // Routes "Reformat Code" through rumdl's LSP textDocument/formatting; without this
    // override the IDE has no Markdown formatter and the action silently does nothing.
    //
    // rumdl is the authoritative Markdown formatter: this descriptor only ever serves
    // Markdown, and the rumdl server always advertises documentFormattingProvider. We
    // therefore claim exclusivity for every Markdown file unconditionally. We must NOT
    // gate on serverExplicitlyWantsToFormatThisFile: that flag can be false before LSP
    // capability negotiation has settled, and the IDE's only fallback is a native
    // Markdown formatter that does nothing useful (no lint fixes). Gating there makes
    // "Reformat Code" a silent no-op - exactly the regression behind issue #2.
    override val lspCustomization: LspCustomization = object : LspCustomization() {
        override val formattingCustomizer: LspFormattingSupport = object : LspFormattingSupport() {
            override fun shouldFormatThisFileExclusivelyByServer(
                file: VirtualFile,
                ideCanFormatThisFileItself: Boolean,
                serverExplicitlyWantsToFormatThisFile: Boolean,
            ): Boolean = isMarkdownFile(file)
        }
    }
}
