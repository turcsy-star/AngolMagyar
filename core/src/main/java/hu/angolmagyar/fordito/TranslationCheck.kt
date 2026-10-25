package hu.angolmagyar.fordito

/**
 * Fordítás-ellenőrzés (angol → magyar): a tipikus, csendben becsúszó hibák automatikus jelzése.
 *
 *  - "szám eltér": az angol szövegben lévő számjegyes szám nem szerepel a magyarban;
 *  - "név hiányzik": tulajdonnév / betűszó (nagybetűs szó mondat közben) nincs a magyar sorban;
 *  - "tagadás eltér": az angol tagad, a magyar sorban nincs tagadószó.
 *
 * Óvatos: inkább hagy ki jelzést, mint hogy hamis riasztással elvegye a figyelmet a valódi hibákról.
 */
object TranslationCheck {

    private const val L = "\\p{L}\\p{N}"
    private val NUM = Regex("\\d+(?:[ ,.]\\d+)*")
    private val WORD = Regex("[$L]+(?:['’-][$L]+)*")

    /** Számjegyes számok, elválasztók nélkül ("1,200" és "1 200" is 1200; "3,5" és "3.5" is 35). */
    fun numbers(s: String): List<String> =
        NUM.findAll(s).map { it.value.filter { c -> c.isDigit() } }.filter { it.isNotEmpty() }.toList()

    private val NOT_NAMES = setOf(
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday",
        "january", "february", "march", "april", "may", "june", "july", "august", "september", "october",
        "november", "december", "english", "american", "british", "hungarian", "german", "french", "spanish",
        "italian", "russian", "chinese", "japanese", "european", "african", "asian", "christmas", "easter",
        "mr", "mrs", "ms", "dr", "sir", "madam", "god", "internet", "okay", "ok", "i"
    )

    /** Mondat közben álló nagybetűs szavak (nem az első szó, nem "I"). */
    fun properNouns(en: String): List<String> {
        val out = LinkedHashSet<String>()
        val words = WORD.findAll(en).toList()
        for ((idx, m) in words.withIndex()) {
            val w = m.value
            if (w.length < 2 || !w[0].isUpperCase()) continue
            val acronym = w.length >= 2 && w.all { it.isUpperCase() || it.isDigit() }
            // mondatkezdő szó: az elején, vagy .!?… után
            val startsSentence = idx == 0 || en.substring(0, m.range.first).trimEnd().let {
                it.isEmpty() || it.last() in ".!?…\"“”"
            }
            if (startsSentence && !acronym) continue
            if (w.lowercase().removeSuffix("'s").removeSuffix("’s") in NOT_NAMES) continue
            if (w.length < 3 && !acronym) continue
            out.add(w.removeSuffix("'s").removeSuffix("’s"))
        }
        return out.toList()
    }

    private val NEG_EN = Regex(
        "(?<![$L])(?:not|no|never|nobody|nothing|nowhere|none|neither|nor|cannot|without)(?![$L])|n['’]t(?![$L])",
        RegexOption.IGNORE_CASE
    )
    private val NEG_HU = Regex(
        "(?<![$L])(?:nem|ne|nincs|nincsen|nincsenek|nincsenek|sem|se|soha|senki|semmi|semmit|sehol|sehová|" +
            "egyik sem|nélkül|tilos|lehetetlen|hiába|hiányzik|nélkülözik|tagad\\p{L}*)(?![$L])",
        RegexOption.IGNORE_CASE
    ) 

    /** A hibajelzések listája (üres = nincs észrevétel). */
    fun check(en: String, hu: String): List<String> {
        val out = ArrayList<String>(3)
        val e = en.trim()
        val h = hu.trim()
        if (e.isEmpty() || h.isEmpty()) return out

        // 1) számok
        val enNums = numbers(e).filter { it.length >= 2 }
        if (enNums.isNotEmpty()) {
            val huNums = numbers(h)
            val missing = enNums.filter { it !in huNums }
            if (missing.isNotEmpty() && (huNums.isNotEmpty() || missing.any { it.length >= 3 })) out.add("szám eltér")
        }

        // 2) nevek, betűszavak (ragozott alakot is elfogad: a név eleje benne van)
        val hl = h.lowercase()
        val names = properNouns(e)
        if (names.any { n ->
                val acronym = n.all { it.isUpperCase() || it.isDigit() }
                val key = n.lowercase()
                if (acronym) !hl.contains(key)
                else {
                    val stem = key.take(maxOf(3, key.length - 2))
                    !hl.contains(stem)
                }
            }) out.add("név hiányzik")

        // 3) tagadás
        if (NEG_EN.containsMatchIn(e) && !NEG_HU.containsMatchIn(h)) out.add("tagadás eltér")
        return out
    }
}
