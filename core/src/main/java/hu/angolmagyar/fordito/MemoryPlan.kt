package hu.angolmagyar.fordito

/**
 * Memóriabarát darabméret: a legnagyobb felismerendő hangdarab hossza a Java heap méretéből.
 * Egy hangmásodperc 32 000 bájt (16 kHz, 16 bit); a feldolgozás közben a darabról egyszerre
 * több másolat él (szelet, csendmentes másolat, float másolat a natív oldalon).
 */
object MemoryPlan {
    const val BYTES_PER_AUDIO_SECOND = 32_000L
    const val WORKING_COPIES = 5L
    const val MIN_CHUNK_MS = 120_000L
    const val MAX_CHUNK_MS = 15 * 60_000L
    const val LOW_HEAP_BYTES = 192L * 1024 * 1024

    fun maxChunkMs(maxHeapBytes: Long, workers: Int = 1, budgetFraction: Double = 0.30): Long {
        if (maxHeapBytes <= 0L) return MIN_CHUNK_MS
        val budget = maxHeapBytes * budgetFraction / workers.coerceAtLeast(1)
        val seconds = budget / (BYTES_PER_AUDIO_SECOND * WORKING_COPIES)
        return (seconds * 1000.0).toLong().coerceIn(MIN_CHUNK_MS, MAX_CHUNK_MS)
    }

    fun isLowMemory(maxHeapBytes: Long): Boolean = maxHeapBytes in 1 until LOW_HEAP_BYTES
}
