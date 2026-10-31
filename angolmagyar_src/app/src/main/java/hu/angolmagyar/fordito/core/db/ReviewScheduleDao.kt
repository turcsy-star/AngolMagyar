package hu.angolmagyar.fordito.core.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ReviewScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ReviewScheduleEntity)

    @Query("SELECT * FROM review_schedule WHERE dueAt <= :now ORDER BY dueAt ASC LIMIT :limit")
    suspend fun due(now: Long = System.currentTimeMillis(), limit: Int = 50): List<ReviewScheduleEntity>

    @Query("SELECT * FROM review_schedule WHERE englishKey = :key LIMIT 1")
    suspend fun find(key: String): ReviewScheduleEntity?

    @Query("SELECT COUNT(*) FROM review_schedule WHERE dueAt <= :now")
    suspend fun dueCount(now: Long = System.currentTimeMillis()): Int

    @Query("SELECT * FROM review_schedule ORDER BY dueAt ASC")
    suspend fun all(): List<ReviewScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ReviewScheduleEntity>)

    @Query("DELETE FROM review_schedule")
    suspend fun clear()
}
