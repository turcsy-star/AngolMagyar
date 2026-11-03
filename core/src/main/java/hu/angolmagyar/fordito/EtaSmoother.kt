package hu.angolmagyar.fordito

/**
 * A hátralévő idő kijelzésének simítása: a megjelenített érték másodpercenként csökken,
 * az új becslés csak 15%-os súllyal húzza el, így nem ugrál a kijelző.
 */
class EtaSmoother {
    private val mem = HashMap<String, Pair<Long, Long>>()

    fun smooth(key: String, remainingMs: Long, nowMs: Long): Long {
        val prev = mem[key]
        if (prev != null && prev.second == nowMs) return prev.first
        val out = if (prev == null) remainingMs else {
            val predicted = prev.first - (nowMs - prev.second)
            if (predicted <= 0) remainingMs else (predicted * 0.85 + remainingMs * 0.15).toLong()
        }
        mem[key] = out to nowMs
        return out
    }

    fun reset() = mem.clear()
}
