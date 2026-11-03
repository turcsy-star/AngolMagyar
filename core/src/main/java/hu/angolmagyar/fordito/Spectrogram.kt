package hu.angolmagyar.fordito

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin

/**
 * Spektrogram-csík: az egész felvételre egyenletesen elosztott oszlopokból, oszloponként egy
 * 512 mintás FFT-ablakból számolt, logaritmikus sávokra bontott hangerő-hőtérkép.
 * Olcsó (oszloponként egy FFT), ugyanazzal az olvasó-szerződéssel, mint az [Envelope].
 */
object Spectrogram {
    const val FFT_SIZE = 512
    const val SAMPLE_RATE = 16_000
    private const val LOW_HZ = 100.0
    private const val HIGH_HZ = 7_600.0
    private const val MIN_DYNAMIC_DB = 6f

    /** data[column * bands + band], 0..1; a 0. sáv a legmélyebb. */
    class Grid(val columns: Int, val bands: Int, val data: FloatArray) {
        fun value(column: Int, band: Int): Float = data[column * bands + band]
        val isEmpty: Boolean get() = columns == 0 || bands == 0 || data.all { it == 0f }
    }

    fun bandEdgesHz(bands: Int): DoubleArray =
        DoubleArray(bands + 1) { LOW_HZ * (HIGH_HZ / LOW_HZ).pow(it.toDouble() / bands) }

    fun compute(totalSamples: Int, columns: Int, bands: Int = 24, read: (Int, Int) -> ShortArray): Grid {
        if (totalSamples <= 0 || columns <= 0 || bands <= 0) return Grid(0, 0, FloatArray(0))
        val edges = bandEdgesHz(bands)
        val hz = SAMPLE_RATE.toDouble() / FFT_SIZE
        val lo = IntArray(bands) { (edges[it] / hz).toInt().coerceIn(1, FFT_SIZE / 2 - 1) }
        val hi = IntArray(bands) { (edges[it + 1] / hz).toInt().coerceIn(lo[it] + 1, FFT_SIZE / 2) }
        val window = DoubleArray(FFT_SIZE) { 0.5 - 0.5 * cos(2 * PI * it / (FFT_SIZE - 1)) }
        val db = FloatArray(columns * bands) { SILENCE_DB }
        val re = DoubleArray(FFT_SIZE)
        val im = DoubleArray(FFT_SIZE)
        for (c in 0 until columns) {
            val start = (c.toLong() * totalSamples / columns).toInt()
            val end = minOf(totalSamples.toLong(), start.toLong() + FFT_SIZE).toInt()
            val s = try { read(start, end) } catch (_: Exception) { ShortArray(0) }
            if (s.isEmpty()) continue
            for (i in 0 until FFT_SIZE) {
                re[i] = (if (i < s.size) s[i] / 32768.0 else 0.0) * window[i]
                im[i] = 0.0
            }
            fft(re, im)
            for (b in 0 until bands) {
                var p = 0.0
                for (k in lo[b] until hi[b]) p += re[k] * re[k] + im[k] * im[k]
                p /= (hi[b] - lo[b])
                db[c * bands + b] = (10.0 * log10(p + 1e-12)).toFloat()
            }
        }
        val sorted = db.sortedArray()
        val floor = sorted[(sorted.size * 0.05).toInt().coerceIn(0, sorted.size - 1)]
        val ceil = sorted[(sorted.size * 0.99).toInt().coerceIn(0, sorted.size - 1)]
        if (ceil - floor < MIN_DYNAMIC_DB) return Grid(columns, bands, FloatArray(columns * bands))
        val span = ceil - floor
        return Grid(columns, bands, FloatArray(db.size) { ((db[it] - floor) / span).coerceIn(0f, 1f) })
    }

    private const val SILENCE_DB = -120f

    /** Hőtérkép-szín: sötét alap → kiemelő szín → fehér csúcs. Átlátszatlan ARGB. */
    fun heatColor(v: Float, base: Int, accent: Int): Int {
        val x = v.coerceIn(0f, 1f)
        return if (x < 0.7f) PhasePalette.blend(base, accent, x / 0.7f)
        else PhasePalette.blend(accent, 0xFFFFFFFF.toInt(), (x - 0.7f) / 0.3f)
    }

    /** Helyben végzett radix-2 FFT (a hossz kettő hatványa). */
    fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val ang = -2 * PI / len
            val wr = cos(ang)
            val wi = sin(ang)
            var i = 0
            while (i < n) {
                var cr = 1.0
                var ci = 0.0
                for (k in 0 until len / 2) {
                    val a = i + k
                    val b = i + k + len / 2
                    val xr = re[b] * cr - im[b] * ci
                    val xi = re[b] * ci + im[b] * cr
                    re[b] = re[a] - xr; im[b] = im[a] - xi
                    re[a] += xr; im[a] += xi
                    val ncr = cr * wr - ci * wi
                    ci = cr * wi + ci * wr
                    cr = ncr
                }
                i += len
            }
            len = len shl 1
        }
    }
}
