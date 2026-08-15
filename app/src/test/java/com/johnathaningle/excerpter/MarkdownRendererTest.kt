package com.johnathaningle.excerpter

import com.johnathaningle.excerpter.util.MarkdownRenderer
import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownRendererTest {

    @Test
    fun inline_parsesBoldItalicCodeAndLinks() {
        val spans = MarkdownRenderer.inlineParse(
            "Some **bold** and *italic* and `code` and [text](#sec-10) here."
        )
        assertEquals(
            listOf(
                MarkdownRenderer.InlineSpan.TextSpan("Some "),
                MarkdownRenderer.InlineSpan.Styled(MarkdownRenderer.InlineStyle.BOLD, "bold"),
                MarkdownRenderer.InlineSpan.TextSpan(" and "),
                MarkdownRenderer.InlineSpan.Styled(MarkdownRenderer.InlineStyle.ITALIC, "italic"),
                MarkdownRenderer.InlineSpan.TextSpan(" and "),
                MarkdownRenderer.InlineSpan.Styled(MarkdownRenderer.InlineStyle.CODE, "code"),
                MarkdownRenderer.InlineSpan.TextSpan(" and "),
                MarkdownRenderer.InlineSpan.Link("text", "#sec-10"),
                MarkdownRenderer.InlineSpan.TextSpan(" here."),
            ),
            spans
        )
    }

    @Test
    fun parse_handlesHeadingsBulletsAndDivider() {
        val markdown = """
            # Title

            ## Alpha

            Body **bold**.

            **Sources:**
            - [Page 3 • abc](#sec-10)
            - [Page 3 • def](#sec-11)

            ---

            ## Beta
        """.trimIndent()

        val blocks = MarkdownRenderer.parse(markdown)
        assertEquals(
            listOf(
                MarkdownRenderer.Block.Heading(1, listOf(MarkdownRenderer.InlineSpan.TextSpan("Title"))),
                MarkdownRenderer.Block.Heading(2, listOf(MarkdownRenderer.InlineSpan.TextSpan("Alpha"))),
                MarkdownRenderer.Block.Paragraph(
                    listOf(
                        MarkdownRenderer.InlineSpan.TextSpan("Body "),
                        MarkdownRenderer.InlineSpan.Styled(MarkdownRenderer.InlineStyle.BOLD, "bold"),
                        MarkdownRenderer.InlineSpan.TextSpan("."),
                    )
                ),
                MarkdownRenderer.Block.Paragraph(
                    listOf(
                        MarkdownRenderer.InlineSpan.Styled(MarkdownRenderer.InlineStyle.BOLD, "Sources:"),
                    )
                ),
                MarkdownRenderer.Block.Bullet(
                    listOf(MarkdownRenderer.InlineSpan.Link("Page 3 • abc", "#sec-10"))
                ),
                MarkdownRenderer.Block.Bullet(
                    listOf(MarkdownRenderer.InlineSpan.Link("Page 3 • def", "#sec-11"))
                ),
                MarkdownRenderer.Block.Divider,
                MarkdownRenderer.Block.Heading(2, listOf(MarkdownRenderer.InlineSpan.TextSpan("Beta"))),
            ),
            blocks
        )
    }
}
