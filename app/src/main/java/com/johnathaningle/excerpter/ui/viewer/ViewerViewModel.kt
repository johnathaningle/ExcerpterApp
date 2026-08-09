package com.johnathaningle.excerpter.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnathaningle.excerpter.ExcerpterApp
import com.johnathaningle.excerpter.data.model.Annotation
import com.johnathaningle.excerpter.util.MlKitTextExtractor
import com.johnathaningle.excerpter.util.SessionPreferences
import com.johnathaningle.excerpter.util.LlmService
import com.johnathaningle.excerpter.ui.viewer.highlightColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ViewerState(
    val pageCount: Int = 0,
    val currentPage: Int = 0,
    val pageBitmap: Bitmap? = null,
    val annotations: List<Annotation> = emptyList(),
    val undoStack: List<Annotation> = emptyList(),
    val redoStack: List<Annotation> = emptyList(),
    val isScrollLocked: Boolean = false,
    val selectedColor: Long = 0xFFFF0000,
    val errorMessage: String? = null,
    val scale: Float = 1f,
    val offset: Offset = Offset.Zero,
    val llmError: String? = null
)

class ViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as ExcerpterApp).repository
    private val sessionPrefs = SessionPreferences(application)

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    private var pdfRenderer: PdfRenderer? = null
    private var currentFileDescriptor: android.os.ParcelFileDescriptor? = null
    private var currentPdfUri: String? = null
    private val colorHistory = mutableListOf<Long>()

    val selectedColor: Long get() = sessionPrefs.lastSelectedColor
    val autoRotateColor: Boolean get() = sessionPrefs.autoRotateColor

    fun loadPdf(uri: String) {
        viewModelScope.launch {
            sessionPrefs.lastOpenedPdfUri = uri
            currentPdfUri = uri
            val context = getApplication<Application>().applicationContext

            withContext(Dispatchers.IO) {
                try {
                    val pfd = context.contentResolver.openFileDescriptor(
                        Uri.parse(uri), "r"
                    ) ?: return@withContext

                    currentFileDescriptor = pfd
                    val renderer = PdfRenderer(pfd)
                    pdfRenderer = renderer

                    // Update document record
                    val existing = repository.getDocument(uri)
                    val startPage = existing?.lastPage ?: 0
                    if (existing == null) {
                        repository.insertDocument(
                            com.johnathaningle.excerpter.data.model.PdfDocument(
                                uri = uri,
                                fileName = uri.substringAfterLast("/"),
                                pageCount = renderer.pageCount
                            )
                        )
                    } else {
                        repository.updateLastOpened(uri)
                    }

                    _state.value = _state.value.copy(
                        pageCount = renderer.pageCount,
                        currentPage = startPage
                    )

                    renderPage(startPage)
                } catch (e: Exception) {
                    Log.e("ViewerViewModel", "Failed to open PDF: $uri", e)
                    _state.value = _state.value.copy(
                        errorMessage = "Unable to open PDF: ${e.message}"
                    )
                }
            }

            // Load annotations
            repository.getAnnotationsForPdf(uri).collect { annotations ->
                _state.value = _state.value.copy(annotations = annotations)
            }
        }
    }

    fun updateTransform(scale: Float, offset: Offset) {
        _state.value = _state.value.copy(scale = scale, offset = offset)
    }

    fun resetTransform() {
        _state.value = _state.value.copy(scale = 1f, offset = Offset.Zero)
    }

    fun renderPage(pageIndex: Int) {
        resetTransform()
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val renderer = pdfRenderer ?: return@withContext
                if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext

                renderer.openPage(pageIndex).use { page ->
                    val scale = 2f
                    val bitmap = Bitmap.createBitmap(
                        (page.width * scale).toInt(),
                        (page.height * scale).toInt(),
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(
                        bitmap,
                        null,
                        android.graphics.Matrix().apply { postScale(scale, scale) },
                        PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY
                    )

                    _state.value = _state.value.copy(
                        currentPage = pageIndex,
                        pageBitmap = bitmap
                    )

                    currentPdfUri?.let { uri ->
                        repository.updateLastPage(uri, pageIndex)
                    }
                }
            }
        }
    }

    fun goToPage(page: Int) {
        val maxPage = _state.value.pageCount - 1
        val target = page.coerceIn(0, maxPage)
        renderPage(target)
    }

    fun nextPage() {
        val current = _state.value.currentPage
        if (current < _state.value.pageCount - 1) {
            renderPage(current + 1)
        }
    }

    fun previousPage() {
        val current = _state.value.currentPage
        if (current > 0) {
            renderPage(current - 1)
        }
    }

    fun addAnnotation(annotation: Annotation) {
        viewModelScope.launch {
            val id = repository.insertAnnotation(annotation)
            val saved = annotation.copy(id = id)
            _state.value = _state.value.copy(
                annotations = _state.value.annotations + saved,
                undoStack = _state.value.undoStack + saved,
                redoStack = emptyList()
            )

            if (autoRotateColor) {
                colorHistory.add(_state.value.selectedColor)
                val currentIndex = highlightColors.indexOfFirst { it.color == _state.value.selectedColor }
                val nextIndex = (currentIndex + 1) % highlightColors.size
                setSelectedColor(highlightColors[nextIndex].color)
            }

            val uri = currentPdfUri ?: return@launch
            val context = getApplication<Application>().applicationContext
            val extractedText = withContext(Dispatchers.IO) {
                try {
                    val task = MlKitTextExtractor.extractTextFromRegion(
                        context = context,
                        pdfUri = uri,
                        pageNumber = annotation.pageNumber,
                        startX = annotation.startX,
                        startY = annotation.startY,
                        endX = annotation.endX,
                        endY = annotation.endY
                    )
                    com.google.android.gms.tasks.Tasks.await(task)
                } catch (e: Exception) {
                    ""
                }
            }

            val withText = saved.copy(text = extractedText)
            repository.updateAnnotation(withText)
            _state.value = _state.value.copy(
                annotations = _state.value.annotations.map {
                    if (it.id == id) withText else it
                },
                undoStack = _state.value.undoStack.map {
                    if (it.id == id) withText else it
                }
            )
        }
    }

    fun undo() {
        val undoStack = _state.value.undoStack
        if (undoStack.isEmpty()) return

        val last = undoStack.last()
        viewModelScope.launch {
            repository.deleteAnnotation(last)
            _state.value = _state.value.copy(
                annotations = _state.value.annotations - last,
                undoStack = undoStack.dropLast(1),
                redoStack = _state.value.redoStack + last
            )
            if (autoRotateColor && colorHistory.isNotEmpty()) {
                val previousColor = colorHistory.removeLast()
                setSelectedColor(previousColor)
            }
        }
    }

    fun redo() {
        val redoStack = _state.value.redoStack
        if (redoStack.isEmpty()) return

        val last = redoStack.last()
        viewModelScope.launch {
            val id = repository.insertAnnotation(last)
            val restored = last.copy(id = id)
            _state.value = _state.value.copy(
                annotations = _state.value.annotations + restored,
                undoStack = _state.value.undoStack + restored,
                redoStack = redoStack.dropLast(1)
            )
            if (autoRotateColor) {
                colorHistory.add(_state.value.selectedColor)
                setSelectedColor(last.color)
            }
        }
    }

    fun deleteAnnotation(annotation: Annotation) {
        viewModelScope.launch {
            repository.deleteAnnotation(annotation)
            _state.value = _state.value.copy(
                annotations = _state.value.annotations - annotation,
                undoStack = _state.value.undoStack - annotation,
                redoStack = _state.value.redoStack - annotation
            )
        }
    }

    fun updateNote(annotation: Annotation, note: String) {
        viewModelScope.launch {
            val updated = annotation.copy(note = note)
            repository.updateAnnotation(updated)
            _state.value = _state.value.copy(
                annotations = _state.value.annotations.map {
                    if (it.id == annotation.id) updated else it
                }
            )
        }
    }

    fun updateHeading(annotation: Annotation, heading: String) {
        viewModelScope.launch {
            val updated = annotation.copy(heading = heading)
            repository.updateAnnotation(updated)
            _state.value = _state.value.copy(
                annotations = _state.value.annotations.map {
                    if (it.id == annotation.id) updated else it
                }
            )
        }
    }

    fun updateNoteAndHeading(annotation: Annotation, heading: String, note: String) {
        viewModelScope.launch {
            val updated = annotation.copy(heading = heading, note = note)
            repository.updateAnnotation(updated)
            _state.value = _state.value.copy(
                annotations = _state.value.annotations.map {
                    if (it.id == annotation.id) updated else it
                }
            )
        }
    }

    fun setSelectedColor(color: Long) {
        sessionPrefs.lastSelectedColor = color
        _state.value = _state.value.copy(selectedColor = color)
    }

    fun setAutoRotateColor(enabled: Boolean) {
        sessionPrefs.autoRotateColor = enabled
    }

    fun toggleScrollLock() {
        _state.value = _state.value.copy(isScrollLocked = !_state.value.isScrollLocked)
    }

    fun initLlm() {
        if (LlmService.isAvailable()) return
        viewModelScope.launch {
            val ctx = getApplication<Application>().applicationContext
            val result = withContext(Dispatchers.IO) {
                if (!LlmService.isModelConfigured(ctx)) {
                    Result.failure(IllegalStateException("No model configured. Go to Settings → AI Model."))
                } else {
                    LlmService.initialize(ctx)
                }
            }
            result.onFailure { e ->
                Log.e("ViewerViewModel", "LLM init failed", e)
                _state.value = _state.value.copy(
                    llmError = e.message ?: "Failed to load the AI model"
                )
            }
        }
    }

    /** Summarizes the selected text (with document context) and returns the summary text. */
    suspend fun summarizeSuspend(annotation: Annotation): String = withContext(Dispatchers.IO) {
        val contextText = buildString {
            val others = _state.value.annotations
                .filter { it.id != annotation.id && it.text.isNotBlank() }
            if (others.isNotEmpty()) {
                appendLine("Related notes from this document:")
                others.forEach { a ->
                    val h = a.heading.ifBlank { "untitled" }
                    appendLine("- $h: ${a.note.ifBlank { a.text }}")
                }
                appendLine()
            }
            appendLine("Selected text:")
            append(annotation.note.ifBlank { annotation.text })
        }
        LlmService.summarize(contextText).getOrElse { throw it }
    }

    suspend fun generateHeadingSuspend(text: String): String = withContext(Dispatchers.IO) {
        val raw = LlmService.generateHeading(text).getOrNull() ?: return@withContext ""
        sanitizeHeading(raw).ifBlank { fallbackHeading(text) }
    }

    private fun sanitizeHeading(raw: String): String {
        val cleaned = raw
            .lines()
            .firstOrNull()
            ?.trim()
            ?.trim('"')
            ?.replace(Regex("(?i)^(topic|subject|heading|title)\\s*[-:]\\s*"), "")
            ?.replace(Regex("[*#_`'()<>:;,]"), " ")
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?: return ""
        if (cleaned.isBlank()) return ""
        val words = cleaned.split(' ').filter { it.isNotBlank() }
        if (words.size > 5) return words.take(5).joinToString(" ")
        return cleaned
    }

    private fun fallbackHeading(text: String): String =
        text.split(Regex("\\s+")).filter { it.isNotBlank() }.take(3).joinToString(" ")

    override fun onCleared() {
        super.onCleared()
        pdfRenderer?.close()
        currentFileDescriptor?.close()
        MlKitTextExtractor.close()
        LlmService.close()
    }
}
