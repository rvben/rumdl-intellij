package com.rumdl.intellij.python

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.python.sdk.pythonSdk
import java.io.File

/**
 * Service for detecting rumdl in Python virtualenvs.
 * This service is only loaded when the Python plugin is available.
 */
@Service(Service.Level.PROJECT)
class RumdlPythonService(private val project: Project) {

    /**
     * Gets the preferred Python SDK for the project.
     * Tries project-level SDK first, then falls back to module-level SDKs.
     */
    private val preferredPythonSdk: Sdk?
        get() = try {
            project.pythonSdk ?: ModuleManager.getInstance(project).modules.asSequence()
                .mapNotNull { it.pythonSdk }.firstOrNull()
        } catch (_: NoClassDefFoundError) {
            null
        }

    /**
     * Finds rumdl executable in the project's configured Python SDK.
     */
    fun findRumdlInSdk(): File? {
        val sdk = preferredPythonSdk ?: return null
        val homePath = sdk.homePath ?: return null

        val pythonFile = File(homePath)
        val sdkHome = pythonFile.parentFile ?: return null
        val binDir = if (SystemInfo.isWindows) {
            // On Windows, scripts are in Scripts/ directory
            sdkHome.resolve("Scripts")
        } else {
            // On Unix, scripts are in bin/ directory (same as python)
            sdkHome
        }

        val executableName = if (SystemInfo.isWindows) "rumdl.exe" else "rumdl"
        val rumdlFile = binDir.resolve(executableName)

        return if (rumdlFile.exists() && rumdlFile.canExecute()) {
            rumdlFile
        } else {
            null
        }
    }
}
