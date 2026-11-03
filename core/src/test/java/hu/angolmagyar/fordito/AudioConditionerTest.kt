package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class AudioConditionerTest {
    private fun speechLike(seconds: Int, amp: Double, noiseAmp: Double, dc: Int = 0): ShortArray {
        val rnd = Random(42)
        val n = seconds * 16_000
        return ShortArray(n) { i ->
            val t = i / 16_000.0
            val burst = if ((t % 1.0) < 0.6) 1.0 else 0.0           // 60% beszédes
            val v = amp * burst * sin(2 * PI * 220 * t) * 32767 + rnd.nextGaussian() * noiseAmp * 32767 + dc
            v.toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    private fun rmsDb(p: ShortArray): Double {
        var acc = 0.0
        for (s in p) acc += s.toDouble() * s
        return 20 * Math.log10(sqrt(acc / p.size) / 32768.0 + 1e-12)
    }

    @Test fun quietSpeechIsBoostedTowardTarget() {
        val pcm = speechLike(6, 0.03, 0.0005)
        val before = rmsDb(pcm)
        val r = AudioConditioner.condition(pcm)
        assertTrue(r.applied)
        assertTrue("gain=${r.gainDb}", r.gainDb in 8f..AudioConditioner.MAX_GAIN_DB)
        assertTrue(rmsDb(pcm) > before + 6)
    }

    @Test fun loudSpeechIsNotChanged() {
        val pcm = speechLike(6, 0.30, 0.001)
        val copy = pcm.copyOf()
        val r = AudioConditioner.condition(pcm, highPassHz = 0f)
        assertEquals(0f, r.gainDb, 0.001f)
        assertTrue(pcm.contentEquals(copy))
    }

    @Test fun silenceStaysSilent() {
        val pcm = ShortArray(16_000 * 5)
        val r = AudioConditioner.condition(pcm)
        assertEquals(0f, r.gainDb, 0.001f)
        assertTrue(pcm.all { it.toInt() == 0 })
    }

    @Test fun shortInputIsSkipped() {
        val pcm = ShortArray(1000) { 100 }
        assertFalse(AudioConditioner.condition(pcm).applied)
    }

    @Test fun dcOffsetIsRemoved() {
        val pcm = ShortArray(16_000 * 3) { 3000 }
        AudioConditioner.highPass(pcm, 70f)
        val tail = pcm.takeLast(8000).map { it.toInt() }
        assertTrue(tail.all { kotlin.math.abs(it) < 50 })
    }

    @Test fun noClippingBeyondRangeAndLengthKept() {
        val pcm = speechLike(5, 0.05, 0.0)
        pcm[16_000] = 32767 // csúcs
        val n = pcm.size
        AudioConditioner.condition(pcm)
        assertEquals(n, pcm.size)
        assertTrue(pcm.all { it in Short.MIN_VALUE..Short.MAX_VALUE })
    }

    @Test fun lowSnrLimitsGain() {
        val pcm = speechLike(6, 0.02, 0.012)
        val r = AudioConditioner.condition(pcm)
        assertTrue("gain=${r.gainDb}", r.gainDb <= AudioConditioner.LOW_SNR_MAX_GAIN_DB + 0.01f)
    }

    @Test fun softLimitIsMonotonicAndBounded() {
        var prev = -2f
        var x = -3f
        while (x <= 3f) {
            val y = AudioConditioner.softLimit(x)
            assertTrue(y >= prev)
            assertTrue(y in -1f..1f)
            prev = y
            x += 0.05f
        }
    }
}
