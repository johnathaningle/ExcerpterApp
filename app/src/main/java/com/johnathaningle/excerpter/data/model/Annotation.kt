package com.johnathaningle.excerpter.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "annotations",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocument::class,
            parentColumns = ["uri"],
            childColumns = ["pdfUri"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["pdfUri"])]
)
data class Annotation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val pdfUri: String,
    val pageNumber: Int,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float,
    val color: Long,
    val text: String = "",
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
