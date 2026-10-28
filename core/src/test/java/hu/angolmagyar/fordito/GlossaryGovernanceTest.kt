package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class GlossaryGovernanceTest {
    @Test fun projectTermsOverrideGlobalOnlyWhenApproved() {
        val global = GlossaryGovernance.Entry("cloud", "felhő", status = GlossaryGovernance.Status.APPROVED)
        val project = GlossaryGovernance.Entry("cloud", "felhőszolgáltatás", scope = GlossaryGovernance.Scope.PROJECT,
            projectId = "p1", status = GlossaryGovernance.Status.PENDING)
        assertEquals("felhő", GlossaryGovernance.resolve("CLOUD", "p1", listOf(global, project))?.target)
        assertEquals("felhőszolgáltatás", GlossaryGovernance.resolve("cloud", "p1", listOf(global, GlossaryGovernance.approve(project)))?.target)
    }

    @Test fun conflictDetectionIsScopedAndIgnoresRetiredEntries() {
        val old = GlossaryGovernance.Entry("bank", "bank", status = GlossaryGovernance.Status.APPROVED)
        val conflict = GlossaryGovernance.Entry("BANK", "part", status = GlossaryGovernance.Status.PENDING)
        assertEquals(1, GlossaryGovernance.conflicts(conflict, listOf(old)).size)
        assertTrue(GlossaryGovernance.conflicts(conflict, listOf(GlossaryGovernance.retire(old))).isEmpty())
    }

    @Test fun impactPreviewUsesWholePhraseAndZeroBasedIndexes() {
        val entry = GlossaryGovernance.Entry("machine learning", "gépi tanulás")
        val impact = GlossaryGovernance.impact(entry, listOf("Machine learning is useful.", "A machine-learning model.", "learning machine"))
        assertEquals(listOf(0, 1), impact.matchingSegments)
    }

    @Test fun tsvRoundTripPreservesOrdinaryEntries() {
        val original = listOf(GlossaryGovernance.Entry("open source", "nyílt forráskód", note = "approved term", status = GlossaryGovernance.Status.APPROVED))
        val decoded = GlossaryGovernance.importTsv(GlossaryGovernance.exportTsv(original))
        assertEquals(original.map { it.source to it.target }, decoded.map { it.source to it.target })
        assertEquals(GlossaryGovernance.Status.APPROVED, decoded.single().status)
    }

    @Test fun projectEntryRequiresProjectId() {
        val entry = GlossaryGovernance.Entry("token", "token", scope = GlossaryGovernance.Scope.PROJECT, projectId = null)
        assertTrue(GlossaryGovernance.validate(entry).isNotEmpty())
    }
}
