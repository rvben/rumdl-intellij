package com.rumdl.intellij

import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener

/**
 * Listener for project lifecycle events.
 * Ensures proper cleanup of LSP server when project closes.
 */
class RumdlProjectListener : ProjectManagerListener {

    override fun projectClosing(project: Project) {
        RumdlLspManager.getInstance(project).stop()
    }
}
