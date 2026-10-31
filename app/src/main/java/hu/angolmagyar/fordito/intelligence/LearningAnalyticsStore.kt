package hu.angolmagyar.fordito.intelligence

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Device-local, privacy-friendly learning telemetry. Stores counters only, never words or sentences. */
class LearningAnalyticsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("learning_analytics_v1", Context.MODE_PRIVATE)
    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    private fun day(time: Long = System.currentTimeMillis()) = synchronized(dayFormat) { dayFormat.format(Date(time)) }
    private fun key(metric: String, date: String = day()) = "$metric.$date"

    @Synchronized fun recordLookup() = increment("lookups")
    @Synchronized fun recordPractice(correct: Boolean) {
        increment("practice")
        if (correct) increment("correct") else increment("incorrect")
    }
    @Synchronized fun recordReview() = increment("reviews")
    @Synchronized private fun increment(metric: String) {
        prefs.edit().putInt(key(metric), (prefs.getInt(key(metric), 0) + 1).coerceAtMost(1_000_000)).apply()
    }

    fun setDailyGoal(value: Int) { prefs.edit().putInt("daily_goal", value.coerceIn(1, 500)).apply() }
    fun dailyGoal(): Int = prefs.getInt("daily_goal", 10)

    fun snapshot(days: Int = 7, now: Long = System.currentTimeMillis()): List<LearningDay> {
        val calendar = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return (days.coerceIn(1, 31) - 1 downTo 0).map { offset ->
            val date = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }.timeInMillis
            val dateKey = day(date)
            LearningDay(dateKey, prefs.getInt(key("lookups", dateKey), 0), prefs.getInt(key("practice", dateKey), 0),
                prefs.getInt(key("correct", dateKey), 0), prefs.getInt(key("reviews", dateKey), 0))
        }
    }

    fun totals(): LearningTotals {
        val week = snapshot(7)
        val month = snapshot(30)
        val practiced = month.sumOf { it.practices }
        val correct = month.sumOf { it.correct }
        return LearningTotals(lookups = month.sumOf { it.lookups }, practices = practiced,
            reviews = month.sumOf { it.reviews }, accuracyPercent = if (practiced == 0) 0 else (correct * 100 / practiced),
            activeDays = month.count { it.lookups + it.practices + it.reviews > 0 }, weeklyActivity = week.sumOf { it.lookups + it.practices + it.reviews })
    }
}

data class LearningDay(val date: String, val lookups: Int, val practices: Int, val correct: Int, val reviews: Int) {
    val activity: Int get() = lookups + practices + reviews
}
data class LearningTotals(val lookups: Int, val practices: Int, val reviews: Int, val accuracyPercent: Int, val activeDays: Int, val weeklyActivity: Int)
