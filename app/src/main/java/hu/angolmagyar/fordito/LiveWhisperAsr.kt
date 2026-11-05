package hu.angolmagyar.fordito
import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt
class LiveWhisperAsr(
    private val ctx: Context,
    private val lang: String,
    private val cb: Callback
) {
    interface Callback {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onFatal(message: String)
        fun onFinalAudio(text: String, audio: FloatArray) { onFinal(text) }
    }
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val busy = AtomicBoolean(false)
    private var whisper: WhisperNative? = null
    private var worker: Thread? = null
    private val queue = LinkedBlockingQueue<ShortArray>(64)
    private val acc = ArrayList<Short>(16_000 * 12)
    private var speechMs = 0
    private var silenceMs = 0
    private var inSpeech = false
    private var lastPartial = ""
    private var prevTail = ""
    private val frameMs = 30
    private val sampleRate = 16_000
    private val frameSamples = sampleRate * frameMs / 1000
    private val minSpeechMs = 400
    private val endSilenceMs = 700
    private val maxSegMs = 12_000
    private val energyThreshMax = 380f
    private val energyThreshMin = 70f
    private var noiseFloor = 40f
    private var dbgFrames = 0
    fun start() {
        if (running.getAndSet(true)) return
        val spec = Engines.whisperSpec(ctx)
        if (spec == null) {
            running.set(false)
            main.post { cb.onFatal("Nincs telepített Whisper modell. Töltsd le a Modellek képernyőn.") }
            return
        }
        val path = ModelManager.dir(ctx, spec).absolutePath
        val w = WhisperNative.open(path)
        if (w == null) {
            running.set(false)
            val why = WhisperNative.error().ifBlank { "ismeretlen hiba" }
            main.post { cb.onFatal("Whisper modell betöltése sikertelen: $why") }
            return
        }
        whisper = w
        worker = Thread({ processLoop() }, "live-whisper").also {
            it.isDaemon = true
            it.start()
        }
    }
    fun feed(data: ShortArray, len: Int) {
        if (!running.get() || len <= 0) return
        val copy = data.copyOfRange(0, len)
        if (!queue.offer(copy)) {
            queue.poll()
            queue.offer(copy)
        }
    }
    fun stop() {
        running.set(false)
        queue.clear()
        worker?.interrupt()
        worker = null
        try {
            whisper?.close()
        } catch (_: Exception) {
        }
        whisper = null
        synchronized(acc) {
            acc.clear()
            speechMs = 0
            silenceMs = 0
            inSpeech = false
        }
    }
    private fun processLoop() {
        val frame = ShortArray(frameSamples)
        var framePos = 0
        while (running.get()) {
            val chunk = try {
                queue.poll(80, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                break
            } ?: continue
            var i = 0
            while (i < chunk.size && running.get()) {
                val take = minOf(frameSamples - framePos, chunk.size - i)
                System.arraycopy(chunk, i, frame, framePos, take)
                framePos += take
                i += take
                if (framePos >= frameSamples) {
                    processFrame(frame)
                    framePos = 0
                }
            }
        }
        flushSegment(force = true)
    }
    private fun processFrame(frame: ShortArray) {
        var sum = 0.0
        for (s in frame) {
            val v = s.toDouble()
            sum += v * v
        }
        val rms = sqrt(sum / frame.size).toFloat()
        val thr = (noiseFloor * 2.2f + 25f).coerceIn(energyThreshMin, energyThreshMax)
        val speech = rms >= thr
        if (!speech && !inSpeech) noiseFloor = noiseFloor * 0.98f + rms * 0.02f
        if (++dbgFrames % 100 == 0) {
            android.util.Log.d("LiveWhisper", "rms=${rms.toInt()} thr=${thr.toInt()} floor=${noiseFloor.toInt()} inSpeech=$inSpeech speechMs=$speechMs")
        }
        synchronized(acc) {
            if (speech) {
                if (!inSpeech) {
                    inSpeech = true
                    silenceMs = 0
                }
                speechMs += frameMs
                silenceMs = 0
                for (s in frame) acc.add(s)
            } else if (inSpeech) {
                silenceMs += frameMs
                for (s in frame) acc.add(s)
                if (silenceMs >= endSilenceMs && speechMs >= minSpeechMs) {
                    flushSegment(force = false)
                }
            }
            if (inSpeech && speechMs >= maxSegMs) {
                flushSegment(force = true)
            }
        }
    }
    private fun flushSegment(force: Boolean) {
        val samples: ShortArray
        synchronized(acc) {
            if (acc.isEmpty() || speechMs < minSpeechMs && !force) {
                if (force) {
                    acc.clear()
                    speechMs = 0
                    silenceMs = 0
                    inSpeech = false
                }
                return
            }
            samples = ShortArray(acc.size)
            for (i in acc.indices) samples[i] = acc[i]
            acc.clear()
            speechMs = 0
            silenceMs = 0
            inSpeech = false
        }
        synchronized(pending) {
            pending.addLast(samples)
            while (pending.size > 3) pending.removeFirst()
        }
        drain()
    }
    private val pending = java.util.ArrayDeque<ShortArray>()
    private fun drain() {
        if (!busy.compareAndSet(false, true)) return
        val w = whisper
        if (w == null) {
            busy.set(false)
            return
        }
        Thread({
            try {
                while (running.get()) {
                    val samples = synchronized(pending) { pending.pollFirst() } ?: break
                    transcribeOne(w, samples)
                }
            } finally {
                busy.set(false)
            }
            if (running.get() && synchronized(pending) { pending.isNotEmpty() }) drain()
        }, "whisper-seg").also { it.isDaemon = true; it.start() }
    }
    private fun transcribeOne(w: WhisperNative, samples: ShortArray) {
        try {
            val audio = FloatArray(samples.size)
            for (i in samples.indices) audio[i] = samples[i] / 32768f
            val auto = lang.equals("auto", ignoreCase = true)
            val prompt = if (auto) "" else WhisperPrompt.build(
                lang = lang,
                prevTail = prevTail,
                domain = "general"
            )
            val beam = AsrPrefs.beam(ctx).coerceIn(1, 3)
            val code = w.transcribe(
                audio, lang, Perf.whisperThreads(), beam, prompt, null, null
            )
            if (code != 0) {
                android.util.Log.w("LiveWhisper", "transcribe hibakód=$code ${WhisperNative.error()}")
                return
            }
            val segs = w.segments()
            val text = segs.joinToString(" ") { it.text }.trim()
            if (text.isNotBlank()) {
                if (!auto) prevTail = (prevTail + " " + text).takeLast(120).trim()
                lastPartial = ""
                main.post {
                    if (running.get()) cb.onFinalAudio(text, audio)
                }
            }
        } catch (t: Throwable) {
            android.util.Log.w("LiveWhisper", "transcribe kivétel", t)
        }
    }
}
