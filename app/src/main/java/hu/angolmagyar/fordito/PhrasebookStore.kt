package hu.angolmagyar.fordito

import android.content.Context
import hu.angolmagyar.fordito.intelligence.ReviewState
import hu.angolmagyar.fordito.intelligence.SpacedRepetition
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Explicitly saved local phrases. Nothing is uploaded or written to diagnostic logs.
 * Review scheduling uses the shared SpacedRepetition (SM-2 style) engine for consistency
 * with ReviewCoachActivity (P4).
 */
data class SavedPhrase(
    val id: String,
    val source: String,
    val translation: String,
    val savedAt: Long,
    val intervalDays: Int = 0,
    val nextReviewAt: Long = 0L,
    val reviewCount: Int = 0,
    /** SM-2 repetitions counter (aligned with SpacedRepetition). */
    val repetitions: Int = 0,
    val easeFactor: Double = 2.5
)

class PhrasebookStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("local_phrasebook_v1", Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<SavedPhrase> {
        val raw = prefs.getString(KEY_ENTRIES, "[]") ?: "[]"
        return try {
            val json = JSONArray(raw)
            buildList {
                for (i in 0 until json.length()) {
                    val item = json.optJSONObject(i) ?: continue
                    val source = item.optString("source").trim()
                    val translation = item.optString("translation").trim()
                    if (source.isNotEmpty() && translation.isNotEmpty()) {
                        add(
                            SavedPhrase(
                                id = item.optString("id", UUID.randomUUID().toString()),
                                source = source,
                                translation = translation,
                                savedAt = item.optLong("savedAt", 0L),
                                intervalDays = item.optInt("intervalDays", 0),
                                nextReviewAt = item.optLong("nextReviewAt", 0L),
                                reviewCount = item.optInt("reviewCount", 0),
                                repetitions = item.optInt("repetitions", 0),
                                easeFactor = item.optDouble("easeFactor", 2.5)
                            )
                        )
                    }
                }
            }.sortedByDescending { it.savedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Phrases due for review at or before [now]. New items (never reviewed) are due immediately. */
    @Synchronized
    fun due(now: Long = System.currentTimeMillis()): List<SavedPhrase> =
        all().filter { it.nextReviewAt <= now }.sortedBy { it.nextReviewAt }

    /** Saves only on an explicit user action; repeated identical pairs are deduplicated. */
    @Synchronized
    fun save(source: String, translation: String): Boolean {
        val cleanSource = source.trim()
        val cleanTranslation = translation.trim()
        if (cleanSource.isEmpty() || cleanTranslation.isEmpty()) return false
        val current = all()
        if (current.any { it.source.equals(cleanSource, true) && it.translation.equals(cleanTranslation, true) }) return false
        val now = System.currentTimeMillis()
        val next = listOf(
            SavedPhrase(
                id = UUID.randomUUID().toString(),
                source = cleanSource,
                translation = cleanTranslation,
                savedAt = now,
                nextReviewAt = now // due immediately for first review
            )
        ) + current
        persist(next.take(MAX_ENTRIES))
        return true
    }

    /**
     * Marks a phrase as reviewed using SpacedRepetition grades:
     * 0 = again, 3 = hard, 4 = good, 5 = easy.
     * Boolean overload kept for existing call sites (remembered → 4, miss → 0).
     */
    @Synchronized
    fun review(id: String, remembered: Boolean): Boolean =
        review(id, grade = if (remembered) 4 else 0)

    @Synchronized
    fun review(id: String, grade: Int, now: Long = System.currentTimeMillis()): Boolean {
        var found = false
        val next = all().map { entry ->
            if (entry.id != id) entry else {
                found = true
                val state = ReviewState(entry.repetitions, entry.intervalDays, entry.easeFactor)
                val plan = SpacedRepetition.schedule(state, grade.coerceIn(0, 5), now)
                entry.copy(
                    intervalDays = plan.intervalDays,
                    nextReviewAt = plan.dueAt,
                    reviewCount = entry.reviewCount + 1,
                    repetitions = plan.repetitions,
                    easeFactor = plan.easeFactor
                )
            }
        }
        if (found) persist(next)
        return found
    }

    @Synchronized
    fun delete(id: String): Boolean {
        val current = all()
        val next = current.filterNot { it.id == id }
        if (next.size == current.size) return false
        persist(next)
        return true
    }

    @Synchronized
    fun clear(): Boolean {
        prefs.edit().remove(KEY_ENTRIES).apply()
        return true
    }

    private fun persist(entries: List<SavedPhrase>) {
        val array = JSONArray()
        entries.forEach { e ->
            array.put(
                JSONObject().apply {
                    put("id", e.id)
                    put("source", e.source)
                    put("translation", e.translation)
                    put("savedAt", e.savedAt)
                    put("intervalDays", e.intervalDays)
                    put("nextReviewAt", e.nextReviewAt)
                    put("reviewCount", e.reviewCount)
                    put("repetitions", e.repetitions)
                    put("easeFactor", e.easeFactor)
                }
            )
        }
        prefs.edit().putString(KEY_ENTRIES, array.toString()).apply()
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 2000
    }
}
