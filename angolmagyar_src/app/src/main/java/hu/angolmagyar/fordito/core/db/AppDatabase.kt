package hu.angolmagyar.fordito.core.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [TranslationEntity::class, DictionaryEntity::class, ReviewScheduleEntity::class],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun translationDao(): TranslationDao
    abstract fun dictionaryDao(): DictionaryDao
    abstract fun reviewScheduleDao(): ReviewScheduleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS dictionary_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, englishKey TEXT NOT NULL, english TEXT NOT NULL, hungarian TEXT NOT NULL, example TEXT NOT NULL, updatedAt INTEGER NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_dictionary_entries_englishKey ON dictionary_entries(englishKey)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS review_schedule (englishKey TEXT NOT NULL PRIMARY KEY, repetitions INTEGER NOT NULL DEFAULT 0, intervalDays INTEGER NOT NULL DEFAULT 0, easeFactor REAL NOT NULL DEFAULT 2.5, dueAt INTEGER NOT NULL, lastReviewedAt INTEGER NOT NULL DEFAULT 0)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_review_schedule_dueAt ON review_schedule(dueAt)")
            }
        }

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "angolmagyar.db"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
