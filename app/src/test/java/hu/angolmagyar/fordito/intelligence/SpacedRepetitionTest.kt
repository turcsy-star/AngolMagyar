package hu.angolmagyar.fordito.intelligence

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpacedRepetitionTest {
    @Test fun failedRecallResetsRepetitionAndSchedulesTomorrow() {
        val now = 1_000L
        val plan = SpacedRepetition.schedule(ReviewState(3, 20, 2.4), 1, now)
        assertEquals(0, plan.repetitions)
        assertEquals(1, plan.intervalDays)
        assertEquals(now + 86_400_000L, plan.dueAt)
    }

    @Test fun successfulReviewsProgressFromOneToSixDays() {
        val first = SpacedRepetition.schedule(ReviewState(), 4, 1_000L)
        val second = SpacedRepetition.schedule(ReviewState(first.repetitions, first.intervalDays, first.easeFactor), 4, 1_000L)
        assertEquals(1, first.intervalDays)
        assertEquals(6, second.intervalDays)
        assertEquals(2, second.repetitions)
    }

    @Test fun easeFactorNeverDropsBelowMinimum() {
        val plan = SpacedRepetition.schedule(ReviewState(easeFactor = 1.3), 0, 0L)
        assertTrue(plan.easeFactor >= 1.3)
    }
}
