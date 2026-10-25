package hu.angolmagyar.fordito

/** Felhasználói javítólista: soronként "régi=új" (kis/nagybetű nem számít, egész szóra illeszt). */
object Fixes {

    fun parse(text: String): List<Pair<Regex, String>> =
        text.lines().mapNotNull { line ->
            val i = line.indexOf('=')
            if (i <= 0) return@mapNotNull null
            val from = line.substring(0, i).trim()
            val to = line.substring(i + 1).trim()
            if (from.isEmpty()) {
                null
            } else {
                Regex(
                    "(?<![\\p{L}\\p{N}])" + Regex.escape(from) + "(?![\\p{L}\\p{N}])",
                    RegexOption.IGNORE_CASE
                ) to to
            }
        }

    fun apply(text: String, rules: List<Pair<Regex, String>>): String {
        var result = text
        for ((re, to) in rules) {
            result = re.replace(result, Regex.escapeReplacement(to))
        }
        return result
    }
}
