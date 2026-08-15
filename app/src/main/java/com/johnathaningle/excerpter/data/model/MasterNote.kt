package com.johnathaningle.excerpter.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "master_notes",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocument::class,
            parentColumns = ["uri"],
            childColumns = ["pdfUri"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MasterNote(
    @PrimaryKey
    val pdfUri: String,
    val markdown: String,
    val highlightCount: Int,
    val sectionCount: Int,
    val generatedAt: Long = System.currentTimeMillis()
)
