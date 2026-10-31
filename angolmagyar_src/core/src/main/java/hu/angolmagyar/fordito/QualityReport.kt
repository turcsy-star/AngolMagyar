package hu.angolmagyar.fordito

/** A kész felirat minőség-összegzése (a végén és az ellenőrző nézet tetején). */
object QualityReport {
    data class Report(
        val total: Int, val flagged: Int, val untranslated: Int, val uncertain: Int,
        val maxOverlapMs: Long,
        /** A legsürgősebb ellenőrzést igénylő sorok indexei, súlyosság szerint rendezve. */
        val worst: List<Int>
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
        // A sorrend szándékosan súlyossági: a felület a legfontosabb hibákat mutassa először,
        // ne alakítsa vissza őket időrendi sorrendbe.
        val score = flags.mapIndexed { i, f -> i to f.sumOf { w(it) } }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenBy { it.first })
        return Report(
            total = cues.size,
            flagged = flags.count { it.isNotEmpty() },
            untranslated = untr,
            uncertain = unc,
            maxOverlapMs = overlap.coerceAtLeast(0),
            worst = score.take(10).map { it.first }
        )
    }

    private fun w(flag: String): Int = when (flag) {
        "üres", "fordítatlan", "hibás idő" -> 5
        // tartalmi hibák: a szám, név és tagadás eltérése a legveszélyesebb fordítási hiba
        "szám eltér", "tagadás eltér" -> 5
        "név hiányzik" -> 4
        "bizonytalan felismerés", "gyenge szókonfidencia", "magányos", "ismétlődés" -> 3
        "beszéd/csend ellenőrzése", "töredék", "hossz-arány" -> 2
        else -> 1
    }
}
