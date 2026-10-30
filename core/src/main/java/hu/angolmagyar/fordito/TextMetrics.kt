package hu.angolmagyar.fordito

/**
 * Fordítás-minőség mérőszám a regressziós tesztekhez: chrF (karakter n-gram F-érték).
 * A szóközöket figyelmen kívül hagyja, a magyar ragozott alakokat jobban tűri, mint a szószintű egyezés.
 * Diagnosztikai mérőszám: a jelentés helyességét nem méri, csak a referenciához való hasonlóságot (0..1).
 */
object TextMetrics {
    fun chrF(reference: String, hypothesis: String, maxN: Int = 6, beta: Double = 2.0): Double {
        val r = reference.filter { !it.isWhitespace() }
        val h = hypothesis.filter { !it.isWhitespace() }
        if (r.isEmpty() && h.isEmpty()) return 1.0
        if (r.isEmpty() || h.isEmpty()) return 0.0
        var pSum = 0.0
        var rSum = 0.0
        var used = 0
        for (n in 1..maxN) {
            if (r.length < n || h.length < n) continue
            val rc = HashMap<String, Int>()
            for (i in 0..r.length - n) rc.merge(r.substring(i, i + n), 1, Int::plus)
            val hc = HashMap<String, Int>()
            for (i in 0..h.length - n) hc.merge(h.substring(i, i + n), 1, Int::plus)
            var match = 0
            for ((k, v) in hc) match += minOf(v, rc[k] ?: 0)
            pSum += match.toDouble() / (h.length - n + 1)
            rSum += match.toDouble() / (r.length - n + 1)
            used++
        }
        if (used == 0) return 0.0
        val p = pSum / used
        val rec = rSum / used
        if (p + rec == 0.0) return 0.0
        val b2 = beta * beta
        return (1 + b2) * p * rec / (b2 * p + rec)
    }
}
