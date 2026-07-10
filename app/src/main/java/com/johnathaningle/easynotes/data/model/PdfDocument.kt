package com.johnathaningle.easynotes.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pdf_documents")
data class PdfDocument(
    @PrimaryKey
    val uri: String,
    val fileName: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val lastOpened: Long = System.currentTimeMillis(),
    val pageCount: Int = 0,
    val thumbnailUri: String = ""
)
