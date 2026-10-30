package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubFormatTest {
    private fun cue(s: Long, e: Long, src: String, tgt: String = "") = SrtExporter.Cue(s, e, src, tgt)

    @Test fun balancedWrapKeepsTwoLinesAndEvensThem() {
        val text = "This is a fairly long sentence that needs two lines to fit"
        val lines = SubFormat.wrapBalanced(text, 42)
        assertEquals(2, lines.size)
        assertTrue(lines.all { it.length <= 42 })
        assertTrue(kotlin.math.abs(lines[0].length - lines[1].length) <= 12)
    }

    @Test fun shortTextStaysOneLine() {
        assertEquals(listOf("Hello there"), SubFormat.wrapBalanced("Hello there"))
    }

    @Test fun longCueIsSplitIntoSeveralInOrder() {
        val long = (1..40).joinToString(" ") { "word$it" }
        val out = SubFormat.reflow(listOf(cue(0, 12_000, long)))
        assertTrue(out.size >= 2)
        assertEquals(0L, out.first().startMs)
        for (i in 1 until out.size) assertTrue(out[i].startMs >= out[i - 1].startMs)
        assertEquals(long, out.joinToString(" ") { it.source })
    }

    @Test fun splitKeepsTargetAlignedWithSource() {
        val src = (1..30).joinToString(" ") { "a$it" }
        val tgt = (1..30).joinToString(" ") { "b$it" }
        val out = SubFormat.reflow(listOf(cue(0, 9000, src, tgt)))
        assertTrue(out.size >= 2)
        assertEquals(tgt, out.joinToString(" ") { it.target })
    }

    @Test fun timingIsReadableAndNotOverlapping() {
        val out = SubFormat.fixTiming(listOf(cue(0, 100, "Hi"), cue(500, 600, "There")))
        assertTrue(out[0].endMs <= out[1].startMs)
        assertTrue(out[1].endMs - out[1].startMs >= 1000)
    }

    @Test fun closeNextCueDoesNotOverlap() {
        val out = SubFormat.fixTiming(listOf(cue(31_149, 31_649, "Oh, my god."), cue(31_400, 36_130, "Well, I thought I was your girlfriend.")))
        assertTrue(out[0].endMs <= out[1].startMs)
        assertTrue(out[0].endMs - out[0].startMs >= 200)
    }

    @Test fun parseAndBuildRawRoundTrip() {
        val srt = "1\n00:00:01,000 --> 00:00:02,500\nHello\nworld\n\n2\n00:01:00,000 --> 00:01:03,000\nBye\n\n"
        val cues = SubFormat.parseSrt(srt)
        assertEquals(2, cues.size)
        assertEquals(1000L, cues[0].startMs)
        assertEquals("Hello\nworld", cues[0].source)
        assertEquals(63_000L, cues[1].endMs)
        assertEquals(srt, SubFormat.buildRaw(cues))
    }

    @Test fun suspiciousDetection() {
        assertNotNull(SubFormat.suspicious(null, cue(0, 1000, "")))
        val a = cue(0, 1000, "Same")
        assertNotNull(SubFormat.suspicious(a, cue(1000, 2000, "same")))
        assertNull(SubFormat.suspicious(a, cue(1000, 3000, "Different text")))
        assertNotNull(SubFormat.suspicious(null, cue(0, 500, "x".repeat(60))))
    }

    @Test fun speakerColorsAreStableByFirstAppearance() {
        val m = SubFormat.speakerColors(listOf(
            SrtExporter.Cue(0, 1, "a", "", "S2"), SrtExporter.Cue(1, 2, "b", "", "S1"), SrtExporter.Cue(2, 3, "c", "", "S2")
        ))
        assertEquals(2, m.size)
        assertEquals(m.keys.toList(), listOf("S2", "S1"))
        val srt = SrtExporter.build(listOf(SrtExporter.Cue(0, 1500, "Hi", "", "S1")), SrtExporter.Mode.SOURCE_ONLY, m)
        assertTrue(srt.contains("<font color="))
    }
}
