package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Offline unit tests for the P2/P3 TranslationIntelligence coordinator.
 * No Android runtime required for quality-flag and pure logic paths.
 */
class TranslationIntelligenceTest {

    @Test
    fun qualityFlagsDetectNegationMismatch() {
        val flags = TranslationIntelligence.buildQualityFlags(
            "I do not agree with the proposal",
            "Egyetértek a javaslattal"
        )
        assertTrue(flags.any { it.code == "tagadás eltér" })
        assertTrue(flags.any { it.severity == "critical" })
    }

    @Test
    fun qualityFlagsDetectNumberMismatch() {
        val flags = TranslationIntelligence.buildQualityFlags(
            "We need 1200 units by Friday",
            "Péntekig 800 egységre van szükségünk"
        )
        assertTrue(flags.any { it.code == "szám eltér" })
    }

    @Test
    fun qualityFlagsEmptyWhenBothBlank() {
        assertTrue(TranslationIntelligence.buildQualityFlags("", "").isEmpty())
        assertTrue(TranslationIntelligence.buildQualityFlags("Hello", "").isEmpty())
    }

    @Test
    fun analyzeWithoutStoreHasNoMemoryMatch() {
        val result = TranslationIntelligence.analyze(
            source = "The bank is closed",
            currentTarget = "A bank zárva van",
            memoryStore = null,
            includeContext = true
        )
        assertEquals(null, result.memoryMatch)
        assertFalse(result.hasClearMemorySuggestion)
        // "bank" is ambiguous in the offline dictionary → context should note something
        assertTrue(result.context != null)
    }

    @Test
    fun pendingEntryDefaultsToGlobalWhenNoProject() {
        val e = TranslationIntelligence.pendingEntry(
            source = "Hello",
            target = "Szia",
            sourceLanguage = "en",
            targetLanguage = "hu"
        )
        assertEquals(SmartTranslationMemory.Scope.GLOBAL, e.scope)
        assertEquals(SmartTranslationMemory.Status.PENDING, e.status)
        assertEquals(null, e.projectId)
    }

    @Test
    fun pendingEntryUsesProjectScopeWhenProjectIdGiven() {
        val e = TranslationIntelligence.pendingEntry(
            source = "Hello",
            target = "Szia",
            sourceLanguage = "en",
            targetLanguage = "hu",
            projectId = "proj-1"
        )
        assertEquals(SmartTranslationMemory.Scope.PROJECT, e.scope)
        assertEquals("proj-1", e.projectId)
    }
}
