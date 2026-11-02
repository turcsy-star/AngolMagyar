package hu.angolmagyar.fordito
object WhisperPrompt {
    private const val MAX_PROMPT_CHARS = 220
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
    private fun usableTail(prev: String): String? {
        val t = prev.trim().replace(Regex("\\s+"), " ")
        if (t.length < MIN_TAIL_CHARS) return null
        if (t.count { it.isLetter() } < MIN_TAIL_CHARS * 2 / 3) return null
        return t
    }
    private fun cutAtWord(s: String, max: Int): String {
        if (s.length <= max) return s
        val cut = s.takeLast(max)
        val sp = cut.indexOf(' ')
        return if (sp in 0 until cut.length - 1) cut.substring(sp + 1) else cut
    }
}
