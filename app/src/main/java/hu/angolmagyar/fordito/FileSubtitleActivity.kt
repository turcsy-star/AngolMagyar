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
        /** Varázslóból érkező előbeállítások. */
        const val EXTRA_TITLE = "title"
        const val EXTRA_SRC = "src"            // "en" | "hu"
        const val EXTRA_TRANSLATE = "translate" // boolean
        const val EXTRA_OUTPUT = "output"      // 0 = forrás, 1 = fordítás, 2 = mindkettő
        const val EXTRA_DIARIZE = "diarize"
        const val EXTRA_AUTOSTART = "autostart" // fájlválasztó azonnal, feldolgozás a választás után
    }

    private var autoStart = false
    private var fromWizard = false
    private val tick = object : Runnable {
        override fun run() {
            if (!working) return
            val now = System.currentTimeMillis()
            val elapsed = now - processStartMs
            val stepElapsed = now - stepStartMs
            if (::timeLabel.isInitialized) {
                timeLabel.text = "Eltelt: ${fmtDur(elapsed)} (lépés: ${fmtDur(stepElapsed)})" +
                    (if (lastEtaMs > 0) " · becsült hátralévő: ${fmtDur(lastEtaMs)}" else "")
            }
            main.postDelayed(this, 1000)
        }
    }
    private var lastEtaMs = 0L
    private lateinit var srcGroup: RadioGroup
    private lateinit var translateSwitch: SwitchCompat


    private lateinit var prefs: Prefs

    /** Beszélők megkülönböztetése (S1/S2…); tárgyalás módban mindig be van kapcsolva. */
    private var useDiarization = false
    private val main = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var modeGroup: RadioGroup

    // Folyamatpanel
    private lateinit var progressPanel: LinearLayout
    private lateinit var stepTitle: TextView
    private lateinit var stepList: TextView
    private lateinit var overallLabel: TextView
    private lateinit var stepLabel: TextView
    private lateinit var stepProgress: ProgressBar
    private lateinit var timeLabel: TextView
    private lateinit var startBtn: android.widget.Button
    private lateinit var cancelBtn: android.widget.Button
    private var processStartMs = 0L
    private var stepStartMs = 0L
    private val stepsLog = ArrayList<String>()
    private var currentStepName = ""

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
                // Ne induljon automatikusan – a felhasználó ellenőrizheti a beállításokat,
                // majd az „SRT készítése” gombbal indít. (autoStart csak a fájlválasztót nyitja)
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
        autoStart = intent.getBooleanExtra(EXTRA_AUTOSTART, false)
        fromWizard = intent.hasExtra(EXTRA_OUTPUT) && intent.hasExtra(EXTRA_SRC)
        val titleText = intent.getStringExtra(EXTRA_TITLE)
        column.addView(Ui.title(this, if (meeting) "Tárgyalás – magyar átirat" else (titleText ?: "Felirat fájlból (SRT)")))
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
                isChecked = intent.getBooleanExtra(EXTRA_DIARIZE, false)
                useDiarization = isChecked
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

        // Mód + nyelv: varázslóból jövet összefoglaló, különben teljes választó
        modeGroup = RadioGroup(this)
        val outIdx = intent.getIntExtra(EXTRA_OUTPUT, 2)
        modeGroup.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = "Csak forrásnyelvű átirat (EN vagy HU)"
            isChecked = outIdx == 0
        })
        modeGroup.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = "Csak fordítás (célnyelv)"
            isChecked = outIdx == 1
        })
        modeGroup.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = "Forrás + fordítás (mindkettő)"
            isChecked = outIdx == 2
        })
        val presetSrc = intent.getStringExtra(EXTRA_SRC)
            ?: if (prefs.translateDir == Prefs.DIR_HU_EN) "hu" else "en"
        if (!meeting) {
            srcGroup = RadioGroup(this)
            srcGroup.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = "Angol"; isChecked = presetSrc != "hu"
            })
            srcGroup.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = "Magyar"; isChecked = presetSrc == "hu"
            })
            translateSwitch = SwitchCompat(this).apply {
                text = "Fordítás a másik nyelvre (angol ⇄ magyar)"
                minHeight = dp(52)
                isChecked = if (intent.hasExtra(EXTRA_TRANSLATE)) intent.getBooleanExtra(EXTRA_TRANSLATE, true)
                else prefs.translateDir != Prefs.DIR_NONE
            }
        }
        if (fromWizard && !meeting) {
            val summary = when (outIdx) {
                0 -> if (presetSrc == "hu") "Magyar átirat (fordítás nélkül)" else "Angol átirat (fordítás nélkül)"
                1 -> if (presetSrc == "hu") "Magyar videó → angol felirat" else "Angol videó → magyar felirat"
                else -> if (presetSrc == "hu") "Magyar + angol felirat" else "Angol + magyar felirat"
            }
            val card = Ui.card(this)
            card.addView(Ui.title(this, "Felirat", 16f))
            card.addView(Ui.body(this, summary))
            card.addView(Ui.small(this, "Válaszd ki a fájlt, majd indítsd az SRT készítést."))
            column.addView(card)
            // Rejtett, de a selectedMode/selectedSrc olvassa
            modeGroup.visibility = View.GONE
            if (::srcGroup.isInitialized) srcGroup.visibility = View.GONE
            if (::translateSwitch.isInitialized) translateSwitch.visibility = View.GONE
        } else {
            val modeCard = Ui.card(this)
            modeCard.addView(Ui.title(this, "Kimenet", 16f))
            modeCard.addView(modeGroup)
            column.addView(modeCard)
            if (!meeting) {
                val langCard = Ui.card(this)
                langCard.addView(Ui.title(this, "A hang nyelve", 16f))
                langCard.addView(srcGroup)
                langCard.addView(translateSwitch)
                column.addView(langCard)
            }
        }

        // --- Folyamatpanel (varázsló-szerű lépések + idő + progress) ---
        progressPanel = Ui.card(this).apply { visibility = View.GONE }
        stepTitle = Ui.title(this, "Folyamat", 16f)
        progressPanel.addView(stepTitle)
        stepList = TextView(this).apply {
            textSize = 13f
            setTextColor(Ui.color(this@FileSubtitleActivity, R.color.textSecondary))
            setLineSpacing(0f, 1.15f)
        }
        progressPanel.addView(stepList)
        overallLabel = Ui.small(this, "Teljes folyamat")
        progressPanel.addView(overallLabel)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(
                Ui.color(this@FileSubtitleActivity, R.color.primary)
            )
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                Ui.color(this@FileSubtitleActivity, R.color.stroke)
            )
            scaleY = 1.5f
        }
        progressPanel.addView(progress)
        stepLabel = Ui.small(this, "Aktuális lépés")
        progressPanel.addView(stepLabel)
        stepProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(
                Ui.color(this@FileSubtitleActivity, R.color.accentGlow)
            )
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                Ui.color(this@FileSubtitleActivity, R.color.stroke)
            )
            scaleY = 1.2f
        }
        progressPanel.addView(stepProgress)
        timeLabel = Ui.small(this, "")
        progressPanel.addView(timeLabel)
        column.addView(progressPanel)

        startBtn = Ui.button(this, "SRT készítése", true) { startProcess() }
        column.addView(startBtn)
        cancelBtn = Ui.button(this, "Megszakítás", false) {
            cancel.set(true)
            Toast.makeText(this, "Megszakítás…", Toast.LENGTH_SHORT).show()
        }
        column.addView(cancelBtn)
        if (autoStart) {
            main.post {
                openLauncher.launch(
                    arrayOf(
                        "audio/*", "video/*",
                        "audio/mpeg", "audio/mp4", "audio/x-wav", "audio/ogg",
                        "video/mp4", "video/x-matroska", "video/webm"
                    )
                )
            }
        }
    }

    private fun fmtDur(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        val m = sec / 60
        val s = sec % 60
        return if (m > 0) "%d:%02d".format(m, s) else "%d s".format(s)
    }

    private fun beginStep(name: String) {
        currentStepName = name
        stepStartMs = System.currentTimeMillis()
        stepsLog.add("▶ $name")
        updateProgressUi(0, 0, name, null)
    }

    private fun finishStep(extra: String = "") {
        val elapsed = System.currentTimeMillis() - stepStartMs
        val last = stepsLog.lastOrNull()
        if (last != null && last.startsWith("▶ ")) {
            stepsLog[stepsLog.size - 1] = "✓ " + last.removePrefix("▶ ") +
                " (${fmtDur(elapsed)})${if (extra.isNotEmpty()) " – $extra" else ""}"
        }
    }

    private fun updateProgressUi(overallPct: Int, stepPct: Int, stepMsg: String, etaMs: Long?) {
        main.post {
            progressPanel.visibility = View.VISIBLE
            if (overallPct >= 0) {
                progress.isIndeterminate = overallPct <= 0 && stepPct <= 0
                progress.progress = overallPct.coerceIn(0, 100)
                overallLabel.text = "Teljes folyamat: $overallPct%"
            }
            stepProgress.isIndeterminate = stepPct <= 0
            stepProgress.progress = stepPct.coerceIn(0, 100)
            stepLabel.text = "Aktuális: $stepMsg" + if (stepPct > 0) " ($stepPct%)" else ""
            val now = System.currentTimeMillis()
            val elapsed = now - processStartMs
            val stepElapsed = now - stepStartMs
            lastEtaMs = when {
                etaMs != null && etaMs > 0 -> etaMs
                overallPct in 1..99 -> {
                    val totalEst = (elapsed * 100.0 / overallPct).toLong()
                    (totalEst - elapsed).coerceAtLeast(0)
                }
                else -> 0L
            }
            val etaStr = if (lastEtaMs > 0) " · becsült hátralévő: ${fmtDur(lastEtaMs)}" else ""
            timeLabel.text = "Eltelt: ${fmtDur(elapsed)} (lépés: ${fmtDur(stepElapsed)})$etaStr"
            stepList.text = stepsLog.joinToString("\n")
            status.text = stepMsg
        }
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

    private fun selectedSrc(): String {
        if (prefs.meetingMode || !::srcGroup.isInitialized) return "hu"
        val checked = srcGroup.checkedRadioButtonId
        val idx = (0 until srcGroup.childCount).indexOfFirst { srcGroup.getChildAt(it).id == checked }
        return if (idx == 1) "hu" else "en"
    }

    private fun selectedDir(): String {
        if (prefs.meetingMode || !::translateSwitch.isInitialized || !translateSwitch.isChecked) {
            return Prefs.DIR_NONE
        }
        return if (selectedSrc() == "hu") Prefs.DIR_HU_EN else Prefs.DIR_EN_HU
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
        processStartMs = System.currentTimeMillis()
        stepsLog.clear()
        main.post {
            startBtn.isEnabled = false
            progressPanel.visibility = View.VISIBLE
            status.setTextColor(Ui.color(this, R.color.textPrimary))
            main.removeCallbacks(tick)
            main.post(tick)
        }
        beginStep("Előkészítés")

        val mode = selectedMode()
        var dir = selectedDir()
        if (prefs.meetingMode) {
            dir = Prefs.DIR_NONE
        }
        val sourceLang = selectedSrc()
        val needTranslate = dir != Prefs.DIR_NONE && !prefs.meetingMode

        var frToClose: FileRecognizer? = null
        Thread {
            try {
                // Vosk modell (angol stb., vagy saját magyar ZIP); null = Android beépített (magyar)
                val modelSpec: ModelManager.Spec? = Engines.voskSpecFor(this, prefs, sourceLang)
                if (modelSpec != null && !ModelManager.isInstalled(this, modelSpec)) {
                    throw IllegalStateException(
                        "Nincs telepítve a ${modelSpec.label} modell. Töltsd le a Modellek képernyőn."
                    )
                }
                if (modelSpec == null) {
                    if (!SystemAsrSupport.audioSourceSupported()) {
                        throw IllegalStateException("A magyar felismeréshez Android 13 vagy újabb kell.")
                    }
                    if (!SystemAsrSupport.onDeviceAvailable(this) &&
                        !(prefs.allowOnlineAsr && SystemAsrSupport.anyAvailable(this))
                    ) {
                        throw IllegalStateException(
                            "Nincs magyar felismerő. Nyisd meg a Modellek képernyőt, és telepítsd a Google magyar offline csomagját."
                        )
                    }
                }
                finishStep()
                beginStep("Hang kinyerése a fájlból")
                val fr = FileRecognizer(this, prefs, cancel)
                frToClose = fr

                val pcmFile = extractPcm(uri)
                if (cancel.get()) throw InterruptedException("Megszakítva")
                finishStep()

                beginStep("PCM betöltése")
                val pcmBytes = pcmFile.readBytes()
                val shorts = ShortArray(pcmBytes.size / 2)
                for (i in shorts.indices) {
                    val lo = pcmBytes[i * 2].toInt() and 0xff
                    val hi = pcmBytes[i * 2 + 1].toInt()
                    shorts[i] = ((hi shl 8) or lo).toShort()
                }
                val durationMin = shorts.size / 16000.0 / 60.0
                finishStep("%.1f perc hang".format(durationMin))
                val useChunked = prefs.meetingMode || prefs.meetingSafeMode || durationMin > 20

                val cues: List<SrtExporter.Cue>
                if (useChunked) {
                    beginStep("Chunkolt felismerés (%.0f perc)".format(durationMin))
                    val chunker = ChunkedTranscriber(this, prefs)
                    val chunkMs = prefs.meetingChunkMinutes * 60 * 1000
                    val result = chunker.run(
                        pcm16kMono = shorts,
                        chunkMs = chunkMs,
                        overlapMs = 8_000,
                        pauseBetweenMs = if (prefs.meetingSafeMode) 10_000L else 5_000L,
                        processChunk = { slice, offsetMs, idx, isFirst, isLast ->
                            val diarizer = DiarizerFactory.create(this, preferOnnx = prefs.preferOnnx)
                            val segs = if (useDiarization || prefs.meetingMode)
                                diarizer.diarize(slice, 16000) else emptyList()
                            val shifted = segs.map {
                                it.copy(startMs = it.startMs + offsetMs, endMs = it.endMs + offsetMs)
                            }
                            // Fontos: onProgress átadása, különben a chunk alatt „megáll” a UI
                            fr.recognize(
                                slice, sourceLang, modelSpec, needTranslate, dir, offsetMs,
                                { t -> speakerAt(shifted, t) }
                            ) { pct, msg ->
                                updateProgressUi(-1, pct, "Chunk ${idx + 1}: $msg", null)
                            }
                        },
                        onProgress = { prog ->
                            val overall = prog.pctOverall.coerceIn(0, 100)
                            val eta = if (overall in 1..99) {
                                val elapsed = System.currentTimeMillis() - processStartMs
                                ((elapsed * 100.0 / overall) - elapsed).toLong().coerceAtLeast(0)
                            } else null
                            val msg = prog.message + if (prog.pausedForCooling) " ⏸" else ""
                            if (prog.pausedForCooling) {
                                main.post { status.setTextColor(Ui.color(this, R.color.warn)) }
                            } else {
                                main.post { status.setTextColor(Ui.color(this, R.color.textPrimary)) }
                            }
                            updateProgressUi(overall, 0, msg, eta)
                        }
                    )
                    cues = result.cues
                    finishStep("${cues.size} blokk")
                    if (result.stoppedEarly) {
                        main.post {
                            Toast.makeText(this, "Feldolgozás megszakítva / korán leállt – részleges SRT mentve.", Toast.LENGTH_LONG).show()
                        }
                    }
                } else {
                    beginStep("Felismerés")
                    val diarizer = DiarizerFactory.create(this, preferOnnx = prefs.preferOnnx)
                    val segments = if (prefs.meetingMode || useDiarization) {
                        diarizer.diarize(shorts, 16000)
                    } else emptyList()
                    cues = fr.recognize(
                        shorts, sourceLang, modelSpec, needTranslate, dir, 0L,
                        { t -> speakerAt(segments, t) }
                    ) { pct, msg ->
                        val eta = if (pct in 1..99) {
                            val elapsed = System.currentTimeMillis() - processStartMs
                            ((elapsed * 100.0 / pct) - elapsed).toLong().coerceAtLeast(0)
                        } else null
                        updateProgressUi(pct, pct, msg, eta)
                    }
                    finishStep("${cues.size} blokk")
                }
                                pcmFile.delete()
                fr.close()

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
                    startBtn.isEnabled = true
                    main.removeCallbacks(tick)
                    finishStep()
                    stepsLog.add("✓ SRT kész – ${cues.size} feliratblokk")
                    updateProgressUi(100, 100, "Kész – ${cues.size} feliratblokk", 0)
                    status.text = "✓ Kész – ${cues.size} feliratblokk\n${out.name}"
                    status.setTextColor(Ui.color(this, R.color.ok))
                    Toast.makeText(this, "SRT kész", Toast.LENGTH_SHORT).show()
                    Haptics.click(this)
                    saveSrtLauncher.launch(name)
                }
            } catch (e: InterruptedException) {
                frToClose?.close()
                main.post {
                    working = false
                    startBtn.isEnabled = true
                    main.removeCallbacks(tick)
                    stepsLog.add("⏸ Megszakítva")
                    stepList.text = stepsLog.joinToString("\n")
                    status.text = "Megszakítva."
                    status.setTextColor(Ui.color(this, R.color.warn))
                }
            } catch (e: Exception) {
                frToClose?.close()
                main.post {
                    working = false
                    startBtn.isEnabled = true
                    main.removeCallbacks(tick)
                    stepsLog.add("✗ Hiba: ${e.message}")
                    stepList.text = stepsLog.joinToString("\n")
                    status.text = "Hiba: ${e.message}"
                    status.setTextColor(Ui.color(this, R.color.warn))
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
        main.post {
            status.text = "Fájl másolása…"
            updateProgressUi(2, 5, "Fájl másolása a gyorsítótárba…", null)
        }
        val cache = File(cacheDir, "input_${System.currentTimeMillis()}")
        contentResolver.openInputStream(uri)?.use { input ->
            cache.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalStateException("Nem olvasható a fájl.")
        if (cancel.get()) throw InterruptedException("Megszakítva")

        main.post {
            updateProgressUi(5, 15, "Hang dekódolása (PCM 16 kHz)…", null)
        }
        val pcm = File(cacheDir, "pcm_${System.currentTimeMillis()}.raw")
        try {
            // periodikus UI frissítés, amíg a dekódolás tart
            val pulse = AtomicBoolean(true)
            Thread {
                var p = 15
                while (pulse.get() && !cancel.get()) {
                    try { Thread.sleep(800) } catch (_: InterruptedException) { break }
                    if (!pulse.get()) break
                    p = (p + 3).coerceAtMost(40)
                    val sec = (System.currentTimeMillis() - stepStartMs) / 1000
                    main.post {
                        if (working) updateProgressUi(p / 2, p, "Hang dekódolása… ${sec}s", null)
                    }
                }
            }.start()
            try {
                AudioHelper.decodeToPcm16kMono(cache, pcm) { cancel.get() }
            } finally {
                pulse.set(false)
            }
        } finally {
            cache.delete()
        }
        if (!pcm.exists() || pcm.length() < 1000) {
            throw IllegalStateException("Nem sikerült dekódolni a hangot. Próbálj WAV/MP3 fájlt.")
        }
        main.post { updateProgressUi(20, 100, "Hang kinyerése kész", null) }
        return pcm
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
