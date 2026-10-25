package hu.angolmagyar.fordito

/**
 * A Whisper hajlamos "hallucinálni", ha zajt, nyögést vagy hosszú csendet kap: ugyanazt a mondatot ismételgeti,
 * vagy magányos, értelmetlen sort ír a hosszú szünetek közepére. Ez a szűrő a kész blokkokat tisztítja meg.
 *
 * Két szabály (mindkettő óvatos, és a végén a hívó kiírja, hány blokkot dobott el):
 *  A) ismétlődő hosszabb mondat: ugyanaz a (legalább [minRepeatWords] szavas) szöveg a 3. előfordulástól kiesik;
 *  B) magányos rövid sor: legfeljebb [isolatedMaxWords] szavas blokk, amelynek mindkét oldalán legalább
 *     [isolatedGapMs] a csend (az első / utolsó blokknál a hiányzó oldal nem számít akadálynak);
 *  C) nagyon bizonytalan felismerés: rövid blokk, amelynek a Whisper-valószínűsége [lowConfLp] alatt van.
 */
object Hallucination {

    data class Result(
        val cues: List<SrtExporter.Cue>, val repeats: Int, val isolated: Int,
        val lowConf: Int = 0, val removed: List<SrtExporter.Cue> = emptyList()
    ) {
        val dropped: Int get() = repeats + isolated + lowConf
    }

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    private fun textOf(c: SrtExporter.Cue): String = c.target.ifBlank { c.source }

    private fun key(c: SrtExporter.Cue): String =
        textOf(c).lowercase().replace(NON_WORD, " ").trim()

    private fun words(c: SrtExporter.Cue): Int = key(c).split(' ').count { it.isNotEmpty() }

    fun clean(
        cues: List<SrtExporter.Cue>,
        minRepeatWords: Int = 4,
        maxSameText: Int = 2,
        isolatedGapMs: Long = 90_000L,
        isolatedMaxWords: Int = 6,
        lowConfLp: Float = -1.5f,
        lowConfMaxWords: Int = 8
    ): Result {
        if (cues.size < 2) return Result(cues, 0, 0)
        val sorted = cues.sortedBy { it.startMs }

        // A) ismétlődő hosszabb mondat
        val seen = HashMap<String, Int>()
        val afterA = ArrayList<SrtExporter.Cue>(sorted.size)
        val removed = ArrayList<SrtExporter.Cue>()
        var repeats = 0
        var lowConf = 0
        for (c in sorted) {
            if (words(c) >= minRepeatWords) {
                val k = key(c)
                val n = (seen[k] ?: 0) + 1
                seen[k] = n
                if (n > maxSameText) { repeats++; removed.add(c); continue }
            }
            if (c.lp < lowConfLp && words(c) <= lowConfMaxWords) { lowConf++; removed.add(c); continue }
            afterA.add(c)
        }

        // B) magányos rövid sor a hosszú csendben
        var isolated = 0
        val out = ArrayList<SrtExporter.Cue>(afterA.size)
        for (i in afterA.indices) {
            val c = afterA[i]
            val prevGap = if (i == 0) Long.MAX_VALUE else c.startMs - afterA[i - 1].endMs
            val nextGap = if (i == afterA.size - 1) Long.MAX_VALUE else afterA[i + 1].startMs - c.endMs
            val alone = prevGap >= isolatedGapMs && nextGap >= isolatedGapMs &&
                !(prevGap == Long.MAX_VALUE && nextGap == Long.MAX_VALUE)
            if (alone && words(c) <= isolatedMaxWords) { isolated++; removed.add(c); continue }
            out.add(c)
        }
        return Result(out, repeats, isolated, lowConf, removed.sortedBy { it.startMs })
    }
}
