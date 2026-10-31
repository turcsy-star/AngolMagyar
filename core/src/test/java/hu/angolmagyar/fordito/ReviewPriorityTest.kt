package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class ReviewPriorityTest {
    private fun cue(i: Int, src: String, tgt: String, lp: Float = 0f, wc: List<Float> = emptyList()) =
        SrtExporter.Cue(i * 5000L, i * 5000L + 2500, src, tgt, "", lp, wc)

    @Test fun negationAndNumberErrorsOutrankMinorFlags() {
        val cues = listOf(
            cue(0, "Fine.", "Rendben."),
            cue(1, "He did not come.", "Ő eljött."),
            cue(2, "Hello there my friend.", "Szia barátom.", lp = -1.2f)
        )
        val ranked = ReviewPriority.rank(cues)
        assertEquals(1, ranked.first().index)
        assertTrue(ranked.none { it.index == 0 })
    }

    @Test fun nextIndexWrapsAround() {
        val items = listOf(ReviewPriority.Item(2, 10, listOf("a")), ReviewPriority.Item(7, 5, listOf("b")))
        assertEquals(7, ReviewPriority.nextIndex(items, 2))
        assertEquals(2, ReviewPriority.nextIndex(items, 7))
        assertNull(ReviewPriority.nextIndex(emptyList(), 0))
    }

    @Test fun marksOnlyWhenConfidenceMatchesWordCount() {
        val ok = cue(0, "see you tomorrow", "", wc = listOf(0.9f, 0.1f, 0.8f))
        assertEquals("see ‹you› tomorrow", ReviewPriority.markUncertainWords(ok))
        val mismatch = cue(0, "see you tomorrow", "", wc = listOf(0.9f, 0.1f))
        assertEquals("see you tomorrow", ReviewPriority.markUncertainWords(mismatch))
    }

    @Test fun limitIsApplied() {
        val cues = (0 until 5).map { cue(it, "He did not come.", "Ő eljött.") }
        assertEquals(2, ReviewPriority.rank(cues, limit = 2).size)
    }
}
