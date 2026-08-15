package com.johnathaningle.excerpter.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object LlmService {
    private const val TAG = "LlmService"
    // .litertlm files start with an 8-byte "LITERTLM" container magic; the TFLite
    // "TFL3" shards are embedded further inside, so this is what we validate.
    private val LITERTLM_MAGIC = byteArrayOf(
        0x4C, 0x49, 0x54, 0x45, 0x52, 0x54, 0x4C, 0x4D // "LITERTLM"
    )

    private var _engine: Engine? = null
    private val initLock = Any()

    // Non-greedy sampling so the model doesn't return the same canned output for
    // every prompt (defaults are topK=0, topP=0, temperature=0 -> deterministic).
    // Matches the values from Google's own LiteRT-LM docs example.
    private val samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = 0.8)

    fun isAvailable(): Boolean = _engine != null

    /**
     * The model to load: the stored path first (app-private, so it is always fopen-able),
     * then a model pushed into filesDir by the dev gradle task. canRead() is not enough
     * to trust a path — scoped storage reports public-Downloads paths as readable but
     * open() then fails with EACCES — so verify by actually reading the model header.
     */
    fun findModelPath(context: Context): String? {
        SessionPreferences(context).modelPath
            ?.takeIf { canReadModel(it) }
            ?.let { return it }
        return context.filesDir.listFiles()
            ?.firstOrNull { it.extension.equals("litertlm", ignoreCase = true) }
            ?.let { if (canReadModel(it.absolutePath)) it.absolutePath else null }
    }

    private fun canReadModel(path: String): Boolean = runCatching {
        FileInputStream(File(path)).use { input ->
            val magic = ByteArray(8)
            input.read(magic) == 8 && magic.contentEquals(LITERTLM_MAGIC)
        }
    }.getOrDefault(false)

    fun isModelConfigured(context: Context): Boolean = findModelPath(context) != null

    suspend fun initialize(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Locked so the launch-time preload, the post-download load and the viewer
            // lazy-load can't build two engines at once.
            synchronized(initLock) {
                if (_engine != null) return@runCatching
                val path = findModelPath(context)
                    ?: throw IllegalStateException("No model configured")
                val useGpu = SessionPreferences(context).useVulkan
                val engine = Engine(
                    EngineConfig(
                        modelPath = path,
                        backend = if (useGpu) Backend.GPU() else Backend.CPU(),
                        // Writable cache dir speeds up subsequent model loads.
                        cacheDir = context.cacheDir.absolutePath
                    )
                )
                engine.initialize()
                _engine = engine
                Log.i(TAG, "LLM initialized from $path (${if (useGpu) "GPU" else "CPU"})")
                Unit
            }
        }.onFailure { e ->
            Log.e(TAG, "LLM load failed: ${findModelPath(context) ?: "no model path"}", e)
        }
    }

    /** True if the first 8 bytes of the file are the "LITERTLM" container magic. */
    fun isModelFile(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val magic = ByteArray(8)
            input.read(magic) == 8 && magic.contentEquals(LITERTLM_MAGIC)
        } ?: false
    }.getOrDefault(false)

    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull()

    // ponytail: fallback only when _data isn't exposed (rare on the Downloads provider);
    // keeps the model loadable while sacrificing the "external storage" promise.
    fun copyToInternal(context: Context, uri: Uri): String? {
        val dest = File(context.filesDir, displayName(context, uri) ?: "model.litertlm")
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(dest).use { input.copyTo(it) }
            } ?: return null
            dest.absolutePath
        }.getOrNull()
    }

    fun summarize(text: String): Result<String> = runCatching {
        val engine = _engine ?: throw IllegalStateException("LLM not initialized")
        engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    "Summarize highlighted text from a PDF document. Be concise and capture the key points."
                ),
                samplerConfig = samplerConfig,
                maxOutputToken = 512
            )
        ).use { conversation ->
            conversation.sendMessage(text).toString()
        }
    }.onFailure { e ->
        Log.e(TAG, "LLM summarize failed", e)
    }

    fun generateHeading(text: String): Result<String> = runCatching {
        val engine = _engine ?: throw IllegalStateException("LLM not initialized")
        engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    "Write a short, accurate, descriptive title that summarizes the main topic of the highlighted text, as a study note heading. 3-6 words. No wit, no puns, no jokes, no quotes, no punctuation, no explanation."
                ),
                samplerConfig = samplerConfig,
                maxOutputToken = 32
            )
        ).use { conversation ->
            conversation.sendMessage(text.take(400)).toString()
        }
    }.onFailure { e ->
        Log.e(TAG, "LLM heading generation failed", e)
    }

    /** Synthesizes one markdown section of the master note from a chunk of highlights. */
    fun generateSection(prompt: String): Result<String> = runCatching {
        val engine = _engine ?: throw IllegalStateException("LLM not initialized")
        engine.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(
                    "You are writing a structured study note from a reader's PDF highlights. " +
                        "Synthesize the highlights into connected, concise knowledge in markdown. " +
                        "One section at a time, always starting with a '## ' heading."
                ),
                samplerConfig = samplerConfig,
                maxOutputToken = 512
            )
        ).use { conversation ->
            conversation.sendMessage(prompt).toString()
        }
    }.onFailure { e ->
        Log.e(TAG, "LLM section generation failed", e)
    }

    fun close() {
        _engine?.close()
        _engine = null
    }
}
