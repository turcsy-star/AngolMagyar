package hu.angolmagyar.fordito
object RuleSuggest {
    private fun words(t: String) = t.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    fun diff(old: String, new: String, maxWords: Int = 4): Pair<String, String>? {
        val a = words(old)
        val b = words(new)
        if (a == b || a.isEmpty() || b.isEmpty()) return null
        var p = 0
        while (p < a.size && p < b.size && a[p] == b[p]) p++
        var s = 0
        while (s < a.size - p && s < b.size - p && a[a.size - 1 - s] == b[b.size - 1 - s]) s++
        val from = a.subList(p, a.size - s).joinToString(" ")
        val to = b.subList(p, b.size - s).joinToString(" ")
        if (from.isBlank() || to.isBlank()) return null
        if (words(from).size > maxWords || words(to).size > maxWords) return null
        val f = from.trim(',', '.', '!', '?', ';', ':', ' ')
        val t = to.trim(',', '.', '!', '?', ';', ':', ' ')
        if (f.isBlank() || t.isBlank() || f.equals(t, ignoreCase = false)) return null
        return f to t
    }
    fun add(existing: String, from: String, to: String): Pair<String, Boolean> {
        val line = "$from=$to"
        val has = existing.lines().any { it.trim().equals(line, ignoreCase = true) }
        if (has) return existing to false
        return (if (existing.isBlank()) line else existing.trimEnd() + "\n" + line) to true
    }
}
