package hu.angolmagyar.fordito

/**
 * Small deterministic offline retrieval helper; ranking is lexical, not semantic.
 *
 * The public score remains normalized to [0, 1].
 * Domain preference is applied separately for ordering.
 */
object OfflineMemoryRanker {
    data class Item(
        val id: String,
        val source: String,
        val target: String,
        val domain: String = "general"
    )

    data class Match(val item: Item, val score: Double)

    private data class RankedMatch(
        val match: Match,
        val rankingScore: Double,
        val domainMatch: Boolean
    )

    fun rank(
        query: String,
        items: List<Item>,
        domain: String? = null,
        limit: Int = 5
    ): List<Match> {
        if (query.isBlank() || limit <= 0) return emptyList()

        val queryTokens = tokens(query)
        if (queryTokens.isEmpty()) return emptyList()

        return items.asSequence()
            .filter {
                it.id.isNotBlank() &&
                    it.source.isNotBlank() &&
                    it.target.isNotBlank()
            }
            .mapNotNull { item ->
                val candidate = tokens(item.source)
                if (candidate.isEmpty()) return@mapNotNull null

                val intersection =
                    queryTokens.intersect(candidate).size.toDouble()
                val union =
                    queryTokens.union(candidate).size.toDouble()

                val jaccard =
                    if (union == 0.0) 0.0 else intersection / union
                val containment =
                    intersection / queryTokens.size.coerceAtLeast(1)

                val baseScore = 0.75 * jaccard + 0.25 * containment
                val domainMatch =
                    domain != null && item.domain.equals(domain, ignoreCase = true)
                val domainBoost = if (domainMatch) 0.08 else 0.0

                if (baseScore <= 0.0) {
                    null
                } else {
                    RankedMatch(
                        match = Match(
                            item = item,
                            score = (baseScore + domainBoost).coerceAtMost(1.0)
                        ),
                        rankingScore = baseScore + domainBoost,
                        domainMatch = domainMatch
                    )
                }
            }
            .sortedWith(
                compareByDescending<RankedMatch> { it.rankingScore }
                    .thenByDescending { it.domainMatch }
                    .thenBy { it.match.item.id }
            )
            .take(limit)
            .map { it.match }
            .toList()
    }

    private fun tokens(value: String): Set<String> =
        Regex("[\\p{L}\\p{N}]+")
            .findAll(value.lowercase())
            .map { it.value }
            .filter { it.length > 1 }
            .toSet()
}
