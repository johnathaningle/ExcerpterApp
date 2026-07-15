package com.johnathaningle.excerpter.util

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInference.LlmInferenceOptions
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object LlmService {
    private const val TAG = "LlmService"
    private const val MODEL_URL = "https://huggingface.co/datasets/calm2026/litert-pack-2c3e55/resolve/main/gemma3-270m-it-q8.task"
    private const val MODEL_FILE = "gemma3-270m-it-q8.task"

    private var _llm: LlmInference? = null

    fun isAvailable(): Boolean = _llm != null

    private fun modelFile(context: Context): File =
        File(context.filesDir, MODEL_FILE)

    fun isModelOnDevice(context: Context): Boolean =
        modelFile(context).exists()

    fun modelFileSize(context: Context): Long =
        modelFile(context).length()

    fun initialize(context: Context): Result<Unit> = runCatching {
        if (_llm != null) return@runCatching
        val options = LlmInferenceOptions.builder()
            .setModelPath(modelFile(context).absolutePath)
            .setMaxTokens(1024)
            .setMaxTopK(40)
            .build()
        _llm = LlmInference.createFromOptions(context, options)
        Log.i(TAG, "LLM initialized")
    }

    fun downloadModel(context: Context, onProgress: (Float) -> Unit): Result<Unit> = runCatching {
        val file = modelFile(context)
        if (file.exists()) {
            onProgress(1f)
            return@runCatching
        }
        file.parentFile?.mkdirs()
        URL(MODEL_URL).openConnection().let { conn ->
            conn.connect()
            val total = conn.contentLengthLong
            conn.getInputStream().use { input ->
                FileOutputStream(file).use { output ->
                    val buf = ByteArray(8192)
                    var read: Int
                    var totalRead = 0L
                    while (input.read(buf).also { read = it } != -1) {
                        output.write(buf, 0, read)
                        totalRead += read
                        if (total > 0) onProgress(totalRead.toFloat() / total.toFloat())
                    }
                }
            }
        }
        onProgress(1f)
        Log.i(TAG, "Model downloaded (${file.length() / 1_000_000} MB)")
    }

    fun summarize(text: String): Result<String> = runCatching {
        val llm = _llm ?: throw IllegalStateException("LLM not initialized")
        val prompt = """Summarize the following highlighted text from a PDF document. Be concise and capture the key points:

$text

Summary:"""
        llm.generateResponse(prompt)
    }

    fun close() {
        _llm?.close()
        _llm = null
    }
}
