package hu.angolmagyar.fordito

/**
 * A Whisper kezdő promptja (initial prompt): a felismert nyelven írt, természetes hangvételű szöveg,
 * amely a központozást, az ékezeteket, a tulajdonneveket és a szakszavakat segíti.
 *
 * Fontos: a prompt nyelve egyezzen a beszéd nyelvével (egy angol prompt a magyar felismerést rontja).
 * Az 'Előző/Previous' kontextus az előző darab vége, ha elég hosszú és értelmes.
 */
object WhisperPrompt {

    /** A prompt legfeljebb ennyi karakter (a Whisper úgyis csak a végét használja, a hosszú prompt lassít). */
    private const val MAX_PROMPT_CHARS = 220

    /** Ennél rövidebb, betűt alig tartalmazó "előző szöveg" zaj, nem használjuk kontextusnak. */
    private const val MIN_TAIL_CHARS = 12

    enum class Domain(val hu: String, val en: String) {
        GENERAL(
            "Hétköznapi beszélgetés.",
            "General everyday conversation."
        ),
        SUBTITLE(
            "Film- vagy sorozatfelirat, természetes párbeszéd.",
            "Movie or TV series dialogue, natural conversation."
        ),
        BUSINESS(
            "Üzleti megbeszélés, szerződések, pénzügy.",
            "Business meeting, contracts, finance."
        ),
        TRAVEL(
            "Utazás, repülőtér, szállás, étterem.",
            "Travel, airport, hotel, restaurant."
        ),
        TECH(
            "Technológia, szoftverfejlesztés, informatika.",
            "Technology, software engineering, IT."
        ),
        SLANG(
            "Laza, köznyelvi beszélgetés, szólások.",
            "Casual street talk, idioms, colloquialisms."
        )
    }

    fun build(
        lang: String = "hu",
        castNames: List<String> = emptyList(),
        prevTail: String = "",
        domain: String = "general",
        terms: List<String> = emptyList()
    ): String {
        val hu = lang.equals("hu", ignoreCase = true)
        val d = Domain.values().firstOrNull { it.name.equals(domain, ignoreCase = true) } ?: Domain.GENERAL

        val head = StringBuilder()
        head.append(
            if (hu) "Magyar nyelvű beszélgetés, helyes központozással és ékezetekkel. "
            else "English conversation, with correct punctuation and capitalization. "
        )
        head.append(if (hu) d.hu else d.en).append(' ')

        val names = castNames.map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(8)
        if (names.isNotEmpty()) {
            head.append(if (hu) "Nevek: " else "Names: ").append(names.joinToString(", ")).append(". ")
        }

        var result = head.toString().trim()
        if (result.length > MAX_PROMPT_CHARS) result = result.take(MAX_PROMPT_CHARS).trimEnd()

        // Egyéni szótár: csak a javított/kanonikus alakok kerülnek a promptba, a hibás alakok nem.
        val knownTerms = terms.map { it.trim() }
            .filter { it.length in 2..48 && it.any(Char::isLetter) }
            .distinctBy { it.lowercase() }
            .filterNot { term -> names.any { it.equals(term, ignoreCase = true) } }
            .take(8)
        if (knownTerms.isNotEmpty()) {
            val label = if (hu) " Kifejezések: " else " Terms: "
            val room = MAX_PROMPT_CHARS - result.length - label.length
            if (room >= 8) {
                val selected = knownTerms.joinToString(", ")
                val suffix = if (selected.length <= room) selected else
                    selected.take(room).substringBeforeLast(' ', selected.take(room)).trimEnd(',', ' ')
                if (suffix.isNotBlank()) result += label + suffix
            }
        }

        val tail = usableTail(prevTail)
        if (tail != null) {
            val label = if (hu) " Előző: " else " Previous: "
            val room = MAX_PROMPT_CHARS - result.length - label.length
            if (room >= MIN_TAIL_CHARS) {
                val t = cutAtWord(tail, room)
                if (t.length >= MIN_TAIL_CHARS) result += label + t
            }
        }
        return result
    }

    /** Az előző darab vége: csak ha érdemi (nem pár hang), a végéből szóhatáron vágva. */
    private fun usableTail(prev: String): String? {
        val t = prev.trim().replace(Regex("\\s+"), " ")
        if (t.length < MIN_TAIL_CHARS) return null
        if (t.count { it.isLetter() } < MIN_TAIL_CHARS * 2 / 3) return null
        return t
    }

    /** A szöveg utolsó [max] karaktere, szó közepén nem kezdve. */
    private fun cutAtWord(s: String, max: Int): String {
        if (s.length <= max) return s
        val cut = s.takeLast(max)
        val sp = cut.indexOf(' ')
        return if (sp in 0 until cut.length - 1) cut.substring(sp + 1) else cut
    }
}
