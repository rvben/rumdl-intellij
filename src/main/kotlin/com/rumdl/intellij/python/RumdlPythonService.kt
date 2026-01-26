package com.rumdl.intellij.python

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.jetbrains.python.sdk.PythonSdkUtil
import java.io.File

/**
 * Service for detecting rumdl in Python virtualenvs.
 * This service is only loaded when the Python plugin is available.
 */
@Service(Service.Level.PROJECT)
class RumdlPythonService(private val project: Project) {

    /**
     * Finds rumdl executable in the project's configured Python SDK.
     */
    fun findRumdlInSdk(): File? {
        val sdk = PythonSdkUtil.findPythonSdk(project) ?: return null
        val homePath = sdk.homePath ?: return null

        val sdkHome = File(homePath).parentFile ?: return null
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
