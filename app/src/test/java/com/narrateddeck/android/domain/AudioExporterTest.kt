package com.narrateddeck.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipFile

class AudioExporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun export_includesScriptsAndAudio() {
        val audio = tmp.newFile("slide_001.wav").apply {
            writeBytes(byteArrayOf(0x52, 0x49, 0x46, 0x46)) // "RIFF" stub
        }
        val slides = listOf(
            SlideNote(0, "Welcome", "Hello world", audioFilePath = audio.absolutePath),
            SlideNote(1, "Empty", "", audioFilePath = null),
        )
        val outDir = tmp.newFolder("out")
        val result = AudioExporter.export(slides, outDir)

        assertTrue(result.zipFile.exists())
        assertEquals(2, result.slideCount)
        assertEquals(1, result.audioCount)

        ZipFile(result.zipFile).use { zip ->
            val names = zip.entries().asSequence().map { it.name }.toSet()
            assertTrue(names.contains("scripts.txt"))
            assertTrue(names.contains("slide_001.wav"))
            val scripts = zip.getInputStream(zip.getEntry("scripts.txt"))
                .bufferedReader().readText()
            assertTrue(scripts.contains("Hello world"))
            assertTrue(scripts.contains("Slide 2"))
        }
    }
}
