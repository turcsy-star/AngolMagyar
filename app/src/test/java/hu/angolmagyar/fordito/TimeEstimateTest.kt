package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeEstimateTest {
    @Test fun measuredWins() {
        assertEquals(5000L, TimeEstimate.msPerWindow("wh_turbo", mapOf("wh_turbo" to 5000L, "wh_large" to 9000L)))
    }

    @Test fun scaledFromOtherModel() {
        assertEquals(10000L, TimeEstimate.msPerWindow("wh_large", mapOf("wh_turbo" to 5000L)))
        assertEquals(0L, TimeEstimate.msPerWindow("wh_large", emptyMap()))
    }

    @Test fun estimate() {
        // 30 perc hang → 24 perc beszéd = 48 ablak
        val ms = TimeEstimate.estimateMs(30 * 60_000L, 10_000L)
        assertEquals(48 * 10_000L + EtaModel.TAIL_MS, ms)
        assertEquals(-1L, TimeEstimate.estimateMs(0, 10_000L))
        assertEquals(-1L, TimeEstimate.estimateMs(60_000, 0))
    }

    @Test fun labels() {
        assertEquals("1 percnél kevesebb", TimeEstimate.label(10_000))
        assertEquals("kb. 14 perc", TimeEstimate.label(14 * 60_000L))
        assertEquals("kb. 1 óra 5 perc", TimeEstimate.label(65 * 60_000L))
        assertTrue(TimeEstimate.label(-1) == "?")
    }
}
