package hu.angolmagyar.fordito
object EtaModel {
    const val WINDOW_MS = 30_000L
    const val TAIL_MS = 25_000L
    fun windows(speechMs: Long): Int = ((speechMs + WINDOW_MS - 1) / WINDOW_MS).toInt().coerceAtLeast(1)
    fun remainingMs(
        lens: LongArray, state: IntArray, frac: DoubleArray, tookMs: LongArray, elapsedMs: LongArray,
        priorMsPerWindow: Long
    ): Long {
        val n = state.size
        if (n == 0 || lens.size != n || frac.size != n || tookMs.size != n || elapsedMs.size != n) return -1L
        var doneWin = 0L
        var doneMs = 0L
        for (i in 0 until n) {
            if (state[i] == 2 && tookMs[i] >= 2000) { doneWin += windows(lens[i]); doneMs += tookMs[i] }
        }
        var perWin = if (doneWin > 0) doneMs.toDouble() / doneWin else priorMsPerWindow.toDouble()
        if (perWin <= 0.0) {
            for (i in 0 until n) {
                if (state[i] == 1 && frac[i] >= 0.1 && elapsedMs[i] >= 5000) {
                    perWin = elapsedMs[i] / (windows(lens[i]) * frac[i])
                    break
                }
            }
        }
        if (perWin <= 0.0) return -1L
        var rem = 0.0
        for (i in 0 until n) {
            val w = windows(lens[i])
            when (state[i]) {
                0 -> rem += w * perWin
                1 -> {
                    val byModel = w * perWin * (1.0 - frac[i])
                    val own = if (frac[i] >= 0.1 && elapsedMs[i] >= 5000) elapsedMs[i] * (1.0 - frac[i]) / frac[i] else -1.0
                    rem += if (own >= 0) (byModel + own) / 2 else byModel
                }
            }
        }
        return rem.toLong() + TAIL_MS
    }
}
