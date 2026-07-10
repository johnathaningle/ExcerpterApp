package com.johnathaningle.easynotes.ui.viewer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnathaningle.easynotes.EasyNotesApp
import com.johnathaningle.easynotes.data.model.Annotation
import com.johnathaningle.easynotes.util.PdfTextExtractor
import com.johnathaningle.easynotes.util.SessionPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val errorMessage: String? = null
)

class ViewerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as EasyNotesApp).repository
    private val sessionPrefs = SessionPreferences(application)

    private val _state = MutableStateFlow(ViewerState())
    val state: StateFlow<ViewerState> = _state.asStateFlow()

    private var pdfRenderer: PdfRenderer? = null
    private var currentFileDescriptor: android.os.ParcelFileDescriptor? = null
    private var currentPdfUri: String? = null

    val selectedColor: Long get() = sessionPrefs.lastSelectedColor

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
                    if (existing == null) {
                        repository.insertDocument(
                            com.johnathaningle.easynotes.data.model.PdfDocument(
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
                        currentPage = 0
                    )

                    renderPage(0)
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

    fun renderPage(pageIndex: Int) {
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

            val uri = currentPdfUri ?: return@launch
            val context = getApplication<Application>().applicationContext
            val extractedText = withContext(Dispatchers.IO) {
                PdfTextExtractor.extractTextFromRegion(
                    context = context,
                    pdfUri = uri,
                    pageNumber = annotation.pageNumber,
                    startX = annotation.startX,
                    startY = annotation.startY,
                    endX = annotation.endX,
                    endY = annotation.endY
                )
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
        }
    }

    fun setSelectedColor(color: Long) {
        sessionPrefs.lastSelectedColor = color
        _state.value = _state.value.copy(selectedColor = color)
    }

    fun toggleScrollLock() {
        _state.value = _state.value.copy(isScrollLocked = !_state.value.isScrollLocked)
    }

    override fun onCleared() {
        super.onCleared()
        pdfRenderer?.close()
        currentFileDescriptor?.close()
    }
}
