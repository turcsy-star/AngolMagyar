package hu.angolmagyar.fordito

/** A kész felirat minőség-összegzése (a végén és az ellenőrző nézet tetején). */
object QualityReport {
    data class Report(
        val total: Int, val flagged: Int, val untranslated: Int, val uncertain: Int,
        val maxOverlapMs: Long, val worst: List<Int>
    ) {
        fun lines(): List<String> {
            val out = ArrayList<String>()
            out.add("$total blokk · $flagged jelölt sor")
            if (untranslated > 0) out.add("$untranslated fordítatlan sor")
            if (uncertain > 0) out.add("$uncertain bizonytalan felismerés")
            if (maxOverlapMs > 300) out.add("legnagyobb időzítési átfedés: ${maxOverlapMs} ms")
            return out
        }
    }

    fun build(cues: List<SrtExporter.Cue>): Report {
        val flags = LineQuality.summarize(cues)
        var untr = 0
        var unc = 0
        var overlap = 0L
        for ((i, f) in flags.withIndex()) {
            if ("fordítatlan" in f) untr++
            if ("bizonytalan felismerés" in f) unc++
            if (i > 0) overlap = maxOf(overlap, cues[i - 1].endMs - cues[i].startMs)
        }
        val score = flags.mapIndexed { i, f -> i to f.sumOf { w(it) } }.filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
        return Report(cues.size, flags.count { it.isNotEmpty() }, untr, unc, overlap.coerceAtLeast(0), score.take(10).map { it.first }.sorted())
    }

    private fun w(flag: String): Int = when (flag) {
        "üres", "fordítatlan", "hibás idő" -> 5
        "bizonytalan felismerés", "magányos", "ismétlődés" -> 3
        else -> 1
    }
}
