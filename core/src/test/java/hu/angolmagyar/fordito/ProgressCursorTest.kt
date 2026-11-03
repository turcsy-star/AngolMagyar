package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressCursorTest {
    private val ranges = longArrayOf(0, 10_000, 10_000, 20_000, 20_000, 30_000)

    @Test fun doneChunksCountFully() {
        assertEquals(1f / 3f, ProgressCursor.fraction(intArrayOf(2, 0, 0), DoubleArray(3), ranges, 30_000), 1e-4f)
    }

    @Test fun activeChunkUsesItsFraction() {
        assertEquals(0.5f, ProgressCursor.fraction(intArrayOf(2, 1, 0), doubleArrayOf(1.0, 0.5, 0.0), ranges, 30_000), 1e-4f)
    }

    @Test fun nothingStartedAndBadInputs() {
        assertEquals(0f, ProgressCursor.fraction(intArrayOf(0, 0, 0), DoubleArray(3), ranges, 30_000), 0f)
        assertEquals(0f, ProgressCursor.fraction(intArrayOf(2), DoubleArray(1), ranges, 0), 0f)
        assertEquals(0f, ProgressCursor.fraction(intArrayOf(2), DoubleArray(1), LongArray(0), 30_000), 0f)
    }

    @Test fun neverExceedsOne() {
        assertEquals(1f, ProgressCursor.fraction(intArrayOf(2, 2, 2), DoubleArray(3), ranges, 20_000), 0f)
    }
}
