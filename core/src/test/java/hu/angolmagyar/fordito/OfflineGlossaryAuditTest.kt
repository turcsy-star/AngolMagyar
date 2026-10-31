package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class OfflineGlossaryAuditTest {
    @Test fun reportsMissingRequiredTerm() {
        val result = OfflineGlossaryAudit.audit("Open the checkpoint", "Nyisd meg az ellenőrzőpontot", listOf(OfflineGlossaryAudit.Entry("checkpoint", "checkpoint")))
        assertEquals(1, result.size)
        assertEquals(OfflineGlossaryAudit.Kind.MISSING_TRANSLATION, result.single().kind)
    }

    @Test fun acceptsRequiredTermIgnoringCase() {
        val result = OfflineGlossaryAudit.audit("Open the checkpoint", "Nyisd meg a CHECKPOINT elemet", listOf(OfflineGlossaryAudit.Entry("checkpoint", "checkpoint")))
        assertTrue(result.isEmpty())
    }

    @Test fun detectsConflictingDefinitions() {
        val entries = listOf(OfflineGlossaryAudit.Entry("trail", "ösvény"), OfflineGlossaryAudit.Entry("trail", "útvonal"))
        val result = OfflineGlossaryAudit.audit("Follow the trail", "Kövesd az ösvényt", entries)
        assertEquals(OfflineGlossaryAudit.Kind.CONFLICTING_ENTRY, result.single().kind)
    }

    @Test fun avoidsSubstringFalsePositive() {
        assertFalse(OfflineGlossaryAudit.containsTerm("checkpoints", "checkpoint"))
    }

    @Test fun ignoresOversizedUntrustedEntries() {
        val entry = OfflineGlossaryAudit.Entry("a".repeat(500), "b")
        assertTrue(OfflineGlossaryAudit.audit("a", "b", listOf(entry)).isEmpty())
    }
}
