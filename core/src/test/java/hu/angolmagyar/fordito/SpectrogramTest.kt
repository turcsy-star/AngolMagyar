package hu.angolmagyar.fordito

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class SpectrogramTest {
    private fun tone(hz: Double, seconds: Int = 4): ShortArray =
        ShortArray(seconds * 16_000) { (sin(2 * PI * hz * it / 16_000.0) * 12_000).toInt().toShort() }

    private fun grid(pcm: ShortArray, cols: Int = 60, bands: Int = 24) =
        Spectrogram.compute(pcm.size, cols, bands) { s, e -> pcm.copyOfRange(s.coerceAtMost(pcm.size), e.coerceAtMost(pcm.size)) }

    private fun peakBand(g: Spectrogram.Grid, col: Int): Int =
        (0 until g.bands).maxByOrNull { g.value(col, it) }!!

    @Test fun toneEnergyLandsInTheRightBand() {
        val edges = Spectrogram.bandEdgesHz(24)
        for (hz in listOf(300.0, 1000.0, 3000.0)) {
            val g = grid(tone(hz))
            val expected = (0 until 24).first { hz >= edges[it] && hz < edges[it + 1] }
            val got = peakBand(g, 30)
            assertTrue("hz=$hz expected=$expected got=$got", kotlin.math.abs(got - expected) <= 1)
        }
    }

    @Test fun silenceGivesEmptyGrid() {
        val g = grid(ShortArray(16_000 * 3))
        assertTrue(g.isEmpty)
        assertEquals(60, g.columns)
    }

    @Test fun valuesAreNormalized() {
        val g = grid(tone(800.0) + ShortArray(16_000 * 2))
        assertTrue(g.data.all { it in 0f..1f })
        assertTrue(g.data.max() > 0.9f)
    }

    @Test fun loudPartIsBrighterThanQuietPart() {
        val loud = tone(1200.0, 2)
        val quiet = ShortArray(loud.size) { (loud[it] / 40).toShort() }
        val g = grid(loud + quiet, cols = 40)
        fun colEnergy(c: Int) = (0 until g.bands).sumOf { g.value(c, it).toDouble() }
        assertTrue(colEnergy(5) > colEnergy(35))
    }

    @Test fun degenerateInputs() {
        assertEquals(0, Spectrogram.compute(0, 10) { _, _ -> ShortArray(0) }.columns)
        assertEquals(0, Spectrogram.compute(1000, 0) { _, _ -> ShortArray(0) }.columns)
        val g = Spectrogram.compute(16_000, 10) { _, _ -> throw RuntimeException("io") }
        assertEquals(10, g.columns)
        assertTrue(g.isEmpty)
    }

    @Test fun fftOfImpulseIsFlat() {
        val re = DoubleArray(8); val im = DoubleArray(8)
        re[0] = 1.0
        Spectrogram.fft(re, im)
        assertTrue(re.all { kotlin.math.abs(it - 1.0) < 1e-9 })
        assertTrue(im.all { kotlin.math.abs(it) < 1e-9 })
    }

    @Test fun heatColorGrowsBrighter() {
        val base = 0xFF0A1020.toInt(); val acc = 0xFF2F9ADA.toInt()
        fun lum(c: Int) = ((c shr 16) and 0xFF) + ((c shr 8) and 0xFF) + (c and 0xFF)
        var prev = -1
        for (i in 0..10) {
            val l = lum(Spectrogram.heatColor(i / 10f, base, acc))
            assertTrue(l >= prev)
            prev = l
        }
        assertEquals(0xFF, (Spectrogram.heatColor(0.5f, base, acc) ushr 24))
    }
}
