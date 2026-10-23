package hu.angolmagyar.fordito

import android.content.Context
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Fájl-átirat: PCM → időzített feliratblokkok (+ javítás, fordítás, beszélő).
 *
 * Az időzítés a hang valós pozíciójából jön:
 *  - Vosk: szó-időbélyegek (setWords), szünetnél / hosszú sornál új blokk
 *  - rendszer-felismerő (magyar): csendeknél vágott szeletek, a szelet eleje/vége az idő
 */
class FileRecognizer(
    private val ctx: Context,
    private val prefs: Prefs,
    private val cancel: AtomicBoolean
) {
    private data class Raw(val startMs: Long, val endMs: Long, val text: String)

    /** Állapotüzenetek a hívónak (pl. fordítómodell letöltése közben). */
    var onStatus: ((String) -> Unit)? = null

    private var translator: Translator? = null
    private var translatorKey = ""
    private var translatorOk = false
    private var blocking: BlockingAsr? = null

    // közös modellek: a chunkok nem töltik újra (a Vosk Model több szálból is használható)
    private var voskModel: Model? = null
    private var voskModelKey = ""
    private var whisper: WhisperNative? = null
    private var whisperKey = ""
    private val whisperLock = Any()

    /** Azonnal, a befejező szálon fut: így a fordítás nem függ a főszáltól (háttérben sem akad meg). */
    private val direct = java.util.concurrent.Executor { it.run() }

    private val enRules by lazy { Fixes.parse(prefs.enFixes) }
    private val huRules by lazy { Fixes.parse(prefs.huFixes) }

    fun close() {
        try { translator?.close() } catch (_: Exception) {}
        translator = null
        blocking?.close()
        blocking = null
        try { synchronized(this) { voskModel?.close(); voskModel = null } } catch (_: Exception) {}
        try { synchronized(whisperLock) { whisper?.close(); whisper = null } } catch (_: Exception) {}
    }

    /** Azonnali leállítás (Whisper közben is). */
    fun abort() {
        try { whisper?.abort(true) } catch (_: Exception) {}
    }

    /** Modellek betöltése a feldolgozás elején, hogy a chunkok már ne várjanak rá. */
    fun prepare(asr: Engines.Asr, needTranslate: Boolean, dir: String) {
        when (asr.kind) {
            Engines.K_VOSK -> voskModel(asr.vosk!!)
            Engines.K_WHISPER -> whisperCtx(asr.whisper!!)
            else -> {}
        }
        if (needTranslate) prepareTranslator(dir)
    }

    /**
     * @param asr a felismerő (Whisper / Vosk / rendszer)
     * @param srcLang a forrásnyelv kódja ("hu", "en", …)
     * @param onProgress (0–100, állapotüzenet): a felismerés, majd a fordítás, majd az utómunka
     */
    fun recognize(
        pcm: ShortArray,
        srcLang: String,
        asr: Engines.Asr,
        needTranslate: Boolean,
        dir: String,
        offsetMs: Long,
        speakerAt: (Long) -> String = { "" },
        onProgress: ((Int, String) -> Unit)? = null
    ): List<SrtExporter.Cue> {
        if (pcm.isEmpty()) return emptyList()
        val recEnd = if (needTranslate) 70 else 90
        val rp: (Int, String) -> Unit = { p, m -> onProgress?.invoke(p * recEnd / 100, m) }
        val raws = when (asr.kind) {
            Engines.K_WHISPER -> rawWhisper(pcm, asr.whisper!!, srcLang, offsetMs, rp)
            Engines.K_VOSK -> rawVosk(pcm, asr.vosk!!, offsetMs, rp)
            else -> rawSystem(pcm, srcLang, offsetMs, rp)
        }
        if (cancel.get()) return emptyList()
        onProgress?.invoke(recEnd, "Szöveg javítása (${raws.size} blokk)…")
        val polished = ArrayList<Raw>(raws.size)
        for (r in raws) {
            var src = Fixes.apply(r.text, if (srcLang == "en") enRules else huRules)
            if (TextPolish.isNoise(src)) continue
            src = if (srcLang == "en") TextPolish.polishEnglish(src) else TextPolish.polishHungarian(src)
            polished.add(Raw(r.startMs, r.endMs, src))
        }
        val targets: List<String> = if (needTranslate && polished.isNotEmpty()) {
            prepareTranslator(dir)
            val prog: (Int, Int) -> Unit = { done, n ->
                onProgress?.invoke(70 + 25 * done / n.coerceAtLeast(1), "Fordítás: $done/$n mondat")
            }
            if (SubPrefs.contextTranslate(ctx)) translateGrouped(polished, dir, prog)
            else translateAll(polished.map { it.text }, dir, prog)
        } else polished.map { "" }
        onProgress?.invoke(97, "Blokkok összeállítása…")
        val out = ArrayList<SrtExporter.Cue>(polished.size)
        for ((i, r) in polished.withIndex()) {
            out.add(SrtExporter.Cue(r.startMs, r.endMs, r.text, targets.getOrElse(i) { "" }, speakerAt(r.startMs)))
        }
        onProgress?.invoke(100, "Kész (${out.size} blokk)")
        return out
    }

    // ---------------------------------------------------------------- Whisper

    private fun whisperCtx(spec: ModelManager.Spec): WhisperNative {
        synchronized(whisperLock) {
            val key = spec.id
            whisper?.let { if (whisperKey == key) return it }
            try { whisper?.close() } catch (_: Exception) {}
            whisper = null
            val path = ModelManager.dir(ctx, spec).absolutePath
            val w = WhisperNative.open(path) ?: throw IllegalStateException(
                if (!WhisperNative.available()) "A Whisper motor nem tölthető be: ${WhisperNative.error()}"
                else "A Whisper modell nem tölthető be (${spec.label}). Töltsd le újra a Modellek képernyőn."
            )
            whisper = w
            whisperKey = key
            return w
        }
    }

    private fun rawWhisper(
        pcm: ShortArray, spec: ModelManager.Spec, lang: String, offsetMs: Long,
        onProgress: ((Int, String) -> Unit)?
    ): List<Raw> {
        val w = whisperCtx(spec)
        val audio = FloatArray(pcm.size) { pcm[it] / 32768f }
        val out = ArrayList<Raw>()
        val prompt = if (lang == "hu") "Ez egy magyar nyelvű felvétel, helyes központozással." else null
        val cb = onProgress
        cb?.invoke(0, "Whisper: felismerés indul…")
        if (cancel.get()) return emptyList()
        var lastPct = -1
        Perf.applyPriority() // a ggml munkaszálak a hívó szál prioritását öröklik
        val rc = synchronized(whisperLock) {
            w.abort(false)
            w.transcribe(audio, lang, Perf.whisperThreads(), AsrPrefs.beam(ctx), prompt,
                object : WhisperNative.Listener {
                    override fun onProgress(percent: Int) {
                        if (percent != lastPct) {
                            lastPct = percent
                            cb?.invoke(percent, "Whisper felismerés: $percent%")
                        }
                    }
                    override fun onSegment(t0: Long, t1: Long, text: ByteArray) {}
                })
        }
        if (cancel.get()) return emptyList()
        if (rc != 0) throw IllegalStateException("A Whisper hibával leállt (kód $rc).")
        var prev1 = ""
        var prev2 = ""
        for (sg in w.segments()) {
            if (sg.noSpeech > 0.8f) continue
            val t = sg.text.trim()
            if (t.isBlank()) continue
            // zajra gyakori ismétlődő "hallucináció" kiszűrése
            if (t == prev1 && t == prev2) continue
            prev2 = prev1; prev1 = t
            out.addAll(splitLong(Raw(offsetMs + sg.startMs, offsetMs + sg.endMs, t)))
        }
        return finishTiming(out)
    }

    // ------------------------------------------------------------------- Vosk

    private fun rawVosk(
        pcm: ShortArray, spec: ModelManager.Spec, offsetMs: Long,
        onProgress: ((Int, String) -> Unit)?
    ): List<Raw> {
        val model = voskModel(spec)
        val rec = Recognizer(model, 16000f)
        rec.setWords(true)
        val out = ArrayList<Raw>()
        try {
            val step = 8000 // 0,5 mp
            var o = 0
            var lastEnd = 0L
            var lastPct = -1
            while (o < pcm.size && !cancel.get()) {
                val e = (o + step).coerceAtMost(pcm.size)
                val slice = pcm.copyOfRange(o, e)
                if (rec.acceptWaveForm(slice, slice.size)) {
                    lastEnd = addVoskResult(rec.result, offsetMs, lastEnd, out)
                }
                o = e
                if (onProgress != null) {
                    val pct = (o * 100L / pcm.size).toInt()
                    if (pct != lastPct) { lastPct = pct; Perf.applyPriority(); onProgress(pct, "Vosk felismerés: $pct%") }
                }
            }
            addVoskResult(rec.finalResult, offsetMs, lastEnd, out)
        } finally {
            rec.close()
        }
        return finishTiming(out)
    }

    @Synchronized
    private fun voskModel(spec: ModelManager.Spec): Model {
        voskModel?.let { if (voskModelKey == spec.id) return it }
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        try { voskModel?.close() } catch (_: Exception) {}
        val m = Model(ModelManager.dir(ctx, spec).absolutePath)
        voskModel = m
        voskModelKey = spec.id
        return m
    }

    private fun addVoskResult(json: String, offsetMs: Long, lastEndMs: Long, out: MutableList<Raw>): Long {
        val obj = try { JSONObject(json) } catch (_: Exception) { return lastEndMs }
        val text = obj.optString("text", "").trim()
        if (text.isBlank()) return lastEndMs
        val arr = obj.optJSONArray("result")
        if (arr == null || arr.length() == 0) {
            // szó-idők nélkül: az előző blokk vége után 2 mp
            val s = offsetMs + lastEndMs
            out.add(Raw(s, s + 2000, text))
            return lastEndMs + 2000
        }
        var cur = StringBuilder()
        var cs = -1L
        var ce = 0L
        var prevEnd = -1L
        var endOfLast = lastEndMs
        for (i in 0 until arr.length()) {
            val w = arr.getJSONObject(i)
            val word = w.optString("word", "")
            if (word.isBlank()) continue
            val ws = (w.optDouble("start", 0.0) * 1000).toLong()
            val we = (w.optDouble("end", 0.0) * 1000).toLong()
            val gap = if (prevEnd >= 0) ws - prevEnd else 0L
            val tooLong = cur.length + word.length + 1 > MAX_CHARS || (cs >= 0 && we - cs > MAX_CUE_MS)
            if (cs >= 0 && (gap >= PAUSE_MS || tooLong)) {
                out.add(Raw(offsetMs + cs, offsetMs + ce, cur.toString().trim()))
                cur = StringBuilder()
                cs = -1
            }
            if (cs < 0) cs = ws
            cur.append(word).append(' ')
            ce = we
            prevEnd = we
            endOfLast = we
        }
        if (cs >= 0 && cur.isNotBlank()) out.add(Raw(offsetMs + cs, offsetMs + ce, cur.toString().trim()))
        return endOfLast
    }

    // ------------------------------------------------------ rendszer-felismerő

    /** A rendszer-felismerő nyelve a forrásnyelvből (korábban fixen hu-HU volt, ami angol videónál téves volt). */
    private fun systemLocale(lang: String): String = when (lang) {
        "hu" -> "hu-HU"
        "de" -> "de-DE"
        "fr" -> "fr-FR"
        "es" -> "es-ES"
        "it" -> "it-IT"
        "uk" -> "uk-UA"
        else -> "en-US"
    }

    private fun rawSystem(
        pcm: ShortArray, lang: String, offsetMs: Long, onProgress: ((Int, String) -> Unit)?
    ): List<Raw> {
        var asr = blocking
        if (asr == null) {
            asr = BlockingAsr(ctx, systemLocale(lang), prefs.allowOnlineAsr)
            val err = asr.open()
            if (err != null) throw IllegalStateException(err)
            blocking = asr
        }
        val segs = Vad.segments(pcm)
        val out = ArrayList<Raw>()
        for ((i, s) in segs.withIndex()) {
            if (cancel.get()) break
            val slice = pcm.copyOfRange(s.start, s.end.coerceAtMost(pcm.size))
            val text = asr.transcribe(slice)
            if (text.isNotBlank()) {
                val st = offsetMs + s.start * 1000L / 16000
                val en = offsetMs + s.end * 1000L / 16000
                out.addAll(splitLong(Raw(st, en, text)))
            }
            if (onProgress != null) {
                val pct = ((i + 1) * 100 / segs.size)
                onProgress(pct, "Android felismerő: ${i + 1}/${segs.size} szakasz")
            }
        }
        return finishTiming(out)
    }

    /** Hosszú blokk szétvágása szóhatárokon, az idő arányosan osztva. */
    private fun splitLong(r: Raw): List<Raw> {
        val words = r.text.split(Regex("\\s+")).filter { it.isNotBlank() }
        val total = r.text.length
        if (total <= MAX_CHARS && r.endMs - r.startMs <= MAX_CUE_MS) return listOf(r)
        val parts = ArrayList<List<String>>()
        var cur = ArrayList<String>()
        var len = 0
        for (w in words) {
            if (len + w.length + 1 > MAX_CHARS && cur.isNotEmpty()) {
                parts.add(cur); cur = ArrayList(); len = 0
            }
            cur.add(w); len += w.length + 1
        }
        if (cur.isNotEmpty()) parts.add(cur)
        val dur = (r.endMs - r.startMs).coerceAtLeast(500)
        val res = ArrayList<Raw>()
        var used = 0
        for (p in parts) {
            val txt = p.joinToString(" ")
            val s = r.startMs + dur * used / total
            used += txt.length + 1
            val e = r.startMs + dur * used.coerceAtMost(total) / total
            res.add(Raw(s, e, txt))
        }
        return res
    }

    /** Olvashatóság: legalább 1 mp, legfeljebb a következő blokk kezdetéig. */
    private fun finishTiming(list: List<Raw>): List<Raw> {
        val out = ArrayList<Raw>(list.size)
        for ((i, r) in list.withIndex()) {
            var end = maxOf(r.endMs, r.startMs + 1000)
            if (i + 1 < list.size) end = minOf(end, maxOf(list[i + 1].startMs - 40, r.startMs + 400))
            out.add(Raw(r.startMs, end, r.text))
        }
        return out
    }

    // ----------------------------------------------------------------- fordítás

    private fun mlKitLang(code: String): String = when (code) {
        "hu" -> TranslateLanguage.HUNGARIAN
        "de" -> TranslateLanguage.GERMAN
        "fr" -> TranslateLanguage.FRENCH
        "es" -> TranslateLanguage.SPANISH
        "it" -> TranslateLanguage.ITALIAN
        "uk" -> "uk" // ML Kit BCP-47
        else -> TranslateLanguage.ENGLISH
    }

    @Synchronized
    private fun prepareTranslator(dir: String) {
        val key = dir
        if (translator != null && translatorKey == key) return
        try { translator?.close() } catch (_: Exception) {}
        translator = null
        translatorOk = false
        // dir: "hu_en" | "en_hu" | "de_hu" | "fr_hu" | …
        val (src, tgt) = when {
            dir == Prefs.DIR_HU_EN || dir.startsWith("hu_") ->
                TranslateLanguage.HUNGARIAN to TranslateLanguage.ENGLISH
            dir.contains("_") -> {
                val from = dir.substringBefore("_")
                val to = dir.substringAfter("_")
                mlKitLang(from) to mlKitLang(to)
            }
            else -> TranslateLanguage.ENGLISH to TranslateLanguage.HUNGARIAN
        }
        val t = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build()
        )
        val latch = CountDownLatch(1)
        var ok = false
        // a visszahívások a háttérszálon futnak (alapból a főszálon: háttérben megakadhatnának)
        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener(direct) { ok = true; latch.countDown() }
            .addOnFailureListener(direct) { latch.countDown() }
        // a letöltés hosszabb is lehet (lassú net): másodpercenként jelzünk, legfeljebb 15 percig várunk
        val t0 = System.currentTimeMillis()
        while (!latch.await(2, TimeUnit.SECONDS)) {
            if (cancel.get()) { t.close(); throw InterruptedException("Megszakítva") }
            val el = (System.currentTimeMillis() - t0) / 1000
            onStatus?.invoke("Fordítómodell letöltése… ${el / 60}:${"%02d".format(el % 60)}")
            if (el > 900) break
        }
        translatorKey = key
        if (ok) {
            translator = t
            translatorOk = true
        } else {
            t.close()
            throw IllegalStateException(
                "A fordítómodell nincs letöltve, és nem sikerült letölteni (internet?). Nyisd meg a Modellek képernyőt, és töltsd le."
            )
        }
    }

    /**
     * Kontextusos fordítás: a mondat közepén vágott blokkokat mondatokká fogjuk, így fordít jól a gépi fordító;
     * a lefordított mondatot arányosan visszaosztjuk az eredeti időzített blokkokra.
     */
    private fun translateGrouped(items: List<Raw>, dir: String, onDone: (Int, Int) -> Unit): List<String> {
        val groups = SentenceGroups.group(items.map { SentenceGroups.Item(it.startMs, it.endMs, it.text) })
        val texts = groups.map { g -> g.joinToString(" ") { idx -> items[idx].text.trim() } }
        val tr = translateAll(texts, dir, onDone)
        val out = ArrayList<String>(items.size)
        for ((gi, g) in groups.withIndex()) {
            val lens = g.map { idx -> items[idx].text.length }
            out.addAll(SentenceGroups.distribute(tr.getOrElse(gi) { "" }, lens))
        }
        return out
    }

    /** Az összes blokk fordítása egyszerre beadva; az előrehaladást a kész darabok számából mutatjuk. */
    private fun translateAll(srcs: List<String>, dir: String, onDone: (Int, Int) -> Unit): List<String> {
        val t = translator ?: return srcs.map { "" }
        val toHu = dir == Prefs.DIR_EN_HU || dir.endsWith("_hu")
        val tasks = srcs.map { t.translate(it) }
        val done = java.util.concurrent.atomic.AtomicInteger(0)
        for (k in tasks) k.addOnCompleteListener(direct) { done.incrementAndGet() }
        val n = srcs.size
        var last = -1
        var lastChange = System.currentTimeMillis()
        onDone(0, n)
        while (done.get() < n && !cancel.get()) {
            try { Thread.sleep(150) } catch (_: InterruptedException) { break }
            val d = done.get()
            if (d != last) { last = d; lastChange = System.currentTimeMillis(); onDone(d, n) }
            else if (System.currentTimeMillis() - lastChange > 120_000) break // beragadt: ami kész, azt használjuk
        }
        return tasks.map { k ->
            if (k.isComplete && k.isSuccessful) {
                val res = k.result ?: ""
                if (toHu) TextPolish.polishHungarian(Fixes.apply(res, huRules))
                else TextPolish.polishEnglish(Fixes.apply(res, enRules))
            } else ""
        }
    }

    companion object {
        private const val MAX_CHARS = 84
        private const val MAX_CUE_MS = 6500L
        private const val PAUSE_MS = 700L
    }
}
