package hu.angolmagyar.fordito.core.review

import org.junit.Assert.*
import org.junit.Test

class ReviewIntelligenceTest {
    @Test fun criticalTermsAreFlaggedWithoutChangingText() {
        val original = "Nem fizetett 125 000 forintot 2026.10.27-én."
        val result = ReviewIntelligence.analyze(listOf(ReviewIntelligence.Line(original, 42)))
        assertEquals(original, original)
        assertTrue(result.findings.any { it.kind == ReviewIntelligence.Kind.NEGATION && it.severity == ReviewIntelligence.Severity.CRITICAL })
        assertTrue(result.findings.any { it.kind == ReviewIntelligence.Kind.NUMBER })
        assertTrue(result.findings.any { it.kind == ReviewIntelligence.Kind.LOW_CONFIDENCE })
    }

    @Test fun plannerRespectsLineAndTimeBudgets() {
        val summary = ReviewIntelligence.analyze(listOf(
            ReviewIntelligence.Line("Nem volt ott", 20),
            ReviewIntelligence.Line("12500 forint", 40),
            ReviewIntelligence.Line("rövid válasz", 90),
            ReviewIntelligence.Line("A hosszabb ismétlődő mondat itt van", 90),
            ReviewIntelligence.Line("A hosszabb ismétlődő mondat itt van", 90)
        ))
        assertTrue(ReviewIntelligence.planTargetedReview(summary, 10, 16, 8).size <= 2)
        assertTrue(ReviewIntelligence.planTargetedReview(summary, 0, 60).isEmpty())
    }

    @Test fun fingerprintIsStableAndSeparatesPartBoundaries() {
        assertEquals(ReviewIntelligence.fingerprint("ab", "c"), ReviewIntelligence.fingerprint("ab", "c"))
        assertNotEquals(ReviewIntelligence.fingerprint("ab", "c"), ReviewIntelligence.fingerprint("a", "bc"))
    }

    @Test fun benchmarkHandlesMissingAudioDuration() {
        assertEquals(0.0, RecognitionBenchmark("v", "r", 0.1, 0, 100, 0).realtimeFactor, 0.0)
        assertEquals(0.5, RecognitionBenchmark("v", "r", 0.1, 0, 500, 1000).realtimeFactor, 0.0)
    }

    @Test fun pairedLineFlagsOnlyRealMismatch() {
        val ok = ReviewIntelligence.analyze(listOf(ReviewIntelligence.Line(
            "Nem fizetett 500 dollárt.", 90, source = "He did not pay 500 dollars.", target = "Nem fizetett 500 dollárt.")))
        assertTrue(ok.findings.none { it.kind == ReviewIntelligence.Kind.NUMBER || it.kind == ReviewIntelligence.Kind.NEGATION })

        val bad = ReviewIntelligence.analyze(listOf(ReviewIntelligence.Line(
            "Kifizette a pénzt.", 90, source = "He did not pay 500 dollars.", target = "Kifizette a pénzt.")))
        assertTrue(bad.findings.any { it.kind == ReviewIntelligence.Kind.NEGATION && it.severity == ReviewIntelligence.Severity.CRITICAL })
        assertTrue(bad.findings.any { it.kind == ReviewIntelligence.Kind.NUMBER })
    }

    @Test fun confidentSingleLineIsNotFlaggedForEveryNumberOrNegation() {
        val r = ReviewIntelligence.analyze(listOf(ReviewIntelligence.Line("Nem volt ott 12 ember.", 95)))
        assertTrue(r.findings.none { it.kind == ReviewIntelligence.Kind.NEGATION || it.kind == ReviewIntelligence.Kind.NUMBER })
    }

    @Test fun unknownConfidenceGivesOnlyReducedSeverity() {
        val r = ReviewIntelligence.analyze(listOf(ReviewIntelligence.Line("Nem volt ott.")))
        val neg = r.findings.first { it.kind == ReviewIntelligence.Kind.NEGATION }
        assertTrue(neg.severity.ordinal < ReviewIntelligence.Severity.CRITICAL.ordinal)
    }

    @Test fun naturalShortAcknowledgementsAreNotMarkedAsFragments() {
        val r = ReviewIntelligence.analyze(listOf(
            ReviewIntelligence.Line("Yes", 95),
            ReviewIntelligence.Line("Okay.", 95),
            ReviewIntelligence.Line("", 95)
        ))
        assertFalse(r.findings.any { it.kind == ReviewIntelligence.Kind.SHORT_FRAGMENT && it.lineIndex in 0..1 })
        assertFalse(r.findings.any { it.kind == ReviewIntelligence.Kind.SHORT_FRAGMENT && it.lineIndex == 2 })
        assertTrue(r.findings.any { it.kind == ReviewIntelligence.Kind.EMPTY_TEXT && it.lineIndex == 2 })
    }

    @Test fun zeroLengthTimestampIsInvalid() {
        val r = ReviewIntelligence.analyze(listOf(ReviewIntelligence.Line(
            "Ez egy teljes mondat.", 90, startMs = 1200, endMs = 1200)))
        assertTrue(r.findings.any { it.kind == ReviewIntelligence.Kind.BAD_TIMING })
    }

    @Test fun reversedTimestampsHaveOwnKind() {
        val r = ReviewIntelligence.analyze(listOf(ReviewIntelligence.Line("Valami hosszabb szöveg itt.", 90, startMs = 5000, endMs = 1000)))
        assertTrue(r.findings.any { it.kind == ReviewIntelligence.Kind.BAD_TIMING })
    }
}
