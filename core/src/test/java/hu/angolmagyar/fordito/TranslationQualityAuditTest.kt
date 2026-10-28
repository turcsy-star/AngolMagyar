package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class TranslationQualityAuditTest {
    @Test fun emptyTranslationIsFlagged() {
        val report = TranslationQualityAudit.inspect("We received 120 items.", "")
        assertTrue(report.needsReview)
        assertEquals("EMPTY_TRANSLATION", report.findings.first().code)
    }

    @Test fun numberMismatchIsFlagged() {
        val report = TranslationQualityAudit.inspect("We received 120 items.", "Tíz terméket kaptunk.")
        assertTrue(report.findings.any { it.code == "NUMBER_MISMATCH" })
    }

    @Test fun negationMismatchIsFlagged() {
        val report = TranslationQualityAudit.inspect("I do not agree with this decision.", "Egyetértek ezzel a döntéssel.")
        assertTrue(report.findings.any { it.code == "NEGATION_MISMATCH" })
    }

    @Test fun cleanShortTranslationDoesNotGetLengthWarning() {
        val report = TranslationQualityAudit.inspect("Hello.", "Szia.")
        assertFalse(report.findings.any { it.code == "LENGTH_OUTLIER" })
    }
}
