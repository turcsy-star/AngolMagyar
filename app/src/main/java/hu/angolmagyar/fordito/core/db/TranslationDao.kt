package hu.angolmagyar.fordito.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface TranslationDao {

    @Query("SELECT * FROM translation_memory WHERE sourceKey = :key LIMIT 1")
    suspend fun lookup(key: String): TranslationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TranslationEntity): Long

    @Update
    suspend fun update(entity: TranslationEntity)

    @Query("SELECT * FROM translation_memory ORDER BY updatedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int = 100): List<TranslationEntity>

    @Query("SELECT COUNT(*) FROM translation_memory")
    suspend fun count(): Int

    @Query("SELECT * FROM translation_memory ORDER BY updatedAt DESC")
    suspend fun all(): List<TranslationEntity>

    @Query("DELETE FROM translation_memory WHERE id IN (SELECT id FROM translation_memory ORDER BY updatedAt ASC LIMIT :n)")
    suspend fun deleteOldest(n: Int)

    @Query("DELETE FROM translation_memory")
    suspend fun clear()
}
