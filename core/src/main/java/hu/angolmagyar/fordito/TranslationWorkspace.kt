package hu.angolmagyar.fordito
import java.time.Instant
object TranslationWorkspace {
    enum class Style { NATURAL, LITERAL, FORMAL, BUSINESS, CONVERSATIONAL }
    data class Variant(
        val id: String,
        val source: String,
        val translation: String,
        val style: Style,
        val providerId: String,
        val createdAt: String = Instant.now().toString(),
        val parentId: String? = null,
        val note: String = ""
    )
    data class DiffPart(val kind: Kind, val text: String) {
        enum class Kind { UNCHANGED, ADDED, REMOVED }
    }
    data class RevisionHistory(val sourceKey: String, val variants: List<Variant>)
    class RevisionStore(private val maxRevisionsPerSource: Int = 50) {
        private val history = linkedMapOf<String, MutableList<Variant>>()
        init { require(maxRevisionsPerSource >= 2) { "Legalább két verziót meg kell őrizni." } }
        @Synchronized
        fun append(variant: Variant): Boolean {
            if (variant.id.isBlank() || variant.source.isBlank() || variant.translation.isBlank() || variant.providerId.isBlank()) return false
            val key = sourceKey(variant.source)
            val revisions = history.getOrPut(key) { mutableListOf() }
            if (revisions.any { it.id == variant.id }) return false
            if (variant.parentId != null && revisions.none { it.id == variant.parentId }) return false
            revisions += variant.copy(source = variant.source.trim(), translation = variant.translation.trim())
            while (revisions.size > maxRevisionsPerSource) revisions.removeAt(0)
            return true
        }
        @Synchronized
        fun list(source: String): RevisionHistory =
            RevisionHistory(sourceKey(source), history[sourceKey(source)]?.toList().orEmpty())
        @Synchronized
        fun restore(source: String, variantId: String, newId: String, providerId: String = "local-restore"): Variant? {
            val revisions = history[sourceKey(source)] ?: return null
            val original = revisions.firstOrNull { it.id == variantId } ?: return null
            val restored = original.copy(
                id = newId,
                createdAt = Instant.now().toString(),
                parentId = original.id,
                providerId = providerId,
                note = "Visszaállítás: ${original.id}"
            )
            return restored.takeIf { append(it) }
        }
        private fun sourceKey(source: String): String = source.trim().replace(Regex("\\s+"), " ").lowercase()
    }
    fun diff(before: String, after: String, maxTokens: Int = 400): List<DiffPart> {
        if (before == after) return listOfNotNull(before.takeIf { it.isNotEmpty() }?.let { DiffPart(DiffPart.Kind.UNCHANGED, it) })
        val a = tokenize(before)
        val b = tokenize(after)
        if (a.size > maxTokens || b.size > maxTokens) return buildList {
            if (before.isNotEmpty()) add(DiffPart(DiffPart.Kind.REMOVED, before))
            if (after.isNotEmpty()) add(DiffPart(DiffPart.Kind.ADDED, after))
        }
        val lcs = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.reversed()) for (j in b.indices.reversed()) {
            lcs[i][j] = if (a[i] == b[j]) 1 + lcs[i + 1][j + 1]
            else maxOf(lcs[i + 1][j], lcs[i][j + 1])
        }
        val raw = mutableListOf<DiffPart>()
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            when {
                i < a.size && j < b.size && a[i] == b[j] -> {
                    raw += DiffPart(DiffPart.Kind.UNCHANGED, a[i]); i++; j++
                }
                j < b.size && (i == a.size || lcs[i][j + 1] >= lcs[i + 1][j]) -> {
                    raw += DiffPart(DiffPart.Kind.ADDED, b[j]); j++
                }
                i < a.size -> { raw += DiffPart(DiffPart.Kind.REMOVED, a[i]); i++ }
            }
        }
        return coalesce(raw)
    }
    fun audit(variant: Variant): TranslationQualityAudit.Report =
        TranslationQualityAudit.inspect(variant.source, variant.translation)
    private fun tokenize(text: String): List<String> = Regex("\\s+|[^\\s]+")
        .findAll(text).map { it.value }.toList()
    private fun coalesce(parts: List<DiffPart>): List<DiffPart> {
        val result = mutableListOf<DiffPart>()
        parts.forEach { part ->
            val last = result.lastOrNull()
            if (last?.kind == part.kind) result[result.lastIndex] = last.copy(text = last.text + part.text)
            else result += part
        }
        return result
    }
}
