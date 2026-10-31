package hu.angolmagyar.fordito

import hu.angolmagyar.fordito.core.review.ReviewIntelligence
import org.junit.Assert.*
import org.junit.Test

class TargetedReviewTest {
    private fun cue(i: Int, text: String, lp: Float = -0.3f) =
        SrtExporter.Cue(i * 2000L, i * 2000L + 1500L, text, "", "", lp)

    @Test fun adjacentLinesFormOneGroupAndFarLinesSplit() {
        val cues = (0 until 10).map { cue(it, "mondat $it") }
        val g = TargetedReview.groups(listOf(2, 3, 4, 8), cues)
        assertEquals(listOf(2..4, 8..8), g)
    }

    @Test fun groupIsCappedAt25Seconds() {
        val cues = (0 until 30).map { cue(it, "mondat $it") }
        val g = TargetedReview.groups((0 until 30).toList(), cues)
        assertTrue(g.size >= 2)
        for (r in g) assertTrue(cues[r.last].endMs - cues[r.first].startMs <= 25_000)
    }

    @Test fun applyReplacesFromTheBackWithoutShiftingIndices() {
        val cues = (0 until 6).map { cue(it, "régi $it") }
        val p1 = TargetedReview.Proposal(1, 2, cues.subList(1, 3), listOf(cue(1, "új egyben")), true)
        val p2 = TargetedReview.Proposal(4, 4, cues.subList(4, 5), listOf(cue(4, "új négy")), true)
        val out = TargetedReview.apply(cues, listOf(p1, p2))
        assertEquals(listOf("régi 0", "új egyben", "régi 3", "új négy", "régi 5"), out.map { it.source })
    }

    @Test fun pickLinesTakesOnlyHighAndCriticalAndRespectsBudget() {
        val s = ReviewIntelligence.analyze(listOf(
            ReviewIntelligence.Line("Nem volt ott", 20),
            ReviewIntelligence.Line("Szép idő van ma", 90),
            ReviewIntelligence.Line("12500 forint", 40)
        ))
        val picked = TargetedReview.pickLines(s, maxLines = 10, maxAudioSeconds = 240)
        assertTrue(picked.contains(0))
        assertFalse(picked.contains(1))
        assertTrue(TargetedReview.pickLines(s, maxLines = 0).isEmpty())
    }

    @Test fun scoreDropsWithCriticalLines() {
        val clean = ReviewIntelligence.analyze(listOf(ReviewIntelligence.Line("Szép idő van ma", 90)))
        val bad = ReviewIntelligence.analyze(listOf(ReviewIntelligence.Line("Nem volt ott", 20)))
        assertEquals(100, ReviewSheet.score(clean))
        assertTrue(ReviewSheet.score(bad) < 50)
    }

    @Test fun thermalScalingReducesThreadsButKeepsTwo() {
        assertEquals(6, Perf.thermalScaled(6, 0))
        assertEquals(4, Perf.thermalScaled(6, 2))
        assertEquals(3, Perf.thermalScaled(6, 3))
        assertEquals(2, Perf.thermalScaled(6, 4))
        assertEquals(2, Perf.thermalScaled(2, 3))
    }
}
