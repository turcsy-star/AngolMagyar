package hu.angolmagyar.fordito

/**
 * Deterministic, offline terminology checker. It only reports findings; it never
 * rewrites user text. Callers own persistence and approval of glossary entries.
 */
object OfflineGlossaryAudit {
    data class Entry(
        val sourceTerm: String,
        val targetTerm: String,
        val domain: String = "general",
        val priority: Int = 0,
        val caseSensitive: Boolean = false
    )

    enum class Kind { MISSING_TRANSLATION, ALTERNATE_TRANSLATION, CONFLICTING_ENTRY }
    data class Finding(
        val kind: Kind,
        val sourceTerm: String,
        val expectedTargets: List<String>,
        val observedTargets: List<String>,
        val message: String
    )

    fun audit(source: String, target: String, entries: List<Entry>): List<Finding> {
        if (source.isBlank() || target.isBlank() || entries.isEmpty()) return emptyList()
        val safeEntries = entries.filter {
            it.sourceTerm.isNotBlank() && it.targetTerm.isNotBlank() &&
                it.sourceTerm.length <= MAX_TERM_LENGTH && it.targetTerm.length <= MAX_TERM_LENGTH &&
                it.domain.length <= MAX_DOMAIN_LENGTH
        }
        val grouped = safeEntries.groupBy { key(it.sourceTerm, it.caseSensitive) }
        val findings = mutableListOf<Finding>()
        grouped.values.forEach { group ->
            val sourceEntry = group.maxWithOrNull(compareBy<Entry> { it.priority }.thenBy { it.sourceTerm.length }) ?: return@forEach
            if (!containsTerm(source, sourceEntry.sourceTerm, sourceEntry.caseSensitive)) return@forEach
            val targets = group.map { it.targetTerm }.distinct()
            if (targets.size > 1) {
                findings += Finding(
                    Kind.CONFLICTING_ENTRY, sourceEntry.sourceTerm, targets, emptyList(),
                    "A szótárban több fordítás szerepel ugyanahhoz a kifejezéshez; ellenőrizd a terminológiát."
                )
                return@forEach
            }
            val expected = targets.single()
            if (!containsTerm(target, expected, false)) {
                findings += Finding(
                    Kind.MISSING_TRANSLATION, sourceEntry.sourceTerm, listOf(expected), emptyList(),
                    "A kötelező szótári kifejezés nem található a célszövegben."
                )
            }
        }
        return findings
    }

    /** Returns true only for a whole phrase, not for a substring inside another word. */
    fun containsTerm(text: String, term: String, caseSensitive: Boolean = false): Boolean {
        if (text.isEmpty() || term.isBlank()) return false
        val flags = if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE)
        val escaped = Regex.escape(term.trim()).replace("\\ ", "\\s+")
        val pattern = Regex("(?<![\\p{L}\\p{N}_])$escaped(?![\\p{L}\\p{N}_])", flags)
        return pattern.containsMatchIn(text)
    }

    private fun key(term: String, caseSensitive: Boolean): String =
        if (caseSensitive) term.trim() else term.trim().lowercase()

    private const val MAX_TERM_LENGTH = 160
    private const val MAX_DOMAIN_LENGTH = 64
}
