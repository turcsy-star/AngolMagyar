package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class LiveLogTest {
    private fun line(id: Long, sp: Int, lang: String, o: String = "x", t: String = "y") =
        LiveLine(id, sp, lang, o, t)

    @Test fun appendKeepsLimit() {
        var l = emptyList<LiveLine>()
        for (i in 1..80) l = LiveLog.append(l, line(i.toLong(), 1, "en"))
        assertEquals(LiveLog.MAX_LINES, l.size)
        assertEquals(80L, l.last().id)
    }

    @Test fun partialReplacedNotAccumulated() {
        var l = LiveLog.append(emptyList(), line(1, 1, "en"))
        l = LiveLog.withPartial(l, line(2, 1, "en", "hel"))
        l = LiveLog.withPartial(l, line(2, 1, "en", "hello"))
        assertEquals(2, l.size)
        assertFalse(l.last().final)
        assertEquals("hello", l.last().original)
        assertEquals(1, LiveLog.withPartial(l, null).size)
    }

    @Test fun translationArrivesLater() {
        val l = LiveLog.append(emptyList(), line(5, 1, "de", "Hallo", ""))
        assertEquals("Szia", LiveLog.setTranslation(l, 5, "Szia")[0].translated)
    }

    @Test fun languageSwitchAndSpeakerChange() {
        val l = listOf(line(1, 1, "en"), line(2, 1, "en"), line(3, 2, "de"), line(4, 2, ""), line(5, 1, "de"))
        assertFalse(LiveLog.langSwitched(l, 0))
        assertFalse(LiveLog.langSwitched(l, 1))
        assertTrue(LiveLog.langSwitched(l, 2))
        assertFalse(LiveLog.langSwitched(l, 3))
        assertFalse(LiveLog.langSwitched(l, 4))
        assertTrue(LiveLog.speakerChanged(l, 0))
        assertFalse(LiveLog.speakerChanged(l, 1))
        assertTrue(LiveLog.speakerChanged(l, 2))
        assertTrue(LiveLog.speakerChanged(l, 4))
    }

    @Test fun catalog() {
        assertEquals("német", LangCatalog.nameHu("de-AT"))
        assertEquals("norvég", LangCatalog.nameHu("nb"))
        assertTrue(LangCatalog.sameLang("EN_us", "en"))
        assertEquals("xx", LangCatalog.nameHu("xx"))
        assertEquals(LangCatalog.codes().size, LangCatalog.codes().toSet().size)
    }

    @Test fun exportText() {
        val t = LiveLog.toText(listOf(line(1, 2, "de", "Hallo", "Szia")), "magyar")
        assertTrue(t.contains("[2. beszélő, német] Hallo"))
        assertTrue(t.contains("→ magyar: Szia"))
    }

    private fun tone(f0: Double, sec: Double = 1.0): FloatArray {
        val n = (16000 * sec).toInt()
        return FloatArray(n) { i ->
            val t = i / 16000.0
            (0.3 * sin(2 * PI * f0 * t) + 0.15 * sin(2 * PI * 2 * f0 * t) + 0.08 * sin(2 * PI * 3 * f0 * t)).toFloat()
        }
    }

    @Test fun f0Estimated() {
        val v = SpeakerTracker.features(tone(120.0))
        assertTrue("f0=${v.f0}", abs(v.f0 - 120.0) < 6)
        val w = SpeakerTracker.features(tone(210.0))
        assertTrue("f0=${w.f0}", abs(w.f0 - 210.0) < 10)
    }

    private fun abs(d: Double) = if (d < 0) -d else d

    @Test fun speakersSeparatedAndReturn() {
        val st = SpeakerTracker()
        val a = st.assign(tone(115.0))
        val b = st.assign(tone(220.0))
        val a2 = st.assign(tone(120.0))
        val b2 = st.assign(tone(214.0))
        assertEquals(1, a); assertEquals(2, b); assertEquals(1, a2); assertEquals(2, b2)
        assertEquals(2, st.speakerCount)
    }

    @Test fun silenceKeepsPreviousSpeaker() {
        val st = SpeakerTracker()
        assertEquals(0, st.assign(FloatArray(16000)))
        assertEquals(1, st.assign(tone(130.0)))
        assertEquals(1, st.assign(FloatArray(16000)))
    }
}
