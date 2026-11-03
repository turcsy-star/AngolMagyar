package hu.angolmagyar.fordito
import org.json.JSONObject
object VocabLearner {
    const val THRESHOLD = 3
    data class Suggestion(val from: String, val to: String, val target: Boolean, val count: Int)
    private fun letters(w: String) = w.isNotEmpty() && w.all { it.isLetter() || it == '\'' || it == '-' }
    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
    /** Elírás-szerű csere (kis betűkülönbség): ezt már a 2. azonos javításnál megtanulja, a többit csak a 3.-nál. */
    private fun close(from: String, to: String): Boolean {
        if (!letters(from) || !letters(to)) return false
        val f = from.lowercase()
        val t = to.lowercase()
        if (f == t) return true
        return lev(f, t) <= maxOf(1, minOf(f.length, t.length) / 3)
    }
    private fun needed(from: String, to: String) = if (close(from, to)) 2 else THRESHOLD
    fun record(prefs: Prefs, oldText: String, newText: String, target: Boolean): List<Suggestion> {
        // Szavankénti összevetés: akkor is működik, ha a szószám máshol megváltozott a sorban.
        val pairs = TokenDiff.replacements(oldText, newText)
        if (pairs.isEmpty()) return emptyList()
        val root = load(prefs)
        val fresh = mutableListOf<Suggestion>()
        for ((from, to) in pairs) {
            if (from.isEmpty() || to.isEmpty() || from == to) continue
            if (!target && revokeAuto(prefs, from, to)) continue
            if (from.length < 2 || from.length > 40 || to.length > 40) continue
            val key = (if (target) "T|" else "S|") + from.lowercase() + "=" + to
            val n = root.optInt(key, 0) + 1
            root.put(key, n)
            if (n == needed(from, to) && !alreadyKnown(prefs, from, to, target)) {
                val sg = Suggestion(from, to, target, n)
                if (auto(prefs) && letters(from) && letters(to)) {
                    accept(prefs, sg)
                    logAuto(prefs, sg)
                } else fresh.add(sg)
            }
        }
        prefs.fixLearnJson = root.toString()
        return fresh
    }
    fun autoRules(prefs: Prefs): String = prefs.raw().getString("autoRulesEn", "") ?: ""
    fun setAutoRules(prefs: Prefs, v: String) { prefs.raw().edit().putString("autoRulesEn", v).apply() }
    fun addAutoRules(prefs: Prefs, rules: Map<String, String>) {
        if (rules.isEmpty()) return
        val have = autoRules(prefs).lines().filter { it.isNotBlank() }.toMutableList()
        val keys = have.map { it.substringBefore('=').trim().lowercase() }.toHashSet()
        for ((from, to) in rules) if (from.lowercase() !in keys) {
            have.add("$from=$to")
            logAuto(prefs, Suggestion(from, to, false, 0))
        }
        setAutoRules(prefs, have.takeLast(1000).joinToString("\n"))
    }
    private fun revokeAuto(prefs: Prefs, current: String, wanted: String): Boolean {
        val lines = autoRules(prefs).lines().filter { it.isNotBlank() }
        val keep = lines.filterNot {
            it.substringBefore('=').trim().equals(wanted, true) && it.substringAfter('=').trim().equals(current, true)
        }
        if (keep.size == lines.size) return false
        setAutoRules(prefs, keep.joinToString("\n"))
        return true
    }
    fun auto(prefs: Prefs): Boolean = prefs.raw().getBoolean("vocabAuto", true)
    fun setAuto(prefs: Prefs, v: Boolean) { prefs.raw().edit().putBoolean("vocabAuto", v).apply() }
    fun logAuto(prefs: Prefs, s: Suggestion) {
        val old = prefs.raw().getString("vocabAutoLog", "") ?: ""
        val line = "${s.from} → ${s.to}" + if (s.target) " (magyar)" else " (angol)"
        val lines = (old.lines().filter { it.isNotBlank() } + line).takeLast(20)
        prefs.raw().edit().putString("vocabAutoLog", lines.joinToString("\n")).apply()
    }
    fun autoLog(prefs: Prefs): List<String> =
        (prefs.raw().getString("vocabAutoLog", "") ?: "").lines().filter { it.isNotBlank() }
    private fun alreadyKnown(prefs: Prefs, from: String, to: String, target: Boolean): Boolean {
        val rule = from.lowercase() + "=" + to.lowercase()
        val lists = listOf(if (target) prefs.huFixes else prefs.enFixes, prefs.customVocab)
        return lists.any { text ->
            text.lines().any { it.trim().lowercase() == rule }
        }
    }
    private fun load(prefs: Prefs): JSONObject =
        try {
            if (prefs.fixLearnJson.isBlank()) JSONObject() else JSONObject(prefs.fixLearnJson)
        } catch (_: Exception) {
            JSONObject()
        }
    fun pending(prefs: Prefs): List<Suggestion> {
        val root = load(prefs)
        val out = mutableListOf<Suggestion>()
        val it = root.keys()
        while (it.hasNext()) {
            val key = it.next()
            val n = root.optInt(key, 0)
            if (key.length < 4) continue
            val target = key.startsWith("T|")
            val body = key.substring(2)
            val i = body.indexOf('=')
            if (i <= 0) continue
            val from = body.substring(0, i)
            val to = body.substring(i + 1)
            if (n < needed(from, to)) continue
            if (!alreadyKnown(prefs, from, to, target)) out.add(Suggestion(from, to, target, n))
        }
        return out.sortedByDescending { it.count }
    }
    fun accept(prefs: Prefs, s: Suggestion) {
        val line = s.from.lowercase() + "=" + s.to
        if (s.target) prefs.huFixes = append(prefs.huFixes, line)
        else prefs.enFixes = append(prefs.enFixes, line)
    }
    fun dismiss(prefs: Prefs, s: Suggestion) {
        val root = load(prefs)
        root.remove((if (s.target) "T|" else "S|") + s.from.lowercase() + "=" + s.to)
        prefs.fixLearnJson = root.toString()
    }
    private fun append(list: String, line: String): String =
        if (list.isBlank()) line else list.trimEnd() + "\n" + line
}
