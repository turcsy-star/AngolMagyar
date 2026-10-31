package hu.angolmagyar.fordito

import android.content.Context
import hu.angolmagyar.fordito.GlossaryGovernance.Entry
import hu.angolmagyar.fordito.GlossaryGovernance.Scope
import hu.angolmagyar.fordito.GlossaryGovernance.Status

/** SharedPreferences adapter for the governed glossary. Active legacy terms remain compatible. */
class GlossaryStore(context: Context) {
    private val prefs = Prefs(context.applicationContext).raw()

    fun load(): List<Entry> {
        val saved = prefs.getString(KEY, "").orEmpty()
        if (saved.isNotBlank()) return GlossaryGovernance.importTsv(saved)
        // One-time migration: the old personal vocabulary is already active in the translator.
        return parseLegacy(Prefs(context.applicationContext).customVocab).map {
            Entry(it.first, it.second, status = Status.APPROVED, note = "Migrálva a régi személyes szótárból")
        }
    }

    private val context = context.applicationContext

    fun save(entries: List<Entry>) {
        val valid = entries.filter { GlossaryGovernance.validate(it).isEmpty() }
        prefs.edit().putString(KEY, GlossaryGovernance.exportTsv(valid)).apply()
        // Keep the existing translation pipeline in sync with approved global terms.
        val active = valid.filter { it.status == Status.APPROVED && it.scope == Scope.GLOBAL }
            .map { it.source.trim() + "=" + it.target.trim() }
            .distinctBy { it.substringBefore('=').lowercase() }
        Prefs(context).customVocab = active.joinToString("\n")
    }

    fun importLegacy(): List<Entry> = parseLegacy(Prefs(context).customVocab).map {
        Entry(it.first, it.second, status = Status.APPROVED, note = "Importált személyes szótár")
    }

    fun exportText(): String = GlossaryGovernance.exportTsv(load())

    private fun parseLegacy(text: String): List<Pair<String, String>> = text.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") }
        .mapNotNull { line ->
            val split = line.indexOf('=')
            val source = if (split >= 0) line.substring(0, split).trim() else line
            val target = if (split >= 0) line.substring(split + 1).trim() else line
            if (source.isBlank() || target.isBlank()) null else source to target
        }.toList()

    companion object { private const val KEY = "governedGlossaryTsv_v1" }
}
