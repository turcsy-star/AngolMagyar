package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingSpeedTest {
    private fun cue(s: Long, e: Long, t: String) = SrtExporter.Cue(s, e, "x", t)

    @Test fun extendsIntoFreeGap() {
        val out = SubFormat.fixReadingSpeed(listOf(cue(0, 1000, "a".repeat(40)), cue(5000, 6000, "ok")))
        assertEquals(2000L, out[0].endMs)
        assertEquals(6000L, out[1].endMs)
    }

    @Test fun neverOverlapsNext() {
        val out = SubFormat.fixReadingSpeed(listOf(cue(0, 1000, "a".repeat(40)), cue(1500, 3000, "next")))
        assertTrue(out[0].endMs <= 1460L && out[0].endMs >= 1000L)
    }
}
