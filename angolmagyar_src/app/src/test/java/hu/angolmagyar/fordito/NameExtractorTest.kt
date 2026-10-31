package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameExtractorTest {
    @Test fun findsRepeatedNames() {
        val t = listOf(
            "You know, Nina's here to take care of me.", "Thank you, Nina, for coming.", "Oh, Okay.",
            "You know, Logan, you make me upset.", "Hey Logan, bring soup.", "Hello there.", "Wow, that was great."
        )
        val n = NameExtractor.extract(t)
        assertEquals(setOf("Nina", "Logan"), n.toSet())
    }

    @Test fun ignoresSingleAndStopWords() {
        val n = NameExtractor.extract(listOf("Oh yes, Bob came. Well, Okay."))
        assertTrue(n.isEmpty())
    }
}
