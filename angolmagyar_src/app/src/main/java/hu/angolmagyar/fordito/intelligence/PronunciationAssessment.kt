package hu.angolmagyar.fordito.intelligence

/** Compares a target phrase with a user-provided speech-recognition transcript (word error rate). */
data class PronunciationResult(val scorePercent: Int, val substitutions: Int, val deletions: Int, val insertions: Int, val referenceWords: Int)

object PronunciationAssessment {
    fun assess(reference: String, recognized: String): PronunciationResult {
        val a = tokens(reference); val b = tokens(recognized)
        if (a.isEmpty()) return PronunciationResult(0, 0, 0, b.size, 0)
        val d = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 0..a.size) d[i][0] = i
        for (j in 0..b.size) d[0][j] = j
        for (i in 1..a.size) for (j in 1..b.size) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
        }
        var i = a.size; var j = b.size; var sub = 0; var del = 0; var ins = 0
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && a[i-1] == b[j-1] && d[i][j] == d[i-1][j-1] -> { i--; j-- }
                i > 0 && j > 0 && d[i][j] == d[i-1][j-1] + 1 -> { sub++; i--; j-- }
                i > 0 && d[i][j] == d[i-1][j] + 1 -> { del++; i-- }
                else -> { ins++; j-- }
            }
        }
        val errors = sub + del + ins
        return PronunciationResult(((1.0 - errors.toDouble() / a.size).coerceIn(0.0, 1.0) * 100).toInt(), sub, del, ins, a.size)
    }
    private fun tokens(value: String) = Regex("[\\p{L}\\p{N}']+").findAll(value.lowercase()).map { it.value }.toList()
}
