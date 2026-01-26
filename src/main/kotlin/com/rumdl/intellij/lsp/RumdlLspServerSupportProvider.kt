@file:Suppress("UnstableApiUsage")

package com.rumdl.intellij.lsp

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.api.LspServerSupportProvider
import com.intellij.platform.lsp.api.LspServerSupportProvider.LspServerStarter
import com.intellij.platform.lsp.api.ProjectWideLspServerDescriptor
import com.rumdl.intellij.Rumdl
import com.rumdl.intellij.RumdlConfigService

/**
 * Provides LSP server support for Markdown files using rumdl.
 */
class RumdlLspServerSupportProvider : LspServerSupportProvider {

    override fun fileOpened(
        project: Project,
        file: VirtualFile,
        serverStarter: LspServerStarter
    ) {
        // Check if LSP is enabled
        val configService = RumdlConfigService.getInstance(project)
        if (!configService.state.enableLsp) {
            return
        }

        // Only handle Markdown files
        if (!isMarkdownFile(file)) {
            return
        }

        // Check if rumdl is available
        if (Rumdl.detectExecutable(project) == null) {
            return
        }

        serverStarter.ensureServerStarted(RumdlLspServerDescriptor(project))
    }

    private fun isMarkdownFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase()
        return extension == "md" || extension == "markdown" || extension == "mdown" || extension == "mkd"
    }
}

/**
 * Descriptor for the rumdl LSP server.
 */
class RumdlLspServerDescriptor(project: Project) : ProjectWideLspServerDescriptor(project, "rumdl") {

    override fun isSupportedFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase()
        return extension == "md" || extension == "markdown" || extension == "mdown" || extension == "mkd"
    }

    override fun createCommandLine(): GeneralCommandLine {
        val workingDir = project.basePath?.let { java.io.File(it) }
        return Rumdl.createLspCommandLine(project, workingDir)
            ?: throw IllegalStateException("rumdl executable not found")
    }
}
