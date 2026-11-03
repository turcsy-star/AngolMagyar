package hu.angolmagyar.fordito

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * Hangkondicionálás a beszédfelismerés előtt (16 kHz, 16 bites mono PCM, helyben módosít).
 *
 * 1. Magas áteresztő szűrő (alapból 70 Hz): kiszedi az egyenfeszültség-eltolódást és a dübörgést.
 * 2. Hangerő-normalizálás a BESZÉDES keretek RMS-e alapján (nem a csendeset veszi figyelembe),
 *    csak felfelé erősít, az erősítést a zajszint és a csúcsok korlátozzák.
 * 3. Lágy limiter, így nincs kemény vágás.
 *
 * Nincs zajzár és nincs spektrális zajszűrés, mert az a Whisper pontosságát rontja.
 */
object AudioConditioner {
    const val SAMPLE_RATE = 16_000
    const val TARGET_RMS_DBFS = -23f
    const val MAX_GAIN_DB = 18f
    const val LOW_SNR_MAX_GAIN_DB = 6f
    private const val FRAME = 320                // 20 ms
    private const val MIN_ACTIVE_FRACTION = 0.02 // ennél kevesebb beszédes keretnél nem erősítünk
    private const val SOFT_KNEE = 0.8f

    data class Report(
        val applied: Boolean,
        val gainDb: Float,
        val speechRmsDbBefore: Float,
        val noiseDbBefore: Float,
        val note: String
    )

    private val NOT_APPLIED = Report(false, 0f, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, "kihagyva")

    private fun db(x: Double): Float = (20.0 * log10(x.coerceAtLeast(1e-9))).toFloat()

    fun condition(pcm: ShortArray, highPassHz: Float = 70f, normalize: Boolean = true): Report {
        if (pcm.size < SAMPLE_RATE / 2) return NOT_APPLIED
        if (highPassHz > 0f) highPass(pcm, highPassHz)
        if (!normalize) return Report(true, 0f, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, "csak szűrő")

        val frames = pcm.size / FRAME
        if (frames < 5) return NOT_APPLIED
        val rms = DoubleArray(frames)
        val peaks = FloatArray(frames)
        for (f in 0 until frames) {
            var acc = 0.0
            var pk = 0
            val base = f * FRAME
            for (i in base until base + FRAME) {
                val s = pcm[i].toInt()
                acc += s.toDouble() * s
                val a = abs(s)
                if (a > pk) pk = a
            }
            rms[f] = sqrt(acc / FRAME) / 32768.0
            peaks[f] = pk / 32768f
        }
        val sorted = rms.sortedArray()
        val noise = sorted[(frames * 0.10).toInt().coerceIn(0, frames - 1)]
        val threshold = maxOf(noise * 3.0, 0.003)
        var activePower = 0.0
        var active = 0
        for (r in rms) if (r >= threshold) { activePower += r * r; active++ }
        if (active < MIN_ACTIVE_FRACTION * frames || active == 0) {
            return Report(true, 0f, Float.NEGATIVE_INFINITY, db(noise), "nincs elég beszéd az erősítéshez")
        }
        val speechRms = sqrt(activePower / active)
        val speechDb = db(speechRms)
        val noiseDb = db(noise)
        val snr = speechDb - noiseDb
        val maxGain = if (snr < 8f) LOW_SNR_MAX_GAIN_DB else MAX_GAIN_DB
        var gainDb = (TARGET_RMS_DBFS - speechDb).coerceIn(0f, maxGain)
        // A 99. percentilis csúcs ne menjen 1.0 fölé az erősítés után.
        val sp = peaks.sortedArray()
        val p99 = sp[(frames * 0.99).toInt().coerceIn(0, frames - 1)].coerceAtLeast(1e-4f)
        val peakCapDb = db(0.98 / p99)
        if (gainDb > peakCapDb) gainDb = peakCapDb.coerceAtLeast(0f)
        if (gainDb < 1.0f) return Report(true, 0f, speechDb, noiseDb, "hangerő rendben")

        val g = 10.0.pow(gainDb / 20.0).toFloat()
        for (i in pcm.indices) {
            val y = softLimit(pcm[i] / 32768f * g)
            pcm[i] = (y * 32767f).toInt().coerceIn(-32768, 32767).toShort()
        }
        return Report(true, gainDb, speechDb, noiseDb, "erősítve %.1f dB".format(java.util.Locale.US, gainDb))
    }

    /** Elsőrendű IIR magas áteresztő, helyben. */
    fun highPass(pcm: ShortArray, cutoffHz: Float, sampleRate: Int = SAMPLE_RATE) {
        if (pcm.isEmpty()) return
        val rc = 1.0 / (2.0 * PI * cutoffHz)
        val dt = 1.0 / sampleRate
        val a = (rc / (rc + dt)).toFloat()
        var prevX = pcm[0].toFloat()
        var prevY = 0f
        for (i in pcm.indices) {
            val x = pcm[i].toFloat()
            val y = a * (prevY + x - prevX)
            prevX = x
            prevY = y
            pcm[i] = y.toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    fun softLimit(x: Float): Float {
        val a = abs(x)
        if (a <= SOFT_KNEE) return x
        val over = a - SOFT_KNEE
        val room = 1f - SOFT_KNEE
        val y = SOFT_KNEE + room * tanh((over / room).toDouble()).toFloat()
        return if (x < 0) -y else y
    }
}
