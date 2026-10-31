package hu.angolmagyar.fordito

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AudioPrepTest {
    private fun tone(amp: Float, dc: Float = 0f) =
        FloatArray(32000) { dc + amp * sin(it * 2 * Math.PI * 300 / 16000).toFloat() }

    private fun rms(a: FloatArray) = Math.sqrt(a.sumOf { it.toDouble() * it } / a.size)

    @Test fun quietAudioGetsLouder() {
        val a = tone(0.02f)
        val before = rms(a)
        AudioPrep.prepare(a)
        assertTrue(rms(a) > before * 2.5)
        assertTrue(a.all { abs(it) <= 0.98f })
    }

    @Test fun dcOffsetIsRemoved() {
        val a = tone(0.2f, dc = 0.1f)
        AudioPrep.prepare(a)
        assertTrue(abs(a.takeLast(8000).average()) < 0.01)
    }

    @Test fun loudAudioIsNotBoosted() {
        val a = tone(0.5f)
        val before = rms(a)
        AudioPrep.prepare(a)
        assertTrue(rms(a) <= before * 1.05)
    }
}
