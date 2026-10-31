package hu.angolmagyar.fordito

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineQualityConfidenceTest {
    @Test fun lowWordConfidenceIsReviewOnly() {
        val cue = SrtExporter.Cue(0, 1800, "The unusual word", "", lp = -0.2f,
            wordConfidence = listOf(0.92f, 0.18f, 0.81f))
        val flags = LineQuality.flags(null, cue, null, false)
        assertTrue(flags.contains("gyenge szókonfidencia"))
        assertFalse(flags.contains("bizonytalan felismerés"))
        assertTrue(cue.source.contains("unusual"))
    }

    @Test fun highNoSpeechProbabilityFlagsButDoesNotDeleteCue() {
        val cue = SrtExporter.Cue(0, 1500, "A quiet sentence.", "", noSpeechProb = 0.91f)
        val flags = LineQuality.flags(null, cue, null, false)
        assertTrue(flags.contains("beszéd/csend ellenőrzése"))
        assertTrue(cue.source.isNotEmpty())
    }
}
