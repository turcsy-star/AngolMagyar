package hu.angolmagyar.fordito
object PronunciationCoach {
    enum class WordStatus { CORRECT, WRONG, MISSING }
    data class WordResult(val word: String, val heard: String?, val status: WordStatus)
    data class Analysis(
        val words: List<WordResult>,
        val extras: List<String>,
        val scorePercent: Int,
        val practiceWords: List<String>,
        val tips: List<String>
    )
    private val TOKEN = Regex("[\\p{L}\\p{N}']+")
    private fun tokens(value: String): List<String> =
        TOKEN.findAll(value.replace('’', '\'').replace('‘', '\'').lowercase())
            .map { it.value.trim('\'') }.filter { it.isNotEmpty() }.toList()
    fun analyze(target: String, recognized: String): Analysis {
        val a = tokens(target)
        val b = tokens(recognized)
        if (a.isEmpty()) return Analysis(emptyList(), b, 0, emptyList(), emptyList())
        val d = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in 0..a.size) d[i][0] = i
        for (j in 0..b.size) d[0][j] = j
        for (i in 1..a.size) for (j in 1..b.size) {
            val cost = if (a[i - 1] == b[j - 1]) 0 else 1
            d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
        }
        val rev = ArrayList<WordResult>()
        val extras = ArrayList<String>()
        var i = a.size
        var j = b.size
        while (i > 0 || j > 0) {
            when {
                i > 0 && j > 0 && a[i - 1] == b[j - 1] && d[i][j] == d[i - 1][j - 1] -> {
                    rev.add(WordResult(a[i - 1], b[j - 1], WordStatus.CORRECT)); i--; j--
                }
                i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + 1 -> {
                    rev.add(WordResult(a[i - 1], b[j - 1], WordStatus.WRONG)); i--; j--
                }
                i > 0 && d[i][j] == d[i - 1][j] + 1 -> {
                    rev.add(WordResult(a[i - 1], null, WordStatus.MISSING)); i--
                }
                else -> { extras.add(b[j - 1]); j-- }
            }
        }
        val words = rev.reversed()
        extras.reverse()
        val errors = words.count { it.status != WordStatus.CORRECT } + extras.size
        val score = ((1.0 - errors.toDouble() / a.size).coerceIn(0.0, 1.0) * 100).toInt()
        val practice = words.filter { it.status != WordStatus.CORRECT }.map { it.word }.distinct()
        return Analysis(words, extras, score, practice, tipsFor(practice))
    }
    private val SILENT_H = setOf("hour", "honest", "honor", "honour", "heir")
    fun tipsFor(words: List<String>): List<String> {
        val tips = LinkedHashSet<String>()
        for (raw in words) {
            val w = raw.lowercase()
            if (w in SILENT_H) tips.add("A „$w” elején a h néma.")
            else if (w.startsWith("h")) tips.add("A h-t ejtsd ki halkan, leheletszerűen („$w”), de ne köhögd.")
            if (w.contains("th")) tips.add("A „th” (pl. „$w”): a nyelvhegy a fogak közé kerül, nem t, d, sz vagy z.")
            if (w.contains("w") && !w.contains("wh")) tips.add("A „w” (pl. „$w”) kerekített ajakkal készül, nem v.")
            if (w.length > 3 && w.endsWith("ed")) tips.add("Az „-ed” végződés t, d vagy id hang: worked /t/, played /d/, wanted /ɪd/.")
            if (w.contains("ough")) tips.add("Az „ough” többféleképpen ejtődik (though, through, enough, cough): „$w” szót érdemes szótárból megtanulni.")
            if (w.endsWith("ng") || w.endsWith("ing")) tips.add("Az „ng” egyetlen orrhang („$w”), nem n + g.")
            if (w.endsWith("tion") || w.endsWith("sion")) tips.add("A „-tion” /ʃn/: „$w” végén sh-hang van, nem „ci-on”.")
            if (w.startsWith("r")) tips.add("Az angol r nem peregtetett: a nyelv nem érinti a szájpadlást („$w”).")
            if (w.contains("ee") || w.contains("ea")) tips.add("Figyelj a hosszú /iː/ és a rövid /ɪ/ különbségére (ship – sheep).")
            if (tips.size >= 4) break
        }
        return tips.take(4)
    }
    fun slowChunks(target: String, maxWords: Int = 3): List<String> =
        target.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.chunked(maxWords.coerceAtLeast(1)).map { it.joinToString(" ") }
    fun trend(scores: List<Int>): String {
        val last = scores.takeLast(5)
        if (last.size < 2) return "kevés adat"
        val diff = last.last() - last.first()
        return when {
            diff >= 5 -> "javul"
            diff <= -5 -> "romlik"
            else -> "stabil"
        }
    }
}
