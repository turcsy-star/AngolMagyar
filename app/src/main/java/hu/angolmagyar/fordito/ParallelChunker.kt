package hu.angolmagyar.fordito

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hosszú felvétel darabokban (chunk) történő feldolgozása.
 *  - a párhuzamos chunkok száma és a pihenők a [Perf] szintből jönnek, FUTÁS KÖZBEN is változtathatók
 *  - melegedés / alacsony akku esetén a következő chunk várakozik (a már futók befejezik)
 *  - az átfedő határokat a felezőpontnál vágja, így nincs duplikált / elveszett mondat
 *  - támogatja az előre meghatározott tartományokat is (VAD-alapú intelligens darabolás)
 */
class ParallelChunker(private val ctx: Context, private val strictThermal: Boolean) {

    interface Events {
        fun onChunkStart(idx: Int, total: Int)
        fun onChunkDone(idx: Int, total: Int, cues: List<SrtExporter.Cue>, audioMs: Long)
        /** Várakozás (hűtés, akku) – cooling=false: a várakozás véget ért. */
        fun onWait(reason: String, cooling: Boolean)
    }

    data class Result(val cues: List<SrtExporter.Cue>, val chunksDone: Int, val total: Int, val stoppedEarly: Boolean)

    private val cancel = AtomicBoolean(false)
    fun cancel() = cancel.set(true)

    private lateinit var starts: IntArray
    private lateinit var ends: IntArray
    private var samplesPerChunk = 0
    private var overlapSamples = 0
    private var total = 0
    private val lock = Any()
    private var next = 0
    private var active = 0
    private var results: Array<List<SrtExporter.Cue>?> = emptyArray()
    private var failure: Throwable? = null

    fun chunkCount(): Int = total

    /**
     * @param sequentialOnly true = mindig egy chunk egyszerre (Whisper: a szálak a modellen belül osztoznak)
     * @param process egy chunk feldolgozása → cue-k (a chunk eleje a [offsetMs])
     */
    fun run(
        pcm: ShortArray, chunkMs: Int, overlapMs: Int, sequentialOnly: Boolean,
        sampleRate: Int = 16000, events: Events,
        process: (slice: ShortArray, offsetMs: Long, idx: Int) -> List<SrtExporter.Cue>
    ): Result {
        samplesPerChunk = (sampleRate * (chunkMs / 1000.0)).toInt().coerceAtLeast(sampleRate * 60)
        overlapSamples = (sampleRate * (overlapMs / 1000.0)).toInt().coerceIn(sampleRate, samplesPerChunk / 3)
        val step = (samplesPerChunk - overlapSamples).coerceAtLeast(sampleRate * 30)
        total = if (pcm.isEmpty()) 1 else ((pcm.size - overlapSamples + step - 1) / step).coerceAtLeast(1)
        starts = IntArray(total) { it * step }
        ends = IntArray(total) { minOf(starts[it] + samplesPerChunk, pcm.size) }
        return runInternal(null, pcm, sampleRate, sequentialOnly, events, process)
    }

    /**
     * Előre meghatározott tartományokkal (pl. VAD-alapú intelligens darabolás után).
     * A [ranges] elemei: (startSample, endSample) – end exkluzív.
     */
    fun runRanges(
        pcm: ShortArray,
        ranges: List<Vad.Seg>,
        sequentialOnly: Boolean,
        sampleRate: Int = 16000,
        events: Events,
        process: (slice: ShortArray, offsetMs: Long, idx: Int) -> List<SrtExporter.Cue>
    ): Result {
        total = ranges.size.coerceAtLeast(0)
        if (total == 0) {
            return Result(emptyList(), 0, 0, false)
        }
        starts = IntArray(total) { ranges[it].start }
        ends = IntArray(total) { ranges[it].end.coerceAtMost(pcm.size) }
        samplesPerChunk = 0
        overlapSamples = 0
        return runInternal(null, pcm, sampleRate, sequentialOnly, events, process)
    }

    /**
     * Hosszú felvétel: a PCM a lemezen marad, csak az aktuális darab (~néhány MB) kerül memóriába.
     * Így elkerülhető az OutOfMemoryError hosszú filmeknél.
     */
    fun runFromFile(
        pcmFile: File,
        totalSamples: Int,
        chunkMs: Int,
        overlapMs: Int,
        sequentialOnly: Boolean,
        sampleRate: Int = 16000,
        ranges: List<Vad.Seg>? = null,
        events: Events,
        process: (slice: ShortArray, offsetMs: Long, idx: Int) -> List<SrtExporter.Cue>
    ): Result {
        if (ranges != null && ranges.isNotEmpty()) {
            total = ranges.size
            starts = IntArray(total) { ranges[it].start.coerceIn(0, totalSamples) }
            ends = IntArray(total) { ranges[it].end.coerceIn(starts[it], totalSamples) }
            samplesPerChunk = 0
            overlapSamples = 0
        } else {
            samplesPerChunk = (sampleRate * (chunkMs / 1000.0)).toInt().coerceAtLeast(sampleRate * 60)
            overlapSamples = (sampleRate * (overlapMs / 1000.0)).toInt().coerceIn(sampleRate, samplesPerChunk / 3)
            val step = (samplesPerChunk - overlapSamples).coerceAtLeast(sampleRate * 30)
            total = if (totalSamples <= 0) 1 else ((totalSamples - overlapSamples + step - 1) / step).coerceAtLeast(1)
            starts = IntArray(total) { it * step }
            ends = IntArray(total) { minOf(starts[it] + samplesPerChunk, totalSamples) }
        }
        return runInternal(pcmFile, null, sampleRate, sequentialOnly, events, process)
    }

    private fun runInternal(
        pcmFile: File?,
        pcm: ShortArray?,
        sampleRate: Int,
        sequentialOnly: Boolean,
        events: Events,
        process: (slice: ShortArray, offsetMs: Long, idx: Int) -> List<SrtExporter.Cue>
    ): Result {
        results = arrayOfNulls(total)
        next = 0; active = 0; failure = null

        val n = if (sequentialOnly) 1 else Perf.maxWorkers()
        val threads = (0 until n).map { w ->
            Thread({
                Perf.applyPriority()
                worker(pcmFile, pcm, sampleRate, sequentialOnly, events, process)
            }, "srt-chunk-$w").also { it.start() }
        }
        for (t in threads) {
            try { t.join() } catch (_: InterruptedException) { cancel.set(true) }
        }
        failure?.let { throw it }
        val done = results.count { it != null }
        return Result(merged(), done, total, cancel.get() && done < total)
    }

    companion object {
        /** Egy sample-tartomány beolvasása a nyers 16-bit LE PCM fájlból. */
        fun readPcmRange(file: File, startSample: Int, endSample: Int): ShortArray {
            val n = (endSample - startSample).coerceAtLeast(0)
            if (n == 0) return ShortArray(0)
            val out = ShortArray(n)
            RandomAccessFile(file, "r").use { raf ->
                val bytePos = startSample.toLong() * 2L
                val byteLen = n.toLong() * 2L
                if (bytePos + byteLen > raf.length()) {
                    val avail = ((raf.length() - bytePos) / 2L).toInt().coerceAtLeast(0)
                    if (avail == 0) return ShortArray(0)
                    return readPcmRange(file, startSample, startSample + avail)
                }
                raf.seek(bytePos)
                val buf = ByteArray(n * 2)
                var off = 0
                while (off < buf.size) {
                    val r = raf.read(buf, off, buf.size - off)
                    if (r <= 0) break
                    off += r
                }
                val bb = ByteBuffer.wrap(buf, 0, off).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                val got = bb.remaining().coerceAtMost(n)
                bb.get(out, 0, got)
            }
            return out
        }
    }

    private fun worker(
        pcmFile: File?,
        pcm: ShortArray?,
        sr: Int,
        sequentialOnly: Boolean,
        events: Events,
        process: (ShortArray, Long, Int) -> List<SrtExporter.Cue>
    ) {
        while (!cancel.get() && failure == null) {
            Perf.applyPriority()
            val idx = claim(sequentialOnly, events)
            if (idx < 0) return
            try {
                val start = starts[idx]
                val end = ends[idx]
                val slice = when {
                    pcm != null -> pcm.copyOfRange(start, end)
                    pcmFile != null -> readPcmRange(pcmFile, start, end)
                    else -> ShortArray(0)
                }
                val offsetMs = start.toLong() * 1000L / sr
                events.onChunkStart(idx, total)
                val cues = process(slice, offsetMs, idx)
                if (cancel.get()) return
                synchronized(lock) { results[idx] = cues }
                events.onChunkDone(idx, total, cues, (end - start).toLong() * 1000L / sr)
            } catch (t: Throwable) {
                if (!cancel.get()) {
                    synchronized(lock) { if (failure == null) failure = t }
                    cancel.set(true)
                }
                return
            } finally {
                synchronized(lock) { active-- }
            }
            // pihenő a chunkok között (a csúszka élőben állítható: ezredmásodpercenként újraolvassuk)
            var waited = 0L
            while (!cancel.get() && waited < Perf.pauseMs()) {
                try { Thread.sleep(250) } catch (_: InterruptedException) { return }
                waited += 250
            }
        }
    }

    /** Következő chunk sorszáma, vagy -1, ha nincs több / leállítás. Itt várunk a kapura és a szálkorlátra. */
    private fun claim(sequentialOnly: Boolean, events: Events): Int {
        var waiting = false
        while (!cancel.get()) {
            val gate = gate()
            if (!gate.block) {
                synchronized(lock) {
                    if (next >= total) return -1
                    val limit = if (sequentialOnly) 1 else Perf.voskWorkers()
                    if (active < limit) {
                        active++
                        if (waiting) events.onWait("", false)
                        return next++
                    }
                }
            } else {
                waiting = true
                events.onWait(gate.reason, true)
            }
            try { Thread.sleep(if (gate.block) 5_000 else 300) } catch (_: InterruptedException) { return -1 }
        }
        return -1
    }

    /** Az eddig kész chunkok egyesítése (részleges mentéshez is jó). */
    fun merged(): List<SrtExporter.Cue> {
        val res = synchronized(lock) { results.copyOf() }
        val out = ArrayList<SrtExporter.Cue>()
        val sr = 16000L
        for (i in res.indices) {
            val cues = res[i] ?: continue
            if (overlapSamples <= 0) {
                // intelligens (VAD) darabolás: nincs átfedés, minden cue bent marad
                out.addAll(cues)
            } else {
                val half = overlapSamples * 1000L / sr / 2
                val lo = if (i == 0) Long.MIN_VALUE else starts[i] * 1000L / sr + half
                val hi = if (i == res.size - 1 || res[i + 1] == null) Long.MAX_VALUE
                else starts[i + 1] * 1000L / sr + half
                for (c in cues) if (c.startMs >= lo && c.startMs < hi) out.add(c)
            }
        }
        // időrendbe
        out.sortBy { it.startMs }
        return out
    }

    private data class Gate(val block: Boolean, val reason: String)

    private fun gate(): Gate {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = try { pm.currentThermalStatus } catch (_: Exception) { PowerManager.THERMAL_STATUS_NONE }
        if (thermal >= Perf.thermalBlockAt(Perf.level, strictThermal)) {
            return Gate(true, "🌡 Melegedés – a következő chunk várakozik, amíg a telefon lehűl")
        }
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
            val st = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            val charging = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            if (pct in 1..10 && !charging) Gate(true, "🔋 Alacsony akkumulátor ($pct%) – tedd töltőre, vagy várj")
            else Gate(false, "")
        } catch (_: Exception) {
            Gate(false, "")
        }
    }
}
