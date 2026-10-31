package hu.angolmagyar.fordito

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Android Keystore-backed persistence adapter for SmartTranslationMemory.
 * The complete memory document is encrypted before it reaches SharedPreferences.
 * This store is intentionally synchronous; call it from Dispatchers.IO.
 */
class SmartTranslationMemoryStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME, Context.MODE_PRIVATE
    )

    @Synchronized
    fun save(entry: SmartTranslationMemory.Entry): Boolean {
        if (!SmartTranslationMemory.validate(entry).valid) return false
        // Do not treat a Keystore/decryption failure as an empty store: that could
        // overwrite the only encrypted copy with a partial replacement.
        val entries = (readEntries() ?: return false).toMutableList()
        val identity = identity(entry)
        val index = entries.indexOfFirst { identity(it) == identity }
        if (index >= 0) entries[index] = entry else entries.add(entry)
        // Bound storage to prevent unbounded preference growth; oldest entries are dropped first.
        val bounded = entries.sortedByDescending { it.updatedAtEpochMs }.take(MAX_ENTRIES)
        return writeEntries(bounded)
    }

    @Synchronized
    fun approve(entry: SmartTranslationMemory.Entry, nowEpochMs: Long): Boolean =
        save(SmartTranslationMemory.approve(entry, nowEpochMs))

    @Synchronized
    fun retire(entry: SmartTranslationMemory.Entry, nowEpochMs: Long): Boolean =
        save(SmartTranslationMemory.retire(entry, nowEpochMs))

    @Synchronized
    fun lookup(
        source: String,
        sourceLanguage: String,
        targetLanguage: String,
        projectId: String? = null
    ): SmartTranslationMemory.Match? = readEntries()?.let { entries ->
        SmartTranslationMemory.lookup(source, sourceLanguage, targetLanguage, projectId, entries)
    }

    /** Records use only after the caller confirms that a suggested translation was accepted. */
    @Synchronized
    fun recordConfirmedUse(entry: SmartTranslationMemory.Entry, nowEpochMs: Long): Boolean =
        save(SmartTranslationMemory.recordConfirmedUse(entry, nowEpochMs))

    @Synchronized
    fun entries(): List<SmartTranslationMemory.Entry> = readEntries().orEmpty()

    @Synchronized
    fun delete(entry: SmartTranslationMemory.Entry): Boolean {
        val identity = identity(entry)
        val current = readEntries() ?: return false
        return writeEntries(current.filterNot { identity(it) == identity })
    }

    @Synchronized
    fun clear(): Boolean = prefs.edit().remove(DATA_KEY).commit()

    /**
     * Returns null on decrypt/parse failure. The encrypted blob is deliberately retained:
     * a temporarily unavailable or invalidated Keystore key must not silently destroy data.
     * Callers that mutate the store must abort when this returns null.
     */
    private fun readEntries(): List<SmartTranslationMemory.Entry>? {
        val encrypted = prefs.getString(DATA_KEY, null) ?: return emptyList()
        return try {
            val json = SecretStore.decrypt(encrypted)
            require(json.length <= MAX_PLAINTEXT_CHARS) { "memória túl nagy" }
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val entry = obj.toEntry() ?: continue
                    if (SmartTranslationMemory.validate(entry).valid) add(entry)
                }
            }
        } catch (e: Exception) {
            // Fail closed and preserve ciphertext for recovery/diagnostics; never overwrite it
            // as if the store were empty and never fall back to plaintext.
            DiagLog.log("SmartTranslationMemoryStore: titkosított memória nem olvasható; az eredeti blob megőrizve (${e.javaClass.simpleName})")
            null
        }
    }

    private fun writeEntries(entries: List<SmartTranslationMemory.Entry>): Boolean {
        return try {
            val array = JSONArray()
            entries.take(MAX_ENTRIES).forEach { array.put(it.toJson()) }
            val plain = array.toString()
            if (plain.length > MAX_PLAINTEXT_CHARS) return false
            val encrypted = SecretStore.encrypt(plain)
            prefs.edit().putString(DATA_KEY, encrypted).commit()
        } catch (e: Exception) {
            DiagLog.log("SmartTranslationMemoryStore: titkosított mentés sikertelen (${e.javaClass.simpleName})")
            false
        }
    }

    private fun identity(e: SmartTranslationMemory.Entry): String = listOf(
        SmartTranslationMemory.normalizeText(e.source),
        SmartTranslationMemory.normalizeLanguage(e.sourceLanguage),
        SmartTranslationMemory.normalizeLanguage(e.targetLanguage),
        e.scope.name,
        e.projectId.orEmpty()
    ).joinToString("\u001f")

    private fun SmartTranslationMemory.Entry.toJson() = JSONObject().apply {
        put("source", source); put("target", target)
        put("sourceLanguage", sourceLanguage); put("targetLanguage", targetLanguage)
        put("scope", scope.name); put("projectId", projectId ?: JSONObject.NULL)
        put("status", status.name); put("usageCount", usageCount)
        put("createdAtEpochMs", createdAtEpochMs); put("updatedAtEpochMs", updatedAtEpochMs)
    }

    private fun JSONObject.toEntry(): SmartTranslationMemory.Entry? = try {
        SmartTranslationMemory.Entry(
            source = getString("source"), target = getString("target"),
            sourceLanguage = getString("sourceLanguage"), targetLanguage = getString("targetLanguage"),
            scope = SmartTranslationMemory.Scope.valueOf(getString("scope")),
            projectId = if (isNull("projectId")) null else getString("projectId"),
            status = SmartTranslationMemory.Status.valueOf(getString("status")),
            usageCount = getInt("usageCount"),
            createdAtEpochMs = getLong("createdAtEpochMs"),
            updatedAtEpochMs = getLong("updatedAtEpochMs")
        )
    } catch (_: Exception) { null }

    companion object {
        private const val PREFS_NAME = "smart_translation_memory_secure"
        private const val DATA_KEY = "entries_aes_gcm_v1"
        private const val MAX_ENTRIES = 2000
        private const val MAX_PLAINTEXT_CHARS = 1_500_000
    }
}
