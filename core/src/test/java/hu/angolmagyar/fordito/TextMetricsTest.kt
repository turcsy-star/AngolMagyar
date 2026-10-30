package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMetricsTest {
    @Test fun identicalTextScoresOne() {
        assertEquals(1.0, TextMetrics.chrF("Jobban érzed magad most?", "Jobban érzed magad most?"), 1e-9)
        assertEquals(1.0, TextMetrics.chrF("", ""), 1e-9)
    }

    @Test fun unrelatedTextScoresZero() {
        assertEquals(0.0, TextMetrics.chrF("abcdef", "xyzuvw"), 1e-9)
        assertEquals(0.0, TextMetrics.chrF("abc", ""), 1e-9)
    }

    @Test fun closerTranslationScoresHigher() {
        val ref = "Jobban érzed magad most?"
        val close = TextMetrics.chrF(ref, "Jobban érzed magad?")
        val far = TextMetrics.chrF(ref, "Hozz levest nekem")
        assertTrue(close > far)
        assertTrue(close > 0.6)
        assertTrue(far < 0.2)
    }
}
