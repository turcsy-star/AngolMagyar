package hu.angolmagyar.fordito

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Hangminőség-előszűrés a felismerés előtt: egyenáram-eltolás és mély zúgás (kb. 70 Hz alatt) eltávolítása,
 * majd a halk felvételek hangerejének felhúzása. A már jól kivezérelt hangot nem bántja. A tömböt helyben módosítja.
 */
object AudioPrep {
    private const val TARGET_RMS = 0.08f
    private const val MAX_GAIN = 6f
    private const val PEAK_LIMIT = 0.97f

    /** @return true, ha módosított valamit. */
    fun prepare(a: FloatArray, sampleRate: Int = 16000): Boolean {
        if (a.size < sampleRate / 2) return false
        // 1) egyenáram + 1. rendű felüláteresztő (~70 Hz)
        val rc = 1.0 / (2 * Math.PI * 70.0)
        val dt = 1.0 / sampleRate
        val alpha = (rc / (rc + dt)).toFloat()
        var prevIn = a[0]
        var prevOut = 0f
        for (i in a.indices) {
            val x = a[i]
            val y = alpha * (prevOut + x - prevIn)
            prevIn = x
            prevOut = y
            a[i] = y
        }
        // 2) a beszédes részek (nem csendes minták) átlagos hangereje
        var sum = 0.0
        var n = 0
        var peak = 0f
        for (v in a) {
            val av = abs(v)
            if (av > peak) peak = av
            if (av > 0.01f) { sum += v.toDouble() * v; n++ }
        }
        if (n < sampleRate / 4) return true
        val rms = sqrt(sum / n).toFloat()
        if (rms <= 1e-5f) return true
        var gain = (TARGET_RMS / rms).coerceIn(1f, MAX_GAIN)
        if (peak * gain > PEAK_LIMIT) gain = PEAK_LIMIT / peak
        if (gain > 1.05f) for (i in a.indices) a[i] *= gain
        return true
    }
}
