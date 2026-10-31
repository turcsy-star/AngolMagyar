package hu.angolmagyar.fordito

/**
 * Ellenőrzési sorrend: melyik felirat-blokkot érdemes először megnézni. Nem állít helyességet:
 * kizárólag rangsorol, a szöveget nem módosítja.
 */
object ReviewPriority {
    data class Item(val index: Int, val score: Int, val reasons: List<String>)

    private val WEIGHTS = mapOf(
        "üres" to 100, "hibás idő" to 60, "tagadás eltér" to 50, "szám eltér" to 45, "név hiányzik" to 35,
        "fordítatlan" to 40, "bizonytalan felismerés" to 30, "gyenge szókonfidencia" to 25, "beszéd/csend ellenőrzése" to 20,
        "ismétlődés" to 20, "töredék" to 10, "nagyon gyors" to 15, "magázó alak" to 10, "hossz-arány" to 15, "magányos" to 15
    )

    fun rank(cues: List<SrtExporter.Cue>, limit: Int = Int.MAX_VALUE): List<Item> {
        val hasTarget = cues.any { it.target.isNotBlank() }
        val items = ArrayList<Item>()
        for (i in cues.indices) {
            val c = cues[i]
            val flags = LineQuality.flags(cues.getOrNull(i - 1), c, cues.getOrNull(i + 1), hasTarget)
            if (flags.isEmpty()) continue
            var score = flags.sumOf { WEIGHTS[it] ?: 10 }
            // minél több gyenge token, annál előrébb; a lp mélysége is számít
            val weak = c.wordConfidence.count { TokenQuality.needsReview(it) }
            score += minOf(weak, 5) * 3
            if (c.lp.isFinite() && c.lp < -1.5f) score += 10
            items.add(Item(i, score, flags))
        }
        return items.sortedWith(compareByDescending<Item> { it.score }.thenBy { it.index }).take(limit)
    }

    /** A következő ellenőrizendő blokk indexe az aktuális után (körbeér); null, ha nincs jelölt. */
    fun nextIndex(items: List<Item>, current: Int): Int? {
        if (items.isEmpty()) return null
        val byPos = items.map { it.index }.sorted()
        return byPos.firstOrNull { it > current } ?: byPos.first()
    }

    /**
     * Szókonfidencia szerinti jelölés a forrásszövegben. Csak akkor, ha a konfidenciák száma pontosan a szavak számával
     * egyezik (különben a hozzárendelés találgatás lenne); a bizonytalan szó köré ‹ › kerül.
     */
    fun markUncertainWords(cue: SrtExporter.Cue, threshold: Float = TokenQuality.REVIEW_PROBABILITY): String {
        val words = cue.source.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || cue.wordConfidence.size != words.size) return cue.source
        return words.indices.joinToString(" ") { i ->
            if (TokenQuality.needsReview(cue.wordConfidence[i], threshold)) "‹${words[i]}›" else words[i]
        }
    }
}
