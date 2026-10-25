package hu.angolmagyar.fordito

/**
 * Szereplők / helyek nevei: a felismerő által elrontott névalakot (pl. "Nena" → "Nina") a megadott névre javítja.
 * Csak mondat közbeni, nagybetűs szavakat javít, így a közönséges mondatkezdő szavak nem sérülnek.
 */
object NameFix {
    private val WORD = Regex("\\p{L}[\\p{L}'’-]*")

    fun parseNames(text: String): List<String> =
        text.split(',', ';', '\n').map { it.trim() }.filter { it.length >= 3 }.distinct()

    private fun lev(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val tmp = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return d[b.length]
    }

    private fun matchName(w: String, names: List<String>): String? {
        val wl = w.lowercase()
        var best: String? = null
        var bestD = Int.MAX_VALUE
        for (n in names) {
            val nl = n.lowercase()
            if (nl == wl) return null // már jó
            if (nl[0] != wl[0] || Math.abs(nl.length - wl.length) > 1) continue
            val lim = if (nl.length >= 7) 2 else 1
            val d = lev(nl, wl)
            if (d in 1..lim && d < bestD) { best = n; bestD = d }
        }
        return best
    }

    fun apply(text: String, names: List<String>): String {
        if (names.isEmpty() || text.isBlank()) return text
        val sb = StringBuilder()
        var last = 0
        for (m in WORD.findAll(text)) {
            val w = m.value
            sb.append(text, last, m.range.first)
            last = m.range.last + 1
            var out = w
            if (w.length >= 4 && w[0].isUpperCase()) {
                // mondat eleji szó: nem nyúlunk hozzá
                var k = m.range.first - 1
                while (k >= 0 && text[k] == ' ') k--
                val sentenceStart = k < 0 || text[k] in ".!?…\n\"“”-—"
                if (!sentenceStart) matchName(w, names)?.let { out = it }
            }
            sb.append(out)
        }
        sb.append(text, last, text.length)
        return sb.toString()
    }
}
