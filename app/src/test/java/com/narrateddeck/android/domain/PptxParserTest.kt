package com.narrateddeck.android.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PptxParserTest {

    @Test
    fun parse_sampleFixture_extractsSlidesAndNotes() {
        val stream = javaClass.classLoader!!
            .getResourceAsStream("sample_deck.pptx")
            ?: error("sample_deck.pptx missing from test resources")

        stream.use {
            val slides = PptxParser.parse(it)
            assertEquals(2, slides.size)
            assertEquals(0, slides[0].index)
            assertEquals("Welcome", slides[0].titleHint)
            assertTrue(slides[0].notes.contains("Hello and welcome"))
            assertEquals(1, slides[1].index)
            assertEquals("Agenda", slides[1].titleHint)
            assertTrue(slides[1].notes.contains("import, review, and export"))
        }
    }

    @Test
    fun parse_generatedInMemory_matchesBySlideNumber() {
        val bytes = buildMinimalPptx(
            slides = mapOf(
                1 to slideXml("Alpha"),
                3 to slideXml("Gamma"),
            ),
            notes = mapOf(
                1 to notesXml("Note for alpha"),
                3 to notesXml("Note for gamma"),
            ),
        )
        val slides = PptxParser.parse(ByteArrayInputStream(bytes))
        assertEquals(2, slides.size)
        assertEquals(0, slides[0].index) // slide1 → index 0
        assertEquals("Alpha", slides[0].titleHint)
        assertEquals("Note for alpha", slides[0].notes)
        assertEquals(2, slides[1].index) // slide3 → index 2
        assertEquals("Gamma", slides[1].titleHint)
        assertEquals("Note for gamma", slides[1].notes)
    }

    @Test
    fun parse_emptyZip_throws() {
        val empty = ByteArrayOutputStream().also { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("readme.txt"))
                zos.write("not a pptx".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()

        try {
            PptxParser.parse(ByteArrayInputStream(empty))
            throw AssertionError("Expected PptxParseException")
        } catch (e: PptxParseException) {
            assertTrue(e.message!!.contains("No slides"))
        }
    }

    @Test
    fun extractTitleHint_readsFirstAText() {
        val xml = slideXml("First Title")
        assertEquals("First Title", PptxParser.extractTitleHint(xml))
    }

    @Test
    fun extractNotesText_joinsRuns() {
        val xml = """
            <?xml version="1.0"?>
            <p:notes xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                     xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
              <a:t>Line one</a:t>
              <a:t>Line two</a:t>
            </p:notes>
        """.trimIndent()
        val notes = PptxParser.extractNotesText(xml)
        assertTrue(notes.contains("Line one"))
        assertTrue(notes.contains("Line two"))
    }

    private fun buildMinimalPptx(
        slides: Map<Int, String>,
        notes: Map<Int, String>,
    ): ByteArray {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            slides.forEach { (n, xml) ->
                zos.putNextEntry(ZipEntry("ppt/slides/slide$n.xml"))
                zos.write(xml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            notes.forEach { (n, xml) ->
                zos.putNextEntry(ZipEntry("ppt/notesSlides/notesSlide$n.xml"))
                zos.write(xml.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
        }
        return baos.toByteArray()
    }

    private fun slideXml(title: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <p:sld xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
               xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
          <p:cSld><p:spTree>
            <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
            <p:grpSpPr/>
            <p:sp><p:txBody><a:p><a:r><a:t>$title</a:t></a:r></a:p></p:txBody></p:sp>
          </p:spTree></p:cSld>
        </p:sld>
    """.trimIndent()

    private fun notesXml(body: String): String = """
        <?xml version="1.0" encoding="UTF-8"?>
        <p:notes xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main"
                 xmlns:p="http://schemas.openxmlformats.org/presentationml/2006/main">
          <p:cSld><p:spTree>
            <p:nvGrpSpPr><p:cNvPr id="1" name=""/><p:cNvGrpSpPr/><p:nvPr/></p:nvGrpSpPr>
            <p:grpSpPr/>
            <p:sp><p:txBody><a:p><a:r><a:t>$body</a:t></a:r></a:p></p:txBody></p:sp>
          </p:spTree></p:cSld>
        </p:notes>
    """.trimIndent()
}
