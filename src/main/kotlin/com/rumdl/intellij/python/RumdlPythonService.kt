package com.rumdl.intellij.python

import com.intellij.openapi.components.Service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.openapi.util.SystemInfo
import java.io.File

/**
 * Finds rumdl inside a project's configured Python SDK (virtualenv).
 *
 * Uses reflection to reach the Python plugin's SDK API so the plugin
 * compiles and runs without a build-time dependency on the Python plugin.
 * If the Python plugin isn't installed at runtime, the lookup returns
 * null and the caller falls back to PATH discovery.
 */
@Service(Service.Level.PROJECT)
class RumdlPythonService(private val project: Project) {

    private val preferredPythonSdk: Sdk?
        get() = try {
            val pythonSdkUtilClass = Class.forName("com.jetbrains.python.sdk.PythonSdkUtil")
            val findPythonSdk = pythonSdkUtilClass.getMethod(
                "findPythonSdk",
                com.intellij.openapi.module.Module::class.java,
            )
            ModuleManager.getInstance(project).modules
                .firstNotNullOfOrNull { findPythonSdk.invoke(null, it) as? Sdk }
        } catch (_: ClassNotFoundException) {
            null
        } catch (_: NoSuchMethodException) {
            null
        } catch (_: NoClassDefFoundError) {
            null
        }

    fun findRumdlInSdk(): File? {
        val sdk = preferredPythonSdk ?: return null
        val homePath = sdk.homePath ?: return null

        val pythonFile = File(homePath)
        val sdkHome = pythonFile.parentFile ?: return null
        val binDir = if (SystemInfo.isWindows) {
            sdkHome.resolve("Scripts")
        } else {
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
