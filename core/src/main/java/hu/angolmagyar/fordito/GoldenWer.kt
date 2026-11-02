package hu.angolmagyar.fordito
object GoldenWer {
    data class Case(val id: String, val reference: String, val hypothesis: String, val maxWer: Double)
    data class Outcome(val id: String, val wer: Double, val maxWer: Double, val referenceWords: Int) {
        val passed: Boolean get() = wer <= maxWer + 1e-9
    }
    fun parseManifest(csv: String): Map<String, Double> = csv.lineSequence()
        .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        .mapNotNull { line ->
            val p = line.split(',', limit = 2)
            if (p.size < 2) null else p[1].trim().replace(',', '.').toDoubleOrNull()?.let { p[0].trim() to it }
        }.toMap()
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
