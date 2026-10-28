package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class SrtQualityCheckTest {
    @Test fun invalidDurationIsAnError() {
        val report = SrtQualityCheck.inspect(
            listOf(SrtExporter.Cue(1000, 1000, "Hello", "Szia")),
            SrtExporter.Mode.BOTH
        )
        assertEquals(1, report.errorCount)
        assertFalse(report.canExport)
        assertEquals("INVALID_DURATION", report.issues.first().code)
    }

    @Test fun emptyTranslationWarnsInTargetMode() {
        val report = SrtQualityCheck.inspect(
            listOf(SrtExporter.Cue(0, 1000, "Hello", "")),
            SrtExporter.Mode.TARGET_ONLY
        )
        assertEquals(1, report.warningCount)
        assertTrue(report.canExport)
    }

    @Test fun overlapIsWarningNotAutomaticFailure() {
        val report = SrtQualityCheck.inspect(
            listOf(
                SrtExporter.Cue(0, 1500, "One", "Egy"),
                SrtExporter.Cue(1000, 2000, "Two", "Kettő")
            ),
            SrtExporter.Mode.BOTH
        )
        assertTrue(report.issues.any { it.code == "OVERLAP" })
        assertTrue(report.canExport)
    }

    @Test fun validCuesHaveNoIssues() {
        val report = SrtQualityCheck.inspect(
            listOf(SrtExporter.Cue(0, 1000, "Hello", "Szia"), SrtExporter.Cue(1000, 2000, "Bye", "Viszlát")),
            SrtExporter.Mode.BOTH
        )
        assertTrue(report.issues.isEmpty())
        assertTrue(report.canExport)
    }
}
