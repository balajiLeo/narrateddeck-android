package com.narrateddeck.android.domain

import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node

/**
 * Pure-Kotlin PPTX (Open XML) parser using java.util.zip.
 *
 * Reads:
 * - ppt/slides/slideN.xml — title-ish text from first shape runs
 * - ppt/notesSlides/notesSlideN.xml — speaker notes text
 *
 * Matching notesSlideN to slideN by number (standard PowerPoint packaging).
 * Does not expand themes, masters, or embedded media.
 */
object PptxParser {

    private val SLIDE_PATH = Regex("""^ppt/slides/slide(\d+)\.xml$""", RegexOption.IGNORE_CASE)
    private val NOTES_PATH = Regex("""^ppt/notesSlides/notesSlide(\d+)\.xml$""", RegexOption.IGNORE_CASE)

    fun parse(input: InputStream): List<SlideNote> {
        val slideTexts = mutableMapOf<Int, String>()
        val notesTexts = mutableMapOf<Int, String>()

        ZipInputStream(input.buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val name = entry.name.replace('\\', '/')
                when {
                    !entry.isDirectory && SLIDE_PATH.matches(name) -> {
                        val n = SLIDE_PATH.matchEntire(name)!!.groupValues[1].toInt()
                        val xml = zis.readBytes().toString(Charsets.UTF_8)
                        slideTexts[n] = extractTitleHint(xml)
                    }
                    !entry.isDirectory && NOTES_PATH.matches(name) -> {
                        val n = NOTES_PATH.matchEntire(name)!!.groupValues[1].toInt()
                        val xml = zis.readBytes().toString(Charsets.UTF_8)
                        notesTexts[n] = extractNotesText(xml)
                    }
                    else -> {
                        // drain entry
                        zis.readBytes()
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        val slideNumbers = (slideTexts.keys + notesTexts.keys).toSortedSet()
        if (slideNumbers.isEmpty()) {
            throw PptxParseException("No slides found in PPTX (expected ppt/slides/slideN.xml)")
        }

        // PowerPoint slideN is 1-based; expose 0-based index to the UI.
        return slideNumbers.map { num ->
            SlideNote(
                index = num - 1,
                titleHint = slideTexts[num].orEmpty().trim(),
                notes = notesTexts[num].orEmpty().trim(),
            )
        }
    }

    /**
     * Prefer first non-empty paragraph from a title-ish shape; fall back to first text run.
     */
    internal fun extractTitleHint(slideXml: String): String {
        val texts = extractAllAText(slideXml)
        return texts.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    /**
     * Notes slides include a notes body shape; we concatenate all a:t runs,
     * skipping tiny placeholder-only noise when possible.
     */
    internal fun extractNotesText(notesXml: String): String {
        return extractAllAText(notesXml)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
            .trim()
    }

    private fun extractAllAText(xml: String): List<String> {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                // Harden against XXE for untrusted PPTX XML
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeature("http://xml.org/sax/features/external-general-entities", false)
                setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                isExpandEntityReferences = false
            }
            val doc = factory.newDocumentBuilder()
                .parse(xml.byteInputStream(Charsets.UTF_8))
            val nodes = doc.getElementsByTagNameNS("*", "t")
            buildList {
                for (i in 0 until nodes.length) {
                    val node = nodes.item(i)
                    if (node.nodeType == Node.ELEMENT_NODE) {
                        val text = (node as Element).textContent ?: continue
                        if (text.isNotEmpty()) add(text)
                    }
                }
            }
        } catch (_: Exception) {
            // Regex fallback if DOM fails on exotic XML
            Regex("""<a:t[^>]*>([^<]*)</a:t>""")
                .findAll(xml)
                .map { it.groupValues[1] }
                .toList()
        }
    }
}

class PptxParseException(message: String, cause: Throwable? = null) : Exception(message, cause)
