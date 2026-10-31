package hu.angolmagyar.fordito

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Explicitly saved local phrases. Nothing is uploaded or written to diagnostic logs. */
data class SavedPhrase(
    val id: String,
    val source: String,
    val translation: String,
    val savedAt: Long,
    val intervalDays: Int = 0,
    val nextReviewAt: Long = 0L,
    val reviewCount: Int = 0
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
                        add(SavedPhrase(item.optString("id", UUID.randomUUID().toString()), source,
                            translation, item.optLong("savedAt", 0L), item.optInt("intervalDays", 0),
                            item.optLong("nextReviewAt", 0L), item.optInt("reviewCount", 0)))
                    }
                }
            }.sortedByDescending { it.savedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** Saves only on an explicit user action; repeated identical pairs are deduplicated. */
    @Synchronized
    fun save(source: String, translation: String): Boolean {
        val cleanSource = source.trim()
        val cleanTranslation = translation.trim()
        if (cleanSource.isEmpty() || cleanTranslation.isEmpty()) return false
        val current = all()
        if (current.any { it.source.equals(cleanSource, true) && it.translation.equals(cleanTranslation, true) }) return false
        val next = listOf(SavedPhrase(UUID.randomUUID().toString(), cleanSource, cleanTranslation, System.currentTimeMillis())) + current
        persist(next.take(MAX_ENTRIES))
        return true
    }

    /** Marks a phrase as reviewed. A miss schedules it again tomorrow; a success uses a small spaced schedule. */
    @Synchronized
    fun review(id: String, remembered: Boolean): Boolean {
        val now = System.currentTimeMillis()
        var found = false
        val next = all().map { entry ->
            if (entry.id != id) entry else {
                found = true
                val interval = if (!remembered) 1 else when (entry.intervalDays) {
                    0 -> 1
                    1 -> 3
                    3 -> 7
                    7 -> 14
                    else -> (entry.intervalDays * 2).coerceAtMost(365)
                }
                entry.copy(intervalDays = interval, nextReviewAt = now + interval * DAY_MS,
                    reviewCount = entry.reviewCount + 1)
            }
        }
        if (found) persist(next)
        return found
    }

    @Synchronized
    fun due(now: Long = System.currentTimeMillis()): List<SavedPhrase> =
        all().filter { it.nextReviewAt == 0L || it.nextReviewAt <= now }

    @Synchronized
    fun delete(id: String) = persist(all().filterNot { it.id == id })

    @Synchronized
    fun clear() = prefs.edit().remove(KEY_ENTRIES).apply()

    private fun persist(entries: List<SavedPhrase>) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(JSONObject().put("id", entry.id).put("source", entry.source)
                .put("translation", entry.translation).put("savedAt", entry.savedAt)
                .put("intervalDays", entry.intervalDays).put("nextReviewAt", entry.nextReviewAt)
                .put("reviewCount", entry.reviewCount))
        }
        prefs.edit().putString(KEY_ENTRIES, json.toString()).apply()
    }

    companion object {
        private const val KEY_ENTRIES = "entries"
        private const val MAX_ENTRIES = 2000
        private const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
