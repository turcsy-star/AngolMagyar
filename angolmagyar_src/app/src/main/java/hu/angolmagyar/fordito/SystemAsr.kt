package hu.angolmagyar.fordito

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Az Android beépített (Google) beszédfelismerője – ezt használjuk magyarra, mert a Vosk-hoz
 * nem létezik magyar modell. A PCM hangot csövön (pipe) adjuk át a felismerőnek
 * (RecognizerIntent.EXTRA_AUDIO_SOURCE, Android 13+), így a mikrofon mellett a belső hang
 * és a fájlból kinyert hang is felismerhető.
 */
object SystemAsrSupport {

    /** Eszközön futó (offline) felismerő elérhető-e. */
    fun onDeviceAvailable(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 && try {
            SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)
        } catch (_: Throwable) {
            false
        }

    /** Bármilyen (akár felhős) felismerő elérhető-e. */
    fun anyAvailable(ctx: Context): Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(ctx)
    } catch (_: Throwable) {
        false
    }

    fun audioSourceSupported(): Boolean = Build.VERSION.SDK_INT >= 33

    /** Főszálon hívd. */
    fun create(ctx: Context, allowOnline: Boolean): SpeechRecognizer? {
        return try {
            if (onDeviceAvailable(ctx)) SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
            else if (allowOnline && anyAvailable(ctx)) SpeechRecognizer.createSpeechRecognizer(ctx)
            else null
        } catch (_: Throwable) {
            null
        }
    }

    fun buildIntent(lang: String, source: ParcelFileDescriptor?, partial: Boolean): Intent {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partial)
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        if (Build.VERSION.SDK_INT >= 33 && source != null) {
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, source)
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            i.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, 16000)
        }
        return i
    }

    fun firstResult(b: Bundle?): String {
        val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        return l?.firstOrNull()?.trim() ?: ""
    }

    fun shortsToBytes(data: ShortArray, len: Int): ByteArray {
        val b = ByteArray(len * 2)
        for (i in 0 until len) {
            val s = data[i].toInt()
            b[i * 2] = (s and 0xff).toByte()
            b[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
        }
        return b
    }

    fun errorText(code: Int): String = when (code) {
        SpeechRecognizer.ERROR_AUDIO -> "hangátviteli hiba"
        SpeechRecognizer.ERROR_CLIENT -> "kliens hiba"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "hiányzó mikrofon-engedély"
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
            "hálózati hiba (a magyar offline csomag nincs letöltve?)"
        SpeechRecognizer.ERROR_NO_MATCH -> "nincs találat"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "a felismerő foglalt"
        SpeechRecognizer.ERROR_SERVER -> "szerverhiba"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "nincs beszéd"
        12 -> "a magyar nyelv nem támogatott ezen a felismerőn"
        13 -> "a magyar offline nyelvi csomag nincs telepítve"
        else -> "hiba ($code)"
    }
}

/**
 * Élő felismerés: folyamatosan újraindított munkamenetek, a hangot a [feed] adja be.
 * A visszahívások a főszálon érkeznek.
 */
class LiveSystemAsr(
    private val ctx: Context,
    private val lang: String,
    private val allowOnline: Boolean,
    private val cb: Callback
) {
    interface Callback {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onFatal(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var running = false
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var sessionOut: OutputStream? = null
    private val queue = LinkedBlockingQueue<ByteArray>(400)
    private var writer: Thread? = null
    private var quickErrors = 0
    private var lastErrorAt = 0L

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onPartialResults(partialResults: Bundle?) {
            val t = SystemAsrSupport.firstResult(partialResults)
            if (t.isNotBlank() && running) cb.onPartial(t)
        }

        override fun onResults(results: Bundle?) {
            val t = SystemAsrSupport.firstResult(results)
            if (t.isNotBlank() && running) cb.onFinal(t)
            quickErrors = 0
            endSession(0)
        }

        override fun onError(error: Int) {
            if (!running) return
            when (error) {
                12, 13, SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    running = false
                    cb.onFatal("Magyar felismerés: " + SystemAsrSupport.errorText(error))
                }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> endSession(100)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> endSession(600)
                SpeechRecognizer.ERROR_CLIENT -> {
                    // Gyakori ideiglenes hiba (nincs offline csomag / Google szolgáltatás).
                    // Többszöri ismétlődés után adunk értelmes üzenetet, és Whisper-t javasolunk.
                    val now = System.currentTimeMillis()
                    quickErrors = if (now - lastErrorAt < 5000) quickErrors + 1 else 1
                    lastErrorAt = now
                    if (quickErrors >= 12) {
                        running = false
                        cb.onFatal(
                            "Magyar felismerés: kliens hiba. Töltsd le a Whisper modellt a Modellek " +
                                "képernyőn (ajánlott), vagy telepítsd a Google offline magyar csomagját."
                        )
                    } else {
                        endSession(500)
                    }
                }
                else -> {
                    val now = System.currentTimeMillis()
                    quickErrors = if (now - lastErrorAt < 4000) quickErrors + 1 else 1
                    lastErrorAt = now
                    if (quickErrors >= 8) {
                        running = false
                        cb.onFatal("Magyar felismerés: " + SystemAsrSupport.errorText(error))
                    } else {
                        endSession(400)
                    }
                }
            }
        }
    }

    fun start() {
        if (running) return
        if (!SystemAsrSupport.audioSourceSupported()) {
            cb.onFatal("A magyar felismerőhöz Android 13 vagy újabb kell.")
            return
        }
        running = true
        main.post {
            if (!running) return@post
            val r = SystemAsrSupport.create(ctx, allowOnline)
            if (r == null) {
                running = false
                cb.onFatal(
                    "Nincs eszközön futó magyar felismerő. Telepítsd a Google magyar offline " +
                        "nyelvi csomagját (Modellek képernyő), vagy engedélyezd az online felismerést."
                )
                return@post
            }
            r.setRecognitionListener(listener)
            recognizer = r
            startSession()
        }
        writer = Thread({
            while (running) {
                val b = queue.poll(200, TimeUnit.MILLISECONDS) ?: continue
                var written = false
                while (running && !written) {
                    val o = sessionOut
                    if (o == null) {
                        try { Thread.sleep(30) } catch (_: InterruptedException) { return@Thread }
                        continue
                    }
                    try {
                        o.write(b)
                        o.flush()
                        written = true
                    } catch (_: IOException) {
                        try { Thread.sleep(30) } catch (_: InterruptedException) { return@Thread }
                    }
                }
            }
        }, "sys-asr-writer").also { it.isDaemon = true; it.start() }
    }

    /** Bármelyik szálról hívható: 16 kHz mono PCM. */
    fun feed(data: ShortArray, len: Int) {
        if (!running || len <= 0) return
        val b = SystemAsrSupport.shortsToBytes(data, len)
        if (!queue.offer(b)) {
            queue.poll()
            queue.offer(b)
        }
    }

    private fun startSession() {
        if (!running) return
        val r = recognizer ?: return
        try {
            val pipe = ParcelFileDescriptor.createPipe()
            val readEnd = pipe[0]
            val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
            r.startListening(SystemAsrSupport.buildIntent(lang, readEnd, true))
            try { readEnd.close() } catch (_: Exception) {}
            sessionOut = out
        } catch (t: Throwable) {
            running = false
            cb.onFatal("Magyar felismerés indítási hiba: ${t.message}")
        }
    }

    private fun endSession(delayMs: Long) {
        main.post {
            try { sessionOut?.close() } catch (_: Exception) {}
            sessionOut = null
            try { recognizer?.cancel() } catch (_: Exception) {}
            if (running) main.postDelayed({ startSession() }, delayMs)
        }
    }

    fun stop() {
        running = false
        queue.clear()
        main.post {
            try { sessionOut?.close() } catch (_: Exception) {}
            sessionOut = null
            try { recognizer?.cancel() } catch (_: Exception) {}
            try { recognizer?.destroy() } catch (_: Exception) {}
            recognizer = null
        }
        writer?.interrupt()
        writer = null
    }
}

/**
 * Fájl-átirathoz: rövid hangszeletek blokkoló felismerése.
 * Háttérszálról használd: [open], majd többször [transcribe], végül [close].
 */
class BlockingAsr(
    private val ctx: Context,
    private val lang: String,
    private val allowOnline: Boolean
) {
    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    @Volatile private var latch: CountDownLatch? = null
    @Volatile private var finalText: String? = null
    @Volatile private var lastPartial = ""
    @Volatile private var lastError = 0

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
        override fun onPartialResults(partialResults: Bundle?) {
            val t = SystemAsrSupport.firstResult(partialResults)
            if (t.isNotBlank()) lastPartial = t
        }

        override fun onResults(results: Bundle?) {
            finalText = SystemAsrSupport.firstResult(results)
            latch?.countDown()
        }

        override fun onError(error: Int) {
            lastError = error
            latch?.countDown()
        }
    }

    /** @return hibaüzenet, vagy null, ha sikerült. */
    fun open(): String? {
        if (!SystemAsrSupport.audioSourceSupported()) {
            return "A magyar felismerőhöz Android 13 vagy újabb kell."
        }
        val l = CountDownLatch(1)
        var err: String? = null
        main.post {
            val r = SystemAsrSupport.create(ctx, allowOnline)
            if (r == null) {
                err = "Nincs eszközön futó magyar felismerő. Telepítsd a Google magyar offline " +
                    "nyelvi csomagját (Modellek képernyő), vagy engedélyezd az online felismerést."
            } else {
                r.setRecognitionListener(listener)
                recognizer = r
            }
            l.countDown()
        }
        l.await(10, TimeUnit.SECONDS)
        return err
    }

    /** Egy hangszelet szövege (üres, ha nincs beszéd). Fatális hibánál kivételt dob. */
    fun transcribe(pcm: ShortArray): String {
        val r = recognizer ?: return ""
        val l = CountDownLatch(1)
        latch = l
        finalText = null
        lastPartial = ""
        lastError = 0
        val pipe = ParcelFileDescriptor.createPipe()
        val readEnd = pipe[0]
        val out = ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])
        val started = CountDownLatch(1)
        main.post {
            try {
                r.startListening(SystemAsrSupport.buildIntent(lang, readEnd, true))
            } catch (_: Throwable) {
                lastError = SpeechRecognizer.ERROR_CLIENT
                l.countDown()
            }
            try { readEnd.close() } catch (_: Exception) {}
            started.countDown()
        }
        started.await(5, TimeUnit.SECONDS)
        // a szelet + 700 ms csend a végén, hogy a felismerő lezárja a mondatot
        val w = Thread {
            try {
                val bytes = SystemAsrSupport.shortsToBytes(pcm, pcm.size)
                var o = 0
                while (o < bytes.size) {
                    val n = minOf(6400, bytes.size - o)
                    out.write(bytes, o, n)
                    o += n
                }
                out.write(ByteArray(16000 * 2 * 7 / 10))
                out.flush()
            } catch (_: IOException) {
            } finally {
                try { out.close() } catch (_: Exception) {}
            }
        }
        w.isDaemon = true
        w.start()
        val limitSec = (pcm.size / 16000L * 3 + 20).coerceAtMost(120)
        val done = l.await(limitSec, TimeUnit.SECONDS)
        if (!done) {
            main.post { try { r.cancel() } catch (_: Exception) {} }
            try { out.close() } catch (_: Exception) {}
        }
        w.join(2000)
        val err = lastError
        if (err == 12 || err == 13 || err == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
            throw IllegalStateException("Magyar felismerés: " + SystemAsrSupport.errorText(err))
        }
        latch = null
        return (finalText ?: lastPartial).trim()
    }

    fun close() {
        main.post {
            try { recognizer?.destroy() } catch (_: Exception) {}
            recognizer = null
        }
    }
}
