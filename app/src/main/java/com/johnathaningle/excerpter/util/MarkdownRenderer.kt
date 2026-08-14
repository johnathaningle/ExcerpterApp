package com.johnathaningle.excerpter.util

/**
 * Minimal markdown renderer for the subset Excerpter generates: `#`/`##`/`###`
 * headings, `**bold**`, `*italic*`/`_italic_`, inline `` `code` ``, `[text](target)`
 * links, `-`/`*` bullets, `---` dividers, and plain paragraphs.
 *
 * Returns plain data ([Block]s and [InlineSpan]s) so it is pure JVM and unit-testable;
 * the UI layer converts spans to Compose [androidx.compose.ui.text.AnnotatedString].
 *
 * ponytail: hand-rolled instead of a library because the surface is tiny and app-
 * controlled (the master-note generator emits exactly this subset), and it avoids a
 * dependency that may lag behind the bleeding-edge Compose version this app compiles with.
 */
object MarkdownRenderer {
    private val INLINE_TOKEN = Regex("""(\*\*[^*]+\*\*|\*[^*]+\*|_[^_]+_|`[^`]+`|\[[^\]\n]+\]\([^)\s]+\))""")

    enum class InlineStyle { BOLD, ITALIC, CODE }

    sealed class InlineSpan {
        data class TextSpan(val text: String) : InlineSpan()
        data class Styled(val style: InlineStyle, val text: String) : InlineSpan()
        data class Link(val text: String, val target: String) : InlineSpan()
    }

    sealed class Block {
        data class Heading(val level: Int, val spans: List<InlineSpan>) : Block()
        data class Paragraph(val spans: List<InlineSpan>) : Block()
        data class Bullet(val spans: List<InlineSpan>) : Block()
        object Divider : Block()
    }

    /** Parses a markdown string into blocks. Heading level = number of leading `#`s. */
    fun parse(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraphLines = mutableListOf<String>()
        var bullets: MutableList<String>? = null

        fun flushParagraph() {
            if (paragraphLines.isNotEmpty()) {
                blocks += Block.Paragraph(inlineParse(paragraphLines.joinToString("\n")))
                paragraphLines.clear()
            }
        }

        fun flushBullets() {
            bullets?.forEach { blocks += Block.Bullet(inlineParse(it)) }
            bullets = null
        }

        for (raw in markdown.split('\n')) {
            val trimmed = raw.trim()
            when {
                trimmed.isBlank() -> { flushParagraph(); flushBullets() }

                trimmed.startsWith("### ") || trimmed.startsWith("## ") || trimmed.startsWith("# ") -> {
                    flushParagraph(); flushBullets()
                    val level = trimmed.takeWhile { it == '#' }.length
                    blocks += Block.Heading(level, inlineParse(trimmed.drop(level).trim()))
                }

                trimmed == "---" -> { flushParagraph(); flushBullets(); blocks += Block.Divider }

                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    flushParagraph()
                    if (bullets == null) bullets = mutableListOf()
                    bullets!!.add(trimmed.drop(2))
                }

                else -> { flushBullets(); paragraphLines.add(trimmed) }
            }
        }
        flushParagraph()
        flushBullets()
        return blocks
    }

    /** Parses inline styling within a single line of text. */
    fun inlineParse(text: String): List<InlineSpan> {
        val spans = mutableListOf<InlineSpan>()
        var pos = 0
        for (m in INLINE_TOKEN.findAll(text)) {
            if (m.range.first > pos) {
                spans += InlineSpan.TextSpan(text.substring(pos, m.range.first))
            }
            spans += toSpan(m.value)
            pos = m.range.last + 1
        }
        if (pos < text.length) spans += InlineSpan.TextSpan(text.substring(pos))
        return spans
    }

    private fun toSpan(token: String): InlineSpan = when {
        token.startsWith("**") && token.endsWith("**") && token.length > 4 ->
            InlineSpan.Styled(InlineStyle.BOLD, token.drop(2).dropLast(2))
        token.startsWith("`") && token.endsWith("`") && token.length > 2 ->
            InlineSpan.Styled(InlineStyle.CODE, token.drop(1).dropLast(1))
        (token.startsWith("*") || token.startsWith("_")) && token.length > 2 &&
            token.first() == token.last() ->
            InlineSpan.Styled(InlineStyle.ITALIC, token.drop(1).dropLast(1))
        token.startsWith("[") -> {
            val closeBracket = token.indexOf(']')
            val openParen = token.indexOf('(', closeBracket)
            val closeParen = token.lastIndexOf(')')
            if (closeBracket > 0 && openParen > closeBracket && closeParen > openParen) {
                InlineSpan.Link(token.substring(1, closeBracket), token.substring(openParen + 1, closeParen))
            } else {
                InlineSpan.TextSpan(token)
            }
        }
        else -> InlineSpan.TextSpan(token)
    }
}
