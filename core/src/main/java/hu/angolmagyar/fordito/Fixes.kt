package hu.angolmagyar.fordito
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
            // Mondat eleji nagybetűs alaknál (Gonna → Going to) megmarad a nagy kezdőbetű, ha a csere kisbetűvel kezdődik.
            result = re.replace(result) { m ->
                val v = m.value
                if (v.isNotEmpty() && v[0].isUpperCase() && to.isNotEmpty() && to[0].isLowerCase() && !v.all { it.isUpperCase() })
                    to.replaceFirstChar { it.uppercase() }
                else to
            }
        }
        return result
    }
}
