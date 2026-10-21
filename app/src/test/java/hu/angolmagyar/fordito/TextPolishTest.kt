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
}
