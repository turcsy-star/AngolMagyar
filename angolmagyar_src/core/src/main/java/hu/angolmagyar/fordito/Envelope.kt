package hu.angolmagyar.fordito

/**
 * A hang burkológörbéje a hullámforma-nézethez: [bins] oszlopra, oszloponként egy rövid mintavétellel
 * (nem olvassa végig a teljes fájlt, így órás felvételnél is másodpercek alatt kész).
 */
object Envelope {
    /** @param read (kezdő minta, záró minta) → 16 bites minták (a fájl vége után üres/rövid tömb is lehet) */
    fun compute(totalSamples: Int, bins: Int, window: Int = 1600, read: (Int, Int) -> ShortArray): FloatArray {
        if (totalSamples <= 0 || bins <= 0) return FloatArray(0)
        val raw = FloatArray(bins)
        for (i in 0 until bins) {
            val start = (i.toLong() * totalSamples / bins).toInt()
            val end = minOf(totalSamples.toLong(), start.toLong() + window).toInt()
            val s = try { read(start, end) } catch (_: Exception) { ShortArray(0) }
            if (s.isEmpty()) continue
            var sum = 0L
            for (v in s) sum += Math.abs(v.toInt())
            raw[i] = sum.toFloat() / s.size / 32768f
        }
        val sorted = raw.sortedArray()
        val ref = sorted[(sorted.size * 95 / 100).coerceAtMost(sorted.size - 1)]
        if (ref <= 1e-6f) return FloatArray(bins)
        return FloatArray(bins) { Math.pow((raw[it] / ref).coerceIn(0f, 1f).toDouble(), 0.6).toFloat() }
    }
}
