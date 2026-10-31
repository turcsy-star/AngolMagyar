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

    @Test fun collapsedTimestampBurstIsRemoved() {
        fun c(s: Long, e: Long, en: String, hu: String) = SrtExporter.Cue(s, e, en, hu)
        val cues = listOf(
            c(10_000, 15_000, "Are you feeling better now?", "Jobban érzed magad most?"),
            c(20_630, 21_132, "And the storm approaches, and they're afraid,", "És a vihar megközelíti,"),
            c(20_630, 21_130, "Not their week, you know, not at all.", "Nem a hétük, tudod, egyáltalán nem."),
            c(21_172, 21_672, "and they're afraid, and it's just not.", "és félnek, és csak nem."),
            c(21_172, 21_672, "They are really afraid of the storm.", "Nagyon félnek a vihartól."),
            c(21_380, 21_880, "The sailors grab everything they have.", "A tengerészek megragadnak mindent."),
            c(21_630, 22_140, "And it is simply not their lucky week.", "És egyszerűen nem az ő szerencsés hetük."),
            c(22_620, 25_105, "I'm so good at storytelling.", "Annyira jó vagyok a mesemondásban.")
        )
        val r = Hallucination.clean(cues)
        assertEquals(6, r.dropped)
        assertEquals(listOf(10_000L, 22_620L), r.cues.map { it.startMs })
    }

    @Test fun shortFastRunBelowThresholdIsKept() {
        fun c(s: Long, e: Long, en: String, hu: String) = SrtExporter.Cue(s, e, en, hu)
        val cues = listOf(
            c(10_000, 12_000, "Are you feeling better now?", "Jobban érzed magad most?"),
            c(20_630, 21_130, "Not their week, you know, not at all.", "Nem a hétük, tudod, egyáltalán nem."),
            c(21_172, 21_672, "They are really afraid of the storm.", "Nagyon félnek a vihartól."),
            c(21_630, 22_140, "And it is simply not their lucky week.", "És egyszerűen nem az ő szerencsés hetük."),
            c(24_000, 26_000, "I'm so good at storytelling.", "Annyira jó vagyok a mesemondásban.")
        )
        assertEquals(0, Hallucination.clean(cues).dropped)
    }

    @Test fun loopedTextIsRecognizedAndNormalTextIsNot() {
        assertTrue(Hallucination.looksLooped(
            "and they're afraid and it's just not and they're afraid and it's just not and they're afraid and it's just not"))
        assertTrue(!Hallucination.looksLooped("So the sailors come and they're charging onto the boat and they see this big whale"))
        assertTrue(!Hallucination.looksLooped(""))
    }

    @Test fun shortRepeatedFillerIsKept() {
        val cues = (0 until 8).map { cue(it * 5, "Rendben.") } + cue(60, "Igen, persze.")
        assertEquals(0, Hallucination.clean(cues).dropped)
    }

    @Test fun sameSentenceFarApartIsNotRemovedAsHallucination() {
        val cues = listOf(cue(10, "Ez egy fontos mondat, amit újra elmondok."),
            cue(400, "Ez egy fontos mondat, amit újra elmondok."),
            cue(900, "Ez egy fontos mondat, amit újra elmondok."))
        assertEquals(0, Hallucination.clean(cues).dropped)
        assertEquals(3, Hallucination.clean(cues).cues.size)
    }

    @Test fun isolatedShortLineInLongSilenceIsFlaggedButPreserved() {
        val cues = listOf(cue(10, "Szia, hogy vagy?"), cue(12, "Jól, köszönöm."), cue(400, "Ó, igen."), cue(900, "Gyakran kell megbizonyosodnom."))
        val r = Hallucination.clean(cues)
        assertEquals(2, r.isolated)
        assertEquals(4, r.cues.size)
        assertEquals(0, r.dropped)
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
    @Test fun veryLowConfidenceShortCueIsFlaggedAndPreserved() {
        val a = SrtExporter.Cue(0, 2000, "", "Ez egy rendes mondat lesz.")
        val b = SrtExporter.Cue(3000, 5000, "", "Hmm igen", "", -2.0f)
        val c = SrtExporter.Cue(6000, 8000, "", "Még egy rendes mondat jön.")
        val r = Hallucination.clean(listOf(a, b, c))
        assertEquals(1, r.lowConf)
        assertTrue(r.cues.contains(b))
        assertTrue(r.removed.isEmpty())
        assertEquals(3, r.cues.size)
    }
}
