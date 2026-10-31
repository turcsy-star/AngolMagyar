package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class TranslationInvariantAuditTest {
    @Test fun detectsChangedNumbers() {
        val findings = TranslationInvariantAudit.audit("Pay 25 dollars by 2026.", "Fizess 20 dollárt 2026-ig.")
        assertTrue(findings.any { it.kind == TranslationInvariantAudit.Kind.NUMBER_MISMATCH })
    }

    @Test fun detectsMissingNegationAcrossEnglishHungarian() {
        val findings = TranslationInvariantAudit.audit("Do not open the door.", "Nyisd ki az ajtót.")
        assertTrue(findings.any { it.kind == TranslationInvariantAudit.Kind.NEGATION_MISMATCH })
    }

    @Test fun acceptsMatchingMeaningBearingTokens() {
        assertTrue(TranslationInvariantAudit.audit("I have 2 books.", "2 könyvem van.").isEmpty())
    }

    @Test fun emptyInputIsIgnored() {
        assertTrue(TranslationInvariantAudit.audit("", "nem").isEmpty())
    }
}
