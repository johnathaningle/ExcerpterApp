package com.johnathaningle.excerpter.ui.viewer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.johnathaningle.excerpter.util.MarkdownRenderer

/** Renders the app's markdown subset as rich text. [onLink] receives `#sec-...` link targets. */
@Composable
fun MarkdownBody(
    markdown: String,
    onLink: (String) -> Unit
) {
    val blocks = remember(markdown) { MarkdownRenderer.parse(markdown) }
    Column {
        blocks.forEach { block ->
            when (block) {
                is MarkdownRenderer.Block.Heading ->
                    MarkdownText(
                        spans = block.spans,
                        style = headingStyle(block.level),
                        onLink = onLink
                    )

                is MarkdownRenderer.Block.Paragraph ->
                    MarkdownText(
                        spans = block.spans,
                        style = MaterialTheme.typography.bodyMedium,
                        onLink = onLink
                    )

                is MarkdownRenderer.Block.Bullet ->
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text("•  ", style = MaterialTheme.typography.bodyMedium)
                        MarkdownText(
                            spans = block.spans,
                            style = MaterialTheme.typography.bodyMedium,
                            onLink = onLink
                        )
                    }

                MarkdownRenderer.Block.Divider ->
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun headingStyle(level: Int): TextStyle {
    val base = when (level) {
        1 -> MaterialTheme.typography.titleLarge
        2 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    return base.copy(fontWeight = FontWeight.SemiBold)
}

@Composable
private fun MarkdownText(
    spans: List<MarkdownRenderer.InlineSpan>,
    style: TextStyle,
    onLink: (String) -> Unit
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val currentOnLink by rememberUpdatedState(onLink)
    val annotated = remember(spans, linkColor) {
        buildAnnotated(spans, linkColor) { currentOnLink(it) }
    }
    BasicText(text = annotated, style = style)
}

private fun buildAnnotated(
    spans: List<MarkdownRenderer.InlineSpan>,
    linkColor: Color,
    onLink: (String) -> Unit
): AnnotatedString {
    val sb = AnnotatedString.Builder()
    for (span in spans) {
        when (span) {
            is MarkdownRenderer.InlineSpan.TextSpan -> sb.append(span.text)

            is MarkdownRenderer.InlineSpan.Styled -> {
                val spanStyle = when (span.style) {
                    MarkdownRenderer.InlineStyle.BOLD ->
                        SpanStyle(fontWeight = FontWeight.Bold)
                    MarkdownRenderer.InlineStyle.ITALIC ->
                        SpanStyle(fontStyle = FontStyle.Italic)
                    MarkdownRenderer.InlineStyle.CODE ->
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = Color(0xFFECEFF1)
                        )
                }
                sb.withStyle(spanStyle) { sb.append(span.text) }
            }

            is MarkdownRenderer.InlineSpan.Link -> {
                sb.withLink(
                    LinkAnnotation.Clickable(
                        tag = span.target,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = LinkInteractionListener { onLink(span.target) }
                    )
                ) { sb.append(span.text) }
            }
        }
    }
    return sb.toAnnotatedString()
}
