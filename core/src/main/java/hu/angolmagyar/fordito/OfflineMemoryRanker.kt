package hu.angolmagyar.fordito

/** Small deterministic offline retrieval helper; ranking is lexical, not semantic. */
object OfflineMemoryRanker {
    data class Item(val id: String, val source: String, val target: String, val domain: String = "general")
    data class Match(val item: Item, val score: Double)

    fun rank(query: String, items: List<Item>, domain: String? = null, limit: Int = 5): List<Match> {
        if (query.isBlank() || limit <= 0) return emptyList()
        val queryTokens = tokens(query)
        if (queryTokens.isEmpty()) return emptyList()
        return items.asSequence()
            .filter { it.id.isNotBlank() && it.source.isNotBlank() && it.target.isNotBlank() }
            .mapNotNull { item ->
                val candidate = tokens(item.source)
                if (candidate.isEmpty()) return@mapNotNull null
                val intersection = queryTokens.intersect(candidate).size.toDouble()
                val union = queryTokens.union(candidate).size.toDouble()
                val jaccard = if (union == 0.0) 0.0 else intersection / union
                val containment = intersection / queryTokens.size.coerceAtLeast(1)
                val domainBoost = if (domain != null && item.domain.equals(domain, true)) 0.08 else 0.0
                val score = (0.75 * jaccard + 0.25 * containment + domainBoost).coerceAtMost(1.0)
                if (score <= 0.0) null else Match(item, score)
            }
            .sortedWith(compareByDescending<Match> { it.score }.thenBy { it.item.id })
            .take(limit)
            .toList()
    }

    private fun tokens(value: String): Set<String> = Regex("[\\p{L}\\p{N}]+")
        .findAll(value.lowercase())
        .map { it.value }
        .filter { it.length > 1 }
        .toSet()
}
