package hu.angolmagyar.fordito
object BuiltinFixes {
    private const val L = "\\p{L}"
    private fun keepCase(orig: String, repl: String): String =
        if (orig.isNotEmpty() && orig[0].isUpperCase()) repl.replaceFirstChar { it.uppercaseChar() } else repl
    private val STEP_COMPOUND = Regex(
        "(?<![$L])lépcső[\\s-]*(?:vagy\\s+)?(testvér|fivér|nővér|húg|báty|öcs|any|ap|szül|fi|lány|gyerek)([$L]*)",
        RegexOption.IGNORE_CASE
    )
    private val MOSTOHA_SPACE = Regex(
        "(?<![$L])mostoha\\s+(testvér|fivér|nővér|húg|báty|öcs|any|ap|szül|fi|lány|gyerek)([$L]*)",
        RegexOption.IGNORE_CASE
    )
    private val HAIR_PACKAGE = Regex("(?<![$L])hair\\s+csomag(?![$L])", RegexOption.IGNORE_CASE)
    fun target(text: String): String {
        if (text.isBlank()) return text
        var t = text
        t = STEP_COMPOUND.replace(t) { keepCase(it.value, "mostoha" + it.groupValues[1] + it.groupValues[2]) }
        t = MOSTOHA_SPACE.replace(t) { keepCase(it.value, "mostoha" + it.groupValues[1] + it.groupValues[2]) }
        t = HAIR_PACKAGE.replace(t) { keepCase(it.value, "csomag") }
        return t
    }
}
