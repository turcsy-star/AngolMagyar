package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TranscriptParseTest {
    @Test fun legacyDashPrefixIsStripped() {
        val cues = SrtExporter.parseCuesFromTranscript("[00:07:09] —: Mm-Hmm.\n[00:07:10] —: Rendben.\n")
        assertEquals(listOf("Mm-Hmm.", "Rendben."), cues.map { it.source })
        assertTrue(cues.all { it.speaker.isBlank() })
    }

    @Test fun speakerLabelBecomesSpeaker() {
        val cues = SrtExporter.parseCuesFromTranscript("[00:00:13] S1: Szia.\n[00:00:20] S2: Szia!\n")
        assertEquals(listOf("S1", "S2"), cues.map { it.speaker })
        assertEquals(listOf("Szia.", "Szia!"), cues.map { it.source })
    }

    @Test fun cueDoesNotStretchOverMinutes() {
        val cues = SrtExporter.parseCuesFromTranscript("[00:11:34] —: Bye.\n[00:13:10] —: Igen.\n")
        assertTrue(cues[0].endMs - cues[0].startMs <= 7000L)
        assertEquals(cues[0].startMs + 2500L, cues[0].endMs)
    }

    @Test fun nextStartStillLimitsTheEnd() {
        val cues = SrtExporter.parseCuesFromTranscript("[00:00:10] Hosszabb mondat, amely sokáig tart.\n[00:00:11] Rövid.\n")
        assertEquals(11_000L, cues[0].endMs)
    }

    @Test fun plainMinutesDoNotWriteDashAnyMore() {
        // a jegyzőkönyv sorában nincs "—: " előtag, ha nincs beszélő (ExportHelper.toPlainMinutes ezt írja)
        val line = "[00:00:13] Szia."
        assertEquals("Szia.", SrtExporter.parseCuesFromTranscript(line).first().source)
    }
}
