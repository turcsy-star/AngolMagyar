package hu.angolmagyar.fordito

import kotlin.math.sqrt

/**
 * Egyszerű energia-alapú beszéd-szegmentálás: a hangot mondatnyi szeletekre vágja a csendeknél.
 * A rendszer-felismerős (magyar) fájl-átirat ezekre a szeletekre épül, a szelet eleje/vége adja
 * a felirat időzítését.
 *
 * Emellett a Whisper útvonalhoz intelligens darabolást is ad: a csendeket kidobja, csak a
 * beszédrészeket dolgoztatja fel (speechChunks).
 */
object Vad {
    data class Seg(val start: Int, val end: Int)

    /**
     * Gyors energia-ellenőrzés: van-e érdemi beszéd a szeletben.
     * Csendes (vagy nagyon halk) chunkoknál true → a Whisper-t ki lehet hagyni.
     */
    fun isMostlySilent(pcm: ShortArray, rate: Int = 16000, minSpeechMs: Int = 500): Boolean {
        if (pcm.size < rate / 2) return true
        val frame = rate / 50 // 20 ms
        val n = pcm.size / frame
        if (n < 10) return true
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
        // Szigorúbb: háttérzaj ne számítson beszédnek (korábban túl sok zaj-szelet ment a Whisper-nek)
        val floor = sorted[(n * 0.20).toInt().coerceIn(0, n - 1)]
        val thr = maxOf(floor * 3.5, 110.0)
        var speechFrames = 0
        for (v in rms) if (v > thr) speechFrames++
        return speechFrames * 20 < minSpeechMs
    }

    /**
     * Intelligens darabolás Whisper-hez: a csendeket kidobja, a beszédrészeket
     * összevonja ha közel vannak, és max. [maxChunkMs] hosszú munkadarabokra vágja.
     * Visszaadott szegmensek sample-indexek (start inkluzív, end exkluzív).
     *
     * @param maxGapMs  ennél rövidebb csendet nem vág szét (összevon)
     * @param padMs     beszédrész eleje/vége plusz padding
     * @param maxChunkMs egy munkadarab maximális hossza (Whisper-barát)
     */
    fun speechChunks(
        pcm: ShortArray,
        rate: Int = 16000,
        maxGapMs: Int = 2500,
        padMs: Int = 400,
        maxChunkMs: Int = 180_000,
        minSpeechMs: Int = 350
    ): List<Seg> {
        val fine = segments(pcm, rate, minSilenceMs = 320, maxSegMs = 15_000, minSegMs = minSpeechMs)
        if (fine.isEmpty()) return emptyList()

        val maxGapSamp = rate * maxGapMs / 1000
        val padSamp = rate * padMs / 1000
        val maxChunkSamp = (rate.toLong() * maxChunkMs / 1000L).coerceAtLeast(rate * 30L).coerceAtMost(Int.MAX_VALUE.toLong() / 2).toInt()

        // 1) közeli finom szegmensek összevonása
        val merged = ArrayList<Seg>()
        var curS = fine[0].start
        var curE = fine[0].end
        for (i in 1 until fine.size) {
            val s = fine[i]
            if (s.start - curE <= maxGapSamp) {
                curE = s.end
            } else {
                merged.add(Seg(curS, curE))
                curS = s.start
                curE = s.end
            }
        }
        merged.add(Seg(curS, curE))

        // 2) padding + max hossz szerinti vágás
        val out = ArrayList<Seg>()
        for (m in merged) {
            var a = (m.start - padSamp).coerceAtLeast(0)
            val b = (m.end + padSamp).coerceAtMost(pcm.size)
            var pos = a
            while (pos < b) {
                val end = (pos + maxChunkSamp).coerceAtMost(b)
                if (end - pos >= rate * minSpeechMs / 1000) {
                    out.add(Seg(pos, end))
                }
                pos = end
            }
        }
        return out
    }

    /**
     * Ugyanaz, mint [speechChunks], de a PCM fájlból ablakonként olvas (nem tölti be az egészet).
     * Hosszú filmeknél kötelező – különben OutOfMemoryError.
     */
    fun speechChunksFromFile(
        pcmFile: java.io.File,
        totalSamples: Int,
        rate: Int = 16000,
        maxGapMs: Int = 2500,
        padMs: Int = 400,
        maxChunkMs: Int = 180_000,
        minSpeechMs: Int = 350
    ): List<Seg> {
        if (totalSamples <= 0) return emptyList()
        // ~90 mp-es ablak, 10 mp átfedés – a határon levő beszéd ne vesszen el
        val win = rate * 90
        val hop = rate * 80
        val allFine = ArrayList<Seg>()
        var pos = 0
        while (pos < totalSamples) {
            val end = (pos + win).coerceAtMost(totalSamples)
            val slice = ParallelChunker.readPcmRange(pcmFile, pos, end)
            if (slice.isNotEmpty()) {
                val local = segments(slice, rate, minSilenceMs = 320, maxSegMs = 15_000, minSegMs = minSpeechMs)
                for (s in local) {
                    allFine.add(Seg(s.start + pos, s.end + pos))
                }
            }
            if (end >= totalSamples) break
            pos += hop
        }
        if (allFine.isEmpty()) return emptyList()
        // egyesít / padding – dummy tömb helyett a totalSamples a felső határ
        val maxGapSamp = rate * maxGapMs / 1000
        val padSamp = rate * padMs / 1000
        val maxChunkSamp = (rate.toLong() * maxChunkMs / 1000L).coerceAtLeast(rate * 30L).coerceAtMost(Int.MAX_VALUE.toLong() / 2).toInt()
        allFine.sortBy { it.start }
        val merged = ArrayList<Seg>()
        var curS = allFine[0].start
        var curE = allFine[0].end
        for (i in 1 until allFine.size) {
            val s = allFine[i]
            if (s.start - curE <= maxGapSamp) {
                curE = maxOf(curE, s.end)
            } else {
                merged.add(Seg(curS, curE))
                curS = s.start
                curE = s.end
            }
        }
        merged.add(Seg(curS, curE))
        val out = ArrayList<Seg>()
        for (m in merged) {
            var a = (m.start - padSamp).coerceAtLeast(0)
            val b = (m.end + padSamp).coerceAtMost(totalSamples)
            var p = a
            while (p < b) {
                val e = (p + maxChunkSamp).coerceAtMost(b)
                if (e - p >= rate * minSpeechMs / 1000) out.add(Seg(p, e))
                p = e
            }
        }
        return out
    }

    fun segments(
        pcm: ShortArray,
        rate: Int = 16000,
        minSilenceMs: Int = 380,
        maxSegMs: Int = 12_000,
        minSegMs: Int = 350
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
        // Szigorúbb energia-küszöb: háttérzaj / halk zörej ne legyen beszédszelet
        val floor = sorted[(n * 0.20).toInt().coerceIn(0, n - 1)]
        val thr = maxOf(floor * 3.8, 130.0)

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
