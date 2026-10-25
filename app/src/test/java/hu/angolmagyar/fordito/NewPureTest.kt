package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameFixTest {
    private val names = listOf("Nina", "Jake", "Kowalski")
    @Test fun fixesMidSentence() {
        assertEquals("Then I told Nina to wait.", NameFix.apply("Then I told Nena to wait.", names))
    }
    @Test fun leavesSentenceStart() {
        assertEquals("Nine of them came.", NameFix.apply("Nine of them came.", names))
    }
    @Test fun leavesOtherWords() {
        assertEquals("We saw Mike there.", NameFix.apply("We saw Mike there.", names))
    }
    @Test fun longNameTwoEdits() {
        assertEquals("Ask Kowalski now.", NameFix.apply("Ask Kowalsky now.", names))
    }
    @Test fun parse() {
        assertEquals(listOf("Nina", "Jake"), NameFix.parseNames("Nina, Jake; ab\nNina"))
    }
}

class EnvelopeTest {
    @Test fun loudPartHigher() {
        val env = Envelope.compute(16000 * 10, 10, 100) { s, e ->
            ShortArray(e - s) { if (s >= 16000 * 5) 20000 else 100 }
        }
        assertEquals(10, env.size)
        assertTrue(env[8] > env[1])
        assertTrue(env.all { it in 0f..1f })
    }
    @Test fun silenceIsZero() {
        val env = Envelope.compute(16000, 4, 100) { s, e -> ShortArray(e - s) }
        assertTrue(env.all { it == 0f })
    }
    @Test fun emptyInput() {
        assertEquals(0, Envelope.compute(0, 4) { _, _ -> ShortArray(0) }.size)
    }
}

class TranslationMemoryTest {
    @Test fun lookupIgnoresCaseAndPunctuation() {
        val tm = TranslationMemory()
        tm.put("Come here, now!", "Gyere ide, most!")
        assertEquals("Gyere ide, most!", tm.lookup("come here now"))
        assertNull(tm.lookup("go away"))
    }
    @Test fun roundTrip() {
        val tm = TranslationMemory()
        tm.put("hello there", "szia")
        val t2 = TranslationMemory.fromText(tm.toText())
        assertEquals("szia", t2.lookup("Hello there."))
    }
    @Test fun capEvictsOldest() {
        val tm = TranslationMemory(LinkedHashMap(), 2)
        tm.put("aaa one", "1"); tm.put("bbb two", "2"); tm.put("ccc three", "3")
        assertEquals(2, tm.size)
        assertNull(tm.lookup("aaa one"))
    }
    @Test fun shortKeysIgnored() {
        val tm = TranslationMemory()
        tm.put("a", "x")
        assertEquals(0, tm.size)
    }
}

class QualityReportTest {
    @Test fun countsAndWorst() {
        val cues = listOf(
            SrtExporter.Cue(0, 2000, "Hello there my friend.", "Szia barátom."),
            SrtExporter.Cue(2500, 4000, "Okay then.", "Okay then."),
            SrtExporter.Cue(4100, 6000, "Fine.", "")
        )
        val r = QualityReport.build(cues)
        assertEquals(3, r.total)
        assertTrue(r.flagged >= 2)
        assertTrue(r.worst.contains(2))
        assertTrue(r.lines().isNotEmpty())
    }
}
