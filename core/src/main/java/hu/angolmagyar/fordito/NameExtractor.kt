package hu.angolmagyar.fordito

/**
 * Szereplők nevei az angol átiratból: a mondat közben nagybetűvel írt, többször előforduló szavak
 * (megszólítások vesszővel is számítanak). A fordítónak háttérként adjuk, hogy a neveket ne fordítsa le.
 */
object NameExtractor {
    private val STOP = setOf(
        "i", "i'm", "i'll", "i've", "i'd", "oh", "okay", "ok", "yeah", "yes", "no", "well", "hey", "hi", "hello", "god",
        "mom", "dad", "thanks", "thank", "please", "sorry", "alright", "right", "so", "but", "and", "the", "you",
        "he", "she", "it", "we", "they", "what", "why", "how", "who", "when", "where", "let", "let's", "just", "now",
        "then", "that", "this", "there", "here", "come", "look", "maybe", "wow", "mr", "mrs", "ms", "dr", "bye",
        "good", "great", "love", "really", "uh", "um", "mm", "hmm", "not", "do", "don't", "can", "can't", "is", "are",
        "was", "were", "my", "your", "his", "her", "our", "their", "if", "because", "sir", "babe", "baby", "honey",
        "guys", "man", "dude", "mommy", "daddy", "gosh", "damn", "fuck", "shit", "monday", "tuesday", "wednesday",
        "thursday", "friday", "saturday", "sunday", "english", "yep", "nope", "ah", "aha", "ooh", "ow"
    )
    private val SENT_END = ".?!…\"”"

    /** Egy szöveg mondat közbeni (vagy megszólításként álló) nagybetűs szavai. */
    fun capitalizedTokens(t: String): List<String> {
        val out = ArrayList<String>()
        val toks = t.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for ((i, raw) in toks.withIndex()) {
            val vocative = raw.endsWith(",")
            val sentenceStart = i == 0 || toks[i - 1].last() in SENT_END
            if (sentenceStart && !vocative) continue
            var w = raw.trim { !it.isLetter() && it != '\'' }
            if (w.endsWith("'s") || w.endsWith("\u2019s")) w = w.dropLast(2)
            if (w.length < 3 || !w[0].isUpperCase() || w.all { it.isUpperCase() }) continue
            if (w.lowercase() in STOP) continue
            out.add(w)
        }
        return out
    }

    fun extract(texts: List<String>, minCount: Int = 2, max: Int = 12): List<String> {
        val counts = LinkedHashMap<String, Int>()
        for (t in texts) for (w in capitalizedTokens(t)) counts[w] = (counts[w] ?: 0) + 1
        return counts.entries.filter { it.value >= minCount }.sortedByDescending { it.value }.take(max).map { it.key }
    }
}
