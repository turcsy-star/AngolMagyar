package hu.angolmagyar.fordito

import android.content.Context
import android.os.BatteryManager
import android.os.PowerManager
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hosszú (7–8 órás) felvételek chunkolt átirata.
 * - Rövid szeletek (pl. 10–15 perc)
 * - Thermal / akkumulátor kapu: melegedésnél vagy alacsony akkunál szünet
 * - Nem folyamatos full-speed futás → kíméli a telefont és az akkumulátort
 */
class ChunkedTranscriber(
    private val ctx: Context,
    private val prefs: Prefs
) {
    data class Progress(
        val chunkIndex: Int,
        val totalChunks: Int,
        val pctOverall: Int,
        val message: String,
        val pausedForCooling: Boolean = false
    )

    data class Result(
        val cues: List<SrtExporter.Cue>,
        val chunksDone: Int,
        val stoppedEarly: Boolean
    )

    private val cancel = AtomicBoolean(false)

    fun cancel() = cancel.set(true)

    /**
     * @param pcm16kMono teljes PCM
     * @param chunkMs chunk hossza (alap 12 perc – biztonságos)
     * @param pauseBetweenMs szünet chunkok között (hűtés)
     * @param processChunk egy chunk PCM → cue lista (ASR + diarization)
     */
    /** Az új chunk átfedő fejének levágása (a felezőpontig): ezt az előző chunk már lefedte. */
    private fun trimOverlapHead(
        cues: List<SrtExporter.Cue>, offsetMs: Long, overlapMs: Long
    ): List<SrtExporter.Cue> {
        val cut = offsetMs + overlapMs / 2
        return cues.filter { it.startMs >= cut }
    }

    fun run(
        pcm16kMono: ShortArray,
        sampleRate: Int = 16000,
        chunkMs: Int = 12 * 60 * 1000,
        /** Átfedés ms – szó/mondat ne vágódjon el, kontextus a chunk szélén. */
        overlapMs: Int = 8_000,
        pauseBetweenMs: Long = 8_000L,
        maxBatteryTempLike: Boolean = true,
        processChunk: (chunk: ShortArray, offsetMs: Long, chunkIndex: Int, isFirst: Boolean, isLast: Boolean) -> List<SrtExporter.Cue>,
        onProgress: (Progress) -> Unit
    ): Result {
        cancel.set(false)
        val samplesPerChunk = (sampleRate * (chunkMs / 1000.0)).toInt().coerceAtLeast(sampleRate * 60)
        val overlapSamples = (sampleRate * (overlapMs / 1000.0)).toInt().coerceIn(sampleRate, samplesPerChunk / 3)
        // lépés: chunk - overlap (így a szeletek átfednek)
        val step = (samplesPerChunk - overlapSamples).coerceAtLeast(sampleRate * 30)
        val totalChunks = if (pcm16kMono.isEmpty()) 1
        else ((pcm16kMono.size - overlapSamples + step - 1) / step).coerceAtLeast(1)
        val all = ArrayList<SrtExporter.Cue>()
        var stoppedEarly = false

        for (i in 0 until totalChunks) {
            if (cancel.get()) {
                stoppedEarly = true
                break
            }

            // --- hűtés / akku kapu ---
            var gate = safetyGate()
            while (gate.block && !cancel.get()) {
                onProgress(
                    Progress(
                        i, totalChunks,
                        (i * 100 / totalChunks),
                        gate.reason,
                        pausedForCooling = true
                    )
                )
                Thread.sleep(15_000)
                gate = safetyGate()
            }
            if (cancel.get()) {
                stoppedEarly = true
                break
            }

            val start = i * step
            val end = minOf(start + samplesPerChunk, pcm16kMono.size)
            val slice = pcm16kMono.copyOfRange(start, end)
            val offsetMs = (start.toLong() * 1000L) / sampleRate
            val isFirst = i == 0
            val isLast = i == totalChunks - 1 || end >= pcm16kMono.size

            onProgress(
                Progress(
                    i + 1, totalChunks,
                    (i * 100 / totalChunks),
                    "Chunk ${i + 1}/$totalChunks (átfedés ${overlapMs / 1000}s)…",
                    pausedForCooling = false
                )
            )

            val rawCues = processChunk(slice, offsetMs, i, isFirst, isLast)
            // átfedő zóna dedup: a következő chunk elejét nem adjuk kétszer
            // Átfedő zóna: az előző chunk farkát és az új chunk fejét a felezőpontnál vágjuk,
            // így egy mondat sem szerepel kétszer, és a határon sem vész el.
            val overlapActualMs = overlapSamples * 1000L / sampleRate
            val cues = if (i == 0) rawCues else trimOverlapHead(rawCues, offsetMs, overlapActualMs)
            if (i > 0) {
                val cut = offsetMs + overlapActualMs / 2
                all.removeAll { it.startMs >= cut }
            }
            all.addAll(cues)

            onProgress(
                Progress(
                    i + 1, totalChunks,
                    ((i + 1) * 100 / totalChunks),
                    "Chunk ${i + 1}/$totalChunks kész (${cues.size} blokk)",
                    pausedForCooling = false
                )
            )

            // szünet a chunkok között – CPU/GPU hűlés
            if (i < totalChunks - 1 && !cancel.get()) {
                val extra = if (gate.warm) pauseBetweenMs * 2 else pauseBetweenMs
                onProgress(
                    Progress(
                        i + 1, totalChunks,
                        ((i + 1) * 100 / totalChunks),
                        "Hűtési szünet ${extra / 1000}s…",
                        pausedForCooling = true
                    )
                )
                Thread.sleep(extra)
            }
        }

        return Result(all, all.size, stoppedEarly)
    }

    data class Gate(val block: Boolean, val warm: Boolean, val reason: String)

    private fun safetyGate(): Gate {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val thermal = try {
            pm.currentThermalStatus
        } catch (_: Exception) {
            PowerManager.THERMAL_STATUS_NONE
        }
        val warm = thermal >= PowerManager.THERMAL_STATUS_MODERATE
        val severe = thermal >= PowerManager.THERMAL_STATUS_SEVERE

        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        // töltés közben is figyelünk melegedésre
        val lowBat = pct in 1..15

        return when {
            severe -> Gate(
                true, true,
                "🌡 Túl meleg – várakozás hűlésre (ne erőltesd, kíméli az akkumulátort)"
            )
            warm && prefs.meetingSafeMode -> Gate(
                true, true,
                "🌡 Melegedés – szünet (biztonságos tárgyalás mód)"
            )
            lowBat && !isCharging() -> Gate(
                true, warm,
                "🔋 Alacsony akkumulátor ($pct%) – csatlakoztasd a töltőt, vagy várj"
            )
            else -> Gate(false, warm, "")
        }
    }

    private fun isCharging(): Boolean {
        return try {
            val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val st = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
            st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
        } catch (_: Exception) {
            false
        }
    }
}
