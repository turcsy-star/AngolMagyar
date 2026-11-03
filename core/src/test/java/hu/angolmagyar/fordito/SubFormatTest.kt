package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubFormatTest {
    private fun cue(s: Long, e: Long, en: String, hu: String = "") = SrtExporter.Cue(s, e, en, hu)

    @Test fun timingNeverOverlapsNextCue() {
        val cues = listOf(cue(0, 9000, "Hello there my friend"), cue(1000, 3000, "Yes"))
        val out = SubFormat.fixTiming(cues.sortedBy { it.startMs })
        assertTrue(out[0].endMs <= out[1].startMs - 40 + 1)
    }

    @Test fun invalidDurationGetsMinimum() {
        val out = SubFormat.fixTiming(listOf(cue(5000, 5000, "Hi")))
        assertTrue(out[0].endMs > out[0].startMs)
    }

    @Test fun readingSpeedExtendsDisplayWhenRoomAllows() {
        val text = "Ez egy elég hosszú felirat, amelyet nehéz lenne elolvasni."
        val out = SubFormat.fixReadingSpeed(listOf(cue(0, 800, "x", text)))
        assertTrue(out[0].endMs > 800)
    }

    @Test fun polishKeepsOrderAndStartTimes() {
        val cues = listOf(cue(4000, 6000, "B", "Be"), cue(0, 2000, "A", "Á"))
        val out = SubFormat.polish(cues)
        assertEquals(listOf(0L, 4000L), out.map { it.startMs })
    }

    @Test fun longCueIsSplitIntoTwoOrMore() {
        val long = "Ez egy nagyon hosszú mondat, amely biztosan nem fér el két sorban, ezért több feliratra kell bontani, " +
            "különben nehezen olvasható lenne a képernyőn, és zavarná a nézőt."
        val out = SubFormat.reflow(listOf(cue(0, 8000, "src", long)))
        assertTrue(out.size >= 2)
        assertTrue(out.all { SubFormat.partsNeeded(it.target) == 1 })
    }

    @Test fun wrapBalancedUsesAtMostTwoLinesForNormalText() {
        val lines = SubFormat.wrapBalanced("Ez egy közepesen hosszú felirat, ami két sorba tördelődik.")
        assertTrue(lines.size <= 2)
        assertTrue(lines.all { it.length <= SubFormat.MAX_LINE })
    }

    @Test fun parseSrtRoundtrip() {
        val srt = "1\n00:00:01,000 --> 00:00:03,500\nHello\n\n2\n00:00:04,000 --> 00:00:05,000\nWorld\n"
        val cues = SubFormat.parseSrt(srt)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals(3500L, cues[0].endMs)
        assertEquals("World", cues[1].source)
    }

    @Test fun parseTimeAcceptsDotAndComma() {
        assertEquals(3_723_450L, SubFormat.parseTime("01:02:03,450"))
        assertEquals(3_723_450L, SubFormat.parseTime("01:02:03.45"))
    }
}
