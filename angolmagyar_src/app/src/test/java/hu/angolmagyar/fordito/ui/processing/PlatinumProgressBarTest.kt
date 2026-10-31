package hu.angolmagyar.fordito.ui.processing

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatinumProgressBarTest {
    @Test fun progressIsClampedAtLowerBound() {
        assertEquals(0, safeProgressPercent(-100))
        assertEquals(0, safeProgressPercent(0))
    }

    @Test fun progressIsClampedAtUpperBound() {
        assertEquals(100, safeProgressPercent(100))
        assertEquals(100, safeProgressPercent(900))
    }

    @Test fun progressPreservesValidValues() {
        assertEquals(1, safeProgressPercent(1))
        assertEquals(47, safeProgressPercent(47))
        assertEquals(99, safeProgressPercent(99))
    }
}
