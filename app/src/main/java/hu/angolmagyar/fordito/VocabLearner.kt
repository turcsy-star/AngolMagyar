package hu.angolmagyar.fordito

import org.json.JSONObject

/**
 * Átirat-szerkesztésekből tanul: ha ugyanazt a szót (régi→új) többször javítod,
 * javaslatot ad a javítólistába.
 */
object VocabLearner {

    const val THRESHOLD = 3

    data class Suggestion(val from: String, val to: String, val target: Boolean, val count: Int)

    private fun tokens(s: String) = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

    private fun strip(w: String) = w.trim { !it.isLetterOrDigit() }

    /**
     * Rögzíti az egy soron belüli, azonos szószámú szócseréket.
     * @param target true = a fordított (cél) sort javították, false = a forrást
     * @return az új javaslatok, amelyek épp most érték el a küszöböt
     */
    fun record(prefs: Prefs, oldText: String, newText: String, target: Boolean): List<Suggestion> {
        val a = tokens(oldText)
        val b = tokens(newText)
        if (a.isEmpty() || a.size != b.size) return emptyList()
        val root = load(prefs)
        val fresh = mutableListOf<Suggestion>()
        for (i in a.indices) {
            val from = strip(a[i])
            val to = strip(b[i])
            if (from.isEmpty() || to.isEmpty() || from == to) continue
            if (from.length < 2 || from.length > 40 || to.length > 40) continue
            val key = (if (target) "T|" else "S|") + from.lowercase() + "=" + to
            val n = root.optInt(key, 0) + 1
            root.put(key, n)
            if (n == THRESHOLD && !alreadyKnown(prefs, from, to, target)) {
                fresh.add(Suggestion(from, to, target, n))
            }
        }
        prefs.fixLearnJson = root.toString()
        return fresh
    }

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

    /** Összes jelenleg elfogadható javaslat (küszöböt elérők, még nem felvettek). */
    fun pending(prefs: Prefs): List<Suggestion> {
        val root = load(prefs)
        val out = mutableListOf<Suggestion>()
        val it = root.keys()
        while (it.hasNext()) {
            val key = it.next()
            val n = root.optInt(key, 0)
            if (n < THRESHOLD || key.length < 4) continue
            val target = key.startsWith("T|")
            val body = key.substring(2)
            val i = body.indexOf('=')
            if (i <= 0) continue
            val from = body.substring(0, i)
            val to = body.substring(i + 1)
            if (!alreadyKnown(prefs, from, to, target)) out.add(Suggestion(from, to, target, n))
        }
        return out.sortedByDescending { it.count }
    }

    /** Felvétel a megfelelő javítólistába. */
    fun accept(prefs: Prefs, s: Suggestion) {
        val line = s.from.lowercase() + "=" + s.to
        if (s.target) prefs.huFixes = append(prefs.huFixes, line)
        else prefs.enFixes = append(prefs.enFixes, line)
    }

    /** Elvetés: a számláló törlődik, így nem jön újra javaslatként. */
    fun dismiss(prefs: Prefs, s: Suggestion) {
        val root = load(prefs)
        root.remove((if (s.target) "T|" else "S|") + s.from.lowercase() + "=" + s.to)
        prefs.fixLearnJson = root.toString()
    }

    private fun append(list: String, line: String): String =
        if (list.isBlank()) line else list.trimEnd() + "\n" + line
}
