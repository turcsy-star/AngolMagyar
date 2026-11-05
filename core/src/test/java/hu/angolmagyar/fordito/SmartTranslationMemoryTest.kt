package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartTranslationMemoryTest {
    private fun entry(target: String, context: String) = SmartTranslationMemory.Entry(
        source = "bank",
        target = target,
        sourceLanguage = "en",
        targetLanguage = "hu",
        status = SmartTranslationMemory.Status.APPROVED,
        context = context
    )

    @Test fun contextSelectsTheMatchingApprovedMeaning() {
        val entries = listOf(
            entry("part", "river water boat shore"),
            entry("bank", "money account loan finance")
        )
        val match = SmartTranslationMemory.lookup(
            source = "bank",
            sourceLanguage = "en",
            targetLanguage = "hu",
            entries = entries,
            context = "river boat near the shore"
        )
        assertNotNull(match)
        assertEquals("part", match!!.preferred!!.target)
        assertFalse(match.ambiguous)
    }

    @Test fun missingContextDoesNotGuessBetweenDifferentMeanings() {
        val entries = listOf(
            entry("part", "river water boat shore"),
            entry("bank", "money account loan finance")
        )
        val match = SmartTranslationMemory.lookup(
            source = "bank",
            sourceLanguage = "en",
            targetLanguage = "hu",
            entries = entries
        )
        assertNotNull(match)
        assertTrue(match!!.ambiguous)
    }

    @Test fun unrelatedContextKeepsAmbiguityInsteadOfGuessing() {
        val entries = listOf(
            entry("part", "river water boat shore"),
            entry("bank", "money account loan finance")
        )
        val match = SmartTranslationMemory.lookup(
            source = "bank",
            sourceLanguage = "en",
            targetLanguage = "hu",
            entries = entries,
            context = "a completely unrelated sentence"
        )
        assertNotNull(match)
        assertTrue(match!!.ambiguous)
    }
}
