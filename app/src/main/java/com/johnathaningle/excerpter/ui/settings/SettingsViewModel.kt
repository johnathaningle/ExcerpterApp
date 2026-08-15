package com.johnathaningle.excerpter.ui.settings

import android.app.Application
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnathaningle.excerpter.util.LlmService
import com.johnathaningle.excerpter.util.SessionPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ModelOption(val name: String, val url: String, val sizeMb: Long)

// ponytail: single non-gated LiteRT-LM option; most other litert-community models require
// HF login (gemma license) which DownloadManager can't do. Import others via file picker.
val popularModels = listOf(
    ModelOption(
        name = "Gemma 4 E2B (2B) Instruct LiteRT-LM",
        url = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm",
        sizeMb = 2583
    )
)

sealed interface ModelState {
    data object NotConfigured : ModelState
    data class Downloading(val fileName: String, val percent: Int) : ModelState
    data object Loading : ModelState
    data class Ready(val name: String, val sizeMb: Long) : ModelState
    data class Error(val message: String) : ModelState
}

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context get() = getApplication<Application>().applicationContext
    private val sessionPrefs = SessionPreferences(application)

    private val _modelState = MutableStateFlow<ModelState>(ModelState.NotConfigured)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    companion object {
        private const val TAG = "SettingsViewModel"
    }

    var autoRotateColor: Boolean
        get() = sessionPrefs.autoRotateColor
        set(value) { sessionPrefs.autoRotateColor = value }

    var useVulkan: Boolean
        get() = sessionPrefs.useVulkan
        set(value) { sessionPrefs.useVulkan = value }

    init {
        // Kick off the load if a model is configured but not yet loaded (e.g. the
        // launch-time preload in ExcerpterApp is still running, or the viewer closed it).
        loadModel()
    }

    private fun loadModel() {
        viewModelScope.launch {
            if (LlmService.isAvailable()) {
                _modelState.value = readyState()
                return@launch
            }
            if (!withContext(Dispatchers.IO) { LlmService.isModelConfigured(context) }) {
                _modelState.value = ModelState.NotConfigured
                return@launch
            }
            _modelState.value = ModelState.Loading
            LlmService.initialize(context)
                .onSuccess { _modelState.value = readyState() }
                .onFailure { _modelState.value = ModelState.Error(it.message ?: "Failed to load the AI model") }
        }
    }

    private fun readyState(): ModelState {
        val path = LlmService.findModelPath(context)
        val name = sessionPrefs.modelDisplayName ?: path?.substringAfterLast('/') ?: "Model"
        val sizeMb = path?.let { File(it).length() / 1_000_000L } ?: 0L
        return ModelState.Ready(name, sizeMb)
    }

    /** Validate the picked model, copy it into app storage so it is always readable, then load it. */
    fun importModel(uri: Uri) {
        viewModelScope.launch {
            _modelState.value = ModelState.Loading
            val error = withContext(Dispatchers.IO) {
                if (!LlmService.isModelFile(context, uri)) {
                    Log.e(TAG, "Rejected model import: not a LiteRT-LM model ($uri)")
                    "Invalid file: not a LiteRT-LM model"
                } else {
                    // Scoped storage denies raw-path access to files this app doesn't own
                    // (e.g. browser-downloaded models), so the model must live in app storage.
                    val name = LlmService.displayName(context, uri)
                    val modelPath = LlmService.copyToInternal(context, uri)
                        ?: run {
                            Log.e(TAG, "Failed to copy model to app storage ($uri)")
                            return@withContext "Could not read the model file"
                        }
                    sessionPrefs.modelPath = modelPath
                    sessionPrefs.modelDisplayName = name
                    null
                }
            }
            if (error != null) {
                _modelState.value = ModelState.Error(error)
            } else {
                loadModel()
            }
        }
    }

    /**
     * Download a LiteRT-LM model into app storage (always readable, no extra permissions)
     * and load it as soon as it finishes.
     */
    fun downloadModel(url: String) {
        viewModelScope.launch {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val fileName = url.substringAfterLast('/')
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(fileName)
                .setDescription("LiteRT-LM model for Excerpter")
                .setMimeType("application/octet-stream")
                .setAllowedNetworkTypes(
                    DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                )
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, null, fileName)
            val id = runCatching { dm.enqueue(request) }.getOrElse {
                Log.e(TAG, "Download enqueue failed: $it")
                _modelState.value = ModelState.Error("Download failed: ${it.message}")
                return@launch
            }
            _modelState.value = ModelState.Downloading(fileName, 0)

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
                                loadModel()
                                return@launch
                            }

                            DownloadManager.STATUS_FAILED -> {
                                _modelState.value = ModelState.Error(
                                    "Download failed. Check your connection and try again."
                                )
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
                                _modelState.value = ModelState.Downloading(fileName, pct.toInt())
                            }
                        }
                    }
                }
                delay(1000)
            }
        }
    }
}
