package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class SubtitlePostProcessorTest {
    @Test fun emptyInputIsPreserved() {
        val result = SubtitlePostProcessor.process("  ", SubtitlePostProcessor.Language.HUNGARIAN)
        assertEquals("  ", result.text)
        assertFalse(result.changed)
        assertFalse(result.reviewRecommended)
    }

    @Test fun explicitEnglishCorrectionIsAppliedAndReported() {
        val rules = Fixes.parse("teh=the")
        val result = SubtitlePostProcessor.process(
            "teh answer is here",
            SubtitlePostProcessor.Language.ENGLISH,
            englishRules = rules
        )
        assertTrue(result.changed)
        assertTrue(result.appliedRuleCount > 0)
        assertTrue(result.text.startsWith("The answer"))
    }

    @Test fun glossaryIsAppliedWithoutReplacingInsideOtherWords() {
        val result = SubtitlePostProcessor.process(
            "I use Chat gpt every day",
            SubtitlePostProcessor.Language.ENGLISH,
            glossary = listOf("Chat gpt" to "ChatGPT")
        )
        assertTrue(result.text.contains("ChatGPT"))
    }

    @Test fun reviewMarkerIsSurfaced() {
        val result = SubtitlePostProcessor.process(
            "The result is [unclear]",
            SubtitlePostProcessor.Language.ENGLISH
        )
        assertTrue(result.reviewRecommended)
        assertTrue(result.notes.isNotEmpty())
    }

    @Test fun punctuationSpacingIsNormalized() {
        val result = SubtitlePostProcessor.process(
            "Hello   ,  world!!",
            SubtitlePostProcessor.Language.ENGLISH
        )
        assertFalse(result.text.contains("   "))
        assertFalse(result.text.contains(" ,"))
        assertFalse(result.text.contains("!!"))
    }
}
