package hu.angolmagyar.fordito

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPolishTest {
    @Test fun blankIsNoise() { assertTrue(TextPolish.isNoise("  ")) }
    @Test fun normalSentenceIsNotNoise() { assertFalse(TextPolish.isNoise("this is a real sentence")) }
    @Test fun capitalizeSentences() {
        assertTrue(TextPolish.capitalizeSentences("hello. i am here").let { it.startsWith("Hello. ") && it.contains("I am") })
    }
    @Test fun ensurePeriod() {
        assertTrue(TextPolish.ensurePeriod("one two three four five").endsWith("."))
        assertTrue(TextPolish.ensurePeriod("short one") == "short one")
    }

    @Test fun hungarianFillersRemoved() {
        val raw = "Ööö hát izé, szerintem ez jó megoldás."
        val cleaned = TextPolish.cleanWhisperOutput(raw)
        assertFalse(cleaned.lowercase().contains("ööö"))
        assertFalse(cleaned.lowercase().contains("hát"))
        assertFalse(cleaned.lowercase().contains("izé"))
        assertTrue(cleaned.lowercase().contains("szerintem") || cleaned.lowercase().contains("megoldás"))
    }

    @Test fun cleanWhisperCapitalizes() {
        val cleaned = TextPolish.cleanWhisperOutput("hello world")
        assertTrue(cleaned.startsWith("H") || cleaned.startsWith("h"))
    }
}
