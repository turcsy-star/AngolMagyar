package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test

class TerminologyConsistencyTest {
    private fun cue(i: Int, src: String, tgt: String) = SrtExporter.Cue(i * 3000L, i * 3000L + 2000, src, tgt)

    @Test fun unifiesNameVariantsKeepingSuffix() {
        val cues = listOf(
            cue(0, "We met Katherine in London.", "Találkoztunk Katherine-nel Londonban."),
            cue(1, "Then Katherine left.", "Aztán Katherine elment."),
            cue(2, "Katherine called me.", "Katherin felhívott.")
        )
        val r = TerminologyConsistency.apply(cues)
        assertTrue(r.cues[2].target.startsWith("Katherine"))
        assertTrue(r.nameFixes.isNotEmpty())
        assertEquals(cues[0].target, r.cues[0].target)
    }

    @Test fun doesNotTouchWhenNameNotInSource() {
        val cues = listOf(
            cue(0, "Meet Katherine today.", "Találkozz Katherine-nel ma."),
            cue(1, "Katherine is here.", "Katherine itt van."),
            cue(2, "The weather is nice.", "Katherin szép idő van.")
        )
        val r = TerminologyConsistency.apply(cues)
        assertEquals(cues[2].target, r.cues[2].target)
    }

    @Test fun unifiesRepeatedSentenceByMajority() {
        val cues = listOf(
            cue(0, "Thank you very much.", "Köszönöm szépen."),
            cue(1, "Thank you very much.", "Köszönöm szépen."),
            cue(2, "Thank you very much.", "Hálás vagyok.")
        )
        val r = TerminologyConsistency.apply(cues)
        assertEquals(1, r.sentenceFixes)
        assertEquals("Köszönöm szépen.", r.cues[2].target)
    }

    @Test fun tieIsNotGuessed() {
        val cues = listOf(
            cue(0, "Thank you very much.", "Köszönöm szépen."),
            cue(1, "Thank you very much.", "Hálás vagyok.")
        )
        val r = TerminologyConsistency.apply(cues)
        assertFalse(r.changed)
        assertEquals(cues, r.cues)
    }

    @Test fun sourceOnlyIsUnchanged() {
        val cues = listOf(cue(0, "Hello there.", ""), cue(1, "Hello there.", ""))
        assertFalse(TerminologyConsistency.apply(cues).changed)
    }

    @Test fun levenshteinBasics() {
        assertEquals(0, TerminologyConsistency.levenshtein("abc", "abc"))
        assertEquals(1, TerminologyConsistency.levenshtein("katherin", "katherine"))
        assertEquals(3, TerminologyConsistency.levenshtein("abc", "xyz"))
    }
}
