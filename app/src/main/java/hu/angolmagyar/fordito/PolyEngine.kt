package hu.angolmagyar.fordito

import android.content.Context
import android.os.Handler
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.common.model.DownloadConditions
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Többnyelvű élő mód: a Whisper automatikusan felismeri a nyelvet (99 nyelv), a beszélőket a hangmagasság
 * alapján különíti el, a szöveget pedig a kiválasztott célnyelvre fordítja (ML Kit offline, vagy online modell,
 * ha be van állítva). Az eredményt az [AppState.liveLog] tartalmazza (kétpaneles nézet).
 */
class PolyEngine(
    private val ctx: Context,
    private val prefs: Prefs,
    private val main: Handler,
    private val listener: Listener
) {
    interface Listener {
        /** Főszálon: egy kész (lefordított) sor az átfedő feliratnak és a mentésnek. */
        fun onFinalLine(original: String, translated: String)
        fun onFatal(message: String)
        fun onNote(message: String?)
    }

    private val exec = Executors.newSingleThreadExecutor()
    private val tracker = SpeakerTracker()
    private val translators = HashMap<String, Translator>()
    private val ready = HashSet<String>()
    private val failed = HashMap<String, Long>()
    private var asr: LiveWhisperAsr? = null
    private var lastLang = ""

    @Volatile private var stopped = false

    fun start() {
        AppState.liveClear()
        tracker.reset()
        // pontosabb felismeréshez nagyobb modellt ajánl, ha csak kicsi van telepítve
        try {
            val spec = Engines.whisperSpec(ctx)
            if (spec != null && (spec.id == ModelManager.WH_BASE.id || spec.id == ModelManager.WH_SMALL.id)) {
                main.post { listener.onNote("Pontosabb, több nyelvű felismeréshez töltsd le a Whisper large-v3-turbo modellt (Modellek képernyő).") }
            }
        } catch (_: Exception) {}
        val a = LiveWhisperAsr(ctx, "auto", object : LiveWhisperAsr.Callback {
            override fun onPartial(text: String) {}
            override fun onFinal(text: String) {}
            override fun onFinalAudio(text: String, audio: FloatArray) {
                try { exec.execute { handle(text, audio) } } catch (_: Exception) {}
            }
            override fun onFatal(message: String) = listener.onFatal(message)
        })
        asr = a
        a.start()
    }

    /** 16 kHz mono PCM. */
    fun feed(data: ShortArray, len: Int) { asr?.feed(data, len) }

    fun stop() {
        stopped = true
        asr?.stop()
        asr = null
        try { exec.shutdownNow() } catch (_: Exception) {}
        for (t in translators.values) try { t.close() } catch (_: Exception) {}
        translators.clear()
    }

    // ------------------------------------------------------------------ egy szakasz feldolgozása

    private fun handle(raw: String, audio: FloatArray) {
        if (stopped) return
        val text = raw.trim()
        if (text.isEmpty() || TextPolish.isNoise(text)) return

        val speaker = try { tracker.assign(audio) } catch (_: Throwable) { 0 }

        var lang = try { LanguageIdHelper.identifyBlocking(text, 1500) } catch (_: Throwable) { null }
            ?.let { LangCatalog.norm(it) } ?: ""
        if (lang.isEmpty()) lang = lastLang // rövid mondatnál az előző nyelvet tartjuk
        if (lang.isNotEmpty()) lastLang = lang

        val tgt = LangCatalog.norm(prefs.polyTarget)
        val id = AppState.nextLiveId()
        val same = lang.isNotEmpty() && lang == tgt
        AppState.liveAppend(LiveLine(id, speaker, lang, text, if (same) text else ""))

        val translated = when {
            same -> text
            lang.isEmpty() -> text
            else -> translate(text, lang, tgt) ?: ""
        }
        if (translated.isNotBlank() && !same) AppState.liveSetTranslation(id, translated)
        else if (translated.isBlank()) AppState.liveSetTranslation(id, "⚠ nem sikerült lefordítani")

        val shown = translated.ifBlank { text }
        if (!stopped) main.post { if (!stopped) listener.onFinalLine(text, shown) }
    }

    // ------------------------------------------------------------------ fordítás

    private fun translate(text: String, src: String, tgt: String): String? {
        val online = translateOnline(text, src, tgt)
        if (online != null) return online
        return translateMlKit(text, src, tgt)
    }

    private fun translateOnline(text: String, src: String, tgt: String): String? {
        return try {
            if (OnlinePrefs.mode(ctx) == OnlinePrefs.OFF) return null
            val cfg = OnlinePrefs.config(ctx, LangCatalog.nameHu(src), LangCatalog.nameHu(tgt)) ?: return null
            val out = mutableListOf("")
            val bad = OnlineTranslator(cfg).translate(listOf(text), out, listOf(0), { _, _ -> }, { stopped })
            if (bad.isEmpty() && out[0].isNotBlank()) out[0].trim() else null
        } catch (_: Throwable) { null }
    }

    private fun mlLang(code: String): String? = TranslateLanguage.fromLanguageTag(code)

    private fun translateMlKit(text: String, src: String, tgt: String): String? {
        val s = mlLang(src)
        val t = mlLang(tgt)
        if (s == null || t == null) {
            main.post { listener.onNote("A(z) ${LangCatalog.nameHu(if (s == null) src else tgt)} nyelvet az offline fordító nem támogatja – állíts be online fordítást.") }
            return null
        }
        val key = "$s>$t"
        if (System.currentTimeMillis() - (failed[key] ?: 0L) < 30_000L) return null
        return try {
            var tr = translators[key]
            if (tr == null) {
                tr = Translation.getClient(
                    TranslatorOptions.Builder().setSourceLanguage(s).setTargetLanguage(t).build()
                )
                translators[key] = tr
            }
            if (key !in ready) {
                main.post { listener.onNote("Fordítómodell letöltése: ${LangCatalog.nameHu(src)} → ${LangCatalog.nameHu(tgt)}…") }
                await(tr!!.downloadModelIfNeeded(DownloadConditions.Builder().build()), 180)
                ready.add(key)
                main.post { listener.onNote(null) }
            }
            await(tr!!.translate(text), 20)?.trim()
        } catch (e: Throwable) {
            failed[key] = System.currentTimeMillis()
            main.post {
                listener.onNote("A ${LangCatalog.nameHu(src)} → ${LangCatalog.nameHu(tgt)} fordítómodell nem érhető el (hálózat?).")
            }
            null
        }
    }

    /** Egy ML Kit feladat bevárása (exec szálon hívd); hibánál/időtúllépésnél kivételt dob. */
    private fun <T> await(task: com.google.android.gms.tasks.Task<T>, timeoutSec: Long): T? {
        val latch = CountDownLatch(1)
        val ok = AtomicReference<Boolean?>(null)
        val value = AtomicReference<T?>(null)
        task.addOnSuccessListener { v -> value.set(v); ok.set(true); latch.countDown() }
        task.addOnFailureListener { ok.set(false); latch.countDown() }
        if (!latch.await(timeoutSec, TimeUnit.SECONDS)) throw java.util.concurrent.TimeoutException("időtúllépés")
        if (ok.get() != true) throw IllegalStateException("a feladat sikertelen")
        return value.get()
    }
}
