package com.johnathaningle.excerpter.ui.home

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnathaningle.excerpter.ExcerpterApp
import com.johnathaningle.excerpter.data.model.PdfDocument
import com.johnathaningle.excerpter.util.LlmService
import com.johnathaningle.excerpter.util.PdfThumbnailGenerator
import com.johnathaningle.excerpter.util.SessionPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ModelOption(val name: String, val url: String, val sizeMb: Long)

val popularModels = listOf(
    ModelOption(
        name = "Gemma 4 E2B (2B) Instruct Q4_K_M",
        url = "https://huggingface.co/lmstudio-community/gemma-4-E2B-it-GGUF/resolve/main/gemma-4-E2B-it-Q4_K_M.gguf",
        sizeMb = 3427
    ),
    ModelOption(
        name = "SmolLM2 360M Instruct Q8",
        url = "https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/main/smollm2-360m-instruct-q8_0.gguf",
        sizeMb = 386
    ),
    ModelOption(
        name = "SmolLM2 1.7B Instruct Q4_K_M",
        url = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf",
        sizeMb = 1055
    ),
    ModelOption(
        name = "Qwen2.5 1.5B Instruct Q8",
        url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q8_0.gguf",
        sizeMb = 1894
    )
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ExcerpterApp).repository
    private val context get() = getApplication<Application>()
    private val sessionPrefs = SessionPreferences(application)

    companion object {
        private const val TAG = "HomeViewModel"
    }

    val documents = repository.allDocuments
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    var autoRotateColor: Boolean
        get() = sessionPrefs.autoRotateColor
        set(value) { sessionPrefs.autoRotateColor = value }

    var useVulkan: Boolean
        get() = sessionPrefs.useVulkan
        set(value) { sessionPrefs.useVulkan = value }

    fun addPdf(uri: Uri, fileName: String) {
        viewModelScope.launch {
            val doc = PdfDocument(
                uri = uri.toString(),
                fileName = fileName,
                dateAdded = System.currentTimeMillis(),
                lastOpened = System.currentTimeMillis()
            )
            repository.insertDocument(doc)

            val thumbnailPath = withContext(Dispatchers.IO) {
                PdfThumbnailGenerator.generate(context, uri)
            }
            if (thumbnailPath != null) {
                repository.updateDocument(doc.copy(thumbnailUri = thumbnailPath))
            }
        }
    }

    fun deletePdf(document: PdfDocument) {
        viewModelScope.launch {
            PdfThumbnailGenerator.delete(context, document.thumbnailUri)
            repository.deleteDocument(document)
        }
    }

    fun getModelStatus(): String {
        val path = LlmService.findModelPath(context) ?: return "Not configured"
        val name = sessionPrefs.modelDisplayName ?: path.substringAfterLast('/')
        return "$name (${String.format("%.1f", File(path).length() / 1_000_000f)} MB)"
    }

    /** Validate the picked GGUF and copy it into app storage so it is always readable. */
    fun importModel(uri: Uri, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!LlmService.isGgufFile(context, uri)) {
                    Log.e(TAG, "Rejected model import: not a GGUF file ($uri)")
                    return@withContext "Invalid file: not a GGUF model"
                }
                // Scoped storage denies raw-path access to files this app doesn't own
                // (e.g. browser-downloaded GGUFs), so the model must live in app storage.
                val name = LlmService.displayName(context, uri)
                val modelPath = LlmService.copyToInternal(context, uri)
                    ?: run {
                        Log.e(TAG, "Failed to copy model to app storage ($uri)")
                        return@withContext "Could not read the model file"
                    }
                sessionPrefs.modelPath = modelPath
                sessionPrefs.modelDisplayName = name
                "Ready — $name (${String.format("%.1f", File(modelPath).length() / 1_000_000f)} MB)"
            }
            onDone(result)
        }
    }

    /**
     * Download a GGUF via DownloadManager into the public Downloads folder.
     * Reads back via llama.cpp require MANAGE_EXTERNAL_STORAGE ("All files access"),
     * granted in Settings → AI Model.
     */
    fun downloadModel(url: String, onProgress: (String) -> Unit, onDone: (String) -> Unit) {
        viewModelScope.launch {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = url.substringAfterLast('/')
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("GGUF model for Excerpter")
                .setMimeType("application/octet-stream")
                .setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            val id = runCatching { dm.enqueue(request) }.getOrElse {
                Log.e(TAG, "Download enqueue failed: $it")
                onDone("Download failed: ${it.message}")
                return@launch
            }

            val query = DownloadManager.Query().setFilterById(id)
            while (true) {
                dm.query(query).use { c ->
                    if (c.moveToFirst()) {
                        when (c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                val localUri = c.getString(
                                    c.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
                                )
                                val path = if (localUri.startsWith("file://")) {
                                    Uri.parse(localUri).path ?: localUri
                                } else localUri
                                sessionPrefs.modelPath = path
                                sessionPrefs.modelDisplayName = fileName
                                onDone("Ready — $fileName")
                                return@launch
                            }

                            DownloadManager.STATUS_FAILED -> {
                                onDone("Download failed. Check your connection and try again.")
                                return@launch
                            }

                            else -> {
                                val total = c.getLong(
                                    c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                                )
                                val done = c.getLong(
                                    c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                                )
                                val pct = if (total > 0) done * 100 / total else 0
                                onProgress("Downloading $fileName — $pct%")
                            }
                        }
                    }
                }
                delay(1000)
            }
        }
    }
}
