package com.johnathaningle.excerpter.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import io.aatricks.llmedge.text.runtime.SmolLM
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object LlmService {
    private const val TAG = "LlmService"
    private val GGUF_MAGIC = byteArrayOf(0x47, 0x47, 0x55, 0x46) // "GGUF"

    private var _llm: SmolLM? = null

    fun isAvailable(): Boolean = _llm != null

    /**
     * The GGUF to load: the stored path first (app-private, so it is always fopen-able),
     * then a model pushed into filesDir by the dev gradle task. canRead() is not enough
     * to trust a path — scoped storage reports public-Downloads paths as readable but
     * open() then fails with EACCES — so verify by actually reading the GGUF header.
     */
    fun findModelPath(context: Context): String? {
        SessionPreferences(context).modelPath
            ?.takeIf { canReadGguf(it) }
            ?.let { return it }
        return context.filesDir.listFiles()
            ?.firstOrNull { it.extension.equals("gguf", ignoreCase = true) }
            ?.let { if (canReadGguf(it.absolutePath)) it.absolutePath else null }
    }

    private fun canReadGguf(path: String): Boolean = runCatching {
        FileInputStream(File(path)).use { input ->
            val magic = ByteArray(4)
            input.read(magic) == 4 && magic.contentEquals(GGUF_MAGIC)
        }
    }.getOrDefault(false)

    fun isModelConfigured(context: Context): Boolean = findModelPath(context) != null

    suspend fun initialize(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (_llm != null) return@runCatching
            val path = findModelPath(context)
                ?: throw IllegalStateException("No model configured")
            val useVulkan = SessionPreferences(context).useVulkan
            val llm = SmolLM(useVulkan = useVulkan)
            // chatTemplate/contextSize stay null: llmedge reads them from the GGUF metadata.
            val params = SmolLM.InferenceParams(
                storeChats = false,
                thinkingMode = SmolLM.ThinkingMode.DISABLED,
                nGpuLayers = if (useVulkan) 99 else 0 // 99 = all layers
            )
            llm.load(path, params)
            _llm = llm
            Log.i(TAG, "LLM initialized from $path (${if (useVulkan) "Vulkan GPU" else "CPU only"})")
        }.onFailure { e ->
            Log.e(TAG, "LLM load failed: ${findModelPath(context) ?: "no model path"}", e)
        }
    }

    /** True if the first four bytes of the file are the GGUF magic number. */
    fun isGgufFile(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val magic = ByteArray(4)
            input.read(magic) == 4 && magic.contentEquals(GGUF_MAGIC)
        } ?: false
    }.getOrDefault(false)

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    // ponytail: fallback only when _data isn't exposed (rare on the Downloads provider);
    // keeps the model loadable while sacrificing the "external storage" promise.
    fun copyToInternal(context: Context, uri: Uri): String? {
        val dest = File(context.filesDir, displayName(context, uri) ?: "model.gguf")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { input.copyTo(it) }
            } ?: return null
            dest.absolutePath
        }.getOrNull()
    }

    fun summarize(text: String): Result<String> = runCatching {
        val llm = _llm ?: throw IllegalStateException("LLM not initialized")
        val prompt = """Summarize the following highlighted text from a PDF document. Be concise and capture the key points:

$text

Summary:"""
        llm.getResponse(prompt, maxTokens = 512)
    }.onFailure { e ->
        Log.e(TAG, "LLM summarize failed", e)
    }

    fun generateHeading(text: String): Result<String> = runCatching {
        val llm = _llm ?: throw IllegalStateException("LLM not initialized")
        val prompt = """Write a short, witty title for this note, the way a chat app names a conversation — clever and specific to what it's about. 3-5 words, no punctuation, no quotes, no explanation. Note: ${text.take(400)} Title:"""
        llm.getResponse(prompt, maxTokens = 32)
    }.onFailure { e ->
        Log.e(TAG, "LLM heading generation failed", e)
    }

    fun close() {
        _llm?.close()
        _llm = null
    }
}
