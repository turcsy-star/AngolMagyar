package hu.angolmagyar.fordito

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Könnyű, eszközön futó beszélő-követés élő hanghoz: minden beszédszakaszból alapfrekvenciát (F0) és
 * zengésjellemzőt számol, majd online csoportosít. Nem tökéletes (két hasonló hangú embert összevonhat),
 * de a női/férfi/gyerek hangok és a váltakozó beszélők megkülönböztetésére jól használható.
 */
class SpeakerTracker(
    private val maxSpeakers: Int = 6,
    /** ln(F0-arány) küszöb: ennél nagyobb eltérés = másik beszélő (0,22 ≈ 4 félhang) */
    private val newSpeakerLogDist: Double = 0.22
) {
    data class Voice(val f0: Double, val zcr: Double, val rms: Double)

    private class Cluster(var f0: Double, var zcr: Double, var n: Int)

    private val clusters = ArrayList<Cluster>()
    private var last = 0

    val speakerCount: Int get() = clusters.size

    fun reset() { clusters.clear(); last = 0 }

    /** A beszédszakasz beszélője (1-től), 0 ha nem állapítható meg és még nincs korábbi beszélő. */
    fun assign(pcm: FloatArray, sampleRate: Int = 16_000): Int {
        val v = features(pcm, sampleRate)
        return assign(v)
    }

    fun assign(v: Voice): Int {
        if (v.f0 <= 0.0) return last // zöngétlen/rövid: marad az előző beszélő
        if (clusters.isEmpty()) {
            clusters.add(Cluster(v.f0, v.zcr, 1)); last = 1; return 1
        }
        var best = -1
        var bestD = Double.MAX_VALUE
        for ((i, c) in clusters.withIndex()) {
            val d = abs(ln(v.f0 / c.f0)) + 0.15 * abs(v.zcr - c.zcr) / (c.zcr + 0.02)
            // az előző beszélő enyhe előnyt kap (a hang magassága mondaton belül is ingadozik)
            val dd = if (i + 1 == last) d * 0.85 else d
            if (dd < bestD) { bestD = dd; best = i }
        }
        if (bestD > newSpeakerLogDist && clusters.size < maxSpeakers) {
            clusters.add(Cluster(v.f0, v.zcr, 1)); last = clusters.size; return last
        }
        val c = clusters[best]
        val w = if (c.n < 5) 1.0 / (c.n + 1) else 0.2
        c.f0 += (v.f0 - c.f0) * w
        c.zcr += (v.zcr - c.zcr) * w
        c.n++
        last = best + 1
        return last
    }

    companion object {
        /** F0 (medián) autokorrelációval a zöngés keretekből; 60–400 Hz között keres. */
        fun features(pcm: FloatArray, sampleRate: Int = 16_000): Voice {
            val frame = sampleRate * 40 / 1000
            val hop = sampleRate * 20 / 1000
            val minLag = sampleRate / 400
            val maxLag = sampleRate / 60
            if (pcm.size < frame + maxLag) return Voice(0.0, 0.0, 0.0)
            val f0s = ArrayList<Double>()
            var rmsSum = 0.0
            var zcrSum = 0.0
            var frames = 0
            var pos = 0
            while (pos + frame + maxLag <= pcm.size) {
                var e = 0.0
                var zc = 0
                for (i in 0 until frame) {
                    val x = pcm[pos + i].toDouble()
                    e += x * x
                    if (i > 0 && (x >= 0) != (pcm[pos + i - 1] >= 0)) zc++
                }
                val rms = sqrt(e / frame)
                rmsSum += rms; zcrSum += zc.toDouble() / frame; frames++
                if (rms > 0.01) {
                    val rs = DoubleArray(maxLag + 1)
                    var bestR = 0.0
                    for (lag in minLag..maxLag) {
                        var s = 0.0
                        var e2 = 0.0
                        for (i in 0 until frame) {
                            val a = pcm[pos + i].toDouble()
                            val b = pcm[pos + i + lag].toDouble()
                            s += a * b
                            e2 += b * b
                        }
                        val r = s / (sqrt(e * e2) + 1e-9)
                        rs[lag] = r
                        if (r > bestR) bestR = r
                    }
                    // oktávhiba ellen: a legrövidebb lag, amelynek csúcsa közel akkora, mint a legjobb
                    var bestLag = 0
                    for (lag in minLag + 1 until maxLag) {
                        if (rs[lag] >= 0.85 * bestR && rs[lag] >= rs[lag - 1] && rs[lag] >= rs[lag + 1]) { bestLag = lag; break }
                    }
                    if (bestR > 0.5 && bestLag > 0) f0s.add(sampleRate.toDouble() / bestLag)
                }
                pos += hop
            }
            if (frames == 0 || f0s.size < 3) return Voice(0.0, zcrSum / maxOf(1, frames), rmsSum / maxOf(1, frames))
            f0s.sort()
            return Voice(f0s[f0s.size / 2], zcrSum / frames, rmsSum / frames)
        }
    }
}
