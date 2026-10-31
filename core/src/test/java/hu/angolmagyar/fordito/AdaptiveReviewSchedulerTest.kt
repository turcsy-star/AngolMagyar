package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class AdaptiveReviewSchedulerTest {
    @Test fun failedRecallResetsRepetitionAndSchedulesTomorrow() {
        val result = AdaptiveReviewScheduler.review(AdaptiveReviewScheduler.State(4, 30, 2.5), 1, 1_000L)
        assertFalse(result.accepted)
        assertEquals(0, result.state.repetitions)
        assertEquals(1, result.state.intervalDays)
        assertEquals(86_401_000L, result.state.dueAtEpochMs)
    }

    @Test fun successfulReviewsUseOneAndSixDayIntervals() {
        val first = AdaptiveReviewScheduler.review(AdaptiveReviewScheduler.State(), 4, 0L).state
        val second = AdaptiveReviewScheduler.review(first, 5, 0L).state
        assertEquals(1, first.intervalDays)
        assertEquals(6, second.intervalDays)
    }

    @Test fun clampsEaseAndRejectsInvalidQuality() {
        val state = AdaptiveReviewScheduler.review(AdaptiveReviewScheduler.State(easeFactor = 99.0), 5, 0L).state
        assertTrue(state.easeFactor <= 3.0)
        try {
            AdaptiveReviewScheduler.review(AdaptiveReviewScheduler.State(), 6, 0L)
            fail("Expected invalid quality to be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun dueCheckIsDeterministic() {
        val state = AdaptiveReviewScheduler.State(dueAtEpochMs = 100L)
        assertFalse(AdaptiveReviewScheduler.isDue(state, 99L))
        assertTrue(AdaptiveReviewScheduler.isDue(state, 100L))
    }
}
