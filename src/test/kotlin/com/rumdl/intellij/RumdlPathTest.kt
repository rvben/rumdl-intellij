package com.rumdl.intellij

import com.intellij.openapi.util.SystemInfo
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RumdlPathTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val executableName = if (SystemInfo.isWindows) "rumdl.exe" else "rumdl"

    private fun executable(directory: File): File = File(directory, executableName).apply {
        writeText("")
        assertTrue(setExecutable(true))
    }

    @Test
    fun `missing or empty PATH returns no executable`() {
        assertNull(Rumdl.findInSystemPath(null))
        assertNull(Rumdl.findInSystemPath(""))
    }

    @Test
    fun `first executable wins in PATH order`() {
        val first = executable(temporaryFolder.newFolder("first"))
        val second = executable(temporaryFolder.newFolder("second"))
        assertEquals(first, Rumdl.findInSystemPath(first.parent + File.pathSeparator + second.parent))
        assertEquals(second, Rumdl.findInSystemPath(second.parent + File.pathSeparator + first.parent))
    }

    @Test
    fun `directory names containing spaces are preserved`() {
        val expected = executable(temporaryFolder.newFolder("directory with spaces"))
        assertEquals(expected, Rumdl.findInSystemPath(expected.parent))
    }

    @Test
    fun `directories and missing candidates are skipped`() {
        val directory = temporaryFolder.newFolder("directory")
        assertTrue(File(directory, executableName).mkdir())
        val missing = temporaryFolder.newFolder("missing")
        val expected = executable(temporaryFolder.newFolder("valid"))
        val path = listOf(directory.path, missing.path, expected.parent).joinToString(File.pathSeparator)
        assertEquals(expected, Rumdl.findInSystemPath(path))
    }

    @Test
    fun `non-executable files are skipped`() {
        assumeFalse("Windows executable permissions differ from POSIX", SystemInfo.isWindows)
        val blocked = executable(temporaryFolder.newFolder("blocked"))
        assertTrue(blocked.setExecutable(false, false))
        val expected = executable(temporaryFolder.newFolder("valid"))
        assertEquals(expected, Rumdl.findInSystemPath(blocked.parent + File.pathSeparator + expected.parent))
    }

    @Test
    fun `relative PATH directories are ignored even when they contain rumdl`() {
        val candidate = executable(temporaryFolder.newFolder("relative"))
        val relative = candidate.parentFile.toPath().toAbsolutePath()
            .let { File("").absoluteFile.toPath().relativize(it).toString() }
        assertFalse(File(relative).isAbsolute)
        assertTrue(File(relative, executableName).isFile)
        assertNull(Rumdl.findInSystemPath(relative))
    }
}
