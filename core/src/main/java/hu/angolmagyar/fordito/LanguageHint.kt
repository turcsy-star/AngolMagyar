package hu.angolmagyar.fordito

/**
 * Könnyű, offline nyelv-tipp HU / EN között (beszélgetés mód irányváltáshoz).
 * Nem helyettesít valódi language-ID modellt – karakter + gyakori szavak alapján.
 */
object LanguageHint {

    enum class Lang { HU, EN, UNKNOWN }

    private val HU_CHARS = Regex("[áéíóöőúüűÁÉÍÓÖŐÚÜŰ]")
    private val HU_WORDS = setOf(
        "a", "az", "és", "hogy", "nem", "van", "volt", "egy", "de", "is",
        "meg", "már", "még", "csak", "vagy", "ezt", "azt", "igen", "nem",
        "mi", "mit", "hol", "mikor", "miért", "hogyan", "kell", "lehet",
        "szerint", "után", "előtt", "között", "mivel", "tehát", "ezért"
    )
    private val EN_WORDS = setOf(
        "the", "is", "are", "was", "were", "you", "this", "that", "with",
        "for", "have", "has", "had", "will", "would", "can", "could",
        "what", "when", "where", "why", "how", "not", "from", "they",
        "we", "our", "your", "about", "into", "than", "then", "there"
    )

    fun detect(text: String): Lang {
        val t = text.trim()
        if (t.length < 3) return Lang.UNKNOWN

        var huScore = 0
        var enScore = 0

        if (HU_CHARS.containsMatchIn(t)) huScore += 4

        val words = t.lowercase()
            .split(Regex("[^\\p{L}]+"))
            .filter { it.length >= 2 }

        for (w in words) {
            if (w in HU_WORDS) huScore += 2
            if (w in EN_WORDS) enScore += 2
        }

        // angol tipikus "th", "ing"
        if (Regex("\\b\\w+ing\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) enScore += 1
        if (Regex("\\bth(e|is|at|ere)\\b", RegexOption.IGNORE_CASE).containsMatchIn(t)) enScore += 1

        return when {
            huScore >= enScore + 2 && huScore >= 2 -> Lang.HU
            enScore >= huScore + 2 && enScore >= 2 -> Lang.EN
            huScore > enScore && huScore >= 3 -> Lang.HU
            enScore > huScore && enScore >= 3 -> Lang.EN
            else -> Lang.UNKNOWN
        }
    }

    /**
     * Beszélgetés módban: true = forrás magyar (HU→EN), false = forrás angol (EN→HU).
     * Csak erős jel esetén vált; különben null (ne változtass).
     */
    fun suggestConversationHuToEn(text: String, currentHuToEn: Boolean): Boolean? {
        return when (detect(text)) {
            Lang.HU -> if (!currentHuToEn) true else null
            Lang.EN -> if (currentHuToEn) false else null
            Lang.UNKNOWN -> null
        }
    }
}
