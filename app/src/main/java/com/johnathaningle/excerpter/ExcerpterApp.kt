package com.johnathaningle.excerpter

import android.app.Application
import com.johnathaningle.excerpter.data.persistence.AppDatabase
import com.johnathaningle.excerpter.data.repository.PdfRepository
import com.johnathaningle.excerpter.util.LlmService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExcerpterApp : Application() {
    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy {
        PdfRepository(database.pdfDocumentDao(), database.annotationDao())
    }

    override fun onCreate() {
        super.onCreate()
        // Preload the LLM model in the background so it is ready as soon as the app opens.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            if (LlmService.isModelConfigured(applicationContext)) {
                LlmService.initialize(applicationContext)
            }
        }
    }
}
