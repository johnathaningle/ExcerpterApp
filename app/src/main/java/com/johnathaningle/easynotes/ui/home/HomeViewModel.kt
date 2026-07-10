package com.johnathaningle.easynotes.ui.home

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.johnathaningle.easynotes.EasyNotesApp
import com.johnathaningle.easynotes.data.model.PdfDocument
import com.johnathaningle.easynotes.util.PdfThumbnailGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as EasyNotesApp).repository
    private val context get() = getApplication<Application>()

    val documents = repository.allDocuments
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

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
}
