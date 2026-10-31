package hu.angolmagyar.fordito.core.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "review_schedule", indices = [Index(value = ["dueAt"])])
data class ReviewScheduleEntity(
    @PrimaryKey val englishKey: String,
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5,
    val dueAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long = 0L
)
