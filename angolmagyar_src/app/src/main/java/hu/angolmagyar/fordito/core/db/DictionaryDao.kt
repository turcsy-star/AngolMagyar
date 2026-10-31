package hu.angolmagyar.fordito.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DictionaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<DictionaryEntity>)

    @Query("SELECT * FROM dictionary_entries ORDER BY english COLLATE NOCASE ASC")
    suspend fun all(): List<DictionaryEntity>

    @Query("SELECT * FROM dictionary_entries WHERE englishKey LIKE '%' || :query || '%' OR hungarian LIKE '%' || :query || '%' ORDER BY english COLLATE NOCASE LIMIT 100")
    suspend fun search(query: String): List<DictionaryEntity>

    @Query("SELECT COUNT(*) FROM dictionary_entries")
    suspend fun count(): Int

    @Query("DELETE FROM dictionary_entries")
    suspend fun clear()
}
