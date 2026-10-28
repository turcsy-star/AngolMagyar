package hu.angolmagyar.fordito

/**
 * A Whisper hajlamos "hallucinálni", ha zajt, nyögést vagy hosszú csendet kap: ugyanazt a mondatot ismételgeti,
 * vagy magányos, értelmetlen sort ír a hosszú szünetek közepére. Ez a szűrő a kész blokkokat tisztítja meg.
 *
 * Két szabály (mindkettő óvatos, és a végén a hívó kiírja, hány blokkot dobott el):
 *  A) ismétlődő hosszabb mondat: a harmadik, egymást követő azonos szöveg gyanús és eltávolítható;
 *  B) magányos rövid sor és C) alacsony logprob csak jelzés, automatikusan nem törlünk valódi beszédet.
 * A magányos vagy bizonytalan szegmens lehet halk, de helyes megszólalás is.
 */
object Hallucination {

    data class Result(
        val cues: List<SrtExporter.Cue>, val repeats: Int, val isolated: Int,
        val lowConf: Int = 0, val removed: List<SrtExporter.Cue> = emptyList()
    ) {
        /** Csak a ténylegesen eltávolított blokkok száma. A többi számláló figyelmeztetés, nem törlés. */
        val dropped: Int get() = removed.size
        val flagged: Int get() = isolated + lowConf
    }

    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")

    private fun textOf(c: SrtExporter.Cue): String = c.target.ifBlank { c.source }

    private fun key(c: SrtExporter.Cue): String =
        textOf(c).lowercase().replace(NON_WORD, " ").trim()

    private fun words(c: SrtExporter.Cue): Int = key(c).split(' ').count { it.isNotEmpty() }

    fun clean(
        cues: List<SrtExporter.Cue>,
        minRepeatWords: Int = QualityRules.REPEAT_MIN_WORDS,
        maxSameText: Int = 2,
        repeatWindowMs: Long = 30_000L,
        isolatedGapMs: Long = QualityRules.ISOLATED_GAP_MS,
        isolatedMaxWords: Int = QualityRules.ISOLATED_MAX_WORDS,
        lowConfLp: Float = QualityRules.REVIEW_LP,
        lowConfMaxWords: Int = 8
    ): Result {
        if (cues.size < 2) return Result(cues, 0, 0)
        val sorted = cues.sortedBy { it.startMs }

        // A) ismétlődő hosszabb mondat
        val seen = HashMap<String, Pair<Int, Long>>() // ismétlésszám + utolsó előfordulás vége
        val afterA = ArrayList<SrtExporter.Cue>(sorted.size)
        val removed = ArrayList<SrtExporter.Cue>()
        var repeats = 0
        var lowConf = 0
        for (c in sorted) {
            if (words(c) >= minRepeatWords) {
                val k = key(c)
                val previous = seen[k]
                val n = if (previous != null && c.startMs - previous.second in 0..repeatWindowMs)
                    previous.first + 1 else 1
                seen[k] = n to c.endMs
                if (n > maxSameText) { repeats++; removed.add(c); continue }
            }
            // A logprob önmagában nem bizonyítja, hogy a szöveg hibás. Megőrizzük, a szerkesztő jelzi.
            if (c.lp.isFinite() && c.lp < lowConfLp && words(c) <= lowConfMaxWords) lowConf++
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
            if (alone && words(c) <= isolatedMaxWords) isolated++
            // Magányos rövid szegmens is lehet valódi megszólalás; automatikus törlés helyett jelöljük.
            out.add(c)
        }
        return Result(out, repeats, isolated, lowConf, removed.sortedBy { it.startMs })
    }
}
