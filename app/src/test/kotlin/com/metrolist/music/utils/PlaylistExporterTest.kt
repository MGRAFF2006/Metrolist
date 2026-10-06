package com.metrolist.music.utils

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlaylistExporterTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `export filenames remain single components with their extensions`() {
        val titles =
            mapOf(
                "AC/DC" to "AC_DC",
                "AC\\DC" to "AC_DC",
                "../Sibling" to ".._Sibling",
                "..\\Sibling" to ".._Sibling",
                "/absolute/path" to "_absolute_path",
                "nul\u0000name" to "nul_name",
                "" to "Export",
                " " to "Export",
                "." to "Export",
                ".." to "Export",
                "..." to "Export",
                "My Playlist" to "My Playlist",
                "My.Playlist" to "My.Playlist",
                "Música 日本語 🎵" to "Música 日本語 🎵",
            )

        titles.forEach { (title, expected) ->
            listOf("csv", "m3u").forEach { extension ->
                val exportDir = File(temporaryFolder.newFolder(), "MetrolistExports")
                val file = createExportFile(exportDir, "$title.$extension")

                assertEquals("$expected.$extension", file.name)
                assertEquals(exportDir.canonicalFile, file.canonicalFile.parentFile)
                assertTrue(file.isFile)
            }
        }
    }

    @Test
    fun `repeated exports reserve unique files without overwriting content`() {
        val exportDir = File(temporaryFolder.newFolder(), "MetrolistExports")
        val first = createExportFile(exportDir, "AC/DC.csv")
        first.writeText("original export")

        assertEquals("AC_DC_1.csv", createExportFile(exportDir, "AC/DC.csv").name)
        assertEquals("AC_DC_2.csv", createExportFile(exportDir, "AC\\DC.csv").name)
        assertEquals("original export", first.readText())
        assertEquals(3, exportDir.listFiles()!!.size)
    }
}
