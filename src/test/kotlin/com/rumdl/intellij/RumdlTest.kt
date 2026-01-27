package com.rumdl.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class RumdlTest : BasePlatformTestCase() {

    fun `test LSP command uses server subcommand`() {
        // The LSP server command must be "server", not "lsp"
        // This test prevents regression of the bug where "lsp" was used incorrectly
        val commandLine = Rumdl.createLspCommandLine(project, null)

        // If rumdl is not installed, skip the parameter check but verify method doesn't crash
        if (commandLine != null) {
            val parameters = commandLine.parametersList.list
            assertTrue(
                "LSP command should use 'server' subcommand, got: $parameters",
                parameters.contains("server")
            )
            assertFalse(
                "LSP command should not use 'lsp' subcommand",
                parameters.contains("lsp")
            )
        }
    }

    fun `test executable detection does not throw`() {
        // Verify executable detection handles missing rumdl gracefully
        val executable = Rumdl.detectExecutable(project)
        // Should return null or a valid file, never throw
        if (executable != null) {
            assertTrue("Detected executable should exist", executable.exists())
            assertTrue("Detected executable should be executable", executable.canExecute())
        }
    }
}
