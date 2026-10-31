package hu.angolmagyar.fordito.intelligence

/** Small deterministic learning counters; persistence is delegated to the caller. */
data class LearningStats(val lookups: Int = 0, val practicedPhrases: Int = 0, val successfulChecks: Int = 0) {
    fun recordLookup() = copy(lookups = lookups + 1)
    fun recordPractice() = copy(practicedPhrases = practicedPhrases + 1)
    fun recordCheck(success: Boolean) = copy(successfulChecks = successfulChecks + if (success) 1 else 0)
}
