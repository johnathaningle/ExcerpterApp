package com.johnathaningle.excerpter

import android.app.Application
import com.johnathaningle.excerpter.data.persistence.AppDatabase
import com.johnathaningle.excerpter.data.repository.PdfRepository

class ExcerpterApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy {
        PdfRepository(database.pdfDocumentDao(), database.annotationDao())
    }
}
