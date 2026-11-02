package hu.angolmagyar.fordito
object TranslationInvariantAudit {
    enum class Severity { WARNING }
    enum class Kind { NUMBER_MISMATCH, NEGATION_MISMATCH }
    data class Finding(val kind: Kind, val severity: Severity, val sourceTokens: List<String>, val targetTokens: List<String>, val message: String)
    private val numberPattern = Regex("(?<![\\p{L}])[-+]?\\d+(?:[.,]\\d+)?%?(?![\\p{L}])")
    private val negations = setOf("not", "n't", "no", "never", "neither", "nor", "without", "cannot", "can't", "don't", "doesn't", "didn't", "won't", "wouldn't", "shouldn't", "isn't", "aren't", "wasn't", "weren't", "nem", "ne", "nincs", "nincsen", "soha", "sem", "se", "nélkül", "nélkul", "tilos")
    fun audit(source: String, target: String): List<Finding> {
        if (source.isBlank() || target.isBlank()) return emptyList()
        val findings = mutableListOf<Finding>()
        val sourceNumbers = numberPattern.findAll(source).map { canonicalNumber(it.value) }.toList()
        val targetNumbers = numberPattern.findAll(target).map { canonicalNumber(it.value) }.toList()
        if (sourceNumbers.sorted() != targetNumbers.sorted()) {
            findings += Finding(Kind.NUMBER_MISMATCH, Severity.WARNING, sourceNumbers, targetNumbers,
                "A számszerű adatok eltérnek; ellenőrizd a fordítást.")
        }
        val sourceNegations = tokens(source).filter { it in negations }
        val targetNegations = tokens(target).filter { it in negations }
        if (sourceNegations.isNotEmpty() != targetNegations.isNotEmpty()) {
            findings += Finding(Kind.NEGATION_MISMATCH, Severity.WARNING, sourceNegations, targetNegations,
                "A tagadás jelenléte eltérhet; ellenőrizd a jelentést.")
        }
        return findings
    }
    private fun tokens(text: String): List<String> = text.lowercase()
        .replace(Regex("[^\\p{L}']+"), " ").trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    private fun canonicalNumber(raw: String): String = raw.trimEnd('%').replace(',', '.') + if (raw.endsWith('%')) "%" else ""
}
