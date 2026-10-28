package hu.angolmagyar.fordito

/**
 * Referenciaátirat és felismert szöveg összehasonlítása regressziós tesztekhez.
 * Az írásjeleket és a kis-/nagybetűt normalizálja, de a magyar ékezeteket megőrzi.
 * Ez diagnosztikai mérőszám, nem a fordítás szemantikai minőségének mérője.
 */
object WordErrorRate {
    data class Result(
        val referenceWords: Int,
        val substitutions: Int,
        val deletions: Int,
        val insertions: Int
    ) {
        val errors: Int get() = substitutions + deletions + insertions
        val rate: Double get() = if (referenceWords == 0) {
            if (errors == 0) 0.0 else 1.0
        } else errors.toDouble() / referenceWords
    }

    private val NON_WORD = Regex("[^\\p{L}\\p{N}']+")

    fun normalize(text: String): List<String> = text
        .lowercase()
        .replace('’', '\'')
        .replace(NON_WORD, " ")
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }

    /** Levenshtein-hátsókövetés: helyettesítés, törlés és beszúrás külön számlálva. */
    fun compare(reference: String, hypothesis: String): Result {
        val ref = normalize(reference)
        val hyp = normalize(hypothesis)
        val n = ref.size
        val m = hyp.size
        val cost = Array(n + 1) { IntArray(m + 1) }
        val op = Array(n + 1) { CharArray(m + 1) }
        for (i in 1..n) { cost[i][0] = i; op[i][0] = 'D' }
        for (j in 1..m) { cost[0][j] = j; op[0][j] = 'I' }
        for (i in 1..n) for (j in 1..m) {
            if (ref[i - 1] == hyp[j - 1]) {
                cost[i][j] = cost[i - 1][j - 1]
                op[i][j] = 'M'
            } else {
                val sub = cost[i - 1][j - 1] + 1
                val del = cost[i - 1][j] + 1
                val ins = cost[i][j - 1] + 1
                val best = minOf(sub, del, ins)
                cost[i][j] = best
                op[i][j] = when (best) { sub -> 'S'; del -> 'D'; else -> 'I' }
            }
        }
        var i = n
        var j = m
        var substitutions = 0
        var deletions = 0
        var insertions = 0
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && op[i][j] == 'M' -> { i--; j-- }
                i > 0 && j > 0 && op[i][j] == 'S' -> { substitutions++; i--; j-- }
                i > 0 && (j == 0 || op[i][j] == 'D') -> { deletions++; i-- }
                j > 0 -> { insertions++; j-- }
            }
        }
        return Result(n, substitutions, deletions, insertions)
    }
}
