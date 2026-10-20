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

    private var translator: Translator? = null
    private var translatorKey = ""
    private var translatorOk = false
    private var blocking: BlockingAsr? = null

    private val enRules by lazy { Fixes.parse(prefs.enFixes) }
    private val huRules by lazy { Fixes.parse(prefs.huFixes) }

    fun close() {
        try { translator?.close() } catch (_: Exception) {}
        translator = null
        blocking?.close()
        blocking = null
    }

    /**
     * @param spec a Vosk modell; null = a rendszer-felismerő (magyar)
     * @param srcLang "en" vagy "hu" – a javító szabályokhoz és a nyelvi utómunkához
     */
    fun recognize(
        pcm: ShortArray,
        srcLang: String,
        spec: ModelManager.Spec?,
        needTranslate: Boolean,
        dir: String,
        offsetMs: Long,
        speakerAt: (Long) -> String = { "" },
        onProgress: ((Int, String) -> Unit)? = null
    ): List<SrtExporter.Cue> {
        if (pcm.isEmpty()) return emptyList()
        val raws = if (spec != null) rawVosk(pcm, spec, offsetMs, onProgress)
        else rawSystem(pcm, offsetMs, onProgress)
        if (needTranslate) prepareTranslator(dir)
        val out = ArrayList<SrtExporter.Cue>()
        for (r in raws) {
            if (cancel.get()) break
            var src = Fixes.apply(r.text, if (srcLang == "en") enRules else huRules)
            if (TextPolish.isNoise(src)) continue
            src = if (srcLang == "en") TextPolish.polishEnglish(src) else TextPolish.polishHungarian(src)
            val target = translate(src, dir)
            out.add(SrtExporter.Cue(r.startMs, r.endMs, src, target, speakerAt(r.startMs)))
        }
        return out
    }

    // ------------------------------------------------------------------- Vosk

    private fun rawVosk(
        pcm: ShortArray, spec: ModelManager.Spec, offsetMs: Long,
        onProgress: ((Int, String) -> Unit)?
    ): List<Raw> {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val model = Model(ModelManager.dir(ctx, spec).absolutePath)
        val rec = Recognizer(model, 16000f)
        rec.setWords(true)
        val out = ArrayList<Raw>()
        try {
            val step = 8000 // 0,5 mp
            var o = 0
            var lastEnd = 0L
            val started = System.currentTimeMillis()
            while (o < pcm.size && !cancel.get()) {
                val e = (o + step).coerceAtMost(pcm.size)
                val slice = pcm.copyOfRange(o, e)
                if (rec.acceptWaveForm(slice, slice.size)) {
                    lastEnd = addVoskResult(rec.result, offsetMs, lastEnd, out)
                }
                o = e
                if (onProgress != null) {
                    val pct = (o * 100L / pcm.size).toInt()
                    val el = System.currentTimeMillis() - started
                    val eta = if (pct > 2 && el > 500) ((el * 100.0 / pct - el) / 1000).toInt() else -1
                    val etaS = if (eta >= 0) " · ~" + (if (eta >= 60) "${eta / 60}p ${eta % 60}mp" else "$eta mp") else ""
                    onProgress(pct, "Felismerés… $pct%$etaS")
                }
            }
            addVoskResult(rec.finalResult, offsetMs, lastEnd, out)
        } finally {
            rec.close()
            model.close()
        }
        return finishTiming(out)
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

    private fun rawSystem(
        pcm: ShortArray, offsetMs: Long, onProgress: ((Int, String) -> Unit)?
    ): List<Raw> {
        var asr = blocking
        if (asr == null) {
            asr = BlockingAsr(ctx, "hu-HU", prefs.allowOnlineAsr)
            val err = asr.open()
            if (err != null) throw IllegalStateException(err)
            blocking = asr
        }
        val segs = Vad.segments(pcm)
        val out = ArrayList<Raw>()
        val started = System.currentTimeMillis()
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
                val el = System.currentTimeMillis() - started
                val eta = if (pct > 2 && el > 1000) ((el * 100.0 / pct - el) / 1000).toInt() else -1
                val etaS = if (eta >= 0) " · ~" + (if (eta >= 60) "${eta / 60}p ${eta % 60}mp" else "$eta mp") else ""
                onProgress(pct, "Felismerés (magyar)… $pct%$etaS")
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

    private fun prepareTranslator(dir: String) {
        val key = dir
        if (translator != null && translatorKey == key) return
        try { translator?.close() } catch (_: Exception) {}
        translator = null
        translatorOk = false
        val (src, tgt) = if (dir == Prefs.DIR_HU_EN) {
            TranslateLanguage.HUNGARIAN to TranslateLanguage.ENGLISH
        } else {
            TranslateLanguage.ENGLISH to TranslateLanguage.HUNGARIAN
        }
        val t = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(tgt).build()
        )
        val latch = CountDownLatch(1)
        var ok = false
        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { ok = true; latch.countDown() }
            .addOnFailureListener { latch.countDown() }
        latch.await(90, TimeUnit.SECONDS)
        translatorKey = key
        if (ok) {
            translator = t
            translatorOk = true
        } else {
            t.close()
            throw IllegalStateException(
                "A fordítómodell nincs letöltve, és most nem érhető el. Nyisd meg a Modellek képernyőt."
            )
        }
    }

    private fun translate(src: String, dir: String): String {
        val t = translator ?: return ""
        var target = ""
        val latch = CountDownLatch(1)
        t.translate(src)
            .addOnSuccessListener { res ->
                target = if (dir == Prefs.DIR_HU_EN) TextPolish.polishEnglish(Fixes.apply(res, enRules))
                else TextPolish.polishHungarian(Fixes.apply(res, huRules))
                latch.countDown()
            }
            .addOnFailureListener { latch.countDown() }
        latch.await(15, TimeUnit.SECONDS)
        return target
    }

    companion object {
        private const val MAX_CHARS = 84
        private const val MAX_CUE_MS = 6500L
        private const val PAUSE_MS = 700L
    }
}
