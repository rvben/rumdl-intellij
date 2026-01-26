@file:Suppress("UnstableApiUsage")

package com.rumdl.intellij

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.api.LspServerManager
import com.rumdl.intellij.lsp.RumdlLspServerSupportProvider

/**
 * Manages the rumdl LSP server lifecycle.
 */
@Service(Service.Level.PROJECT)
class RumdlLspManager(private val project: Project) {

    private val lspServerManager: LspServerManager?
        get() = try {
            LspServerManager.getInstance(project)
        } catch (e: Exception) {
            null
        }

    /**
     * Starts the LSP server if not already running.
     */
    fun start() {
        lspServerManager?.stopAndRestartIfNeeded(RumdlLspServerSupportProvider::class.java)
    }

    /**
     * Stops the LSP server.
     */
    fun stop() {
        lspServerManager?.stopServers(RumdlLspServerSupportProvider::class.java)
    }

    /**
     * Restarts the LSP server.
     */
    fun restart() {
        lspServerManager?.stopAndRestartIfNeeded(RumdlLspServerSupportProvider::class.java)
    }

    companion object {
        fun getInstance(project: Project): RumdlLspManager {
            return project.service()
        }
    }
}
