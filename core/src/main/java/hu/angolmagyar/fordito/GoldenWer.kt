package hu.angolmagyar.fordito

/**
 * Arany-referencia regressziós mérés: kézzel ellenőrzött referenciaátirat és a pipeline kimenete közti szó-hibaarány
 * (WER). A CI minden verziónál összeveti az ismert alapvonallal; a pontosság így nem romolhat észrevétlenül.
 * A WER csak a felismerést méri, nem a fordítás szemantikai jóságát.
 */
object GoldenWer {
    data class Case(val id: String, val reference: String, val hypothesis: String, val maxWer: Double)
    data class Outcome(val id: String, val wer: Double, val maxWer: Double, val referenceWords: Int) {
        val passed: Boolean get() = wer <= maxWer + 1e-9
    }

    /** Sorok: `id,maxWer` ('#' kezdetű és üres sorok kimaradnak; tizedespont vagy tizedesvessző is jó). */
    fun parseManifest(csv: String): Map<String, Double> = csv.lineSequence()
        .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val p = line.split(',', limit = 2)
            if (p.size < 2) null else p[1].trim().replace(',', '.').toDoubleOrNull()?.let { p[0].trim() to it }
        }.toMap()

    /** SRT-ből csak a beszélt szöveg (sorszám és idő nélkül); sima szöveget változatlanul hagy. */
    fun plainText(text: String): String {
        if (!text.contains("-->")) return text
        return SubFormat.parseSrt(text).joinToString(" ") { it.source.ifBlank { it.target } }
    }

    fun evaluate(cases: List<Case>): List<Outcome> = cases.map {
        val r = WordErrorRate.compare(it.reference, plainText(it.hypothesis))
        Outcome(it.id, r.rate, it.maxWer, r.referenceWords)
    }

    fun failures(outcomes: List<Outcome>): List<Outcome> = outcomes.filter { !it.passed }

    fun report(outcomes: List<Outcome>): String = outcomes.joinToString("\n") {
        "%s: WER %.1f%% (határ %.1f%%, %d szó) %s".format(java.util.Locale.ROOT, it.id, it.wer * 100, it.maxWer * 100, it.referenceWords, if (it.passed) "OK" else "ROMLÁS")
    }
}
