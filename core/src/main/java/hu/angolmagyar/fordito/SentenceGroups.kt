package hu.angolmagyar.fordito

import kotlin.math.roundToInt

/**
 * Kontextusos fordítás segédje: a rövid, mondat közepén vágott felismerési blokkokat mondatokká fogja össze
 * (a gépi fordítás így sokkal jobb), majd a lefordított mondatot arányosan visszaosztja a blokkokra.
 */
object SentenceGroups {
    data class Item(val startMs: Long, val endMs: Long, val text: String)

    private const val SENTENCE_END = ".?!…\"”)"

    /** Szavak, amelyekre egy valódi mondat nem végződik – ha ilyen után "." áll, az a Whisper-darab végi hamis pont. */
    private val DANGLING = setOf(
        "a", "an", "the", "and", "or", "but", "so", "of", "to", "in", "on", "at", "for", "with", "from", "by",
        "my", "your", "his", "our", "their", "is", "are", "was", "were", "am", "be", "been", "i", "we", "they",
        "he", "she", "that", "this", "can", "will", "would", "could", "should", "what", "who", "how", "why",
        "if", "because", "as", "than", "not", "just", "very", "supposed", "gonna", "wanna", "going"
    )
    private val WH = setOf("what", "where", "why", "how", "who", "when")

    /** True, ha a "." végű blokk valójában félbevágott mondat (hamis pont a darab végén). */
    fun falseStop(text: String): Boolean {
        val t = text.trim()
        if (!t.endsWith(".") || t.endsWith("...")) return false
        val words = t.trimEnd('.').split(Regex("[\\s,;:!?]+")).filter { it.isNotBlank() }
        val last = words.lastOrNull()?.lowercase()?.trim('"', '\'', '“', '”') ?: return false
        if (last in DANGLING) return true
        // a kérdőszóval induló utolsó tagmondat pont nélkül/ponttal végződve is kérdés → folytatódik
        val clause = t.trimEnd('.').split(Regex("[.?!]")).last().trim().lowercase()
        val cw = clause.split(Regex("[\\s,]+")).filter { it.isNotBlank() }
        return cw.size <= 6 && cw.take(3).any { it in WH }
    }

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
                val sentenceEnd = ends && !(ellipsis && nextLower) && !(falseStop(t) && gap <= maxOf(maxGapMs, 2500L))
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

    /** Magyar szavak, amelyekre egy sor nem végződhet szépen (névelő, kötőszó, igekötő). */
    private val HU_DANGLING = setOf(
        "a", "az", "egy", "és", "hogy", "de", "vagy", "meg", "is", "mint", "mert", "ha", "ki", "el", "fel", "le", "be", "át"
    )

    /** A vágás "ára" a cand. szó után (kisebb = szebb): mondatvégi írásjel a legjobb, a függő szó a legrosszabb. */
    private fun cutCost(words: List<String>, cand: Int): Double {
        if (cand >= words.size) return 0.0
        val w = words[cand - 1]
        var c = when (w.last()) {
            '.', '?', '!', '…' -> 0.0
            ',', ';', ':' -> 1.0
            else -> 2.5
        }
        val core = w.lowercase().filter { it.isLetterOrDigit() }
        if (core in HU_DANGLING) c += 2.0
        return c
    }

    /**
     * A cél-szöveg szétosztása a forrásblokkok hosszának arányában. A vágáspontot az arányos helytől legfeljebb
     * 4 szóval arrébb keresi: a mondatvégi írásjel a legjobb, utána a vessző; kötőszó, névelő után nem vág szívesen.
     * (A fordítás szórendje eltér az angoltól, ezért a tisztán arányos vágás gyakran mondat közepére esne.)
     */
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
            var e: Int
            if (i == n - 1) {
                e = words.size
            } else {
                val ideal = words.size * acc.toDouble() / total
                // ha elég szó van, minden résznek jusson legalább egy
                val enough = words.size - used >= n - i
                val minE = minOf(words.size, used + (if (enough) 1 else 0))
                val maxE = maxOf(minE, words.size - (if (enough) n - 1 - i else 0))
                val base = ideal.roundToInt().coerceIn(minE, maxE)
                e = base
                var bestCost = Double.MAX_VALUE
                for (cand in maxOf(minE, base - 4)..minOf(maxE, base + 4)) {
                    val cost = kotlin.math.abs(cand - ideal) * 0.6 + cutCost(words, cand)
                    if (cost < bestCost - 1e-9) { bestCost = cost; e = cand }
                }
            }
            out.add(words.subList(used, e).joinToString(" "))
            used = e
        }
        return out
    }
}
