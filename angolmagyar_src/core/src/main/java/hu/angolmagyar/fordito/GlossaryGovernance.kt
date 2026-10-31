package hu.angolmagyar.fordito

import java.text.Normalizer
import java.time.Instant

/**
 * Platform-independent terminology governance primitives.
 *
 * This module deliberately does not rewrite translations. It validates glossary entries,
 * detects conflicts, computes conservative impact previews and supports reversible snapshots.
 * UI / persistence adapters must explicitly apply an approved change.
 */
object GlossaryGovernance {
    enum class Scope { GLOBAL, PROJECT }
    enum class Status { PENDING, APPROVED, DISMISSED, RETIRED }

    data class Entry(
        val source: String,
        val target: String,
        val scope: Scope = Scope.GLOBAL,
        val projectId: String? = null,
        val note: String = "",
        val status: Status = Status.PENDING,
        val createdAt: String = Instant.now().toString(),
        val updatedAt: String = createdAt,
        val revision: Int = 1
    )

    data class Conflict(val entry: Entry, val reason: String, val conflictingEntries: List<Entry>)
    data class Impact(val entry: Entry, val matchingSegments: List<Int>)
    data class Snapshot(val revision: Int, val entries: List<Entry>, val createdAt: String = Instant.now().toString())

    fun normalize(value: String): String =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFC).lowercase()

    fun validate(entry: Entry): List<String> = buildList {
        if (entry.source.isBlank()) add("A forráskifejezés nem lehet üres.")
        if (entry.target.isBlank()) add("A célnyelvi megfelelő nem lehet üres.")
        if (entry.source.length > 200) add("A forráskifejezés legfeljebb 200 karakter lehet.")
        if (entry.target.length > 200) add("A célnyelvi megfelelő legfeljebb 200 karakter lehet.")
        if (entry.scope == Scope.PROJECT && entry.projectId.isNullOrBlank()) {
            add("Projekt-szintű bejegyzéshez projektazonosító szükséges.")
        }
        if (entry.source.contains('\n') || entry.target.contains('\n')) {
            add("Egy bejegyzésben nem lehet sortörés.")
        }
    }

    /** Same normalized source + same scope/project but different target is a conflict. */
    fun conflicts(candidate: Entry, entries: List<Entry>): List<Conflict> {
        if (validate(candidate).isNotEmpty()) return emptyList()
        val sameContext = entries.filter {
            it.status != Status.DISMISSED && it.status != Status.RETIRED &&
                normalize(it.source) == normalize(candidate.source) &&
                it.scope == candidate.scope &&
                (candidate.scope == Scope.GLOBAL || it.projectId == candidate.projectId) &&
                normalize(it.target) != normalize(candidate.target)
        }
        if (sameContext.isEmpty()) return emptyList()
        return listOf(Conflict(candidate, "Azonos forráskifejezéshez eltérő fordítás tartozik ugyanebben a hatókörben.", sameContext))
    }

    /** Approved project entries override approved global entries; pending entries never apply. */
    fun resolve(source: String, projectId: String?, entries: List<Entry>): Entry? {
        val key = normalize(source)
        val approved = entries.filter { it.status == Status.APPROVED && normalize(it.source) == key }
        return approved.firstOrNull { it.scope == Scope.PROJECT && it.projectId == projectId }
            ?: approved.firstOrNull { it.scope == Scope.GLOBAL }
    }

    /** Exact normalized phrase matching only; no fuzzy or automatic replacement is performed. */
    fun impact(entry: Entry, segments: List<String>): Impact {
        val needle = normalize(entry.source)
        if (needle.isBlank()) return Impact(entry, emptyList())
        val matches = segments.mapIndexedNotNull { index, text ->
            if (containsPhrase(text, needle)) index else null
        }
        return Impact(entry, matches)
    }

    private fun containsPhrase(text: String, normalizedNeedle: String): Boolean {
        val normalizedText = normalize(text).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        val needle = normalizedNeedle.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        if (needle.isBlank()) return false
        return " $normalizedText ".contains(" $needle ")
    }

    fun snapshot(revision: Int, entries: List<Entry>): Snapshot =
        Snapshot(revision.coerceAtLeast(1), entries.toList())

    /** TSV is intentionally simple; tabs and line breaks are flattened to keep rows intact. */
    fun exportTsv(entries: List<Entry>): String {
        val header = listOf("source", "target", "scope", "projectId", "note", "status", "revision").joinToString("\t")
        val rows = entries.map { e ->
            listOf(e.source, e.target, e.scope.name, e.projectId.orEmpty(), e.note, e.status.name, e.revision.toString())
                .joinToString("\t") { it.replace("\t", " ").replace("\r", " ").replace("\n", " ") }
        }
        return (listOf(header) + rows).joinToString("\n")
    }

    /** Invalid rows are skipped; caller should report them and request confirmation before import. */
    fun importTsv(text: String): List<Entry> {
        val lines = text.lineSequence().filter { it.isNotBlank() }.toList()
        if (lines.isEmpty()) return emptyList()
        val start = if (lines.first().startsWith("source\t")) 1 else 0
        return lines.drop(start).mapNotNull { line ->
            val c = line.split('\t')
            if (c.size < 2 || c[0].isBlank() || c[1].isBlank()) return@mapNotNull null
            val scope = runCatching { Scope.valueOf(c.getOrNull(2).orEmpty().ifBlank { "GLOBAL" }.uppercase()) }.getOrNull()
                ?: return@mapNotNull null
            val status = runCatching { Status.valueOf(c.getOrNull(5).orEmpty().ifBlank { "PENDING" }.uppercase()) }.getOrNull()
                ?: Status.PENDING
            val e = Entry(
                source = c[0].trim(), target = c[1].trim(), scope = scope,
                projectId = c.getOrNull(3)?.ifBlank { null }, note = c.getOrNull(4).orEmpty(),
                status = status, revision = c.getOrNull(6)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            )
            e.takeIf { validate(it).isEmpty() }
        }
    }

    /** Retire rather than delete so callers can preserve audit history. */
    fun retire(entry: Entry): Entry = entry.copy(
        status = Status.RETIRED, updatedAt = Instant.now().toString(), revision = entry.revision + 1
    )

    fun approve(entry: Entry): Entry = entry.copy(
        status = Status.APPROVED, updatedAt = Instant.now().toString(), revision = entry.revision + 1
    )
}
