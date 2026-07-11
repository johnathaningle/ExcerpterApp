package com.johnathaningle.easynotes

import android.app.Application
import com.johnathaningle.easynotes.data.persistence.AppDatabase
import com.johnathaningle.easynotes.data.repository.PdfRepository

class EasyNotesApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy {
        PdfRepository(database.pdfDocumentDao(), database.annotationDao())
    }
}
