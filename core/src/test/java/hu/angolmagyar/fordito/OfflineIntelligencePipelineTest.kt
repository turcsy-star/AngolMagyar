package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class OfflineIntelligencePipelineTest {
    @Test fun auditCombinesInvariantAndGlossaryWarnings() {
        val report = OfflineIntelligencePipeline.audit(
            "Do not carry 2 bags",
            "Vigyél 3 táskát",
            listOf(OfflineGlossaryAudit.Entry("bags", "csomag"))
        )
        assertTrue(report.hasWarnings)
        assertEquals(report.warningCount, report.invariantFindings.size + report.terminologyFindings.size)
        assertTrue(report.invariantFindings.any { it.kind == TranslationInvariantAudit.Kind.NUMBER_MISMATCH })
        assertTrue(report.invariantFindings.any { it.kind == TranslationInvariantAudit.Kind.NEGATION_MISMATCH })
        assertTrue(report.terminologyFindings.any { it.kind == OfflineGlossaryAudit.Kind.MISSING_TRANSLATION })
    }

    @Test fun cleanAuditHasNoWarnings() {
        val report = OfflineIntelligencePipeline.audit("Carry 2 bags", "Vigyél 2 csomagot", emptyList())
        assertFalse(report.hasWarnings)
        assertEquals(0, report.warningCount)
    }

    @Test fun memoryRankingDelegatesToStableOfflineRanker() {
        val items = listOf(
            OfflineMemoryRanker.Item("b", "green hiking trail", "zöld túraútvonal"),
            OfflineMemoryRanker.Item("a", "green hiking trail", "zöld túraútvonal")
        )
        val result = OfflineIntelligencePipeline.rankMemory("green hiking trail", items)
        assertEquals(listOf("a", "b"), result.map { it.item.id })
    }

    @Test fun reviewSchedulingIsDeterministicAndOffline() {
        val result = OfflineIntelligencePipeline.scheduleReview(AdaptiveReviewScheduler.State(), 4, 1_000L)
        assertTrue(result.accepted)
        assertEquals(1, result.state.intervalDays)
        assertEquals(1_000L + 86_400_000L, result.state.dueAtEpochMs)
    }
}
