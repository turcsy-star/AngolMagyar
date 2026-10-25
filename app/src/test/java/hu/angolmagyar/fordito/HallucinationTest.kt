package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HallucinationTest {
    private fun cue(startS: Int, text: String, lenS: Int = 2) =
        SrtExporter.Cue(startS * 1000L, (startS + lenS) * 1000L, "", text)

    @Test fun repeatedLongSentenceKeepsFirstTwo() {
        val cues = listOf(
            cue(10, "Ó, ez sokkal jobban érzem magam."), cue(12, "Ó, ez sokkal jobban érzem magam."),
            cue(30, "Ó, ez sokkal jobban érzem magam."), cue(40, "Ó, ez sokkal jobban érzem magam."),
            cue(45, "Rendben.")
        )
        val r = Hallucination.clean(cues)
        assertEquals(2, r.repeats)
        assertEquals(3, r.cues.size)
    }

    @Test fun shortRepeatedFillerIsKept() {
        val cues = (0 until 8).map { cue(it * 5, "Rendben.") } + cue(60, "Igen, persze.")
        assertEquals(0, Hallucination.clean(cues).dropped)
    }

    @Test fun isolatedShortLineInLongSilenceIsDropped() {
        val cues = listOf(cue(10, "Szia, hogy vagy?"), cue(12, "Jól, köszönöm."), cue(400, "Ó, igen."), cue(900, "Gyakran kell megbizonyosodnom."))
        val r = Hallucination.clean(cues)
        assertEquals(2, r.isolated)
        assertEquals(2, r.cues.size)
    }

    @Test fun denseDialogueAndSingleCueAreUntouched() {
        val dense = (0 until 20).map { cue(it * 4, "Ez egy rendes mondat lesz ${it}.") }
        assertEquals(0, Hallucination.clean(dense).dropped)
        assertEquals(1, Hallucination.clean(listOf(cue(500, "Ó, igen."))).cues.size)
    }

    @Test fun usesSourceWhenNoTranslation() {
        val c = { s: Int -> SrtExporter.Cue(s * 1000L, s * 1000L + 2000, "Oh, that feels so much better.", "") }
        assertEquals(2, Hallucination.clean(listOf(c(1), c(5), c(9), c(13))).repeats)
    }
}

class HallucinationLowConfTest {
    @Test fun veryLowConfidenceShortCueIsRemovedAndReturned() {
        val a = SrtExporter.Cue(0, 2000, "", "Ez egy rendes mondat lesz.")
        val b = SrtExporter.Cue(3000, 5000, "", "Hmm igen", "", -2.0f)
        val c = SrtExporter.Cue(6000, 8000, "", "Még egy rendes mondat jön.")
        val r = Hallucination.clean(listOf(a, b, c))
        assertEquals(1, r.lowConf)
        assertEquals(listOf(b), r.removed)
        assertEquals(2, r.cues.size)
    }
}
