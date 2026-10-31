package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class TokenQualityTest {
    @Test fun probabilityThresholdOnlyFlagsForReview() {
        assertTrue(TokenQuality.needsReview(0.2f))
        assertFalse(TokenQuality.needsReview(0.8f))
        assertTrue(TokenQuality.needsReview(Float.NaN))
    }

    @Test fun reviewSpansPreserveOriginalTokenAndTiming() {
        val spans = TokenQuality.reviewSpans(listOf(
            TokenQuality.Token("hello", 0.9f, 100, 250),
            TokenQuality.Token(" worble", 0.12f, 250, 470)
        ))
        assertEquals(1, spans.size)
        assertEquals(" worble", spans[0].text)
        assertEquals(250L, spans[0].startMs)
        assertEquals(470L, spans[0].endMs)
    }
}
