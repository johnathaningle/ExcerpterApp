package com.johnathaningle.easynotes.data.repository

import com.johnathaningle.easynotes.data.model.Annotation
import com.johnathaningle.easynotes.data.model.PdfDocument
import com.johnathaningle.easynotes.data.persistence.AnnotationDao
import com.johnathaningle.easynotes.data.persistence.PdfDocumentDao
import kotlinx.coroutines.flow.Flow

class PdfRepository(
    private val documentDao: PdfDocumentDao,
    private val annotationDao: AnnotationDao
) {
    val allDocuments: Flow<List<PdfDocument>> = documentDao.getAllDocuments()

    suspend fun getDocument(uri: String): PdfDocument? = documentDao.getDocument(uri)

    suspend fun insertDocument(document: PdfDocument) = documentDao.insertDocument(document)

    suspend fun updateDocument(document: PdfDocument) = documentDao.updateDocument(document)

    suspend fun deleteDocument(document: PdfDocument) = documentDao.deleteDocument(document)

    suspend fun updateLastOpened(uri: String) {
        documentDao.updateLastOpened(uri, System.currentTimeMillis())
    }

    fun getAnnotationsForPdf(pdfUri: String): Flow<List<Annotation>> =
        annotationDao.getAnnotationsForPdf(pdfUri)

    fun getAnnotationsForPage(pdfUri: String, pageNumber: Int): Flow<List<Annotation>> =
        annotationDao.getAnnotationsForPage(pdfUri, pageNumber)

    suspend fun getAllAnnotationsForPdf(pdfUri: String): List<Annotation> =
        annotationDao.getAllAnnotationsForPdf(pdfUri)

    suspend fun getAnnotationsSince(startTime: Long): List<Annotation> =
        annotationDao.getAnnotationsSince(startTime)

    suspend fun insertAnnotation(annotation: Annotation): Long =
        annotationDao.insertAnnotation(annotation)

    suspend fun updateAnnotation(annotation: Annotation) =
        annotationDao.updateAnnotation(annotation)

    suspend fun deleteAnnotation(annotation: Annotation) =
        annotationDao.deleteAnnotation(annotation)

    suspend fun deleteAnnotationsForPdf(pdfUri: String) =
        annotationDao.deleteAnnotationsForPdf(pdfUri)
}
