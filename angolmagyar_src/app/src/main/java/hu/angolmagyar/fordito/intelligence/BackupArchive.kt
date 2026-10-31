package hu.angolmagyar.fordito.intelligence

import hu.angolmagyar.fordito.core.db.DictionaryEntity
import hu.angolmagyar.fordito.core.db.ReviewScheduleEntity
import hu.angolmagyar.fordito.core.db.TranslationEntity
import org.json.JSONArray
import org.json.JSONObject

/** Versioned, portable backup format. Contains only local dictionary and learning data. */
data class LearningBackup(
    val createdAt: Long,
    val dictionary: List<DictionaryEntity>,
    val translations: List<TranslationEntity>,
    val reviews: List<ReviewScheduleEntity>
)

object BackupArchive {
    const val CURRENT_VERSION = 1
    private const val MAX_ENTRIES = 100_000

    fun encode(backup: LearningBackup): String {
        val root = JSONObject()
            .put("format", "angolmagyar-infinity-backup")
            .put("version", CURRENT_VERSION)
            .put("createdAt", backup.createdAt)
            .put("dictionary", JSONArray().apply {
                backup.dictionary.forEach { e -> put(JSONObject()
                    .put("id", e.id).put("englishKey", e.englishKey).put("english", e.english)
                    .put("hungarian", e.hungarian).put("example", e.example).put("updatedAt", e.updatedAt)) }
            })
            .put("translations", JSONArray().apply {
                backup.translations.forEach { e -> put(JSONObject()
                    .put("id", e.id).put("sourceKey", e.sourceKey).put("sourceText", e.sourceText)
                    .put("targetText", e.targetText).put("contextHint", e.contextHint)
                    .put("hitCount", e.hitCount).put("updatedAt", e.updatedAt)) }
            })
            .put("reviews", JSONArray().apply {
                backup.reviews.forEach { e -> put(JSONObject()
                    .put("englishKey", e.englishKey).put("repetitions", e.repetitions)
                    .put("intervalDays", e.intervalDays).put("easeFactor", e.easeFactor)
                    .put("dueAt", e.dueAt).put("lastReviewedAt", e.lastReviewedAt)) }
            })
        return root.toString(2)
    }

    fun decode(json: String): LearningBackup {
        require(json.length <= 20_000_000) { "A mentésfájl túl nagy." }
        val root = JSONObject(json)
        require(root.optString("format") == "angolmagyar-infinity-backup") { "Ismeretlen mentésformátum." }
        val version = root.optInt("version", -1)
        require(version == CURRENT_VERSION) { "Nem támogatott mentésverzió: $version" }
        fun array(name: String): JSONArray {
            val result = root.optJSONArray(name) ?: JSONArray()
            require(result.length() <= MAX_ENTRIES) { "Túl sok rekord: $name" }
            return result
        }
        val dictionary = buildList {
            val a = array("dictionary")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val key = o.getString("englishKey").trim()
                val en = o.getString("english").trim()
                val hu = o.getString("hungarian").trim()
                require(key.isNotEmpty() && en.isNotEmpty() && hu.isNotEmpty()) { "Hiányos szótárrekord ($i)." }
                add(DictionaryEntity(o.optLong("id", 0), key, en, hu, o.optString("example"), o.optLong("updatedAt", 0)))
            }
        }
        val translations = buildList {
            val a = array("translations")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val key = o.getString("sourceKey").trim()
                val src = o.getString("sourceText").trim()
                val dst = o.getString("targetText").trim()
                require(key.isNotEmpty() && src.isNotEmpty() && dst.isNotEmpty()) { "Hiányos fordítási rekord ($i)." }
                add(TranslationEntity(o.optLong("id", 0), key, src, dst,
                    o.optString("contextHint").takeUnless { it == "null" },
                    o.optInt("hitCount", 1).coerceAtLeast(1), o.optLong("updatedAt", 0)))
            }
        }
        val reviews = buildList {
            val a = array("reviews")
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                val key = o.getString("englishKey").trim()
                require(key.isNotEmpty()) { "Üres ismétlési kulcs ($i)." }
                val ease = o.optDouble("easeFactor", 2.5)
                require(ease.isFinite() && ease in 1.3..4.0) { "Érvénytelen ismétlési érték ($i)." }
                add(ReviewScheduleEntity(key, o.optInt("repetitions", 0).coerceAtLeast(0),
                    o.optInt("intervalDays", 0).coerceAtLeast(0), ease,
                    o.optLong("dueAt", 0), o.optLong("lastReviewedAt", 0)))
            }
        }
        return LearningBackup(root.optLong("createdAt", 0), dictionary, translations, reviews)
    }
}
