package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VadTest {
    private fun tone(ms: Int, amp: Int): ShortArray =
        ShortArray(16 * ms) { i -> (if (i % 2 == 0) amp else -amp).toShort() }

    @Test fun tooShortIsEmpty() {
        assertTrue(Vad.segments(ShortArray(100)).isEmpty())
    }

    @Test fun twoUtterancesSplitAtSilence() {
        val pcm = tone(1500, 4000) + ShortArray(16 * 1000) + tone(1500, 4000)
        val segs = Vad.segments(pcm)
        assertEquals(2, segs.size)
        assertTrue(segs[0].end <= segs[1].start)
    }

    @Test fun longSpeechIsCapped() {
        var pcm = ShortArray(0)
        repeat(20) { pcm += tone(1200, 4000) + tone(300, 100) }
        val segs = Vad.segments(pcm, maxSegMs = 12_000)
        assertTrue(segs.isNotEmpty())
        for (s in segs) assertTrue((s.end - s.start) <= 16 * 12_500)
    }

    /** Regresszió: a darabhossz-számítás Int-túlcsordult, és minden beszédrész kiesett. */
    @Test fun speechChunksAreNotLostToOverflow() {
        val pcm = tone(2000, 4000) + ShortArray(16 * 1500) + tone(2000, 4000)
        val merged = Vad.speechChunks(pcm, maxGapMs = 2500, maxChunkMs = 180_000)
        assertEquals(1, merged.size)
        assertTrue(merged[0].end > merged[0].start)
        val split = Vad.speechChunks(pcm, maxGapMs = 500, maxChunkMs = 180_000)
        assertEquals(2, split.size)
        assertTrue(split.all { it.start >= 0 && it.end <= pcm.size && it.end > it.start })
    }

    @Test fun mostlySilentDetectsSilenceOnly() {
        assertTrue(Vad.isMostlySilent(ShortArray(16000 * 5)))
        // a csend-ellenőrzés relatív (a halk zajszinthez képest): kell némi csend + beszéd
        assertTrue(!Vad.isMostlySilent(ShortArray(16000) + tone(2000, 4000)))
    }
}
