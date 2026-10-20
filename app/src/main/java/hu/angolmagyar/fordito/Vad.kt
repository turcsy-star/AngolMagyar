package hu.angolmagyar.fordito

import kotlin.math.sqrt

/**
 * Egyszerű energia-alapú beszéd-szegmentálás: a hangot mondatnyi szeletekre vágja a csendeknél.
 * A rendszer-felismerős (magyar) fájl-átirat ezekre a szeletekre épül, a szelet eleje/vége adja
 * a felirat időzítését.
 */
object Vad {
    data class Seg(val start: Int, val end: Int)

    fun segments(
        pcm: ShortArray,
        rate: Int = 16000,
        minSilenceMs: Int = 350,
        maxSegMs: Int = 12_000,
        minSegMs: Int = 300
    ): List<Seg> {
        val frame = rate / 50 // 20 ms
        val n = pcm.size / frame
        if (n < 5) return emptyList()
        val rms = DoubleArray(n)
        for (f in 0 until n) {
            var s = 0.0
            val o = f * frame
            for (k in 0 until frame) {
                val v = pcm[o + k].toDouble()
                s += v * v
            }
            rms[f] = sqrt(s / frame)
        }
        val sorted = rms.sortedArray()
        val floor = sorted[(n * 0.15).toInt().coerceIn(0, n - 1)]
        val thr = maxOf(floor * 3.0, 90.0)

        val minSilFrames = (minSilenceMs / 20).coerceAtLeast(1)
        val padBefore = 8
        val padAfter = 12
        val out = ArrayList<Seg>()
        var segStart = -1
        var lastSpeech = -1
        var silentRun = 0
        for (f in 0 until n) {
            if (rms[f] > thr) {
                if (segStart < 0) segStart = f
                lastSpeech = f
                silentRun = 0
            } else if (segStart >= 0) {
                silentRun++
                if (silentRun >= minSilFrames) {
                    addSeg(out, segStart, lastSpeech, n, frame, padBefore, padAfter, minSegMs, maxSegMs, rms)
                    segStart = -1
                    lastSpeech = -1
                    silentRun = 0
                }
            }
        }
        if (segStart >= 0) {
            addSeg(out, segStart, lastSpeech, n, frame, padBefore, padAfter, minSegMs, maxSegMs, rms)
        }
        // a szeletek ne fedjék egymást
        val res = ArrayList<Seg>()
        var prevEnd = 0
        for (s in out) {
            val st = maxOf(s.start, prevEnd)
            if (s.end - st > rate * minSegMs / 1000) {
                res.add(Seg(st, s.end.coerceAtMost(pcm.size)))
                prevEnd = s.end
            }
        }
        return res
    }

    private fun addSeg(
        out: MutableList<Seg>, sf: Int, ef: Int, n: Int, frame: Int,
        padBefore: Int, padAfter: Int, minSegMs: Int, maxSegMs: Int, rms: DoubleArray
    ) {
        var a = (sf - padBefore).coerceAtLeast(0)
        val b = (ef + padAfter).coerceAtMost(n)
        if ((b - a) * 20 < minSegMs) return
        val maxF = maxSegMs / 20
        while (b - a > maxF) {
            // a legcsendesebb keret az ablak utolsó 3 másodpercében
            val lo = (a + maxF - 150).coerceAtLeast(a + 50)
            val hi = a + maxF
            var best = lo
            var bv = Double.MAX_VALUE
            for (f in lo until hi.coerceAtMost(n)) {
                if (rms[f] < bv) { bv = rms[f]; best = f }
            }
            out.add(Seg(a * frame, best * frame))
            a = best
        }
        out.add(Seg(a * frame, b * frame))
    }
}
