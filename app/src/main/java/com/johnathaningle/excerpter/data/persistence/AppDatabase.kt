package com.johnathaningle.excerpter.data.persistence

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.johnathaningle.excerpter.data.model.Annotation
import com.johnathaningle.excerpter.data.model.MasterNote
import com.johnathaningle.excerpter.data.model.PdfDocument

@Database(
    entities = [PdfDocument::class, Annotation::class, MasterNote::class],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pdfDocumentDao(): PdfDocumentDao
    abstract fun annotationDao(): AnnotationDao
    abstract fun masterNoteDao(): MasterNoteDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE annotations ADD COLUMN text TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE annotations ADD COLUMN note TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pdf_documents ADD COLUMN thumbnailUri TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE annotations ADD COLUMN heading TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE pdf_documents ADD COLUMN lastPage INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `master_notes` (" +
                        "`pdfUri` TEXT NOT NULL, " +
                        "`markdown` TEXT NOT NULL, " +
                        "`highlightCount` INTEGER NOT NULL, " +
                        "`sectionCount` INTEGER NOT NULL, " +
                        "`generatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`pdfUri`), " +
                        "FOREIGN KEY(`pdfUri`) REFERENCES `pdf_documents`(`uri`) ON DELETE CASCADE" +
                        ")"
                )
            }
        }

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "excerpter.db"
                ).addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6,
                    MIGRATION_6_7
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
