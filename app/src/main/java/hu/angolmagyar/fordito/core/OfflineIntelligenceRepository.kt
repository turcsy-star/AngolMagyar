package hu.angolmagyar.fordito.core

import android.content.Context
import hu.angolmagyar.fordito.AdaptiveReviewScheduler
import hu.angolmagyar.fordito.OfflineGlossaryAudit
import hu.angolmagyar.fordito.OfflineIntelligencePipeline
import hu.angolmagyar.fordito.OfflineMemoryRanker
import hu.angolmagyar.fordito.core.db.AppDatabase
import hu.angolmagyar.fordito.core.db.ReviewScheduleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Room adapter for the offline intelligence pipeline. Existing tables and schema
 * version are reused; this adapter does not change translation output or permissions.
 */
class OfflineIntelligenceRepository(context: Context) {
    private val database = AppDatabase.get(context.applicationContext)
    private val translations = database.translationDao()
    private val dictionary = database.dictionaryDao()
    private val reviews = database.reviewScheduleDao()

    suspend fun auditTranslation(source: String, target: String): OfflineIntelligencePipeline.AuditReport =
        withContext(Dispatchers.IO) {
            val glossary = dictionary.recent(MAX_GLOSSARY_SCAN).map {
                OfflineGlossaryAudit.Entry(sourceTerm = it.english, targetTerm = it.hungarian)
            }
            OfflineIntelligencePipeline.audit(source, target, glossary)
        }

    suspend fun rankSimilarTranslations(
        query: String,
        limit: Int = 5
    ): List<OfflineMemoryRanker.Match> = withContext(Dispatchers.IO) {
        val safeLimit = limit.coerceIn(1, MAX_RANK_RESULTS)
        val items = translations.recent(MAX_MEMORY_SCAN).map {
            OfflineMemoryRanker.Item(
                id = it.id.toString(),
                source = it.sourceText,
                target = it.targetText
            )
        }
        OfflineIntelligencePipeline.rankMemory(query, items, limit = safeLimit)
    }

    suspend fun recordReview(englishKey: String, quality: Int, nowEpochMs: Long = System.currentTimeMillis()): AdaptiveReviewScheduler.Result =
        withContext(Dispatchers.IO) {
            require(englishKey.isNotBlank() && englishKey.length <= MAX_KEY_LENGTH) {
                "Érvénytelen ismétlési kulcs."
            }
            val previousEntity = reviews.find(englishKey)
            val previous = previousEntity?.let {
                AdaptiveReviewScheduler.State(
                    repetitions = it.repetitions,
                    intervalDays = it.intervalDays,
                    easeFactor = it.easeFactor,
                    dueAtEpochMs = it.dueAt,
                    lastReviewedAtEpochMs = it.lastReviewedAt.takeIf { timestamp -> timestamp > 0L }
                )
            } ?: AdaptiveReviewScheduler.State()
            val result = OfflineIntelligencePipeline.scheduleReview(previous, quality, nowEpochMs)
            reviews.upsert(
                ReviewScheduleEntity(
                    englishKey = englishKey,
                    repetitions = result.state.repetitions,
                    intervalDays = result.state.intervalDays,
                    easeFactor = result.state.easeFactor,
                    dueAt = result.state.dueAtEpochMs,
                    lastReviewedAt = result.state.lastReviewedAtEpochMs ?: 0L
                )
            )
            result
        }

    suspend fun dueReviews(nowEpochMs: Long = System.currentTimeMillis(), limit: Int = 50): List<ReviewScheduleEntity> =
        withContext(Dispatchers.IO) {
            reviews.due(nowEpochMs, limit.coerceIn(1, MAX_REVIEW_RESULTS))
        }

    suspend fun dueReviewCount(nowEpochMs: Long = System.currentTimeMillis()): Int =
        withContext(Dispatchers.IO) { reviews.dueCount(nowEpochMs) }

    suspend fun glossaryCount(): Int = withContext(Dispatchers.IO) { dictionary.count() }

    suspend fun translationMemoryCount(): Int = withContext(Dispatchers.IO) { translations.count() }

    private companion object {
        const val MAX_GLOSSARY_SCAN = 5_000
        const val MAX_MEMORY_SCAN = 2_000
        const val MAX_RANK_RESULTS = 20
        const val MAX_REVIEW_RESULTS = 200
        const val MAX_KEY_LENGTH = 512
    }
}
