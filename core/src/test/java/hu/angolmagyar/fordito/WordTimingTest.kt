package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class WordTimingTest {
    private fun cue(s: Long, e: Long, t: String = "x") = SrtExporter.Cue(s, e, t, "")

    @Test fun snapsBoundariesToWordsWithPadding() {
        val cues = listOf(cue(1000, 5000))
        val spans = listOf(WordTiming.Span(1400, 1800), WordTiming.Span(2000, 3000))
        val r = WordTiming.snapCues(cues, spans)
        assertEquals(1320L, r[0].startMs)
        assertEquals(3500L, r[0].endMs) // 3200 lenne, de legfeljebb 1500 ms-ot mozdulhat
    }

    @Test fun neverOverlapsNeighbours() {
        val cues = listOf(cue(0, 2000), cue(2100, 4000))
        val spans = listOf(WordTiming.Span(100, 2300), WordTiming.Span(2400, 3800))
        val r = WordTiming.snapCues(cues, spans)
        assertTrue(r[0].endMs <= r[1].startMs - 40)
        assertTrue(r[0].startMs < r[0].endMs && r[1].startMs < r[1].endMs)
    }

    @Test fun cueWithoutSpansIsUntouched() {
        val cues = listOf(cue(0, 1000), cue(60_000, 61_000))
        val r = WordTiming.snapCues(cues, listOf(WordTiming.Span(100, 900)))
        assertEquals(cues[1], r[1])
    }

    @Test fun shiftIsLimited() {
        val cues = listOf(cue(10_000, 12_000))
        val r = WordTiming.snapCues(cues, listOf(WordTiming.Span(10_000, 12_000)), maxShiftMs = 1500)
        assertTrue(r[0].startMs in 9_900..10_000)
    }

    @Test fun minimumDurationIsKept() {
        val r = WordTiming.snapCues(listOf(cue(1000, 3000)), listOf(WordTiming.Span(1500, 1600)))
        assertTrue(r[0].endMs - r[0].startMs >= 700)
    }

    @Test fun offsetEstimateUsesMedianAndNeedsEnoughPairs() {
        val cues = (0 until 6).map { cue(it * 10_000L, it * 10_000L + 2000) }
        val spans = cues.map { WordTiming.Span(it.startMs + 500, it.endMs + 500) }
        assertEquals(500L, WordTiming.estimateOffsetMs(cues, spans))
        assertNull(WordTiming.estimateOffsetMs(cues.take(2), spans))
        val shifted = WordTiming.shift(cues, 500)
        assertEquals(500L, shifted[0].startMs)
        assertEquals(cues, WordTiming.shift(cues, 0))
    }

    @Test fun tightenOnlyShrinks() {
        val toks = listOf(WordTiming.Span(1500, 1800), WordTiming.Span(1900, 2300), WordTiming.Span(2400, 3000))
        val (s, e) = WordTiming.tighten(1000, 5000, toks)
        assertEquals(1400L, s)
        assertEquals(3250L, e)
        assertTrue(s >= 1000 && e <= 5000)
    }

    @Test fun tightenIgnoresUnreliableTokens() {
        assertEquals(1000L to 5000L, WordTiming.tighten(1000, 5000, listOf(WordTiming.Span(1500, 1800))))
        val outside = listOf(WordTiming.Span(100, 200), WordTiming.Span(300, 400), WordTiming.Span(500, 600))
        assertEquals(1000L to 5000L, WordTiming.tighten(1000, 5000, outside))
        val unordered = listOf(WordTiming.Span(3000, 3100), WordTiming.Span(2000, 2100), WordTiming.Span(2500, 2600))
        assertEquals(1000L to 5000L, WordTiming.tighten(1000, 5000, unordered))
    }
}
