package hu.angolmagyar.fordito

/** Deterministic offline spaced-repetition scheduling (SM-2 inspired, not a clinical/AI score). */
object AdaptiveReviewScheduler {
    data class State(
        val repetitions: Int = 0,
        val intervalDays: Int = 0,
        val easeFactor: Double = 2.5,
        val dueAtEpochMs: Long = 0L,
        val lastReviewedAtEpochMs: Long? = null
    )

    data class Result(val state: State, val accepted: Boolean)

    fun review(previous: State, quality: Int, nowEpochMs: Long): Result {
        require(quality in 0..5) { "A minősítés 0 és 5 közötti legyen." }
        require(nowEpochMs >= 0L) { "Az időbélyeg nem lehet negatív." }
        val safeEase = previous.easeFactor.takeIf { it.isFinite() }?.coerceIn(1.3, 3.0) ?: 2.5
        val ease = (safeEase + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))).coerceIn(1.3, 3.0)
        val repetitions: Int
        val interval: Int
        if (quality < 3) {
            repetitions = 0
            interval = 1
        } else {
            repetitions = if (previous.repetitions >= Int.MAX_VALUE) Int.MAX_VALUE else previous.repetitions.coerceAtLeast(0) + 1
            interval = when (repetitions) {
                1 -> 1
                2 -> 6
                else -> (previous.intervalDays.coerceAtLeast(1) * ease).toInt().coerceIn(1, MAX_INTERVAL_DAYS)
            }
        }
        val due = safeAdd(nowEpochMs, interval.toLong() * MILLIS_PER_DAY)
        return Result(State(repetitions, interval, ease, due, nowEpochMs), quality >= 3)
    }

    fun isDue(state: State, nowEpochMs: Long): Boolean = state.dueAtEpochMs <= nowEpochMs

    private fun safeAdd(a: Long, b: Long): Long = if (Long.MAX_VALUE - a < b) Long.MAX_VALUE else a + b
    private const val MILLIS_PER_DAY = 86_400_000L
    private const val MAX_INTERVAL_DAYS = 36_500
}
