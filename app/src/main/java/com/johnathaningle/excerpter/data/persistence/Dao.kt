package com.johnathaningle.excerpter.data.persistence

import androidx.room.*
import com.johnathaningle.excerpter.data.model.Annotation
import com.johnathaningle.excerpter.data.model.PdfDocument
import kotlinx.coroutines.flow.Flow

@Dao
interface PdfDocumentDao {
    @Query("SELECT * FROM pdf_documents ORDER BY lastOpened DESC")
    fun getAllDocuments(): Flow<List<PdfDocument>>

    @Query("SELECT * FROM pdf_documents WHERE uri = :uri")
    suspend fun getDocument(uri: String): PdfDocument?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(document: PdfDocument)

    @Update
    suspend fun updateDocument(document: PdfDocument)

    @Delete
    suspend fun deleteDocument(document: PdfDocument)

    @Query("UPDATE pdf_documents SET lastOpened = :timestamp WHERE uri = :uri")
    suspend fun updateLastOpened(uri: String, timestamp: Long)
}

@Dao
interface AnnotationDao {
    @Query("SELECT * FROM annotations WHERE pdfUri = :pdfUri ORDER BY pageNumber, startY")
    fun getAnnotationsForPdf(pdfUri: String): Flow<List<Annotation>>

    @Query("SELECT * FROM annotations WHERE pdfUri = :pdfUri AND pageNumber = :pageNumber")
    fun getAnnotationsForPage(pdfUri: String, pageNumber: Int): Flow<List<Annotation>>

    @Query("SELECT * FROM annotations WHERE pdfUri = :pdfUri")
    suspend fun getAllAnnotationsForPdf(pdfUri: String): List<Annotation>

    @Query("SELECT * FROM annotations WHERE timestamp >= :startTime")
    suspend fun getAnnotationsSince(startTime: Long): List<Annotation>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotation(annotation: Annotation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnnotations(annotations: List<Annotation>)

    @Update
    suspend fun updateAnnotation(annotation: Annotation)

    @Delete
    suspend fun deleteAnnotation(annotation: Annotation)

    @Query("DELETE FROM annotations WHERE pdfUri = :pdfUri")
    suspend fun deleteAnnotationsForPdf(pdfUri: String)
}
