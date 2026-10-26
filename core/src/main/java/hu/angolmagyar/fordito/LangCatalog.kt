package hu.angolmagyar.fordito

/** Nyelvek katalógusa a célnyelv-választóhoz és a nyelvjelöléshez (BCP-47 kód → magyar név). */
object LangCatalog {
    /** (kód, magyar név, saját név) – az ML Kit fordító által támogatott nyelvek + gyakoriak. */
    val ALL: List<Triple<String, String, String>> = listOf(
        Triple("hu", "magyar", "Magyar"), Triple("en", "angol", "English"),
        Triple("de", "német", "Deutsch"), Triple("fr", "francia", "Français"),
        Triple("es", "spanyol", "Español"), Triple("it", "olasz", "Italiano"),
        Triple("pt", "portugál", "Português"), Triple("nl", "holland", "Nederlands"),
        Triple("pl", "lengyel", "Polski"), Triple("cs", "cseh", "Čeština"),
        Triple("sk", "szlovák", "Slovenčina"), Triple("sl", "szlovén", "Slovenščina"),
        Triple("hr", "horvát", "Hrvatski"), Triple("sr", "szerb", "Srpski"),
        Triple("ro", "román", "Română"), Triple("bg", "bolgár", "Български"),
        Triple("uk", "ukrán", "Українська"), Triple("ru", "orosz", "Русский"),
        Triple("el", "görög", "Ελληνικά"), Triple("tr", "török", "Türkçe"),
        Triple("sv", "svéd", "Svenska"), Triple("da", "dán", "Dansk"),
        Triple("no", "norvég", "Norsk"), Triple("fi", "finn", "Suomi"),
        Triple("et", "észt", "Eesti"), Triple("lv", "lett", "Latviešu"),
        Triple("lt", "litván", "Lietuvių"), Triple("is", "izlandi", "Íslenska"),
        Triple("ar", "arab", "العربية"), Triple("he", "héber", "עברית"),
        Triple("fa", "perzsa", "فارسی"), Triple("hi", "hindi", "हिन्दी"),
        Triple("bn", "bengáli", "বাংলা"), Triple("ur", "urdu", "اردو"),
        Triple("th", "thai", "ไทย"), Triple("vi", "vietnami", "Tiếng Việt"),
        Triple("id", "indonéz", "Bahasa Indonesia"), Triple("ms", "maláj", "Bahasa Melayu"),
        Triple("zh", "kínai", "中文"), Triple("ja", "japán", "日本語"),
        Triple("ko", "koreai", "한국어"), Triple("sq", "albán", "Shqip"),
        Triple("mk", "macedón", "Македонски"), Triple("ca", "katalán", "Català"),
        Triple("ga", "ír", "Gaeilge"), Triple("af", "afrikaans", "Afrikaans"),
        Triple("sw", "szuahéli", "Kiswahili"), Triple("ka", "grúz", "ქართული"),
        Triple("be", "belarusz", "Беларуская"), Triple("ta", "tamil", "தமிழ்")
    )

    private val byCode = ALL.associateBy { it.first }

    /** A kód nyelvi része kisbetűsen ("en-US" → "en", "nb" → "no"). */
    fun norm(code: String): String {
        val c = code.trim().lowercase().substringBefore('-').substringBefore('_')
        return when (c) {
            "nb", "nn" -> "no"
            "iw" -> "he"
            "in" -> "id"
            "jw" -> "jv"
            else -> c
        }
    }

    fun nameHu(code: String): String = byCode[norm(code)]?.second ?: code.ifBlank { "ismeretlen" }
    fun nativeName(code: String): String = byCode[norm(code)]?.third ?: code
    fun isKnown(code: String): Boolean = byCode.containsKey(norm(code))
    fun codes(): List<String> = ALL.map { it.first }

    /** Az ML Kit Whisper-nyelvkódok: a Whisper "zh", "he" stb. kódokat használja, ezért azonos a normalizáltal. */
    fun sameLang(a: String, b: String): Boolean = norm(a) == norm(b)

    private val country = mapOf(
        "en" to "GB", "hu" to "HU", "de" to "DE", "fr" to "FR", "es" to "ES", "it" to "IT", "pt" to "PT",
        "nl" to "NL", "pl" to "PL", "cs" to "CZ", "sk" to "SK", "sl" to "SI", "hr" to "HR", "sr" to "RS",
        "ro" to "RO", "bg" to "BG", "uk" to "UA", "ru" to "RU", "el" to "GR", "tr" to "TR", "sv" to "SE",
        "da" to "DK", "no" to "NO", "fi" to "FI", "et" to "EE", "lv" to "LV", "lt" to "LT", "is" to "IS",
        "ar" to "SA", "he" to "IL", "fa" to "IR", "hi" to "IN", "bn" to "BD", "ur" to "PK", "th" to "TH",
        "vi" to "VN", "id" to "ID", "ms" to "MY", "zh" to "CN", "ja" to "JP", "ko" to "KR", "sq" to "AL",
        "mk" to "MK", "ca" to "ES", "ga" to "IE", "af" to "ZA", "sw" to "KE", "ka" to "GE", "be" to "BY", "ta" to "IN"
    )

    /** Zászló-emoji a nyelvhez (regionális jelölő karakterekből); ismeretlen nyelvnél földgömb. */
    fun flag(code: String): String {
        val c = country[norm(code)] ?: return "\uD83C\uDF10"
        val sb = StringBuilder()
        for (ch in c) sb.appendCodePoint(0x1F1E6 + (ch - 'A'))
        return sb.toString()
    }
}
