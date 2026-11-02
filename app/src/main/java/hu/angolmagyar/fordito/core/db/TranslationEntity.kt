package hu.angolmagyar.fordito.core.db
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
@Entity(
    tableName = "translation_memory",
    indices = [Index(value = ["sourceKey"], unique = true)]
)
data class TranslationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceKey: String,
    val sourceText: String,
    val targetText: String,
    val contextHint: String? = null,
    val hitCount: Int = 1,
    val updatedAt: Long = System.currentTimeMillis()
)
