package hu.angolmagyar.fordito.core.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary_entries", indices = [Index(value = ["englishKey"], unique = true)])
data class DictionaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val englishKey: String,
    val english: String,
    val hungarian: String,
    val example: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)
