package hu.angolmagyar.fordito

/**
 * A feldolgozott hang aránya a darabok állapotából (0 = vár, 1 = fut, 2 = kész), a spektrogram
 * és más nézetek "feldolgozott / hátralévő" jelöléséhez.
 */
object ProgressCursor {
    fun fraction(states: IntArray, fractions: DoubleArray, ranges: LongArray, totalMs: Long): Float {
        if (totalMs <= 0L) return 0f
        var best = 0L
        for (i in states.indices) {
            val a = ranges.getOrNull(2 * i) ?: continue
            val b = ranges.getOrNull(2 * i + 1) ?: a
            val pos = when (states[i]) {
                2 -> b
                1 -> a + ((b - a) * fractions.getOrElse(i) { 0.0 }.coerceIn(0.0, 1.0)).toLong()
                else -> continue
            }
            if (pos > best) best = pos
        }
        return (best.toDouble() / totalMs).toFloat().coerceIn(0f, 1f)
    }
}
