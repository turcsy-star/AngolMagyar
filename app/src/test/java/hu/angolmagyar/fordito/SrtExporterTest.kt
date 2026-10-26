package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SrtExporterTest {
    @Test fun formatTime() {
        assertEquals("00:00:00,000", SrtExporter.formatTime(0))
        assertEquals("01:02:03,456", SrtExporter.formatTime(3_723_456))
    }

    @Test fun formatTimeVtt() {
        assertEquals("00:00:01.500", SrtExporter.formatTimeVtt(1500))
    }

    @Test fun wrapKeepsLinesShort() {
        val lines = SrtExporter.wrap("one two three four five six seven eight nine ten eleven twelve", 20)
        assertTrue(lines.size > 1)
        assertTrue(lines.all { it.length <= 20 })
    }

    @Test fun buildNumbersAndTimes() {
        val cues = listOf(
            SrtExporter.Cue(0, 1000, "Hello", "Szia"),
            SrtExporter.Cue(1000, 2500, "World", "Világ", "S1")
        )
        val srt = SrtExporter.build(cues, SrtExporter.Mode.TARGET_ONLY)
        assertTrue(srt.startsWith("1\n00:00:00,000 --> 00:00:01,000\nSzia\n"))
        assertTrue(srt.contains("2\n00:00:01,000 --> 00:00:02,500\nS1: Világ\n"))
    }

    @Test fun bothModeStacksSourceAndTarget() {
        val srt = SrtExporter.build(listOf(SrtExporter.Cue(0, 500, "Hi", "Szia")), SrtExporter.Mode.BOTH)
        assertTrue(srt.contains("Hi\nSzia"))
    }

    @Test fun emptyCueSkippedAndBadEndFixed() {
        val srt = SrtExporter.build(
            listOf(SrtExporter.Cue(0, 0, "", ""), SrtExporter.Cue(5000, 5000, "A", "B")),
            SrtExporter.Mode.SOURCE_ONLY
        )
        assertTrue(srt.startsWith("1\n00:00:05,000 --> 00:00:07,000\nA"))
    }

    @Test fun speakerLabelOnlyOnChange() {
        val cues = listOf(
            SrtExporter.Cue(0, 1000, "a", "Egy", "Anna"),
            SrtExporter.Cue(1200, 2000, "b", "Kettő", "Anna"),
            SrtExporter.Cue(2200, 3000, "c", "Három", "Bob"),
            SrtExporter.Cue(9000, 9500, "d", "Négy", "Bob")
        )
        val srt = SrtExporter.build(cues, SrtExporter.Mode.TARGET_ONLY)
        assertTrue(srt.contains("Anna: Egy"))
        assertTrue(srt.contains("\nKettő\n"))
        assertTrue(srt.contains("Bob: Három"))
        assertTrue(srt.contains("Bob: Négy"))
    }
}
