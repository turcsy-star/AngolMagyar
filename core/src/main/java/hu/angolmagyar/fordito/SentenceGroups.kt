package hu.angolmagyar.fordito

import kotlin.math.roundToInt

/**
 * Kontextusos fordítás segédje: a rövid, mondat közepén vágott felismerési blokkokat mondatokká fogja össze
 * (a gépi fordítás így sokkal jobb), majd a lefordított mondatot arányosan visszaosztja a blokkokra.
 */
object SentenceGroups {
    data class Item(val startMs: Long, val endMs: Long, val text: String)

    private const val SENTENCE_END = ".?!…\"”)"

    fun group(
        items: List<Item>, maxChars: Int = 260, maxGapMs: Long = 1500, maxItems: Int = 6
    ): List<IntRange> {
        val out = ArrayList<IntRange>()
        if (items.isEmpty()) return out
        var start = 0
        var chars = 0
        for (i in items.indices) {
            val t = items[i].text.trim()
            chars += t.length + 1
            val last = i == items.size - 1
            var cut = last
            if (!last) {
                val ends = t.isNotEmpty() && t.last() in SENTENCE_END
                val gap = items[i + 1].startMs - items[i].endMs
                val next = items[i + 1].text.trim()
                val nextLower = next.isNotEmpty() && next[0].isLowerCase()
                // a mondat közepén vágott blokk (vessző / nincs záró írásjel + kisbetűs folytatás) kicsit hosszabb szünetet is áthidal;
                // a "…" végű blokk kisbetűs folytatással szintén egy mondat
                val ellipsis = t.endsWith("...") || t.endsWith("…")
                val sentenceEnd = ends && !(ellipsis && nextLower)
                val gapLimit = if (!ends && nextLower) maxOf(maxGapMs, 3000L) else maxGapMs
                cut = sentenceEnd || gap > gapLimit || chars + next.length > maxChars || (i - start + 1) >= maxItems
            }
            if (cut) {
                out.add(start..i)
                start = i + 1
                chars = 0
            }
        }
        return out
    }

    /** A cél-szöveg szétosztása a forrásblokkok hosszának arányában, lehetőleg írásjelnél vágva. */
    fun distribute(target: String, srcLens: List<Int>): List<String> {
        val n = srcLens.size
        if (n == 0) return emptyList()
        val words = target.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (n == 1) return listOf(words.joinToString(" "))
        if (words.isEmpty()) return List(n) { "" }
        val total = srcLens.sum().coerceAtLeast(1)
        val out = ArrayList<String>(n)
        var used = 0
        var acc = 0
        for (i in 0 until n) {
            acc += srcLens[i]
            var e = if (i == n - 1) words.size
            else (words.size * acc.toDouble() / total).roundToInt().coerceIn(used, words.size)
            if (i < n - 1) {
                // szívesebben vágunk írásjel után (max. 2 szóval arrébb)
                var found = -1
                for (d in 0..2) {
                    for (cand in intArrayOf(e - d, e + d)) {
                        if (found < 0 && cand > used && cand < words.size && words[cand - 1].last() in ",.;:!?…") {
                            found = cand
                        }
                    }
                }
                if (found >= 0) e = found
            }
            out.add(words.subList(used, e).joinToString(" "))
            used = e
        }
        return out
    }
}
