package hu.angolmagyar.fordito

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hang- vagy videófájlból SRT felirat készítése.
 * Támogatott: mp3, m4a, wav, ogg, flac, mp4, mkv, webm (MediaExtractor dekódolható formátumok).
 *
 * Megjegyzés: a dekódolás MediaExtractor + nyers PCM olvasással történik ahol lehetséges;
 * komplex kodekeknél a minőség/kompatibilitás változó lehet.
 */
class FileSubtitleActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MEETING = "meeting"
    }


    private lateinit var prefs: Prefs

    /** Beszélők megkülönböztetése (S1/S2…); tárgyalás módban mindig be van kapcsolva. */
    private var useDiarization = false
    private val main = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var modeGroup: RadioGroup
    private lateinit var dirGroup: RadioGroup

    private var selectedUri: Uri? = null
    private var working = false
    private val cancel = AtomicBoolean(false)

    private val openLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                selectedUri = uri
                try {
                    contentResolver.takePersistableUriPermission(
                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {
                }
                status.text = "Kiválasztva: ${uri.lastPathSegment ?: uri}"
            }
        }

    private val saveSrtLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
            if (uri != null && pendingSrt != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(pendingSrt!!.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(this, "SRT elmentve.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Mentési hiba: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            pendingSrt = null
        }

    private var pendingSrt: String? = null

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onDestroy() {
        // Ideiglenes tárgyalás mód: kilépéskor visszaállítjuk az eredeti beállításokat.
        if (isFinishing && ::prefs.isInitialized && prefs.meetingMode && prefs.meetingTemporary) {
            MeetingMode.clear(prefs)
        }
        super.onDestroy()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val meeting = intent.getBooleanExtra(EXTRA_MEETING, false) || prefs.meetingMode
        if (meeting) {
            // Ha a felhasználó nem kapcsolta be tartósan, csak a képernyő idejére érvényes.
            MeetingMode.apply(prefs, temporary = !prefs.meetingMode || prefs.meetingTemporary)
        }

        val column = Ui.column(this).apply {
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val scroll = Ui.scrollOf(this, column)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        column.addView(Ui.button(this, "← Vissza", false) { finish() })
        column.addView(Ui.title(this, if (meeting) "Tárgyalás – magyar átirat" else "Felirat fájlból (SRT)"))
        if (meeting) {
            column.addView(Ui.small(this, MeetingMode.statusText(this)))
            column.addView(Ui.small(this, DiarizerFactory.onnxStatus(this)))
            column.addView(Ui.small(this, "Beszélők: S1/S2… az SRT-ben (energia-alapú, ONNX ha telepítve)"))
        }
        column.addView(
            Ui.small(
                this,
                "Hang- vagy videófájlból offline átirat + opcionális fordítás, SRT kimenettel."
            )
        )
        if (!meeting) {
            column.addView(SwitchCompat(this).apply {
                text = getString(R.string.f_diarize)
                minHeight = dp(52)
                setOnCheckedChangeListener { _, checked -> useDiarization = checked }
            })
        }

        column.addView(Ui.button(this, "Fájl kiválasztása…", true) {
            openLauncher.launch(
                arrayOf(
                    "audio/*", "video/*",
                    "audio/mpeg", "audio/mp4", "audio/x-wav", "audio/ogg",
                    "video/mp4", "video/x-matroska", "video/webm"
                )
            )
        })

        status = Ui.body(this, "Nincs fájl kiválasztva.")
        column.addView(status)

        // Mód
        val modeCard = Ui.card(this)
        modeCard.addView(Ui.title(this, "Kimenet", 16f))
        modeGroup = RadioGroup(this)
        modeGroup.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = "Csak forrásnyelvű átirat (EN vagy HU)"
            isChecked = false
        })
        modeGroup.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = "Csak fordítás (célnyelv)"
            isChecked = false
        })
        modeGroup.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = "Forrás + fordítás (mindkettő)"
            isChecked = true
        })
        modeCard.addView(modeGroup)
        column.addView(modeCard)

        // Irány
        val dirCard = Ui.card(this)
        dirCard.addView(Ui.title(this, "Nyelv / irány", 16f))
        dirGroup = RadioGroup(this)
        dirGroup.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = "Angol → Magyar"
            isChecked = prefs.translateDir == Prefs.DIR_EN_HU
        })
        dirGroup.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = "Magyar → Angol"
            isChecked = prefs.translateDir == Prefs.DIR_HU_EN
        })
        dirGroup.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = "Csak átirat (nincs fordítás)"
            isChecked = prefs.translateDir == Prefs.DIR_NONE
        })
        dirCard.addView(dirGroup)
        column.addView(dirCard)

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(
                Ui.color(this@FileSubtitleActivity, R.color.primary)
            )
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                Ui.color(this@FileSubtitleActivity, R.color.stroke)
            )
            scaleY = 1.5f
        }
        column.addView(progress)

        column.addView(Ui.button(this, "SRT készítése", true) { startProcess() })
        column.addView(Ui.button(this, "Megszakítás", false) {
            cancel.set(true)
            Toast.makeText(this, "Megszakítás…", Toast.LENGTH_SHORT).show()
        })
    }

    private fun selectedMode(): SrtExporter.Mode {
        val checked = modeGroup.checkedRadioButtonId
        val idx = (0 until modeGroup.childCount).indexOfFirst { modeGroup.getChildAt(it).id == checked }
        return when (idx) {
            0 -> SrtExporter.Mode.SOURCE_ONLY
            1 -> SrtExporter.Mode.TARGET_ONLY
            else -> SrtExporter.Mode.BOTH
        }
    }

    private fun selectedDir(): String {
        val checked = dirGroup.checkedRadioButtonId
        val idx = (0 until dirGroup.childCount).indexOfFirst { dirGroup.getChildAt(it).id == checked }
        return when (idx) {
            1 -> Prefs.DIR_HU_EN
            2 -> Prefs.DIR_NONE
            else -> Prefs.DIR_EN_HU
        }
    }

    private fun startProcess() {
        val uri = selectedUri
        if (uri == null) {
            Toast.makeText(this, "Előbb válassz fájlt.", Toast.LENGTH_SHORT).show()
            return
        }
        if (working) return
        working = true
        cancel.set(false)
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        status.text = "Feldolgozás indul…"

        val mode = selectedMode()
        var dir = selectedDir()
        if (prefs.meetingMode) {
            dir = Prefs.DIR_NONE
        }
        val sourceLang = if (dir == Prefs.DIR_HU_EN || prefs.meetingMode) "hu" else "en"
        val needTranslate = dir != Prefs.DIR_NONE && !prefs.meetingMode

        Thread {
            try {
                val modelSpec = if (sourceLang == "hu" || prefs.meetingMode) {
                    MeetingMode.preferredSpec(this)
                } else {
                    prefs.effectiveSpec(this)
                }
                if (!ModelManager.isInstalled(this, modelSpec)) {
                    throw IllegalStateException(
                        "Nincs telepítve a ${modelSpec.label} modell. Töltsd le a főképernyőn / beállításokban."
                    )
                }

                main.post { status.text = "Hang kinyerése…" }
                val pcmFile = extractPcm(uri)
                if (cancel.get()) throw InterruptedException("Megszakítva")

                main.post { status.text = "PCM betöltése…" }
                val pcmBytes = pcmFile.readBytes()
                val shorts = ShortArray(pcmBytes.size / 2)
                for (i in shorts.indices) {
                    val lo = pcmBytes[i * 2].toInt() and 0xff
                    val hi = pcmBytes[i * 2 + 1].toInt()
                    shorts[i] = ((hi shl 8) or lo).toShort()
                }
                val durationMin = shorts.size / 16000.0 / 60.0
                val useChunked = prefs.meetingMode || prefs.meetingSafeMode || durationMin > 20

                val cues: List<SrtExporter.Cue>
                if (useChunked) {
                    main.post {
                        status.text = "Chunkolt feldolgozás (%.0f perc, biztonságos mód)".format(durationMin)
                    }
                    val chunker = ChunkedTranscriber(this, prefs)
                    val chunkMs = prefs.meetingChunkMinutes * 60 * 1000
                    val result = chunker.run(
                        pcm16kMono = shorts,
                        chunkMs = chunkMs,
                        overlapMs = 8_000, // 8 s átfedés – szó/mondat ne vágódjon el
                        pauseBetweenMs = if (prefs.meetingSafeMode) 10_000L else 5_000L,
                        processChunk = { slice, offsetMs, idx, isFirst, isLast ->
                            val diarizer = DiarizerFactory.create(this, preferOnnx = prefs.preferOnnx)
                            val segs = if (useDiarization || prefs.meetingMode)
                                diarizer.diarize(slice, 16000) else emptyList()
                            val shifted = segs.map {
                                it.copy(startMs = it.startMs + offsetMs, endMs = it.endMs + offsetMs)
                            }
                            recognizeSlice(slice, modelSpec, needTranslate, dir, shifted, offsetMs)
                        },
                        onProgress = { prog ->
                            main.post {
                                progress.isIndeterminate = false
                                progress.progress = prog.pctOverall.coerceIn(0, 100)
                                status.text = prog.message + if (prog.pausedForCooling) " ⏸" else ""
                                if (prog.pausedForCooling) {
                                    status.setTextColor(Ui.color(this, R.color.warn))
                                } else {
                                    status.setTextColor(Ui.color(this, R.color.textPrimary))
                                }
                            }
                        }
                    )
                    cues = result.cues
                    if (result.stoppedEarly) {
                        main.post {
                            Toast.makeText(this, "Feldolgozás megszakítva / korán leállt – részleges SRT mentve.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    val diarizer = DiarizerFactory.create(this, preferOnnx = prefs.preferOnnx)
                    val segments = if (prefs.meetingMode || useDiarization) {
                        diarizer.diarize(shorts, 16000)
                    } else emptyList()
                    main.post {
                        status.text = if (segments.isNotEmpty())
                            "Felismerés… (${segments.size} szegmens)"
                        else "Felismerés…"
                    }
                    cues = recognize(pcmFile, modelSpec, needTranslate, dir, segments)
                }
                pcmFile.delete()

                if (cancel.get()) throw InterruptedException("Megszakítva")

                val srt = SrtExporter.build(cues, mode)
                pendingSrt = srt

                // mentés a belső tárba is
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val name = "felirat_" + stamp + ".srt"
                val out = File(TranscriptStore.dir(this), name)
                out.writeText(srt, Charsets.UTF_8)
                val md = ExportHelper.toMarkdown(cues, "Tárgyalási átirat")
                ExportHelper.write(File(TranscriptStore.dir(this), "jegyzokonyv_" + stamp + ".md"), md)
                val plain = ExportHelper.toPlainMinutes(cues)
                ExportHelper.write(File(TranscriptStore.dir(this), "jegyzokonyv_" + stamp + ".txt"), plain)

                main.post {
                    working = false
                    progress.visibility = View.GONE
                    status.text = "✓ Kész – ${cues.size} feliratblokk\n${out.name}"
                    status.setTextColor(Ui.color(this, R.color.ok))
                    Toast.makeText(this, "SRT kész", Toast.LENGTH_SHORT).show()
                    Haptics.click(this)
                    saveSrtLauncher.launch(name)
                }
            } catch (e: InterruptedException) {
                main.post {
                    working = false
                    progress.visibility = View.GONE
                    status.text = "Megszakítva."
                }
            } catch (e: Exception) {
                main.post {
                    working = false
                    progress.visibility = View.GONE
                    status.text = "Hiba: ${e.message}"
                    Toast.makeText(this, "Hiba: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    /**
     * Egyszerűsített hangkinyerés: a fájlt a cache-be másolja, majd
     * MediaExtractor-ral próbál PCM-et olvasni. Ha nem sikerül dekódolni,
     * WAV/raw esetén közvetlenül használja.
     */
    private fun extractPcm(uri: Uri): File {
        val cache = File(cacheDir, "input_${System.currentTimeMillis()}")
        contentResolver.openInputStream(uri)?.use { input ->
            cache.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalStateException("Nem olvasható a fájl.")

        val pcm = File(cacheDir, "pcm_${System.currentTimeMillis()}.raw")
        try {
            AudioHelper.decodeToPcm16kMono(cache, pcm) { cancel.get() }
        } finally {
            cache.delete()
        }
        if (!pcm.exists() || pcm.length() < 1000) {
            throw IllegalStateException("Nem sikerült dekódolni a hangot. Próbálj WAV/MP3 fájlt.")
        }
        return pcm
    }

    private fun recognize(
        pcmFile: File,
        modelSpec: ModelManager.Spec,
        needTranslate: Boolean,
        dir: String,
        segments: List<SpeakerDiarizer.Segment> = emptyList()
    ): List<SrtExporter.Cue> {
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val model = Model(ModelManager.dir(this, modelSpec).absolutePath)
        val recognizer = Recognizer(model, 16000f)

        var translator: Translator? = null
        if (needTranslate) {
            val (src, tgt) = if (dir == Prefs.DIR_HU_EN) {
                TranslateLanguage.HUNGARIAN to TranslateLanguage.ENGLISH
            } else {
                TranslateLanguage.ENGLISH to TranslateLanguage.HUNGARIAN
            }
            translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(src)
                    .setTargetLanguage(tgt)
                    .build()
            )
            val latch = CountDownLatch(1)
            var ok = false
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { ok = true; latch.countDown() }
                .addOnFailureListener { latch.countDown() }
            latch.await(60, TimeUnit.SECONDS)
            if (!ok) {
                translator.close()
                translator = null
            }
        }

        val enRules = Fixes.parse(prefs.enFixes)
        val huRules = Fixes.parse(prefs.huFixes)
        val cues = mutableListOf<SrtExporter.Cue>()
        val bytes = pcmFile.readBytes()
        // 16-bit mono feltételezés
        val sampleRate = 16000
        val bytesPerSec = sampleRate * 2
        val chunkBytes = bytesPerSec / 2 // 0.5 mp
        var offset = 0
        var timeMs = 0L
        var lastPartial = ""
        val recognizeStartedAt = System.currentTimeMillis()

        while (offset < bytes.size && !cancel.get()) {
            val end = (offset + chunkBytes).coerceAtMost(bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            // short array
            val shorts = ShortArray(chunk.size / 2)
            for (i in shorts.indices) {
                val lo = chunk[i * 2].toInt() and 0xff
                val hi = chunk[i * 2 + 1].toInt()
                shorts[i] = ((hi shl 8) or lo).toShort()
            }
            if (recognizer.acceptWaveForm(shorts, shorts.size)) {
                val json = recognizer.result
                val text = JSONObject(json).optString("text", "").trim()
                if (text.isNotBlank()) {
                    var fixedSrc = if (modelSpec.lang == "en") {
                        Fixes.apply(text, enRules)
                    } else {
                        Fixes.apply(text, huRules)
                    }
                    if (TextPolish.isNoise(fixedSrc)) continue
                    fixedSrc = if (modelSpec.lang == "en") TextPolish.polishEnglish(fixedSrc)
                    else TextPolish.polishHungarian(fixedSrc)
                    var target = ""
                    if (translator != null) {
                        val latch = CountDownLatch(1)
                        translator.translate(fixedSrc)
                            .addOnSuccessListener { t ->
                                target = if (dir == Prefs.DIR_HU_EN) {
                                    TextPolish.polishEnglish(Fixes.apply(t, enRules))
                                } else {
                                    TextPolish.polishHungarian(Fixes.apply(t, huRules))
                                }
                                latch.countDown()
                            }
                            .addOnFailureListener { latch.countDown() }
                        latch.await(15, TimeUnit.SECONDS)
                    }
                    val start = timeMs
                    val dur = ((end - offset).toLong() * 1000 / bytesPerSec).coerceAtLeast(800)
                    val spk = speakerAt(segments, start)
                    cues.add(SrtExporter.Cue(start, start + dur, fixedSrc, target, spk))
                    timeMs += dur
                    lastPartial = ""
                }
            } else {
                val p = JSONObject(recognizer.partialResult).optString("partial", "")
                lastPartial = p
            }
            offset = end
            val pct = (offset * 100 / bytes.size.coerceAtLeast(1))
            val elapsed = System.currentTimeMillis() - recognizeStartedAt
            val etaSec = if (pct > 2 && elapsed > 500) {
                val totalEst = elapsed * 100.0 / pct
                ((totalEst - elapsed) / 1000.0).toInt().coerceAtLeast(0)
            } else -1
            val etaStr = if (etaSec >= 0) {
                val m = etaSec / 60
                val s = etaSec % 60
                if (m > 0) " · ~${m}p ${s}mp" else " · ~${s} mp"
            } else ""
            main.post {
                progress.isIndeterminate = false
                progress.progress = pct
                status.text = "Felismerés… $pct%$etaStr"
            }
        }

        // utolsó
        val finalJson = recognizer.finalResult
        val finalText = JSONObject(finalJson).optString("text", "").trim()
        if (finalText.isNotBlank()) {
            val fixedSrc = if (modelSpec.lang == "en") {
                Fixes.apply(finalText, enRules)
            } else {
                Fixes.apply(finalText, huRules)
            }
            var target = ""
            if (translator != null) {
                val latch = CountDownLatch(1)
                translator.translate(fixedSrc)
                    .addOnSuccessListener { t ->
                        target = if (dir == Prefs.DIR_HU_EN) {
                                    TextPolish.polishEnglish(Fixes.apply(t, enRules))
                                } else {
                                    TextPolish.polishHungarian(Fixes.apply(t, huRules))
                                }
                        latch.countDown()
                    }
                    .addOnFailureListener { latch.countDown() }
                latch.await(15, TimeUnit.SECONDS)
            }
            val spk = speakerAt(segments, timeMs)
            cues.add(SrtExporter.Cue(timeMs, timeMs + 2000, fixedSrc, target, spk))
        }

        recognizer.close()
        model.close()
        translator?.close()
        return cues
    }


    /** Egy PCM chunk felismerése (chunkolt hosszú felvételekhez). */
    private fun recognizeSlice(
        pcm: ShortArray,
        modelSpec: ModelManager.Spec,
        needTranslate: Boolean,
        dir: String,
        segments: List<SpeakerDiarizer.Segment>,
        offsetMs: Long
    ): List<SrtExporter.Cue> {
        if (pcm.isEmpty()) return emptyList()
        LibVosk.setLogLevel(LogLevel.WARNINGS)
        val model = Model(ModelManager.dir(this, modelSpec).absolutePath)
        val recognizer = Recognizer(model, 16000f)
        var translator: Translator? = null
        if (needTranslate) {
            val (src, tgt) = if (dir == Prefs.DIR_HU_EN) {
                TranslateLanguage.HUNGARIAN to TranslateLanguage.ENGLISH
            } else {
                TranslateLanguage.ENGLISH to TranslateLanguage.HUNGARIAN
            }
            translator = Translation.getClient(
                TranslatorOptions.Builder()
                    .setSourceLanguage(src)
                    .setTargetLanguage(tgt)
                    .build()
            )
            val latch = CountDownLatch(1)
            var ok = false
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build())
                .addOnSuccessListener { ok = true; latch.countDown() }
                .addOnFailureListener { latch.countDown() }
            latch.await(30, TimeUnit.SECONDS)
            if (!ok) { translator.close(); translator = null }
        }
        val enRules = Fixes.parse(prefs.enFixes)
        val huRules = Fixes.parse(prefs.huFixes)
        val cues = mutableListOf<SrtExporter.Cue>()
        val chunkSamples = 16000 / 2 // 0.5s
        var offset = 0
        var timeMs = offsetMs
        while (offset < pcm.size && !cancel.get()) {
            val end = (offset + chunkSamples).coerceAtMost(pcm.size)
            val slice = pcm.copyOfRange(offset, end)
            if (recognizer.acceptWaveForm(slice, slice.size)) {
                val text = JSONObject(recognizer.result).optString("text", "").trim()
                if (text.isNotBlank() && !TextPolish.isNoise(text)) {
                    var fixedSrc = if (modelSpec.lang == "en") Fixes.apply(text, enRules) else Fixes.apply(text, huRules)
                    fixedSrc = if (modelSpec.lang == "en") TextPolish.polishEnglish(fixedSrc) else TextPolish.polishHungarian(fixedSrc)
                    var target = ""
                    if (translator != null) {
                        val latch = CountDownLatch(1)
                        translator.translate(fixedSrc)
                            .addOnSuccessListener { t ->
                                target = if (dir == Prefs.DIR_HU_EN) TextPolish.polishEnglish(Fixes.apply(t, enRules))
                                else TextPolish.polishHungarian(Fixes.apply(t, huRules))
                                latch.countDown()
                            }
                            .addOnFailureListener { latch.countDown() }
                        latch.await(15, TimeUnit.SECONDS)
                    }
                    val dur = ((end - offset).toLong() * 1000 / 16000).coerceAtLeast(800)
                    val spk = speakerAt(segments, timeMs)
                    cues.add(SrtExporter.Cue(timeMs, timeMs + dur, fixedSrc, target, spk))
                    timeMs += dur
                }
            }
            offset = end
        }
        val finalText = JSONObject(recognizer.finalResult).optString("text", "").trim()
        if (finalText.isNotBlank() && !TextPolish.isNoise(finalText)) {
            var fixedSrc = if (modelSpec.lang == "en") Fixes.apply(finalText, enRules) else Fixes.apply(finalText, huRules)
            fixedSrc = if (modelSpec.lang == "en") TextPolish.polishEnglish(fixedSrc) else TextPolish.polishHungarian(fixedSrc)
            val spk = speakerAt(segments, timeMs)
            cues.add(SrtExporter.Cue(timeMs, timeMs + 2000, fixedSrc, "", spk))
        }
        recognizer.close()
        model.close()
        translator?.close()
        return cues
    }

    private fun speakerAt(segments: List<SpeakerDiarizer.Segment>, timeMs: Long): String {
        if (segments.isEmpty()) return ""
        val seg = segments.firstOrNull { timeMs >= it.startMs && timeMs < it.endMs }
            ?: segments.minByOrNull { kotlin.math.abs(it.startMs - timeMs) }
        if (seg == null) return ""
        val map = SpeakerNames.fromPrefs(prefs)
        return SpeakerNames.label(seg.speakerId, map)
    }
}
