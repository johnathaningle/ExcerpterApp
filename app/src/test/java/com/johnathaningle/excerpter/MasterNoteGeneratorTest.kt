package com.johnathaningle.excerpter

import com.johnathaningle.excerpter.data.model.Annotation
import com.johnathaningle.excerpter.util.MasterNoteGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MasterNoteGeneratorTest {

    private fun ann(id: Long, page: Int, y: Float, text: String, note: String = "") = Annotation(
        id = id,
        pdfUri = "u",
        pageNumber = page,
        startX = 0f,
        startY = y,
        endX = 0.5f,
        endY = y,
        color = 0,
        text = text,
        note = note
    )

    @Test
    fun chunkAnnotations_sortsByReadingOrder() {
        val anns = listOf(
            ann(1, page = 2, y = 0.5f, text = "later page"),
            ann(2, page = 1, y = 0.2f, text = "first on page"),
            ann(3, page = 1, y = 0.9f, text = "last on page"),
        )
        val chunks = MasterNoteGenerator.chunkAnnotations(anns)
        assertEquals(1, chunks.size)
        assertEquals(listOf(2L, 3L, 1L), chunks.first().map { it.id })
    }

    @Test
    fun chunkAnnotations_respectsMaxHighlightsPerChunk() {
        val anns = (1L..60L).map { ann(it, page = 1, y = 0f, text = "x") }
        val chunks = MasterNoteGenerator.chunkAnnotations(anns)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.first().size <= 25)
    }

    @Test
    fun chunkAnnotations_skipsBlankHighlights() {
        val anns = listOf(
            ann(1, page = 1, y = 0.1f, text = "usable"),
            ann(2, page = 1, y = 0.2f, text = "", note = "has a note"),
            ann(3, page = 1, y = 0.3f, text = "", note = ""),
        )
        val chunks = MasterNoteGenerator.chunkAnnotations(anns)
        assertEquals(1, chunks.size)
        assertEquals(listOf(1L, 2L), chunks.first().map { it.id })
    }

    @Test
    fun parseSections_extractsHeadingsSourcesAndAnchors() {
        val md = """
            # My Title

            ## Alpha section

            Some synthesis text.

            **Related:** [Earlier](#sec-10)

            **Sources:**
            - [Page 3 • abc](#sec-10)
            - [Page 3 • def](#sec-11)

            ## Beta section

            More text.

            **Sources:**
            - [Page 5 • ghi](#sec-20)
        """.trimIndent()

        val sections = MasterNoteGenerator.parseSections(md)
        assertEquals(2, sections.size)

        val alpha = sections[0]
        assertEquals("Alpha section", alpha.title)
        assertEquals(listOf(10L, 11L), alpha.sourceIds)
        assertEquals(10L, alpha.anchorId)
        assertTrue(alpha.body.contains("Some synthesis text"))
        assertTrue(alpha.body.contains("Related"))

        val beta = sections[1]
        assertEquals("Beta section", beta.title)
        assertEquals(listOf(20L), beta.sourceIds)
        assertEquals(20L, beta.anchorId)
    }
}
