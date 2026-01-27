package com.rumdl.intellij

import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.PathEnvironmentVariableUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import java.io.File
import java.nio.file.Path

/**
 * Utility object for rumdl executable detection and command line building.
 */
object Rumdl {
    private const val EXECUTABLE_NAME = "rumdl"

    /**
     * Detects the rumdl executable path with the following priority:
     * 1. Project-configured path (from settings)
     * 2. Python virtualenv (if Python plugin is available)
     * 3. System PATH
     * 4. Common installation locations (~/.cargo/bin, Homebrew)
     */
    fun detectExecutable(project: Project): File? {
        // 1. Check project-configured path
        val configService = RumdlConfigService.getInstance(project)
        configService.state.rumdlPath?.let { path ->
            val file = File(path)
            if (file.exists() && file.canExecute()) {
                return file
            }
        }

        // 2. Try Python virtualenv detection (handled by RumdlPythonService if available)
        findInPythonEnvironment(project)?.let { return it }

        // 3. Search system PATH
        findInSystemPath()?.let { return it }

        // 4. Check common installation locations
        findInCommonLocations()?.let { return it }

        return null
    }

    /**
     * Attempts to find rumdl in the project's Python environment.
     * This is handled by RumdlPythonService when the Python plugin is available.
     */
    private fun findInPythonEnvironment(project: Project): File? {
        return try {
            val pythonService = project.getService(
                Class.forName("com.rumdl.intellij.python.RumdlPythonService")
            )
            val method = pythonService.javaClass.getMethod("findRumdlInSdk")
            method.invoke(pythonService) as? File
        } catch (e: Exception) {
            // Python plugin not available or service not found
            null
        }
    }

    /**
     * Searches for rumdl in the system PATH.
     */
    private fun findInSystemPath(): File? {
        return PathEnvironmentVariableUtil.findInPath(executableName())
    }

    /**
     * Checks common installation locations.
     */
    private fun findInCommonLocations(): File? {
        val locations = mutableListOf<Path>()

        // Cargo bin directory
        System.getenv("CARGO_HOME")?.let {
            locations.add(Path.of(it, "bin", executableName()))
        }
        System.getProperty("user.home")?.let {
            locations.add(Path.of(it, ".cargo", "bin", executableName()))
        }

        // Homebrew on macOS
        if (SystemInfo.isMac) {
            locations.add(Path.of("/opt/homebrew/bin", executableName()))
            locations.add(Path.of("/usr/local/bin", executableName()))
        }

        // Linux common paths
        if (SystemInfo.isLinux) {
            locations.add(Path.of("/usr/local/bin", executableName()))
            System.getProperty("user.home")?.let {
                locations.add(Path.of(it, ".local", "bin", executableName()))
            }
        }

        return locations
            .map { it.toFile() }
            .firstOrNull { it.exists() && it.canExecute() }
    }

    /**
     * Returns the platform-specific executable name.
     */
    private fun executableName(): String {
        return if (SystemInfo.isWindows) "$EXECUTABLE_NAME.exe" else EXECUTABLE_NAME
    }

    /**
     * Creates a GeneralCommandLine for running rumdl with the given arguments.
     */
    fun createCommandLine(
        project: Project,
        workingDir: File?,
        vararg args: String
    ): GeneralCommandLine? {
        val executable = detectExecutable(project) ?: return null

        return GeneralCommandLine(executable.absolutePath)
            .withParameters(*args)
            .apply {
                workingDir?.let { withWorkDirectory(it) }
                withCharset(Charsets.UTF_8)
            }
    }

    /**
     * Creates a command line for starting the LSP server.
     */
    fun createLspCommandLine(project: Project, workingDir: File?): GeneralCommandLine? {
        return createCommandLine(project, workingDir, "server")
    }
}
