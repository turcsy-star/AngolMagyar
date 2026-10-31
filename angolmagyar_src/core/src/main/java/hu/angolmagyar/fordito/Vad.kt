package hu.angolmagyar.fordito

import java.io.File
import java.io.RandomAccessFile
import kotlin.math.max
import kotlin.math.min

/**
 * Beszéd-szakaszolás (VAD): csendek kiszűrése, darabolás a Whisper számára.
 *
 * Két mód:
 *  - energia-alapú, adaptív küszöbbel (a fájl zajszintjéhez viszonyítva), mindig elérhető;
 *  - neurális (Silero, whisper.cpp), ha a hívó modellfájlt ad – hiba esetén automatikusan energiára vált.
 *
 * Minden pozíció 16 kHz-es minta-index (Int). v2.22.3: a v2.22.0 csonkot kiegészítettem a hiányzó
 * függvényekkel (segments, speechChunks, speechChunksFromFile, splitAtPauses, mergeSmallPieces, isMostlySilent).
 */
object Vad {
    data class Seg(val start: Int, val end: Int)

    private const val RATE = 16000
    private const val FRAME = 320                // 20 ms
    private const val ABS_MIN_RMS = 200f         // ennél halkabb nem beszéd (kb. -44 dBFS)

    /**
     * Neurális (Silero) VAD hívása: (modellfájl, hang, szálak) → [t0,t1,…] centiszekundumban, vagy null.
     * Az :app állítja be induláskor (App.onCreate); a :core így nem függ a natív könyvtártól.
     */
    @Volatile var neuralProvider: ((String, FloatArray, Int) -> FloatArray?)? = null

    @Volatile var lastMethod: String = ""
    @Volatile var lastSpeechMs: Long = 0L
    @Volatile var lastFine: List<Seg> = emptyList()

    // ------------------------------------------------------------------ keret-energia

    private fun rmsOf(pcm: ShortArray, from: Int, to: Int): Float {
        var sum = 0.0
        for (i in from until to) { val v = pcm[i].toDouble(); sum += v * v }
        return Math.sqrt(sum / max(1, to - from)).toFloat()
    }

    private fun frameRms(pcm: ShortArray): FloatArray {
        val n = pcm.size / FRAME
        return FloatArray(n) { rmsOf(pcm, it * FRAME, (it + 1) * FRAME) }
    }

    /** Küszöb: zajszint (10. percentilis) háromszorosa, de legalább az abszolút minimum és a csúcsszint 5%-a. */
    internal fun threshold(rms: FloatArray): Float {
        if (rms.isEmpty()) return ABS_MIN_RMS
        val sorted = rms.copyOf().also { it.sort() }
        val floor = sorted[(sorted.size * 0.10).toInt().coerceIn(0, sorted.size - 1)]
        val peak = sorted[(sorted.size * 0.95).toInt().coerceIn(0, sorted.size - 1)]
        return max(ABS_MIN_RMS, max(floor * 3f, peak * 0.05f))
    }

    /** Keret-szintű beszéd-maszkból finom szakaszok: a [minSilenceMs]-nél rövidebb szüneteket átível. */
    internal fun fineFromFrames(
        rms: FloatArray, thr: Float, minSilenceMs: Int, minSpeechMs: Int, totalSamples: Int
    ): List<Seg> {
        val out = ArrayList<Seg>()
        val minSilFrames = max(1, minSilenceMs * RATE / 1000 / FRAME)
        var start = -1
        var lastSpeech = -1
        for (i in rms.indices) {
            val sp = rms[i] > thr
            if (sp) {
                if (start < 0) start = i
                lastSpeech = i
            } else if (start >= 0 && i - lastSpeech >= minSilFrames) {
                out.add(Seg(start * FRAME, (lastSpeech + 1) * FRAME))
                start = -1
            }
        }
        if (start >= 0) out.add(Seg(start * FRAME, min(totalSamples, (lastSpeech + 1) * FRAME)))
        val minLen = minSpeechMs * RATE / 1000
        return out.filter { it.end - it.start >= minLen }
    }

    // ------------------------------------------------------------------ memóriában lévő hang

    /**
     * Beszédszakaszok (kis kitöltéssel). A [maxSegMs]-nál hosszabb folyamatos beszédet a legcsendesebb pontnál vágja.
     */
    fun segments(
        pcm: ShortArray,
        minSilenceMs: Int = 500,
        minSpeechMs: Int = 250,
        maxSegMs: Int = 30_000,
        padMs: Int = 200
    ): List<Seg> {
        if (pcm.size < FRAME * 2) return emptyList()
        val rms = frameRms(pcm)
        val fine = fineFromFrames(rms, threshold(rms), minSilenceMs, minSpeechMs, pcm.size)
        val capped = ArrayList<Seg>()
        val maxLen = maxSegMs.toLong() * RATE / 1000
        for (s in fine) capSegment(s, rms, maxLen.toInt(), capped)
        return pad(capped, padMs, pcm.size)
    }

    private fun capSegment(s: Seg, rms: FloatArray, maxLen: Int, out: MutableList<Seg>) {
        var cur = s
        while (cur.end - cur.start > maxLen) {
            // a megengedett hossz hátsó felében a legcsendesebb keret
            val lo = (cur.start + maxLen / 2) / FRAME
            val hi = min((cur.start + maxLen) / FRAME, rms.size - 1)
            var best = hi
            var bestV = Float.MAX_VALUE
            for (f in lo..hi) if (f in rms.indices && rms[f] < bestV) { bestV = rms[f]; best = f }
            val cut = (best * FRAME).coerceIn(cur.start + FRAME, cur.end - FRAME)
            out.add(Seg(cur.start, cut))
            cur = Seg(cut, cur.end)
        }
        out.add(cur)
    }

    private fun pad(segs: List<Seg>, padMs: Int, total: Int): List<Seg> {
        if (segs.isEmpty()) return segs
        val p = padMs * RATE / 1000
        val out = ArrayList<Seg>(segs.size)
        for ((i, s) in segs.withIndex()) {
            var a = max(0, s.start - p)
            var b = min(total, s.end + p)
            if (i > 0) { val prevEnd = segs[i - 1].end; if (a < prevEnd) a = (prevEnd + s.start) / 2 }
            if (i < segs.size - 1) { val nextStart = segs[i + 1].start; if (b > nextStart) b = (s.end + nextStart) / 2 }
            out.add(Seg(a, max(a, b)))
        }
        return out
    }

    /** Csak csend (vagy a zajszintnél alig hangosabb) van-e a szeletben. */
    fun isMostlySilent(slice: ShortArray): Boolean {
        if (slice.size < FRAME) return true
        val rms = frameRms(slice)
        val sorted = rms.copyOf().also { it.sort() }
        val floor = sorted[(sorted.size * 0.10).toInt().coerceIn(0, sorted.size - 1)]
        val thr = max(ABS_MIN_RMS, floor * 3f)
        val speechFrames = rms.count { it > thr }
        return speechFrames * FRAME * 1000L / RATE < 300
    }

    /**
     * Beszédszakaszok darabokba fűzése: a [maxGapMs]-nál kisebb szünetek összekötnek, a [maxChunkMs]-nál hosszabb
     * csoportot a legnagyobb szünetnél vágja.
     */
    fun speechChunks(
        pcm: ShortArray,
        maxGapMs: Int,
        maxChunkMs: Int,
        padMs: Int = 300,
        minSpeechMs: Int = 250
    ): List<Seg> {
        if (pcm.size < FRAME * 2) return emptyList()
        val rms = frameRms(pcm)
        val fine = fineFromFrames(rms, threshold(rms), 300, minSpeechMs, pcm.size)
        lastFine = fine
        lastSpeechMs = fine.sumOf { (it.end - it.start).toLong() } / 16L
        lastMethod = "energia"
        return chunksFromFine(fine, pcm.size, maxGapMs, padMs, maxChunkMs, 0, 0, 0, 0, 0)
    }

    // ------------------------------------------------------------------ fájlból (streaming)

    /** 16 bites, little-endian PCM fájl [startSample, endSample) tartománya. */
    internal fun readRange(file: File, startSample: Int, endSample: Int): ShortArray {
        val n = max(0, endSample - startSample)
        val out = ShortArray(n)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(startSample.toLong() * 2)
            val buf = ByteArray(1 shl 16)
            var done = 0
            while (done < n) {
                val want = min(buf.size / 2, n - done)
                val got = raf.read(buf, 0, want * 2)
                if (got <= 1) break
                val cnt = got / 2
                for (i in 0 until cnt) {
                    out[done + i] = ((buf[2 * i].toInt() and 0xFF) or (buf[2 * i + 1].toInt() shl 8)).toShort()
                }
                done += cnt
            }
        }
        return out
    }

    private fun frameRmsFromFile(file: File, totalSamples: Int): FloatArray {
        val nFrames = totalSamples / FRAME
        val out = FloatArray(nFrames)
        RandomAccessFile(file, "r").use { raf ->
            val framesPerRead = 500
            val buf = ByteArray(framesPerRead * FRAME * 2)
            var f = 0
            while (f < nFrames) {
                val want = min(framesPerRead, nFrames - f)
                val got = raf.read(buf, 0, want * FRAME * 2)
                if (got < FRAME * 2) break
                val frames = got / (FRAME * 2)
                for (k in 0 until frames) {
                    var sum = 0.0
                    val base = k * FRAME * 2
                    for (i in 0 until FRAME) {
                        val v = ((buf[base + 2 * i].toInt() and 0xFF) or (buf[base + 2 * i + 1].toInt() shl 8)).toShort().toDouble()
                        sum += v * v
                    }
                    out[f + k] = Math.sqrt(sum / FRAME).toFloat()
                }
                f += frames
            }
        }
        return out
    }

    /**
     * Teljes fájl beszéd-darabolása, közös küszöbbel (a halk háttérzaj nem beszéd). Közben a hang nincs egyben a
     * memóriában. Eredmény: kitöltött darabok; a finom szakaszok a [lastFine]-ban, a tiszta beszédhossz a [lastSpeechMs]-ben.
     */
    fun speechChunksFromFile(
        file: File,
        totalSamples: Int,
        maxGapMs: Int,
        padMs: Int,
        maxChunkMs: Int,
        minSpeechMs: Int = 250,
        neuralModel: String? = null,
        threads: Int = 4,
        minChunkMs: Int = 0,
        softGapMs: Int = 0,
        mergeMinSpeechMs: Int = 0,
        mergeMaxSpeechMs: Int = 0,
        mergeMaxSpanMs: Int = 0
    ): List<Seg> {
        if (totalSamples < FRAME * 2) {
            lastFine = emptyList(); lastSpeechMs = 0; lastMethod = "energia"
            return emptyList()
        }
        var fine: List<Seg>? = null
        if (neuralModel != null) {
            fine = try { neuralFine(file, totalSamples, neuralModel, threads, minSpeechMs) } catch (_: Throwable) { null }
            if (fine != null) lastMethod = "neurális (Silero)"
        }
        if (fine == null) {
            val rms = frameRmsFromFile(file, totalSamples)
            fine = fineFromFrames(rms, threshold(rms), 300, minSpeechMs, totalSamples)
            lastMethod = "energia"
        }
        lastFine = fine
        lastSpeechMs = fine.sumOf { (it.end - it.start).toLong() } / 16L
        return chunksFromFine(
            fine, totalSamples, maxGapMs, padMs, maxChunkMs, minChunkMs, softGapMs,
            mergeMinSpeechMs, mergeMaxSpeechMs, mergeMaxSpanMs
        )
    }

    /** Neurális VAD 5 perces ablakokban (a teljes hang nem kerül egyszerre a memóriába). */
    private fun neuralFine(file: File, total: Int, model: String, threads: Int, minSpeechMs: Int): List<Seg>? {
        val window = 5 * 60 * RATE
        val out = ArrayList<Seg>()
        var pos = 0
        while (pos < total) {
            val end = min(total, pos + window)
            val pcm = readRange(file, pos, end)
            val audio = FloatArray(pcm.size) { pcm[it] / 32768f }
            val provider = neuralProvider ?: return null
            val cs = provider(model, audio, threads) ?: return null
            var i = 0
            while (i + 1 < cs.size) {
                val a = pos + (cs[i] * 160f).toInt()
                val b = pos + (cs[i + 1] * 160f).toInt()
                if (b > a) out.add(Seg(a.coerceIn(0, total), b.coerceIn(0, total)))
                i += 2
            }
            pos = end
        }
        // az ablakhatáron kettévágott szakaszok összeillesztése + minimális hossz
        val merged = ArrayList<Seg>()
        for (s in out.sortedBy { it.start }) {
            val last = merged.lastOrNull()
            if (last != null && s.start - last.end <= FRAME) merged[merged.size - 1] = Seg(last.start, max(last.end, s.end))
            else merged.add(s)
        }
        val minLen = minSpeechMs * RATE / 1000
        return merged.filter { it.end - it.start >= minLen }
    }

    // ------------------------------------------------------------------ darabolási logika (tiszta)

    internal fun chunksFromFine(
        fine: List<Seg>, total: Int, maxGapMs: Int, padMs: Int, maxChunkMs: Int,
        minChunkMs: Int, softGapMs: Int, mergeMinMs: Int, mergeMaxMs: Int, mergeSpanMs: Int
    ): List<Seg> {
        if (fine.isEmpty()) return emptyList()
        val maxGap = maxGapMs.toLong() * 16
        val softGap = softGapMs.toLong() * 16
        val minChunk = minChunkMs.toLong() * 16
        val maxChunk = maxChunkMs.toLong() * 16
        // 1) csoportok: nagy szünet, vagy (elég hosszú darab után) lágy szünet vág
        val groups = ArrayList<MutableList<Seg>>()
        var cur = mutableListOf(fine[0])
        for (i in 1 until fine.size) {
            val s = fine[i]
            val gap = (s.start - cur.last().end).toLong()
            val curLen = (cur.last().end - cur.first().start).toLong()
            val br = gap > maxGap || (softGap > 0 && minChunk > 0 && curLen >= minChunk && gap >= softGap)
            if (br) { groups.add(cur); cur = mutableListOf(s) } else cur.add(s)
        }
        groups.add(cur)
        // 2) túl hosszú csoport: vágás a legnagyobb szünetnél
        var pieces = ArrayList<Seg>()
        for (g in groups) pieces.addAll(splitAtPauses(g, maxChunk.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()))
        // 3) kis darabok összevonása
        if (mergeMinMs > 0 && mergeMaxMs > 0) {
            pieces = ArrayList(mergeSmallPieces(
                pieces, fine, mergeMinMs.toLong() * 16, mergeMaxMs.toLong() * 16,
                if (mergeSpanMs > 0) mergeSpanMs.toLong() * 16 else Long.MAX_VALUE
            ))
        }
        return pad(pieces, padMs, total)
    }

    /**
     * Szakaszok egy csoportját [maxSamples]-nél nem hosszabb darabokra bontja, mindig a legnagyobb szünetnél vágva.
     * Egyetlen, a határnál hosszabb szakaszt nem darabol tovább.
     */
    fun splitAtPauses(segs: List<Seg>, maxSamples: Int): List<Seg> {
        if (segs.isEmpty()) return emptyList()
        val span = segs.last().end.toLong() - segs.first().start
        if (span <= maxSamples || segs.size == 1) return listOf(Seg(segs.first().start, segs.last().end))
        var bestI = 1
        var bestGap = -1
        for (i in 1 until segs.size) {
            val g = segs[i].start - segs[i - 1].end
            if (g > bestGap) { bestGap = g; bestI = i }
        }
        return splitAtPauses(segs.subList(0, bestI), maxSamples) + splitAtPauses(segs.subList(bestI, segs.size), maxSamples)
    }

    /**
     * Kis (kevés beszédet tartalmazó) darabok összevonása a szomszédjukkal, amíg az összevont beszéd legfeljebb
     * [maxSpeech], a lefedett időtartam pedig legfeljebb [maxSpan] minta.
     * "Kicsi" az a darab, amelyben a beszéd kevesebb, mint [minSpeech].
     */
    fun mergeSmallPieces(pieces: List<Seg>, fine: List<Seg>, minSpeech: Long, maxSpeech: Long, maxSpan: Long): List<Seg> {
        if (pieces.isEmpty()) return emptyList()
        fun speechIn(s: Seg): Long {
            var sum = 0L
            for (f in fine) {
                if (f.end <= s.start) continue
                if (f.start >= s.end) break
                sum += (min(f.end, s.end) - max(f.start, s.start)).toLong()
            }
            return sum
        }
        val out = ArrayList<Seg>()
        var cur = pieces[0]
        var curSp = speechIn(cur)
        for (i in 1 until pieces.size) {
            val nx = pieces[i]
            val nxSp = speechIn(nx)
            val small = curSp < minSpeech || nxSp < minSpeech
            val ok = small && curSp + nxSp <= maxSpeech && (nx.end.toLong() - cur.start) <= maxSpan
            if (ok) { cur = Seg(cur.start, nx.end); curSp += nxSp }
            else { out.add(cur); cur = nx; curSp = nxSp }
        }
        out.add(cur)
        return out
    }
}
