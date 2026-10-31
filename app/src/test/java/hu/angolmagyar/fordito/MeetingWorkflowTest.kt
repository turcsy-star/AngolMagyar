package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeetingWorkflowTest {

    @Test
    fun emptyCuesYieldPerfectQualityAndEmptySummaryBits() {
        val pack = MeetingWorkflow.buildPackage(emptyList(), title = "Teszt")
        assertEquals(100, pack.quality.score0to100)
        assertEquals(0, pack.quality.totalFindings)
        assertTrue(pack.phraseCandidates.isEmpty())
        assertTrue(pack.summary.cueCount == 0)
    }

    @Test
    fun bilingualCuesProducePhraseCandidates() {
        val cues = listOf(
            SrtExporter.Cue(0, 2000, "We need to follow up by Monday", "Hétfőig utána kell járnunk", "S1"),
            SrtExporter.Cue(2000, 4000, "Agreed", "Megállapodtunk", "S2")
        )
        val pack = MeetingWorkflow.buildPackage(cues, title = "Demo")
        assertEquals(2, pack.summary.cueCount)
        assertTrue(pack.phraseCandidates.isNotEmpty())
        assertTrue(pack.reportText.contains("Minőség"))
    }

    @Test
    fun negationMismatchLowersQualityOrAddsFinding() {
        val cues = listOf(
            SrtExporter.Cue(
                0, 3000,
                source = "I do not approve this plan",
                target = "Jóváhagyom ezt a tervet",
                speaker = "A"
            )
        )
        val quality = MeetingWorkflow.analyzeQuality(cues)
        // Either a finding is present or score is not perfect – both are acceptable offline signals
        assertTrue(quality.totalFindings > 0 || quality.score0to100 < 100)
    }
}
