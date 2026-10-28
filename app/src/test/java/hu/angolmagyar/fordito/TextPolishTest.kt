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

    @Test fun contentWordsArePreservedByDefault() {
        val raw = "Hát ezt most nem így gondoltam. Na, izé, menjünk tovább."
        val cleaned = TextPolish.cleanWhisperOutput(raw).lowercase()
        assertTrue(cleaned.contains("hát"))
        assertTrue(cleaned.contains("na"))
        assertTrue(cleaned.contains("izé"))
        assertTrue(cleaned.contains("gondoltam"))
    }

    @Test fun fillerRemovalRequiresExplicitOptIn() {
        val raw = "Ööö hát izé, szerintem ez jó megoldás."
        val cleaned = TextPolish.cleanWhisperOutput(raw, removeFillers = true).lowercase()
        assertFalse(cleaned.contains("ööö"))
        assertFalse(cleaned.contains("hát"))
        assertFalse(cleaned.contains("izé"))
        assertTrue(cleaned.contains("szerintem") || cleaned.contains("megoldás"))
    }

    @Test fun hallucinationPhraseInsideRealSentenceIsPreserved() {
        val cleaned = TextPolish.cleanWhisperOutput("The phrase thanks for watching was on the slide.")
        assertTrue(cleaned.lowercase().contains("thanks for watching"))
    }

    @Test fun standaloneSubtitleCreditIsRemoved() {
        assertTrue(TextPolish.cleanWhisperOutput("Subtitles by Example Team").isBlank())
    }

    @Test fun cleanWhisperCapitalizes() {
        val cleaned = TextPolish.cleanWhisperOutput("hello world")
        assertTrue(cleaned.startsWith("H") || cleaned.startsWith("h"))
    }
}
