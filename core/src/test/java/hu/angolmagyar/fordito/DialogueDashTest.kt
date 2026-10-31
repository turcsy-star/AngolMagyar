package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class DialogueDashTest {
    private fun cue(s: Long, e: Long, sp: String, src: String, tgt: String = "") = SrtExporter.Cue(s, e, src, tgt, sp)

    @Test fun mergesQuickSpeakerChangeIntoDashedBlock() {
        val r = DialogueDash.apply(listOf(cue(0, 1500, "S1", "Hello.", "Szia."), cue(1600, 2800, "S2", "Hi!", "Szervusz!")))
        assertEquals(1, r.size)
        assertEquals("- Hello.\n- Hi!", r[0].source)
        assertEquals("- Szia.\n- Szervusz!", r[0].target)
        assertEquals("", r[0].speaker)
        assertEquals(2800L, r[0].endMs)
    }

    @Test fun sameSpeakerOrBigGapIsNotMerged() {
        val same = DialogueDash.apply(listOf(cue(0, 1000, "S1", "A."), cue(1100, 2000, "S1", "B.")))
        assertEquals(2, same.size)
        val gap = DialogueDash.apply(listOf(cue(0, 1000, "S1", "A."), cue(5000, 6000, "S2", "B.")))
        assertEquals(2, gap.size)
    }

    @Test fun tooLongBlockIsNotMerged() {
        val r = DialogueDash.apply(listOf(cue(0, 4000, "S1", "A."), cue(4100, 8000, "S2", "B.")))
        assertEquals(2, r.size)
    }

    @Test fun missingSpeakerIsNotMerged() {
        assertEquals(2, DialogueDash.apply(listOf(cue(0, 1000, "", "A."), cue(1100, 2000, "S2", "B."))).size)
    }
}
