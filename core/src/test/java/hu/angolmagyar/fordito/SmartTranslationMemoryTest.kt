package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class SmartTranslationMemoryTest {
    private fun entry(
        source: String = "Good morning",
        target: String = "Jó reggelt",
        scope: SmartTranslationMemory.Scope = SmartTranslationMemory.Scope.GLOBAL,
        projectId: String? = null,
        status: SmartTranslationMemory.Status = SmartTranslationMemory.Status.APPROVED,
        usage: Int = 0,
        sourceLanguage: String = "en",
        targetLanguage: String = "hu"
    ) = SmartTranslationMemory.Entry(
        source, target, sourceLanguage, targetLanguage, scope, projectId, status, usage
    )

    @Test fun `approved exact match is suggested but needs confirmation`() {
        val match = SmartTranslationMemory.lookup(" GOOD   MORNING! ", "EN", "hu", entries = listOf(entry()))
        assertNotNull(match)
        assertEquals("Jó reggelt", match!!.preferred!!.target)
        assertTrue(match.requiresUserConfirmation)
        assertFalse(match.ambiguous)
    }

    @Test fun `pending entries are not eligible`() {
        assertNull(SmartTranslationMemory.lookup("Good morning", "en", "hu", entries = listOf(entry(status = SmartTranslationMemory.Status.PENDING))))
    }

    @Test fun `wrong language pair is not eligible`() {
        assertNull(SmartTranslationMemory.lookup("Good morning", "de", "hu", entries = listOf(entry())))
    }

    @Test fun `project entry overrides global entry`() {
        val global = entry(target = "Jó reggelt")
        val project = entry(target = "Szép jó reggelt", scope = SmartTranslationMemory.Scope.PROJECT, projectId = "travel")
        val match = SmartTranslationMemory.lookup("Good morning", "en", "hu", "travel", listOf(global, project))
        assertEquals("Szép jó reggelt", match!!.preferred!!.target)
    }

    @Test fun `conflicting entries are marked ambiguous`() {
        val a = entry(target = "Jó reggelt")
        val b = entry(target = "Szép napot")
        val match = SmartTranslationMemory.lookup("Good morning", "en", "hu", entries = listOf(a, b))
        assertTrue(match!!.ambiguous)
        assertEquals(2, match.candidates.size)
    }

    @Test fun `project memory does not leak into another project`() {
        val project = entry(scope = SmartTranslationMemory.Scope.PROJECT, projectId = "one")
        assertNull(SmartTranslationMemory.lookup("Good morning", "en", "hu", "two", listOf(project)))
    }

    @Test fun `invalid same language entry is rejected`() {
        val validation = SmartTranslationMemory.validate(entry(sourceLanguage = "en", targetLanguage = "EN"))
        assertFalse(validation.valid)
    }

    @Test fun `usage increments only through explicit confirmed use`() {
        val updated = SmartTranslationMemory.recordConfirmedUse(entry(usage = 3), 1234L)
        assertEquals(4, updated.usageCount)
        assertEquals(1234L, updated.updatedAtEpochMs)
    }

    @Test fun `retired entry is not suggested`() {
        val retired = entry(status = SmartTranslationMemory.Status.RETIRED)
        assertNull(SmartTranslationMemory.lookup("Good morning", "en", "hu", entries = listOf(retired)))
    }
}
