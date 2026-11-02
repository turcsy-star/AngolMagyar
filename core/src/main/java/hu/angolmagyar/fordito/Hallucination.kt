package hu.angolmagyar.fordito
object Hallucination {
    data class Result(
        val cues: List<SrtExporter.Cue>, val repeats: Int, val isolated: Int,
        val lowConf: Int = 0, val removed: List<SrtExporter.Cue> = emptyList()
    ) {
        val dropped: Int get() = removed.size
        val flagged: Int get() = isolated + lowConf
    }
    private val NON_WORD = Regex("[^\\p{L}\\p{N}]+")
    fun looksLooped(text: String, minRepeats: Int = 3): Boolean {
        val w = text.lowercase().split(NON_WORD).filter { it.isNotEmpty() }
        if (w.size < 3 * minRepeats) return false
        val counts = HashMap<String, Int>()
        for (i in 0..w.size - 3) {
            val k = w[i] + " " + w[i + 1] + " " + w[i + 2]
            val n = (counts[k] ?: 0) + 1
            if (n >= minRepeats) return true
            counts[k] = n
        }
        return false
    }
    private fun textOf(c: SrtExporter.Cue): String = c.target.ifBlank { c.source }
    private fun key(c: SrtExporter.Cue): String =
        textOf(c).lowercase().replace(NON_WORD, " ").trim()
    private fun words(c: SrtExporter.Cue): Int = key(c).split(' ').count { it.isNotEmpty() }
    private fun tooFast(list: List<SrtExporter.Cue>, idx: Int, maxCps: Double): Boolean {
        val c = list[idx]
        val chars = maxOf(c.source.length, c.target.length)
        if (chars < 12) return false
        val own = c.endMs - c.startMs
        val gap = if (idx + 1 < list.size) list[idx + 1].startMs - c.startMs else own
        val dur = maxOf(100L, minOf(own, gap))
        return chars * 1000.0 / dur > maxCps
    }
    fun clean(
        cues: List<SrtExporter.Cue>,
        minRepeatWords: Int = QualityRules.REPEAT_MIN_WORDS,
        maxSameText: Int = 2,
        repeatWindowMs: Long = 30_000L,
        isolatedGapMs: Long = QualityRules.ISOLATED_GAP_MS,
        isolatedMaxWords: Int = QualityRules.ISOLATED_MAX_WORDS,
        lowConfLp: Float = QualityRules.REVIEW_LP,
        lowConfMaxWords: Int = 8,
        burstMaxCps: Double = 35.0,
        burstMinRun: Int = 4,
        repeatRunMin: Int = 3,
        repeatRunGapMs: Long = 30_000L,
        silentSpanMs: Long = 120_000L,
        silentSpanMaxWords: Int = 4
    ): Result {
        if (cues.size < 2) return Result(cues, 0, 0)
        val sorted = cues.sortedBy { it.startMs }
        val seen = HashMap<String, Pair<Int, Long>>()
        val afterRepeat = ArrayList<SrtExporter.Cue>(sorted.size)
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
            if (c.lp.isFinite() && c.lp < lowConfLp && words(c) <= lowConfMaxWords) lowConf++
            afterRepeat.add(c)
        }
        val runCleaned = ArrayList<SrtExporter.Cue>(afterRepeat.size)
        var ri = 0
        while (ri < afterRepeat.size) {
            val k0 = key(afterRepeat[ri])
            var rj = ri + 1
            if (k0.isNotEmpty() && words(afterRepeat[ri]) < minRepeatWords) {
                while (rj < afterRepeat.size && key(afterRepeat[rj]) == k0) rj++
            }
            val loopInSilence = rj - ri >= repeatRunMin &&
                (ri + 1 until rj).any { afterRepeat[it].startMs - afterRepeat[it - 1].startMs >= repeatRunGapMs }
            if (loopInSilence) {
                runCleaned.add(afterRepeat[ri])
                for (k in ri + 1 until rj) removed.add(afterRepeat[k])
                repeats += rj - ri - 1
            } else {
                for (k in ri until rj) runCleaned.add(afterRepeat[k])
            }
            ri = rj
        }
        val shortLong = ArrayList<SrtExporter.Cue>(runCleaned.size)
        for (c in runCleaned) {
            if (words(c) <= silentSpanMaxWords && c.endMs - c.startMs >= silentSpanMs) {
                removed.add(c); repeats++
            } else shortLong.add(c)
        }
        val kept = ArrayList<SrtExporter.Cue>(shortLong.size)
        var bi = 0
        while (bi < shortLong.size) {
            var bj = bi
            while (bj < shortLong.size && tooFast(shortLong, bj, burstMaxCps)) bj++
            if (bj - bi >= burstMinRun) {
                for (k in bi until bj) removed.add(shortLong[k])
                repeats += bj - bi
                bi = bj
            } else {
                kept.add(shortLong[bi])
                bi++
            }
        }
        var isolated = 0
        val out = ArrayList<SrtExporter.Cue>(kept.size)
        for (i in kept.indices) {
            val c = kept[i]
            val prevGap = if (i == 0) Long.MAX_VALUE else c.startMs - kept[i - 1].endMs
            val nextGap = if (i == kept.size - 1) Long.MAX_VALUE else kept[i + 1].startMs - c.endMs
            val alone = prevGap >= isolatedGapMs && nextGap >= isolatedGapMs &&
                !(prevGap == Long.MAX_VALUE && nextGap == Long.MAX_VALUE)
            if (alone && words(c) <= isolatedMaxWords) isolated++
            out.add(c)
        }
        return Result(out, repeats, isolated, lowConf, removed.sortedBy { it.startMs })
    }
}
