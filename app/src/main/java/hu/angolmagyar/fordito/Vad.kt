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

    /** Az utolsó darabolás módja (naplózáshoz): "neurális (Silero)" vagy "hangerő-alapú". */
    @Volatile var lastMethod: String = ""

    /** A VAD által beszédnek ítélt hang tiszta hossza (ms) az utolsó darabolásnál – a szünetek nélkül. */
    @Volatile var lastSpeechMs: Long = 0L

    /** Silero VAD egy szeleten (sample-indexek); null = hiba. */
    fun neuralSegments(pcm: ShortArray, modelPath: String, threads: Int): List<Seg>? {
        val f = FloatArray(pcm.size) { pcm[it] / 32768f }
        val r = WhisperNative.vadSegments(modelPath, f, threads) ?: return null
        val out = ArrayList<Seg>()
        var i = 0
        while (i + 1 < r.size) {
            // centiszekundum → 16 kHz sample (1 cs = 160 sample)
            val a = (r[i] * 160f).toInt().coerceIn(0, pcm.size)
            val b = (r[i + 1] * 160f).toInt().coerceIn(0, pcm.size)
            if (b > a) out.add(Seg(a, b))
            i += 2
        }
        return out
    }

    /**
     * Gyors energia-ellenőrzés: van-e érdemi beszéd a szeletben.
     * Csendes (vagy nagyon halk) chunkoknál true → a Whisper-t ki lehet hagyni.
     */
    fun isMostlySilent(pcm: ShortArray, rate: Int = 16000, minSpeechMs: Int = 400): Boolean {
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
        val floor = sorted[(n * 0.15).toInt().coerceIn(0, n - 1)]
        val thr = maxOf(floor * 2.8, 70.0)
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
        minSpeechMs: Int = 250
    ): List<Seg> {
        val fine = segments(pcm, rate, minSilenceMs = 280, maxSegMs = 15_000, minSegMs = minSpeechMs)
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
        minSpeechMs: Int = 250,
        neuralModel: String? = null,
        threads: Int = 4,
        minChunkMs: Int = 0,
        softGapMs: Int = 0
    ): List<Seg> {
        if (totalSamples <= 0) return emptyList()
        lastMethod = "hangerő-alapú"
        var neuralOk = 0
        var neuralFail = 0
        // 1. menet: a TELJES fájl zajszintje és csúcsa – egy közös küszöb, hogy a halk, zajos / zenés
        // részeket ne tekintse beszédnek (ablakonként a „relatív” küszöb a csendes ablakokban mindent beszédnek vett)
        val thr = if (neuralModel == null) globalThreshold(pcmFile, totalSamples, rate) else 0.0
        var thrLazy: Double? = if (neuralModel == null) thr else null
        // energia-alapú: ~90 mp-es ablak, 10 mp átfedés; neurális: 5 perces, átfedés nélküli ablak
        val win = if (neuralModel != null) rate * 300 else rate * 90
        val hop = if (neuralModel != null) win else rate * 80
        val allFine = ArrayList<Seg>()
        var pos = 0
        while (pos < totalSamples) {
            val end = (pos + win).coerceAtMost(totalSamples)
            val slice = ParallelChunker.readPcmRange(pcmFile, pos, end)
            if (slice.isNotEmpty()) {
                var local: List<Seg>? = null
                if (neuralModel != null) {
                    local = neuralSegments(slice, neuralModel, threads)
                    if (local != null) neuralOk++ else neuralFail++
                }
                if (local == null) {
                    if (thrLazy == null) thrLazy = globalThreshold(pcmFile, totalSamples, rate)
                    local = segments(slice, rate, minSilenceMs = 280, maxSegMs = 15_000, minSegMs = minSpeechMs, thrOverride = thrLazy)
                }
                for (s in local) {
                    allFine.add(Seg(s.start + pos, s.end + pos))
                }
            }
            if (end >= totalSamples) break
            pos += hop
        }
        lastMethod = when {
            neuralOk > 0 && neuralFail == 0 -> "neurális (Silero)"
            neuralOk > 0 -> "vegyes (Silero + hangerő)"
            else -> "hangerő-alapú"
        }
        lastSpeechMs = allFine.sumOf { (it.end - it.start).toLong() } / (rate / 1000L)
        if (allFine.isEmpty()) return emptyList()
        // egyesít / padding – dummy tömb helyett a totalSamples a felső határ
        val maxGapSamp = rate * maxGapMs / 1000
        val padSamp = rate * padMs / 1000
        val maxChunkSamp = (rate.toLong() * maxChunkMs / 1000L).coerceAtLeast(rate * 30L).coerceAtMost(Int.MAX_VALUE.toLong() / 2).toInt()
        allFine.sortBy { it.start }
        val minChunkSamp = rate.toLong() * minChunkMs / 1000L
        val softGapSamp = rate.toLong() * softGapMs / 1000L
        // 1) természetes csoportok: ahol a szünet hosszú, ott darabhatár van
        val groups = ArrayList<ArrayList<Seg>>()
        var cur = arrayListOf(allFine[0])
        var curS = allFine[0].start
        var curE = allFine[0].end
        for (i in 1 until allFine.size) {
            val s = allFine[i]
            val gap = s.start - curE
            // közeli beszédrészek egy darabba; a túl rövid darabot nagyobb szünettel is összevonjuk (kontextus)
            val canJoin = gap <= maxGapSamp || ((curE - curS).toLong() < minChunkSamp && gap.toLong() <= softGapSamp)
            if (canJoin) {
                cur.add(s)
                curE = maxOf(curE, s.end)
            } else {
                groups.add(cur)
                cur = arrayListOf(s)
                curS = s.start
                curE = s.end
            }
        }
        groups.add(cur)
        // 2) a túl hosszú csoportot a LEGHOSSZABB SZÜNETNÉL vágjuk, nem beszéd közben
        val pieces = ArrayList<Seg>()
        for (g in groups) pieces.addAll(splitAtPauses(g, maxChunkSamp))
        // 3) padding a szünetbe; a szomszédos darabok nem lóghatnak egymásba (nincs kettős felismerés)
        val out = ArrayList<Seg>()
        for (m in pieces) {
            val a2 = (m.start - padSamp).coerceAtLeast(0)
            val b2 = (m.end + padSamp).coerceAtMost(totalSamples)
            if (b2 - a2 >= rate * minSpeechMs / 1000) out.add(Seg(a2, b2))
        }
        for (i in 0 until out.size - 1) {
            if (out[i].end > out[i + 1].start) {
                val mid = (out[i].end + out[i + 1].start) / 2
                out[i] = Seg(out[i].start, mid)
                out[i + 1] = Seg(mid, out[i + 1].end)
            }
        }
        return out
    }

    /**
     * Egy beszédrész-csoport darabolása legfeljebb [maxSamp] hosszú darabokra úgy, hogy a vágás a
     * legnagyobb szünetben legyen (a darab felső felében), ne beszéd közben.
     */
    fun splitAtPauses(segs: List<Seg>, maxSamp: Int): List<Seg> {
        val out = ArrayList<Seg>()
        if (segs.isEmpty()) return out
        var i = 0
        while (i < segs.size) {
            val start = segs[i].start
            var last = i
            while (last + 1 < segs.size && segs[last + 1].end.toLong() - start <= maxSamp) last++
            if (last == segs.size - 1) {
                out.add(Seg(start, segs.subList(i, last + 1).maxOf { it.end }))
                break
            }
            if (last == i && segs[i].end.toLong() - start > maxSamp) {
                // egyetlen, óriási folyamatos beszédrész: kényszervágás (nagyon ritka)
                out.add(Seg(start, start + maxSamp))
                i++
                continue
            }
            // vágáspont: a darab felső felében a legnagyobb szünet (ha nincs ilyen, bárhol a legnagyobb)
            var best = -1
            var bestGap = -1
            for (k in i..last) {
                if (k + 1 >= segs.size) break
                val len = segs[k].end.toLong() - start
                if (len < maxSamp / 2) continue
                val gap = segs[k + 1].start - segs[k].end
                if (gap >= bestGap) { bestGap = gap; best = k }
            }
            if (best < 0) {
                for (k in i..last) {
                    if (k + 1 >= segs.size) break
                    val gap = segs[k + 1].start - segs[k].end
                    if (gap >= bestGap) { bestGap = gap; best = k }
                }
            }
            if (best < 0) best = last
            out.add(Seg(start, segs.subList(i, best + 1).maxOf { it.end }))
            i = best + 1
        }
        return out
    }

    /** A teljes fájl 20 ms-os RMS értékeiből közös beszéd-küszöb: zajszint×3,5 és a csúcs 6%-a közül a nagyobb. */
    fun globalThreshold(pcmFile: java.io.File, totalSamples: Int, rate: Int = 16000): Double {
        val frame = rate / 50
        val all = DoubleArray(totalSamples / frame + 1)
        var cnt = 0
        val win = rate * 60
        var pos = 0
        while (pos < totalSamples) {
            val end = (pos + win).coerceAtMost(totalSamples)
            val slice = ParallelChunker.readPcmRange(pcmFile, pos, end)
            val n = slice.size / frame
            for (f in 0 until n) {
                var s = 0.0
                val o = f * frame
                for (k in 0 until frame) { val v = slice[o + k].toDouble(); s += v * v }
                if (cnt < all.size) all[cnt++] = sqrt(s / frame)
            }
            pos = end
        }
        if (cnt < 10) return 90.0
        val sorted = all.copyOf(cnt).also { it.sort() }
        val floor = sorted[(cnt * 0.20).toInt().coerceIn(0, cnt - 1)]
        val peak = sorted[(cnt * 0.95).toInt().coerceIn(0, cnt - 1)]
        return maxOf(floor * 3.5, peak * 0.06, 100.0)
    }

    fun segments(
        pcm: ShortArray,
        rate: Int = 16000,
        minSilenceMs: Int = 350,
        maxSegMs: Int = 12_000,
        minSegMs: Int = 300,
        thrOverride: Double? = null
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
        val thr = thrOverride ?: maxOf(floor * 3.0, 90.0)

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
