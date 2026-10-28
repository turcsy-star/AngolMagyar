package hu.angolmagyar.fordito

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Beszélő-szegmentálás (diarization) – két réteg:
 *
 * 1) **EnergyDiarizer** (most működik, offline, modell nélkül):
 *    hangerő + spektrális durva jegyek + csend → S1/S2… váltás.
 *
 * 2) **OnnxDiarizer** (váz): később ECAPA/wespeaker ONNX embedding + klaszterezés.
 *
 * A Magic5 Pro (8 Gen 2, 12 GB) elbírja az ONNX utat; a modellfájl külön telepítendő.
 */
interface SpeakerDiarizer {
    data class Segment(
        val startMs: Long,
        val endMs: Long,
        val speakerId: Int, // 0-based → S1, S2…
        val pcmStart: Int,
        val pcmEnd: Int
    )

    fun diarize(pcm16Mono: ShortArray, sampleRate: Int = 16000): List<Segment>

    fun label(speakerId: Int): String = "S${speakerId + 1}"
}

/**
 * Gyakorlati, modell nélküli diarization tárgyaláshoz.
 * - Csend vágja a szegmenseket
 * - Új szegmensnél: ha a hangjegy (RMS + ZCR) jelentősen más, új beszélő, különben ugyanaz
 * - Max [maxSpeakers] klaszter
 */
class EnergyDiarizer(
    private val silenceRms: Double = 40.0,
    private val minSilenceMs: Int = 900,
    private val minSpeechMs: Int = 400,
    private val maxSpeakers: Int = 6,
    private val changeThreshold: Double = 0.35
) : SpeakerDiarizer {

    private data class Feat(val rms: Double, val zcr: Double)

    override fun diarize(pcm16Mono: ShortArray, sampleRate: Int): List<SpeakerDiarizer.Segment> {
        if (pcm16Mono.isEmpty()) return emptyList()
        val frameMs = 30
        val frameLen = sampleRate * frameMs / 1000
        val hop = frameLen
        val nFrames = (pcm16Mono.size / hop).coerceAtLeast(1)

        val voiced = BooleanArray(nFrames)
        val feats = Array(nFrames) { Feat(0.0, 0.0) }

        for (f in 0 until nFrames) {
            val start = f * hop
            val end = (start + frameLen).coerceAtMost(pcm16Mono.size)
            var sum = 0.0
            var zc = 0
            var prev = 0
            for (i in start until end) {
                val s = pcm16Mono[i].toInt()
                sum += s.toDouble() * s
                if (i > start && ((prev >= 0) != (s >= 0))) zc++
                prev = s
            }
            val n = (end - start).coerceAtLeast(1)
            val rms = sqrt(sum / n)
            val zcr = zc.toDouble() / n
            feats[f] = Feat(rms, zcr)
            voiced[f] = rms >= silenceRms
        }

        // szegmensek: összefüggő voiced futamok, minSilenceMs szünettel elválasztva
        val minSilFrames = (minSilenceMs / frameMs).coerceAtLeast(1)
        val minSpeechFrames = (minSpeechMs / frameMs).coerceAtLeast(1)
        val raw = mutableListOf<IntRange>()
        var i = 0
        while (i < nFrames) {
            while (i < nFrames && !voiced[i]) i++
            if (i >= nFrames) break
            val s = i
            while (i < nFrames && voiced[i]) i++
            var e = i
            // rövid csendet „áthidaljuk”, ha utána megint beszéd
            while (i < nFrames) {
                var sil = 0
                val silStart = i
                while (i < nFrames && !voiced[i]) {
                    sil++; i++
                }
                if (sil >= minSilFrames || i >= nFrames) {
                    i = silStart
                    break
                }
                // short gap – continue speech
                while (i < nFrames && voiced[i]) i++
                e = i
            }
            if (e - s >= minSpeechFrames) raw.add(s until e)
            while (i < nFrames && !voiced[i]) i++
        }

        // beszélő hozzárendelés
        val centers = ArrayList<Feat>()
        val segs = ArrayList<SpeakerDiarizer.Segment>()

        for (range in raw) {
            val feat = average(feats, range)
            var best = -1
            var bestDist = Double.MAX_VALUE
            for (c in centers.indices) {
                val d = distance(feat, centers[c])
                if (d < bestDist) {
                    bestDist = d
                    best = c
                }
            }
            val spk = if (best >= 0 && bestDist < changeThreshold) {
                // frissítsük a centrumot
                centers[best] = mix(centers[best], feat, 0.2)
                best
            } else if (centers.size < maxSpeakers) {
                centers.add(feat)
                centers.lastIndex
            } else {
                best.coerceAtLeast(0)
            }

            val startMs = range.first.toLong() * frameMs
            val endMs = range.last.toLong() * frameMs + frameMs
            val pcmStart = range.first * hop
            val pcmEnd = ((range.last + 1) * hop).coerceAtMost(pcm16Mono.size)
            segs.add(
                SpeakerDiarizer.Segment(
                    startMs, endMs, spk, pcmStart, pcmEnd
                )
            )
        }
        return segs
    }

    private fun average(feats: Array<Feat>, range: IntRange): Feat {
        var r = 0.0
        var z = 0.0
        var n = 0
        for (i in range) {
            r += feats[i].rms
            z += feats[i].zcr
            n++
        }
        n = n.coerceAtLeast(1)
        return Feat(r / n, z / n)
    }

    private fun distance(a: Feat, b: Feat): Double {
        val rmsA = ln(1 + a.rms)
        val rmsB = ln(1 + b.rms)
        val dr = abs(rmsA - rmsB) / 5.0
        val dz = abs(a.zcr - b.zcr) * 8.0
        return dr + dz
    }

    private fun mix(a: Feat, b: Feat, w: Double) =
        Feat(a.rms * (1 - w) + b.rms * w, a.zcr * (1 - w) + b.zcr * w)
}

/**
 * ONNX speaker-embedding diarization.
 * Modell: files/models/speaker_encoder.onnx
 * Bemenet: float32 [1,N] vagy [1,1,N] 16 kHz mono (−1…1)
 * Kimenet: embedding vektor. Hiba esetén EnergyDiarizer.
 */
class OnnxDiarizer(
    private val modelFile: java.io.File?,
    private val fallback: SpeakerDiarizer = EnergyDiarizer(),
    private val maxSpeakers: Int = 6,
    private val clusterThreshold: Double = 0.55
) : SpeakerDiarizer {

    val isOnnxReady: Boolean
        get() = modelFile != null && modelFile.isFile && modelFile.length() > 1000

    @Volatile private var session: ai.onnxruntime.OrtSession? = null
    @Volatile private var env: ai.onnxruntime.OrtEnvironment? = null
    private var inputName: String? = null
    private var loadFailed = false

    private fun ensureSession(): ai.onnxruntime.OrtSession? {
        if (loadFailed || !isOnnxReady) return null
        session?.let { return it }
        synchronized(this) {
            session?.let { return it }
            return try {
                val e = ai.onnxruntime.OrtEnvironment.getEnvironment()
                env = e
                val opts = ai.onnxruntime.OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setOptimizationLevel(ai.onnxruntime.OrtSession.SessionOptions.OptLevel.ALL_OPT)
                }
                val s = e.createSession(modelFile!!.absolutePath, opts)
                inputName = s.inputNames.firstOrNull()
                session = s
                s
            } catch (_: Exception) {
                loadFailed = true
                null
            }
        }
    }

    override fun diarize(pcm16Mono: ShortArray, sampleRate: Int): List<SpeakerDiarizer.Segment> {
        if (!isOnnxReady) return fallback.diarize(pcm16Mono, sampleRate)
        val sess = ensureSession() ?: return fallback.diarize(pcm16Mono, sampleRate)
        val inName = inputName ?: return fallback.diarize(pcm16Mono, sampleRate)
        return try {
            val energySegs = fallback.diarize(pcm16Mono, sampleRate)
            if (energySegs.isEmpty()) return emptyList()
            val embeddings = energySegs.map { seg ->
                embedWindow(sess, inName, pcm16Mono, seg.pcmStart, seg.pcmEnd, sampleRate)
                    ?: floatArrayOf()
            }
            val centers = ArrayList<FloatArray>()
            val labels = IntArray(energySegs.size) { -1 }
            for (i in energySegs.indices) {
                val emb = embeddings[i]
                if (emb.isEmpty()) {
                    labels[i] = 0
                    continue
                }
                var best = -1
                var bestDist = Double.MAX_VALUE
                for (c in centers.indices) {
                    val d = cosineDistance(emb, centers[c])
                    if (d < bestDist) {
                        bestDist = d
                        best = c
                    }
                }
                labels[i] = if (best >= 0 && bestDist < clusterThreshold) {
                    centers[best] = mixVec(centers[best], emb, 0.15f)
                    best
                } else if (centers.size < maxSpeakers) {
                    centers.add(emb.copyOf())
                    centers.lastIndex
                } else {
                    best.coerceAtLeast(0)
                }
            }
            val out = ArrayList<SpeakerDiarizer.Segment>()
            var i = 0
            while (i < energySegs.size) {
                var j = i + 1
                while (j < energySegs.size && labels[j] == labels[i]) j++
                val first = energySegs[i]
                val last = energySegs[j - 1]
                out.add(
                    SpeakerDiarizer.Segment(
                        first.startMs, last.endMs, labels[i].coerceAtLeast(0),
                        first.pcmStart, last.pcmEnd
                    )
                )
                i = j
            }
            out
        } catch (_: Exception) {
            fallback.diarize(pcm16Mono, sampleRate)
        }
    }

    private fun embedWindow(
        sess: ai.onnxruntime.OrtSession,
        inName: String,
        pcm: ShortArray,
        pcmStart: Int,
        pcmEnd: Int,
        sampleRate: Int
    ): FloatArray? {
        val maxSamples = sampleRate * 2
        val len = (pcmEnd - pcmStart).coerceAtLeast(1)
        val take = len.coerceAtMost(maxSamples)
        val mid = pcmStart + len / 2
        val start = (mid - take / 2).coerceIn(0, (pcm.size - take).coerceAtLeast(0))
        val end = (start + take).coerceAtMost(pcm.size)
        if (end <= start) return null
        val floats = FloatArray(end - start) { i -> pcm[start + i] / 32768f }
        val env = this.env ?: return null
        for (shape in listOf(
            longArrayOf(1, floats.size.toLong()),
            longArrayOf(1, 1, floats.size.toLong())
        )) {
            try {
                val tensor = ai.onnxruntime.OnnxTensor.createTensor(
                    env, java.nio.FloatBuffer.wrap(floats), shape
                )
                tensor.use { t ->
                    sess.run(mapOf(inName to t)).use { result ->
                        // Result.get(int): OnnxValue (régebbi/újabb verziókban Optional is lehet)
                        val raw: Any? = result.get(0)
                        val ov = if (raw is java.util.Optional<*>) raw.orElse(null) else raw
                        return flattenOutput((ov as? ai.onnxruntime.OnnxValue)?.value)
                    }
                }
            } catch (e: Exception) { DiagLog.swallow("SpeakerDiarizer", e) }
        }
        return null
    }

    private fun flattenOutput(value: Any?): FloatArray? = when (value) {
        is FloatArray -> value
        is Array<*> -> when (val first = value.getOrNull(0)) {
            is FloatArray -> first
            is Array<*> -> first.getOrNull(0) as? FloatArray
            else -> null
        }
        else -> null
    }

    private fun cosineDistance(a: FloatArray, b: FloatArray): Double {
        val n = minOf(a.size, b.size)
        if (n == 0) return 1.0
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in 0 until n) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        val denom = sqrt(na) * sqrt(nb)
        if (denom < 1e-9) return 1.0
        return 1.0 - (dot / denom).coerceIn(-1.0, 1.0)
    }

    private fun mixVec(a: FloatArray, b: FloatArray, w: Float): FloatArray {
        val n = minOf(a.size, b.size)
        return FloatArray(n) { i -> a[i] * (1 - w) + b[i] * w }
    }

    fun close() {
        try { session?.close() } catch (e: Exception) { DiagLog.swallow("SpeakerDiarizer", e) }
        session = null
        env = null
    }
}

object DiarizerFactory {
    fun modelFile(ctx: android.content.Context): java.io.File =
        java.io.File(ctx.filesDir, "models/speaker_encoder.onnx")

    fun create(ctx: android.content.Context, preferOnnx: Boolean = true): SpeakerDiarizer {
        val onnx = modelFile(ctx)
        return if (preferOnnx) OnnxDiarizer(onnx) else EnergyDiarizer()
    }

    fun onnxStatus(ctx: android.content.Context): String {
        val f = modelFile(ctx)
        return if (f.isFile && f.length() > 1000)
            "ONNX speaker embedding kész (${f.length() / 1024} KB)"
        else
            "ONNX modell nincs – energia S1/S2 (speaker_encoder.onnx → models/)"
    }

    fun isOnnxInstalled(ctx: android.content.Context): Boolean {
        val f = modelFile(ctx)
        return f.isFile && f.length() > 1000
    }
}
