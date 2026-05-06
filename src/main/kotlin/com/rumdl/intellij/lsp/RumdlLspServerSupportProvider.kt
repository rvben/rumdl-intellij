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
    override val lspCustomization: LspCustomization = object : LspCustomization() {
        override val formattingCustomizer: LspFormattingSupport = object : LspFormattingSupport() {
            override fun shouldFormatThisFileExclusivelyByServer(
                file: VirtualFile,
                ideCanFormatThisFileItself: Boolean,
                serverExplicitlyWantsToFormatThisFile: Boolean,
            ): Boolean = serverExplicitlyWantsToFormatThisFile && isMarkdownFile(file)
        }
    }
}
