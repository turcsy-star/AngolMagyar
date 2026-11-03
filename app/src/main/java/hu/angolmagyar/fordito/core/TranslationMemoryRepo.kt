package hu.angolmagyar.fordito.core
import android.content.Context
import hu.angolmagyar.fordito.TranslationMemory
import hu.angolmagyar.fordito.core.db.AppDatabase
import hu.angolmagyar.fordito.core.db.TranslationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class TranslationMemoryRepo(context: Context, private val cap: Int = 5000) {
    private val dao = AppDatabase.get(context).translationDao()
    companion object {
        fun key(en: String): String = TranslationMemory.key(en)
    }
    suspend fun lookup(en: String): String? = withContext(Dispatchers.IO) {
        val k = key(en)
        if (k.length < 3) return@withContext null
        dao.lookup(k)?.let { entity ->
            dao.update(entity.copy(hitCount = entity.hitCount + 1, updatedAt = System.currentTimeMillis()))
            entity.targetText.takeIf { it.isNotBlank() }
        }
    }
    suspend fun put(en: String, hu: String, contextHint: String? = null) = withContext(Dispatchers.IO) {
        val k = key(en)
        val v = hu.replace('\t', ' ').replace('\n', ' ').trim()
        if (k.length < 3 || v.isBlank()) return@withContext
        val existing = dao.lookup(k)
        if (existing != null) {
            dao.update(
                existing.copy(
                    sourceText = en.trim(),
                    targetText = v,
                    contextHint = contextHint ?: existing.contextHint,
                    hitCount = existing.hitCount + 1,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else {
            dao.insert(
                TranslationEntity(
                    sourceKey = k,
                    sourceText = en.trim(),
                    targetText = v,
                    contextHint = contextHint
                )
            )
            val count = dao.count()
            if (count > cap) {
                dao.deleteOldest(count - cap)
            }
        }
    }
    suspend fun size(): Int = withContext(Dispatchers.IO) { dao.count() }
    suspend fun recent(limit: Int = 50) = withContext(Dispatchers.IO) { dao.recent(limit) }
}
