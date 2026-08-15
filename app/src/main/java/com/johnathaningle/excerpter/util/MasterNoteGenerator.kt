package com.johnathaningle.excerpter.util

import android.util.Log
import com.johnathaningle.excerpter.data.model.Annotation

/**
 * Turns a document's highlights into a single markdown "master note":
 * highlights are walked in reading order, chunked to fit the model's context
 * window, and the LLM writes one `## `-headed section per chunk. Sources are
 * appended deterministically so every highlight is reachable via a stable
 * `#sec-<annotationId>` anchor — regeneration never breaks links because the
 * anchors are annotation IDs, not AI-generated names.
 */
object MasterNoteGenerator {
    const val TAG = "MasterNoteGenerator"

    // ponytail: conservative per-chunk char budget (~4 chars/token) that leaves
    // room for the system prompt, prior-section list and 512 output tokens in a
    // ~4k-token window. Bump if the model truncates or quality suggests more context.
    private const val MAX_CHUNK_CHARS = 4000
    private const val MAX_HIGHLIGHTS_PER_CHUNK = 25
    private const val MAX_PRIOR_SECTIONS = 15
    private const val PREVIEW_CHARS = 60
    private const val MAX_SECTION_TITLE_WORDS = 6

    private val SEC_LINK = Regex("""\]\(#sec-(\d+)\)""")

    data class MasterNoteSection(
        val title: String,
        val body: String,
        val anchorId: Long,
        val sourceIds: List<Long>
    )

    /** Splits usable highlights (sorted by reading order) into context-budgeted chunks. */
    fun chunkAnnotations(annotations: List<Annotation>): List<List<Annotation>> {
        val usable = annotations
            .filter { it.note.isNotBlank() || it.text.isNotBlank() }
            .sortedWith(compareBy({ it.pageNumber }, { it.startY }))

        if (usable.isEmpty()) {
            Log.w(TAG, "chunkAnnotations: no usable highlights (blank text/note)")
            return emptyList()
        }

        val chunks = mutableListOf<List<Annotation>>()
        var current = mutableListOf<Annotation>()
        var currentChars = 0
        for (a in usable) {
            val len = a.note.ifBlank { a.text }.length + 12 // page-label overhead
            if (current.isNotEmpty() &&
                (current.size >= MAX_HIGHLIGHTS_PER_CHUNK || currentChars + len > MAX_CHUNK_CHARS)
            ) {
                chunks += current
                current = mutableListOf()
                currentChars = 0
            }
            current += a
            currentChars += len
        }
        if (current.isNotEmpty()) chunks += current

        Log.i(
            TAG,
            "Chunked ${usable.size} highlights into ${chunks.size} chunks " +
                "(budget=${MAX_CHUNK_CHARS} chars, ${MAX_HIGHLIGHTS_PER_CHUNK} max/chunk)"
        )
        chunks.forEachIndexed { i, c ->
            Log.i(
                TAG,
                "  chunk[$i]: ${c.size} highlights, ${c.sumOf { it.note.ifBlank { it.text }.length }} chars, " +
                    "pages ${c.minOf { it.pageNumber } + 1}-${c.maxOf { it.pageNumber } + 1}"
            )
        }
        return chunks
    }

    /**
     * Generates the master note markdown. Calls [onProgress] after each section
     * with (currentSection, totalSections) so the UI can show progress.
     */
    suspend fun generate(
        annotations: List<Annotation>,
        fallbackTitle: String,
        onProgress: (currentSection: Int, totalSections: Int) -> Unit = { _, _ -> }
    ): Result<String> {
        if (!LlmService.isAvailable()) {
            Log.e(TAG, "generate aborted: LLM not initialized")
            return Result.failure(IllegalStateException("AI model is not loaded. Open Settings to configure it."))
        }

        val startMs = System.currentTimeMillis()
        Log.i(TAG, "=== Master note generation start: ${annotations.size} annotations ===")

        val chunks = chunkAnnotations(annotations)
        if (chunks.isEmpty()) {
            Log.w(TAG, "generate aborted: no highlights with text")
            return Result.failure(IllegalStateException("No highlights with text to summarize"))
        }

        // Document title via the existing heading generator; fall back to the file name.
        val firstText = chunks.first()
            .joinToString(" ") { it.note.ifBlank { it.text } }
            .take(400)
        val title = LlmService.generateHeading(firstText).getOrNull()?.trim()?.take(80)
            ?.ifBlank { null } ?: fallbackTitle
        Log.i(TAG, "Document title: '$title'")

        val sb = StringBuilder()
        sb.append("# ").append(title).append("\n\n")

        val priorSections = mutableListOf<Pair<String, Long>>() // (title, anchorId)
        val priorAnchorIds = mutableSetOf<Long>()

        chunks.forEachIndexed { index, chunk ->
            val sectionStart = System.currentTimeMillis()
            Log.i(TAG, "Generating section ${index + 1}/${chunks.size} (${chunk.size} highlights)")
            val prompt = buildSectionPrompt(chunk, priorSections)
            Log.d(TAG, "  prompt chars=${prompt.length}\n$prompt")

            val raw = LlmService.generateSection(prompt).getOrElse { e ->
                Log.e(TAG, "Section ${index + 1} LLM call failed: ${e.message}", e)
                "\n## ${fallbackSectionTitle(chunk)}\n\n_AI summary unavailable (${e.message ?: "model error"})._"
            }
            Log.d(TAG, "  raw output chars=${raw.length}\n$raw")

            val (sectionTitle, sectionBody) = splitSection(raw, chunk)
            val sourceIds = chunk.map { it.id }
            val anchorId = sourceIds.first()

            // Log (but don't rewrite) any links to anchors that don't exist yet,
            // so broken "Related:" references are visible in Logcat under this tag.
            val unknownLinks = SEC_LINK.findAll(sectionBody)
                .mapNotNull { it.groupValues[1].toLongOrNull() }
                .filterNot { it in priorAnchorIds }
                .toList()
            if (unknownLinks.isNotEmpty()) {
                Log.w(TAG, "Section ${index + 1} body references unknown anchors $unknownLinks (leaving as-is)")
            }

            sb.append("## ").append(sectionTitle).append("\n\n")
            sb.append(sectionBody.trim()).append("\n\n")
            sb.append("**Sources:**\n")
            chunk.forEach { a ->
                val preview = a.note.ifBlank { a.text }
                    .replace('\n', ' ')
                    .trim()
                    .take(PREVIEW_CHARS)
                sb.append("- [Page ${a.pageNumber + 1} • $preview](#sec-${a.id})\n")
            }
            sb.append("\n")

            priorSections.add(sectionTitle to anchorId)
            priorAnchorIds.add(anchorId)

            Log.i(
                TAG,
                "Section ${index + 1} done in ${System.currentTimeMillis() - sectionStart}ms, " +
                    "title='$sectionTitle', bodyChars=${sectionBody.length}"
            )
            onProgress(index + 1, chunks.size)
        }

        val result = sb.toString()
        Log.i(
            TAG,
            "=== Master note generation finished in ${System.currentTimeMillis() - startMs}ms, " +
                "${chunks.size} sections, ${result.length} chars ==="
        )
        return Result.success(result)
    }

    /** Parses the generated markdown into sections (headings + body + source anchors). */
    fun parseSections(markdown: String): List<MasterNoteSection> {
        val sections = mutableListOf<MasterNoteSection>()
        var title: String? = null
        val bodyLines = mutableListOf<String>()

        fun flush() {
            val t = title ?: return
            val body = bodyLines.joinToString("\n").trim()
            val ids = SEC_LINK.findAll(body)
                .mapNotNull { it.groupValues[1].toLongOrNull() }
                .distinct()
                .toList()
            if (ids.isNotEmpty()) {
                sections += MasterNoteSection(t, body, ids.first(), ids)
            }
            bodyLines.clear()
        }

        markdown.split('\n').forEach { line ->
            if (line.startsWith("## ")) {
                flush()
                title = line.removePrefix("## ").trim()
            } else {
                title?.let { bodyLines += line }
            }
        }
        flush()
        return sections
    }

    private fun buildSectionPrompt(
        chunk: List<Annotation>,
        priorSections: List<Pair<String, Long>>
    ): String = buildString {
        appendLine("Highlights from a PDF, in reading order. Each numbered item is a passage the reader found important:")
        appendLine()
        chunk.forEachIndexed { i, a ->
            val text = a.note.ifBlank { a.text }.replace('\n', ' ').trim()
            appendLine("${i + 1}. [Page ${a.pageNumber + 1}] $text")
        }
        appendLine()
        if (priorSections.isNotEmpty()) {
            val recent = priorSections.takeLast(MAX_PRIOR_SECTIONS)
            appendLine("Earlier sections already in this note — use their EXACT markdown link text if you reference one:")
            recent.forEach { (t, anchor) -> appendLine("- [$t](#sec-$anchor)") }
            appendLine()
        }
        appendLine("Write exactly ONE markdown section for these highlights:")
        appendLine("- First line: a heading starting with `## ` (at most $MAX_SECTION_TITLE_WORDS words, no trailing punctuation).")
        appendLine("- Then 3-6 sentences synthesizing the highlights into connected knowledge. Do not repeat the highlights as a bullet list.")
        appendLine("- If this section is genuinely related to an earlier section, end the body with a line like:")
        appendLine("  `**Related:** [earlier title](#sec-<id>)` using the exact link from the list above. Otherwise omit it.")
    }

    private fun splitSection(raw: String, chunk: List<Annotation>): Pair<String, String> {
        val trimmed = raw.trim()
        val headingLine = trimmed.lines().firstOrNull { it.startsWith("## ") }
        return if (headingLine != null) {
            val title = headingLine.removePrefix("## ").trim()
            val body = trimmed.lines()
                .dropWhile { it != headingLine }
                .drop(1)
                .joinToString("\n")
                .trim()
            title to body
        } else {
            Log.w(TAG, "splitSection: model output had no '## ' heading, using fallback")
            fallbackSectionTitle(chunk) to trimmed
        }
    }

    private fun fallbackSectionTitle(chunk: List<Annotation>): String {
        val first = chunk.firstOrNull() ?: return "Highlights"
        val text = first.note.ifBlank { first.text }
        return text.split(Regex("\\s+")).filter { it.isNotBlank() }.take(4).joinToString(" ")
    }
}
