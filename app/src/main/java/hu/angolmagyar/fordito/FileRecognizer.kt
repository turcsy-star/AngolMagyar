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
    private data class Raw(val startMs: Long, val endMs: Long, val text: String, val lp: Float = 1f, val wordConfidence: List<Float> = emptyList(), val noSpeechProb: Float = 0f)

    /** Állapotüzenetek a hívónak (pl. fordítómodell letöltése közben). */
    var onStatus: ((String) -> Unit)? = null

    /** Tájékoztató megjegyzés a fordításról (pl. online hiba, tartalék használata) – nem módosítja a haladást. */
    var onNote: ((String) -> Unit)? = null

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

    private val enRules by lazy { Fixes.parse(prefs.enFixes + "\n" + (prefs.raw().getString("autoRulesEn", "") ?: "")) }
    private val huRules by lazy { Fixes.parse(Projects.huRules(ctx)) }
    /** A projekt szereplői / nevei: a felismerés kezdőszövege és a névjavítás alapja. */
    private val castNames by lazy { Projects.names(ctx) }

    fun close() {
        try { translator?.close() } catch (e: Exception) { DiagLog.swallow("FileRecognizer", e) }
        translator = null
        blocking?.close()
        blocking = null
        try { synchronized(this) { voskModel?.close(); voskModel = null } } catch (e: Exception) { DiagLog.swallow("FileRecognizer", e) }
        try { synchronized(whisperLock) { whisper?.close(); whisper = null } } catch (e: Exception) { DiagLog.swallow("FileRecognizer", e) }
    }

    /** Azonnali leállítás (Whisper közben is). */
    fun abort() {
        try { whisper?.abort(true) } catch (e: Exception) { DiagLog.swallow("FileRecognizer", e) }
    }

    /** Modellek betöltése a feldolgozás elején, hogy a chunkok már ne várjanak rá. */
    fun prepare(asr: Engines.Asr, needTranslate: Boolean, dir: String) {
        when (asr.kind) {
            Engines.K_VOSK -> voskModel(asr.vosk!!)
            Engines.K_WHISPER -> whisperCtx(asr.whisper!!)
            else -> {}
        }
        if (needTranslate) {
            // csak online módban nem kell az offline modell; "auto" módban nem végzetes, ha nincs meg
            val mode = if (OnlinePrefs.usable(ctx)) OnlinePrefs.mode(ctx) else OnlinePrefs.OFF
            when (mode) {
                OnlinePrefs.ONLINE -> {}
                OnlinePrefs.AUTO -> try { prepareTranslator(dir) } catch (e: IllegalStateException) { onNote?.invoke("Offline tartalék nem elérhető: ${e.message}") }
                else -> prepareTranslator(dir)
            }
        }
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
        timeMap: TimeMap? = null,
        onProgress: ((Int, String) -> Unit)? = null
    ): List<SrtExporter.Cue> {
        if (pcm.isEmpty()) return emptyList()
        val recEnd = if (needTranslate) 70 else 90
        val rp: (Int, String) -> Unit = { p, m -> onProgress?.invoke(p * recEnd / 100, m) }
        val raws = when (asr.kind) {
            Engines.K_WHISPER -> rawWhisper(pcm, asr.whisper!!, srcLang, offsetMs, rp, timeMap)
            Engines.K_VOSK -> rawVosk(pcm, asr.vosk!!, offsetMs, rp)
            else -> rawSystem(pcm, srcLang, offsetMs, rp)
        }
        if (cancel.get()) return emptyList()
        onProgress?.invoke(recEnd, "Szöveg javítása (${raws.size} blokk)…")
        val polished = ArrayList<Raw>(raws.size)
        for (r in raws) {
            var src = Fixes.apply(r.text, if (srcLang == "en") enRules else huRules)
            if (castNames.isNotEmpty()) src = NameFix.apply(src, castNames)
            if (TextPolish.isNoise(src)) continue
            src = if (srcLang == "en") TextPolish.polishEnglish(src) else TextPolish.polishHungarian(src)
            val sourceWordCount = src.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            val rawWordCount = r.text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
            val alignedScores = if (sourceWordCount == rawWordCount) r.wordConfidence else emptyList()
            polished.add(Raw(r.startMs, r.endMs, src, r.lp, alignedScores, r.noSpeechProb))
        }
        val targets: List<String> = if (needTranslate && polished.isNotEmpty()) {
            translateCore(
                polished.map { it.text }, polished.map { it.startMs to it.endMs }, null, null, dir, null
            ) { done, n, label -> onProgress?.invoke(70 + 25 * done / n.coerceAtLeast(1), "$label: $done/$n blokk") }
        } else polished.map { "" }
        onProgress?.invoke(97, "Blokkok összeállítása…")
        val out = ArrayList<SrtExporter.Cue>(polished.size)
        for ((i, r) in polished.withIndex()) {
            out.add(SrtExporter.Cue(r.startMs, r.endMs, r.text, targets.getOrElse(i) { "" }, speakerAt(r.startMs), r.lp, r.wordConfidence, r.noSpeechProb))
        }
        onProgress?.invoke(100, "Kész (${out.size} blokk)")
        return out
    }

    // ---------------------------------------------------------------- Whisper

    private fun whisperCtx(spec: ModelManager.Spec): WhisperNative {
        synchronized(whisperLock) {
            val key = spec.id
            whisper?.let { if (whisperKey == key) return it }
            try { whisper?.close() } catch (e: Exception) { DiagLog.swallow("FileRecognizer", e) }
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
        onProgress: ((Int, String) -> Unit)?, timeMap: TimeMap? = null, beamOverride: Int = 0
    ): List<Raw> {
        val w = whisperCtx(spec)
        val audio = FloatArray(pcm.size) { pcm[it] / 32768f }
        if (Prefs(ctx).raw().getBoolean("audioPrep", true)) try { AudioPrep.prepare(audio) } catch (_: Throwable) {}
        val out = ArrayList<Raw>()
        // v2.21.1 – központi kontextuális prompt (nevek + stílus + előző mondatok)
        val prompt = WhisperPrompt.build(
            lang = lang,
            castNames = castNames,
            prevTail = prevTail,
            domain = "subtitle",
            terms = TextPolish.parseVocab(Prefs(ctx).customVocab).map { it.second }
        )
        val cb = onProgress
        cb?.invoke(0, "Whisper: felismerés indul…")
        if (cancel.get()) return emptyList()
        var lastPct = -1
        Perf.applyPriority() // a ggml munkaszálak a hívó szál prioritását öröklik
        // ha a darab már csak beszédet tartalmaz (összefűzve), a Whisper belső VAD-ja fölösleges
        val vadPath = if (timeMap != null) null
            else VadModel.file(ctx).takeIf { it.exists() && it.length() > 300_000L }?.absolutePath
        val rc = synchronized(whisperLock) {
            w.abort(false)
            w.transcribe(audio, lang, Perf.whisperThreads(), if (beamOverride > 0) beamOverride else AsrPrefs.beam(ctx), prompt,
                object : WhisperNative.Listener {
                    override fun onProgress(percent: Int) {
                        if (percent != lastPct) {
                            lastPct = percent
                            cb?.invoke(percent, "Whisper felismerés: $percent%")
                        }
                    }
                    override fun onSegment(t0: Long, t1: Long, text: ByteArray) {}
                }, vadModel = vadPath)
        }
        if (cancel.get()) return emptyList()
        if (rc != 0) throw IllegalStateException("A Whisper hibával leállt (kód $rc).")
        var prev1 = ""
        var prev2 = ""
        var prev1Lp = 1f
        var prev2Lp = 1f
        for (sg in w.segments()) {
            // A no-speech pontszám önmagában nem elég a törléshez: halk beszédnél is magas lehet.
            val t = sg.text.trim()
            if (t.isBlank()) continue
            // Három egymást követő azonos szegmens csak akkor dobható el, ha mindegyik gyenge
            // felismerési pontszámú; a valódi ismétlést egyébként megőrizzük.
            val weakRepeat = t == prev1 && t == prev2 && sg.avgLogProb < -1.0f &&
                prev1Lp < -1.0f && prev2Lp < -1.0f
            if (weakRepeat) continue
            prev2 = prev1; prev1 = t
            prev2Lp = prev1Lp; prev1Lp = sg.avgLogProb
            val t0 = if (timeMap != null) timeMap.toOrig(sg.startMs) else offsetMs + sg.startMs
            val t1 = if (timeMap != null) maxOf(timeMap.toOrig(sg.endMs), t0 + 200) else offsetMs + sg.endMs
            // A szegmens- és szóminőséget külön kezeljük: az avgLogProb a teljes szegmensé,
            // az egyes szavak kiemelését a TokenScore alapú wordConfidence végzi.
            out.addAll(splitLong(Raw(t0, t1, t, sg.avgLogProb, sg.wordConfidence, sg.noSpeech)))
        }
        if (out.isNotEmpty()) {
            prevTail = out.joinToString(" ") { it.text }.takeLast(220)
        }
        return finishTiming(out)
    }

    /** Az előző felismert darab szövegének vége (Whisper kontextus a következő darabhoz). */
    private var prevTail = ""

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
        try { voskModel?.close() } catch (e: Exception) { DiagLog.swallow("FileRecognizer", e) }
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
            val startWord = parts.take(res.size).sumOf { it.size }
            val scores = if (r.wordConfidence.size >= startWord + p.size) r.wordConfidence.subList(startWord, startWord + p.size) else emptyList()
            res.add(Raw(s, e, txt, r.lp, scores, r.noSpeechProb))
        }
        return res
    }

    /** Olvashatóság: legalább 1 mp, legfeljebb a következő blokk kezdetéig. */
    private fun finishTiming(list: List<Raw>): List<Raw> {
        val out = ArrayList<Raw>(list.size)
        for ((i, r) in list.withIndex()) {
            var end = maxOf(r.endMs, r.startMs + 1000)
            if (i + 1 < list.size) end = minOf(end, maxOf(list[i + 1].startMs - 40, r.startMs + 400))
            out.add(Raw(r.startMs, end, r.text, r.lp, r.wordConfidence, r.noSpeechProb))
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
        try { translator?.close() } catch (e: Exception) { DiagLog.swallow("FileRecognizer", e) }
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
        translatorKey = key
        try {
            MlKitModels.ensure(listOf(src, tgt), { cancel.get() }, { el ->
                onStatus?.invoke("Fordítómodell letöltése… ${el / 60}:${"%02d".format(el % 60)}")
            })
        } catch (e: Exception) {
            t.close()
            throw e
        }
        if (cancel.get()) { t.close(); throw InterruptedException("Megszakítva") }
        translator = t
        translatorOk = true
    }

    /**
     * Kontextusos fordítás: a mondat közepén vágott blokkokat mondatokká fogjuk, így fordít jól a gépi fordító;
     * a lefordított mondatot arányosan visszaosztjuk az eredeti időzített blokkokra.
     */
    private fun translateGrouped(items: List<SentenceGroups.Item>, dir: String, onDone: (Int, Int) -> Unit): List<String> {
        val groups = SentenceGroups.group(items)
        val texts = groups.map { g -> g.joinToString(" ") { idx -> items[idx].text.trim() } }
        val tr = translateAll(texts, dir, onDone)
        val out = ArrayList<String>(items.size)
        for ((gi, g) in groups.withIndex()) {
            val lens = g.map { idx -> items[idx].text.length }
            out.addAll(SentenceGroups.distribute(tr.getOrElse(gi) { "" }, lens))
        }
        return out
    }

    /** A fordított szöveg utómunkája: szótár-javítások, központozás, (magyarnál) tegezés. */
    private fun postTarget(res: String, toHu: Boolean): String =
        if (toHu) {
            val hu = TextPolish.polishHungarian(Fixes.apply(res, huRules))
            if (SubPrefs.informalHu(ctx)) TextPolish.informalHungarian(hu) else hu
        } else TextPolish.polishEnglish(Fixes.apply(res, enRules))

    private fun isToHu(dir: String) = dir == Prefs.DIR_EN_HU || dir.endsWith("_hu")

    /**
     * A blokkok fordítása a beállított móddal (offline ML Kit / online LLM / online + offline tartalék).
     * @param huInit meglévő fordítások (kontextus és a nem érintett sorok); null = üres
     * @param indices a lefordítandó sorok; null = mind
     * @param modeOverride [OnlinePrefs] mód felülbírálása (pl. az ellenőrző nézet gombjai); null = beállítás szerint
     * @return minden sor célszövege (az érintetlen sorok a [huInit] értékével)
     */
    /** True: a részhalmaz (pl. egy szakasz) újrafordítása is mondatokba fogva, a környező sorok nélkül is egységként. */
    @Volatile var groupSubset = false

    private fun translateCore(
        en: List<String>, times: List<Pair<Long, Long>>, huInit: List<String>?, indices: List<Int>?,
        dir: String, modeOverride: Int?, prog: (done: Int, total: Int, label: String) -> Unit
    ): List<String> {
        val n = en.size
        val hu = (huInit ?: List(n) { "" }).toMutableList()
        var idx = indices ?: (0 until n).toList()
        if (indices == null && modeOverride == null && TranslationMemoryStore.enabled(ctx)) {
            // ugyanaz az angol mondat mindig ugyanúgy forduljon (fordítási memória)
            val tm = TranslationMemoryStore.get(ctx)
            idx = idx.filter { i ->
                val h = tm.lookup(en[i])
                if (h != null && hu[i].isBlank()) { hu[i] = h; false } else true
            }
        }
        if (idx.isEmpty()) return hu
        val toHu = isToHu(dir)
        val mode = modeOverride ?: OnlinePrefs.mode(ctx)
        var todo = idx
        if (mode != OnlinePrefs.OFF) {
            val cfg0 = OnlinePrefs.config(
                ctx, SrtJobService.langName(dir.substringBefore("_", "en")), SrtJobService.langName(dir.substringAfter("_", "hu"))
            )
            // a szereplők nevei (a gyakran visszatérő nagybetűs szavak) háttérként a fordítónak
            val found = try { NameExtractor.extract(en) } catch (_: Exception) { emptyList() }
            if (found.isNotEmpty()) SrtJob.names = found
            val cfg = if (cfg0 != null && found.isNotEmpty())
                cfg0.copy(hint = (cfg0.hint + "\nNevek (ne fordítsd, magyarosan ragozhatók): " + found.joinToString(", ")).trim())
            else cfg0
            if (cfg != null) {
                val ot = OnlineTranslator(cfg)
                val failed: List<Int>
                if ((indices == null || groupSubset) && SubPrefs.contextTranslate(ctx)) {
                    // mondatokba fogott fordítás: a félbevágott blokkokat egyben kapja a fordító, utána visszaosztjuk
                    val items = idx.map { SentenceGroups.Item(times[it].first, times[it].second, en[it]) }
                    val groups = SentenceGroups.group(items)
                    val gEn = groups.map { g -> g.joinToString(" ") { items[it].text.trim() } }
                    val gHu = MutableList(groups.size) { "" }
                    val gFailed = ot.translate(gEn, gHu, gEn.indices.toList(), { d, t -> prog(d, t, "Fordítás (online)") }, { cancel.get() })
                        .toHashSet()
                    val fl = ArrayList<Int>()
                    for ((gi, g) in groups.withIndex()) {
                        if (gi in gFailed) { for (k in g) fl.add(idx[k]); continue }
                        val parts = SentenceGroups.distribute(postTarget(gHu[gi], toHu), g.map { items[it].text.length })
                        for ((j, k) in g.withIndex()) hu[idx[k]] = parts.getOrElse(j) { "" }
                    }
                    failed = fl
                } else {
                    failed = ot.translate(en, hu, idx, { d, t -> prog(d, t, "Fordítás (online)") }, { cancel.get() })
                    val fs = failed.toHashSet()
                    for (i in idx) if (i !in fs) hu[i] = postTarget(hu[i], toHu)
                }
                todo = failed
                SrtJob.addEngine(
                    when (cfg.provider) {
                        OnlineTranslator.Provider.DEEPL -> "DeepL"
                        OnlineTranslator.Provider.ANTHROPIC -> "Claude"
                        else -> "OpenAI-kompatibilis"
                    }, idx.size - failed.size, 0
                )
                if (failed.isNotEmpty()) {
                    onNote?.invoke("Online fordítás: ${failed.size} sor nem sikerült" +
                        (if (ot.lastError.isNotBlank()) " – ${ot.lastError}" else ""))
                }
                if (mode == OnlinePrefs.ONLINE) {
                    // nincs offline tartalék: a sikertelen sor forrásszövege marad, az ellenőrző nézet jelöli
                    for (i in failed) if (hu[i].isBlank()) hu[i] = en[i]
                    return hu
                }
            } else {
                onNote?.invoke("Az online fordítás nincs (teljesen) beállítva – offline fordítás készül.")
            }
        }
        if (todo.isNotEmpty() && !cancel.get()) {
            try {
                prepareTranslator(dir)
                val sub = todo.map { en[it] }
                val off = if ((indices == null || groupSubset) && SubPrefs.contextTranslate(ctx)) {
                    translateGrouped(
                        todo.map { SentenceGroups.Item(times[it].first, times[it].second, en[it]) }, dir
                    ) { d, t -> prog(d, t, "Fordítás") }
                } else translateAll(sub, dir) { d, t -> prog(d, t, "Fordítás") }
                for ((k, i) in todo.withIndex()) hu[i] = off.getOrElse(k) { "" }
                SrtJob.addEngine("", 0, todo.size)
            } catch (e: IllegalStateException) {
                if (mode == OnlinePrefs.OFF) throw e
                onNote?.invoke("Offline tartalék sem elérhető: ${e.message}")
                for (i in todo) if (hu[i].isBlank()) hu[i] = en[i]
            }
        }
        return hu
    }

    /**
     * Bizonytalan szakaszok második menete: a megadott időtartományokat nagyobb nyalábbal újra felismeri.
     * @return szakaszonként a talált blokkok (csak forrásszöveg, megbízhatósággal); null = nem sikerült
     */
    fun refine(
        pcmFile: java.io.File, spans: List<Pair<Long, Long>>, spec: ModelManager.Spec, lang: String, beam: Int,
        prog: (done: Int, total: Int) -> Unit
    ): List<List<SrtExporter.Cue>?> {
        val out = ArrayList<List<SrtExporter.Cue>?>()
        val keepTail = prevTail
        try {
            for ((k, sp) in spans.withIndex()) {
                if (cancel.get()) { out.add(null); continue }
                prog(k, spans.size)
                val a = (sp.first * 16L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                val b = (sp.second * 16L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
                val pcm = ParallelChunker.readPcmRange(pcmFile, a, b)
                if (pcm.size < 8000) { out.add(null); continue }
                prevTail = ""
                val raws = try { rawWhisper(pcm, spec, lang, sp.first, null, null, beam) } catch (_: Exception) { null }
                out.add(raws?.map { r ->
                    val src = if (lang == "en") TextPolish.polishEnglish(
                        NameFix.apply(Fixes.apply(r.text, enRules), castNames)
                    ) else TextPolish.polishHungarian(Fixes.apply(r.text, huRules))
                    SrtExporter.Cue(r.startMs, r.endMs, src, "", "", r.lp, r.wordConfidence, r.noSpeechProb)
                })
            }
            prog(spans.size, spans.size)
        } finally {
            prevTail = keepTail
        }
        return out
    }

    /**
     * Meglévő blokkok (újra)fordítása – az ellenőrző nézetből.
     * @param indices a lefordítandó blokkok indexei; null = mind
     * @param modeOverride [OnlinePrefs.OFF] / [OnlinePrefs.ONLINE] / [OnlinePrefs.AUTO]; null = a beállítás
     */
    fun retranslate(
        cues: List<SrtExporter.Cue>, indices: List<Int>?, dir: String, modeOverride: Int?,
        prog: (done: Int, total: Int, label: String) -> Unit
    ): List<SrtExporter.Cue> {
        val hu = translateCore(
            cues.map { it.source }, cues.map { it.startMs to it.endMs }, cues.map { it.target }, indices, dir, modeOverride, prog
        )
        return cues.mapIndexed { i, c -> if (indices == null || i in indices) c.copy(target = hu[i]) else c }
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
                postTarget(k.result ?: "", toHu)
            } else ""
        }
    }

    companion object {
        private const val MAX_CHARS = 84
        private const val MAX_CUE_MS = 6500L
        private const val PAUSE_MS = 700L
    }
}
