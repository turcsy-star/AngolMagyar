package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class PerformanceRegressionSuiteTest {
    @Test
    fun builtinSuitePasses() {
        val report = PerformanceRegressionSuite.runBuiltin()
        assertTrue(
            "Failed checks: ${report.failedChecks.joinToString { it.name + ": " + it.detail }}",
            report.allPassed
        )
        assertTrue(report.elapsedMs >= 0)
        assertTrue(report.timings.isNotEmpty())
        assertTrue(report.checks.size >= 8)
    }

    @Test
    fun smartContextDetectsAmbiguity() {
        assertTrue(SmartContextTranslation.hasAmbiguity("go to the bank"))
        val s = SmartContextTranslation.suggest("Please set the light to the right level.")
        assertTrue(s.notes.isNotEmpty())
    }

    @Test
    fun translationQualityFlagsEmpty() {
        val r = TranslationQuality.analyze("Source text here.", "")
        assertTrue(r.needsHumanReview)
        assertTrue(r.criticalCount >= 1)
    }

    @Test
    fun hybridOfflineOnly() {
        val offline = HybridTranslationEngine.OfflineTranslator { list -> list.map { "X$it" } }
        val res = HybridTranslationEngine.translate(
            HybridTranslationEngine.Request(
                listOf("a", "b"),
                HybridTranslationEngine.Mode.OFFLINE_ONLY,
                HybridTranslationEngine.PrivacyFlags(allowNetwork = false)
            ),
            offline,
            null
        )
        assertEquals("Xa", res.segments[0].translation)
        assertFalse(res.onlineAttempted)
        assertTrue(res.offlineAttempted)
    }
}
