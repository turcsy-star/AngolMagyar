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
    private lateinit var srcGroup: RadioGroup
    private lateinit var translateSwitch: SwitchCompat


    private lateinit var prefs: Prefs

    /** Beszélők megkülönböztetése (S1/S2…); tárgyalás módban mindig be van kapcsolva. */
    private var useDiarization = false
    private val main = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var modeGroup: RadioGroup

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
                if (autoStart && !working) startProcess()
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

        // Mód
        val modeCard = Ui.card(this)
        modeCard.addView(Ui.title(this, "Kimenet", 16f))
        modeGroup = RadioGroup(this)
        modeGroup.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = "Csak forrásnyelvű átirat (EN vagy HU)"
            isChecked = intent.getIntExtra(EXTRA_OUTPUT, 2) == 0
        })
        modeGroup.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = "Csak fordítás (célnyelv)"
            isChecked = intent.getIntExtra(EXTRA_OUTPUT, 2) == 1
        })
        modeGroup.addView(RadioButton(this).apply {
            id = View.generateViewId()
            text = "Forrás + fordítás (mindkettő)"
            isChecked = intent.getIntExtra(EXTRA_OUTPUT, 2) == 2
        })
        modeCard.addView(modeGroup)
        column.addView(modeCard)

        // Nyelv
        if (!meeting) {
            val langCard = Ui.card(this)
            langCard.addView(Ui.title(this, "A hang nyelve", 16f))
            val presetSrc = intent.getStringExtra(EXTRA_SRC)
                ?: if (prefs.translateDir == Prefs.DIR_HU_EN) "hu" else "en"
            srcGroup = RadioGroup(this)
            srcGroup.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = "Angol"; isChecked = presetSrc != "hu"
            })
            srcGroup.addView(RadioButton(this).apply {
                id = View.generateViewId(); text = "Magyar"; isChecked = presetSrc == "hu"
            })
            langCard.addView(srcGroup)
            translateSwitch = SwitchCompat(this).apply {
                text = "Fordítás a másik nyelvre (angol ⇄ magyar)"
                minHeight = dp(52)
                isChecked = if (intent.hasExtra(EXTRA_TRANSLATE)) intent.getBooleanExtra(EXTRA_TRANSLATE, true)
                else prefs.translateDir != Prefs.DIR_NONE
            }
            langCard.addView(translateSwitch)
            column.addView(langCard)
        }

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
        progress.visibility = View.VISIBLE
        progress.isIndeterminate = true
        status.text = "Feldolgozás indul…"

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
                val fr = FileRecognizer(this, prefs, cancel)
                frToClose = fr

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
                            fr.recognize(slice, sourceLang, modelSpec, needTranslate, dir, offsetMs, { t -> speakerAt(shifted, t) }, null)
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
                    cues = fr.recognize(
                        shorts, sourceLang, modelSpec, needTranslate, dir, 0L,
                        { t -> speakerAt(segments, t) }
                    ) { pct, msg ->
                        main.post {
                            progress.isIndeterminate = false
                            progress.progress = pct
                            status.text = msg
                        }
                    }
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
                    progress.visibility = View.GONE
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
                    progress.visibility = View.GONE
                    status.text = "Megszakítva."
                }
            } catch (e: Exception) {
                frToClose?.close()
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

    private fun speakerAt(segments: List<SpeakerDiarizer.Segment>, timeMs: Long): String {
        if (segments.isEmpty()) return ""
        val seg = segments.firstOrNull { timeMs >= it.startMs && timeMs < it.endMs }
            ?: segments.minByOrNull { kotlin.math.abs(it.startMs - timeMs) }
        if (seg == null) return ""
        val map = SpeakerNames.fromPrefs(prefs)
        return SpeakerNames.label(seg.speakerId, map)
    }
}
