package hu.angolmagyar.fordito
/**
 * Két szöveg szavankénti összevetése (LCS): megkeresi a kicserélt szavakat akkor is, ha a szavak száma
 * a szövegben máshol megváltozott. Csak az azonos hosszúságú kicserélt szakaszokat párosítja (1:1, 2:2…).
 */
object TokenDiff {
    private fun strip(w: String) = w.trim { !it.isLetterOrDigit() }
    private fun key(w: String) = strip(w).lowercase()
    fun replacements(oldText: String, newText: String): List<Pair<String, String>> {
        val a = oldText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val b = newText.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (a.isEmpty() || b.isEmpty() || a.size > 400 || b.size > 400) return emptyList()
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            dp[i][j] = if (key(a[i]) == key(b[j])) dp[i + 1][j + 1] + 1 else maxOf(dp[i + 1][j], dp[i][j + 1])
        }
        val out = ArrayList<Pair<String, String>>()
        var i = 0
        var j = 0
        val del = ArrayList<String>()
        val ins = ArrayList<String>()
        fun flush() {
            if (del.isNotEmpty() && del.size == ins.size) {
                for (k in del.indices) {
                    val f = strip(del[k])
                    val t = strip(ins[k])
                    if (f.isNotEmpty() && t.isNotEmpty() && f != t) out.add(f to t)
                }
            }
            del.clear()
            ins.clear()
        }
        while (i < n && j < m) {
            if (key(a[i]) == key(b[j])) {
                flush()
                // csak a nagybetű/írásjel változása is számít, ha a kulcs azonos, de a szó más (pl. iphone → iPhone)
                val f = strip(a[i])
                val t = strip(b[j])
                if (f != t && f.isNotEmpty() && t.isNotEmpty() && f.equals(t, ignoreCase = true)) out.add(f to t)
                i++; j++
            } else if (dp[i + 1][j] >= dp[i][j + 1]) {
                del.add(a[i]); i++
            } else {
                ins.add(b[j]); j++
            }
        }
        while (i < n) { del.add(a[i]); i++ }
        while (j < m) { ins.add(b[j]); j++ }
        flush()
        return out
    }
}
