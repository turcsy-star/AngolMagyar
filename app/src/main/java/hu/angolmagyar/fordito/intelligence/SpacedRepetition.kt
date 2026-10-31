package hu.angolmagyar.fordito.intelligence

/** Deterministic SM-2-inspired scheduler. Grade: 0=again, 3=hard, 4=good, 5=easy. */
data class ReviewState(
    val repetitions: Int = 0,
    val intervalDays: Int = 0,
    val easeFactor: Double = 2.5
)

data class ReviewPlan(val repetitions: Int, val intervalDays: Int, val easeFactor: Double, val dueAt: Long)

object SpacedRepetition {
    fun schedule(state: ReviewState, grade: Int, now: Long = System.currentTimeMillis()): ReviewPlan {
        require(grade in 0..5) { "A minősítés 0 és 5 közötti lehet." }
        val quality = grade
        val ease = (state.easeFactor + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))).coerceAtLeast(1.3)
        val reps: Int
        val interval: Int
        if (quality < 3) {
            reps = 0
            interval = 1
        } else {
            reps = state.repetitions + 1
            interval = when (reps) {
                1 -> 1
                2 -> 6
                else -> (state.intervalDays * ease).roundToInt().coerceAtLeast(state.intervalDays + 1)
            }
        }
        return ReviewPlan(reps, interval, ease, now + interval * DAY_MS)
    }

    private const val DAY_MS = 86_400_000L
    private fun Double.roundToInt() = kotlin.math.round(this).toInt()
}
