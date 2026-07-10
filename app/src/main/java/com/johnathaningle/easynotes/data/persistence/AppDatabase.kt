package com.johnathaningle.easynotes.data.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.johnathaningle.easynotes.data.model.Annotation
import com.johnathaningle.easynotes.data.model.PdfDocument

@Database(
    entities = [PdfDocument::class, Annotation::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfDocumentDao(): PdfDocumentDao
    abstract fun annotationDao(): AnnotationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "easynotes.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
