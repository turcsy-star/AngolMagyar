package hu.angolmagyar.fordito

/**
 * Magyar helyesírási és tipográfiai utójavítás a gépi fordítás kimenetén.
 * Csak olyan szabályokat tartalmaz, amelyeknek elhanyagolható a téves találati kockázata:
 *  - "a" → "az" magánhangzóval kezdődő szó előtt (a fordítók gyakori hibája),
 *  - idézőjelek: "…" és “…” → „…”,
 *  - számok: 1,000,000 → 1 000 000; 3.5 % → 3,5 %; 5kg → 5 kg,
 *  - írásjel utáni hiányzó szóköz (, ; ! ?),
 *  - hónapok és napok kisbetűje mondat közben.
 */
object HungarianRules {
    data class Options(
        val articles: Boolean = true,
        val quotes: Boolean = true,
        val numbers: Boolean = true,
        val spacing: Boolean = true,
        val casing: Boolean = true
    )

    private const val NBSP = ' '
    private const val VOWELS = "aáeéiíoóöőuúüűAÁEÉIÍOÓÖŐUÚÜŰ"
    private const val UNITS =
        "%|százalék|fok|°|km|kg|dkg|cm|mm|ml|dollár|euró|forint|font|méter|kilométer|kiló|liter|óra|perc|másodperc|millió|milliárd"

    private val ARTICLE = Regex("(?<![\\p{L}\\d'’-])([Aa])(\\s+)(\\p{L}+)")
    private val STRAIGHT_QUOTES = Regex("\"([^\"\\r\\n]{1,300})\"")
    private val CURLY_QUOTES = Regex("“([^”\\r\\n]{1,300})”")
    private val THOUSANDS = Regex("(?<![\\d,.])(\\d{1,3})((?:,\\d{3}){2,})(?![\\d,])")
    private val DECIMAL_DOT = Regex("(?<![\\d.,])(\\d{1,3})\\.(\\d{1,2})(?=\\s?(?:$UNITS)(?![\\p{L}]))")
    private val UNIT_SPACE = Regex("(?<![\\p{L}\\d.,])(\\d+(?:[.,]\\d+)?)(kg|dkg|km|cm|mm|ml)(?![\\p{L}])")
    private val MISSING_SPACE = Regex("([,;!?])(?=\\p{L})")
    private val DAYS_MONTHS = listOf(
        "január", "február", "március", "április", "május", "június", "július", "augusztus",
        "szeptember", "október", "november", "december",
        "hétfő", "kedd", "szerda", "csütörtök", "péntek", "szombat", "vasárnap"
    )
    private val CASING = Regex(
        "(?<=[\\p{Ll}\\d,;]\\s)(" +
            DAYS_MONTHS.joinToString("|") { it.replaceFirstChar { c -> c.uppercaseChar() } } +
            ")(\\p{L}*+)(?!\\s+\\p{Lu})"
    )

    fun apply(text: String, o: Options = Options()): String {
        if (text.isBlank()) return text
        var t = text
        if (o.quotes) t = fixQuotes(t)
        if (o.numbers) t = fixNumbers(t)
        if (o.spacing) t = MISSING_SPACE.replace(t, "$1 ")
        if (o.casing) t = fixCasing(t)
        if (o.articles) t = fixArticles(t)
        return t
    }

    fun fixArticles(text: String): String = ARTICLE.replace(text) { m ->
        val word = m.groupValues[3]
        val acronym = word.length >= 2 && word.all { it.isUpperCase() }
        if (!acronym && VOWELS.indexOf(word[0]) >= 0) {
            val az = if (m.groupValues[1] == "A") "Az" else "az"
            az + m.groupValues[2] + word
        } else m.value
    }

    fun fixQuotes(text: String): String {
        if (text.contains("=\"") || text.contains('<')) return text
        var t = CURLY_QUOTES.replace(text) { "„" + it.groupValues[1] + "”" }
        if (t.count { it == '"' } % 2 == 0) t = STRAIGHT_QUOTES.replace(t) { "„" + it.groupValues[1] + "”" }
        return t
    }

    fun fixNumbers(text: String): String {
        var t = THOUSANDS.replace(text) { m ->
            m.groupValues[1] + m.groupValues[2].split(',').filter { it.isNotEmpty() }.joinToString("") { NBSP + it }
        }
        t = DECIMAL_DOT.replace(t) { it.groupValues[1] + "," + it.groupValues[2] }
        t = UNIT_SPACE.replace(t) { it.groupValues[1] + " " + it.groupValues[2] }
        return t
    }

    fun fixCasing(text: String): String = CASING.replace(text) { m ->
        m.groupValues[1].replaceFirstChar { it.lowercaseChar() } + m.groupValues[2]
    }
}
