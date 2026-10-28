package hu.angolmagyar.fordito

/**
 * Önjavító szótár: egy felvételen belül ugyanaz a név gyakran többféleképpen íródik le (Nina Skye / Nina Sky).
 * A ritka, a gyakori névhez nagyon hasonló alakot a gyakoribbra javítja. Csak nagybetűs szavakkal dolgozik.
 */
object SpellCluster {
    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /** @return ritka alak → gyakori alak */
    fun find(texts: List<String>): Map<String, String> {
        val counts = HashMap<String, Int>()
        for (t in texts) NameExtractor.capitalizedTokens(t).forEach { counts[it] = (counts[it] ?: 0) + 1 }
        val out = LinkedHashMap<String, String>()
        val sorted = counts.entries.sortedByDescending { it.value }
        for ((rare, rc) in sorted.reversed()) {
            if (rc > 2 || rare.length < 4) continue
            var best: String? = null
            for ((big, bc) in sorted) {
                if (bc < 3 || bc < rc * 3 || big == rare || big[0] != rare[0]) continue
                val d = lev(rare.lowercase(), big.lowercase())
                if (d in 1..maxOf(1, rare.length / 4)) { best = big; break }
            }
            if (best != null) out[rare] = best
        }
        return out
    }

    fun apply(text: String, map: Map<String, String>): String {
        var r = text
        for ((from, to) in map) {
            r = Regex("(?<![\\p{L}\\p{N}])" + Regex.escape(from) + "(?![\\p{L}\\p{N}])").replace(r, Regex.escapeReplacement(to))
        }
        return r
    }
}
