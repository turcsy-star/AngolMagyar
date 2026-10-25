package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EtaModelTest {
    private val s = 1000L

    @Test fun windowsRoundUp() {
        assertEquals(1, EtaModel.windows(1 * s))
        assertEquals(1, EtaModel.windows(30 * s))
        assertEquals(2, EtaModel.windows(31 * s))
        assertEquals(8, EtaModel.windows(219 * s))
    }

    /** A naplóból: 8 darab; az 1–2. kész (167 s, 434 s), a 3. dolgozik. A valós hátralévő idő ~351 s volt. */
    @Test fun realLogEstimateIsClose() {
        val lens = longArrayOf(85 * s, 219 * s, 46 * s, 3 * s, 6 * s, 10 * s, 1 * s, 5 * s)
        val state = intArrayOf(2, 2, 1, 0, 0, 0, 0, 0)
        val frac = doubleArrayOf(1.0, 1.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
        val took = longArrayOf(167 * s, 434 * s, 0, 0, 0, 0, 0, 0)
        val el = longArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
        val rem = EtaModel.remainingMs(lens, state, frac, took, el, 0)
        assertTrue("rem=$rem", rem in 300 * s..480 * s)
    }

    @Test fun noDataMeansUnknown() {
        val n = 3
        val rem = EtaModel.remainingMs(LongArray(n) { 60 * s }, IntArray(n), DoubleArray(n), LongArray(n), LongArray(n), 0)
        assertEquals(-1L, rem)
    }

    @Test fun priorIsUsedBeforeFirstChunkFinishes() {
        val lens = longArrayOf(60 * s, 60 * s)   // 2 + 2 ablak
        val rem = EtaModel.remainingMs(lens, intArrayOf(1, 0), doubleArrayOf(0.0, 0.0), LongArray(2), LongArray(2), 50 * s)
        assertEquals(4 * 50 * s + EtaModel.TAIL_MS, rem)
    }

    @Test fun mismatchedArraysAreRejected() {
        assertEquals(-1L, EtaModel.remainingMs(LongArray(2), IntArray(3), DoubleArray(3), LongArray(3), LongArray(3), 1))
    }
}
