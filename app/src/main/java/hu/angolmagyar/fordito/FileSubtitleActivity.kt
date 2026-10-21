package hu.angolmagyar.fordito

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
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
import androidx.core.graphics.ColorUtils
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
        const val EXTRA_TGT = "tgt" // célnyelv: hu/en/de/…
    }

    private var autoStart = false
    private var fromWizard = false
    /** Varázslóból jövő forrásnyelv (en/de/fr/…); ha üres, a UI választó érvényes. */
    private var wizardSrc: String? = null
    private var wizardTgt: String = "hu"
    private val jobListener: () -> Unit = { render() }
    private val tick = object : Runnable {
        override fun run() {
            if (!SrtJob.running) return
            render()
            main.postDelayed(this, 1000)
        }
    }
    /** Ez a képernyő látta futni a feladatot → a végén magától felkínálja a mentést. */
    private var watchedRun = false
    private var saveAsked = false
    private var rowsTranslate = false
    private lateinit var srcGroup: RadioGroup
    private lateinit var translateSwitch: SwitchCompat


    private lateinit var prefs: Prefs

    /** Beszélők megkülönböztetése (S1/S2…); tárgyalás módban mindig be van kapcsolva. */
    private var useDiarization = false
    private val main = Handler(Looper.getMainLooper())

    private lateinit var status: TextView
    private lateinit var segBar: SegmentedBar
    private lateinit var modeGroup: RadioGroup

    // Folyamatpanel
    private lateinit var progressPanel: LinearLayout
    private lateinit var stepsBox: LinearLayout
    private lateinit var overallLabel: TextView
    private lateinit var timeLabel: TextView
    private lateinit var startBtn: android.widget.Button
    private lateinit var cancelBtn: android.widget.Button
    private val stepRows = ArrayList<StepRow>()
    private lateinit var saveBtn: android.widget.Button
    private lateinit var bgHint: LinearLayout

    private class StepRow(
        val root: LinearLayout,
        val stripe: View,
        val icon: TextView,
        val spinner: ProgressBar,
        val time: TextView,
        val name: TextView,
        val comment: TextView,
        val bar: ProgressBar,
        val color: Int
    ) {
        var state = -1
    }

    private var selectedUri: Uri? = null

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
            val srt = SrtJob.resultSrt
            if (uri != null && srt != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(srt.toByteArray(Charsets.UTF_8))
                    }
                    SrtJob.saved = true
                    Toast.makeText(this, "SRT elmentve.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Mentési hiba: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            render()
        }

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
        wizardSrc = intent.getStringExtra(EXTRA_SRC)
        wizardTgt = intent.getStringExtra(EXTRA_TGT) ?: "hu"
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
            val langName = when (presetSrc) {
                "hu" -> "Magyar"; "de" -> "Német"; "fr" -> "Francia"
                "es" -> "Spanyol"; "it" -> "Olasz"; "uk" -> "Ukrán"
                else -> "Angol"
            }
            val tgtName = when (wizardTgt) {
                "en" -> "angol"; "de" -> "német"; "fr" -> "francia"; "es" -> "spanyol"; else -> "magyar"
            }
            val summary = when (outIdx) {
                0 -> "$langName átirat (fordítás nélkül)"
                1 -> "$langName videó → $tgtName felirat"
                else -> "$langName + $tgtName felirat"
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
        progressPanel.addView(Ui.title(this, "Folyamat", 16f))
        overallLabel = Ui.small(this, "Teljes: 0%")
        progressPanel.addView(overallLabel)
        // Színkódolt teljes sáv: minden lépés a saját színében, a súlyának megfelelő szélességben
        segBar = SegmentedBar(this)
        progressPanel.addView(segBar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(14)
        ).apply { topMargin = dp(4) })
        // Lépéslista: minden lépés külön sor, a saját színével
        stepsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(6))
        }
        progressPanel.addView(stepsBox)
        timeLabel = Ui.small(this, "")
        progressPanel.addView(timeLabel)
        column.addView(progressPanel)

        // Figyelmeztetés, ha az akkumulátor-optimalizálás leállíthatja a háttérben futó munkát
        bgHint = Ui.card(this)
        bgHint.addView(Ui.title(this, "Háttérben is fusson", 16f))
        bgHint.addView(Ui.small(this,
            "A feldolgozás előtérszolgáltatásként fut: másik appra válthatsz, vagy zárolhatod a telefont. " +
                "Egyes telefonok az akkumulátor-optimalizálással ettől még leállíthatják – kapcsold ki az apphoz."))
        bgHint.addView(Ui.button(this, "Akkumulátor-optimalizálás kikapcsolása", false) {
            BatterySettings.open(this)
        })
        column.addView(bgHint)

        startBtn = Ui.button(this, "SRT készítése", true) { startProcess() }
        column.addView(startBtn)
        saveBtn = Ui.button(this, "SRT mentése a telefonra…", true) {
            val n = SrtJob.resultName
            if (SrtJob.resultSrt != null && n.isNotEmpty()) saveSrtLauncher.launch(n)
        }
        saveBtn.visibility = View.GONE
        column.addView(saveBtn)
        cancelBtn = Ui.button(this, "Megszakítás", false) {
            SrtJobService.cancel(this)
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

    // ------------------------------------------------------------ életciklus

    override fun onStart() {
        super.onStart()
        SrtJob.addListener(jobListener)
        render()
        if (SrtJob.running) {
            main.removeCallbacks(tick)
            main.post(tick)
        }
    }

    override fun onStop() {
        SrtJob.removeListener(jobListener)
        main.removeCallbacks(tick)
        super.onStop()
    }

    // ------------------------------------------------------------ lépéslista

    private fun buildStepRow(idx: Int): StepRow {
        val color = SrtJob.COLORS[idx]
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        // színes csík a sor bal szélén (a sáv szegmensének színe)
        val stripe = View(this).apply {
            background = GradientDrawable().apply { cornerRadius = dp(2).toFloat(); setColor(color) }
        }
        outer.addView(stripe, LinearLayout.LayoutParams(dp(4), ViewGroup.LayoutParams.MATCH_PARENT).apply {
            marginEnd = dp(6)
        })
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(6), dp(8), dp(8), dp(8))
        }
        outer.addView(root, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val iconFrame = FrameLayout(this)
        val icon = TextView(this).apply {
            textSize = 18f
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
        }
        val spinner = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(color)
        }
        iconFrame.addView(icon, FrameLayout.LayoutParams(dp(28), dp(28), Gravity.CENTER))
        iconFrame.addView(spinner, FrameLayout.LayoutParams(dp(22), dp(22), Gravity.CENTER))
        head.addView(iconFrame, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(8) })
        val name = TextView(this).apply { textSize = 15f; text = "${idx + 1}. ${SrtJob.label(idx)}" }
        head.addView(name, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val time = TextView(this).apply {
            textSize = 13f
            typeface = Typeface.MONOSPACE
            setTextColor(Ui.color(this@FileSubtitleActivity, R.color.textSecondary))
        }
        head.addView(time)
        root.addView(head)
        val comment = TextView(this).apply {
            textSize = 13f
            setTextColor(Ui.color(this@FileSubtitleActivity, R.color.textSecondary))
            setPadding(dp(36), dp(3), 0, 0)
            visibility = View.GONE
        }
        root.addView(comment)
        val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            visibility = View.GONE
            progressTintList = android.content.res.ColorStateList.valueOf(color)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(
                ColorUtils.setAlphaComponent(color, 60)
            )
        }
        root.addView(bar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(6)
        ).apply { marginStart = dp(36); topMargin = dp(6) })
        // a külső sor kerül a listába
        stepsBox.addView(outer)
        return StepRow(root, stripe, icon, spinner, time, name, comment, bar, color)
    }

    private fun buildRows(withTranslation: Boolean) {
        stepsBox.removeAllViews()
        stepRows.clear()
        rowsTranslate = withTranslation
        for (i in 0 until SrtJob.WEIGHTS.size) stepRows.add(buildStepRow(i))
    }

    private fun applyRowState(r: StepRow, idx: Int, state: Int) {
        r.state = state
        val textPrimary = Ui.color(this, R.color.textPrimary)
        val textSecondary = Ui.color(this, R.color.textSecondary)
        r.root.background = if (state == SrtJob.ACTIVE) GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(ColorUtils.setAlphaComponent(r.color, 40))
        } else null
        r.stripe.alpha = if (state == SrtJob.PENDING) 0.35f else 1f
        r.spinner.visibility = if (state == SrtJob.ACTIVE) View.VISIBLE else View.GONE
        r.icon.visibility = if (state == SrtJob.ACTIVE) View.INVISIBLE else View.VISIBLE
        r.bar.visibility = if (state == SrtJob.ACTIVE) View.VISIBLE else View.GONE
        r.name.text = "${idx + 1}. ${SrtJob.label(idx)}"
        when (state) {
            SrtJob.PENDING -> {
                r.icon.text = "○"; r.icon.setTextColor(textSecondary)
                r.name.setTextColor(textSecondary); r.name.setTypeface(null, Typeface.NORMAL)
            }
            SrtJob.ACTIVE -> {
                r.name.setTextColor(textPrimary); r.name.setTypeface(null, Typeface.BOLD)
            }
            SrtJob.DONE -> {
                r.icon.text = "✓"; r.icon.setTextColor(Ui.color(this, R.color.ok))
                r.name.setTextColor(textPrimary); r.name.setTypeface(null, Typeface.NORMAL)
            }
            SrtJob.ERROR -> {
                r.icon.text = "✗"; r.icon.setTextColor(Ui.color(this, R.color.err))
                r.name.setTextColor(Ui.color(this, R.color.err)); r.name.setTypeface(null, Typeface.BOLD)
            }
            else -> {
                r.icon.text = "⏸"; r.icon.setTextColor(Ui.color(this, R.color.warn))
                r.name.setTextColor(Ui.color(this, R.color.warn)); r.name.setTypeface(null, Typeface.BOLD)
            }
        }
    }

    /** A közös állapot (SrtJob) alapján újrarajzolja a folyamat-panelt. Csak a főszálon hívjuk. */
    private fun render() {
        if (!::progressPanel.isInitialized) return
        val job = SrtJob
        val show = job.running || job.finished || job.cancelled || job.error != null
        bgHint.visibility = if (BatterySettings.isIgnoring(this)) View.GONE else View.VISIBLE
        progressPanel.visibility = if (show) View.VISIBLE else View.GONE
        startBtn.isEnabled = !job.running
        cancelBtn.isEnabled = job.running
        saveBtn.visibility = if (job.finished && !job.saved) View.VISIBLE else View.GONE
        if (!show) return

        if (job.running) watchedRun = true
        if (stepRows.size != job.steps.size || rowsTranslate != job.withTranslation) buildRows(job.withTranslation)

        val now = System.currentTimeMillis()
        for (i in stepRows.indices) {
            val st = job.steps[i]
            val r = stepRows[i]
            if (r.state != st.state) applyRowState(r, i, st.state)
            r.time.text = when (st.state) {
                SrtJob.PENDING -> ""
                SrtJob.ACTIVE -> fmtDur(now - job.stepStartMs)
                else -> fmtDur(st.durMs)
            }
            if (st.state == SrtJob.ACTIVE) {
                if (st.pct < 0) r.bar.isIndeterminate = true
                else { r.bar.isIndeterminate = false; r.bar.progress = st.pct }
            }
            val pctTxt = if (st.state == SrtJob.ACTIVE && st.pct > 0) "${st.pct}% · " else ""
            val etaTxt = if (i == SrtJob.ST_RECOG && st.state == SrtJob.ACTIVE && job.etaMs > 0)
                " · hátra ${fmtDur(job.etaMs)}" else ""
            val text = if (st.comment.isEmpty()) "" else pctTxt + st.comment + etaTxt
            r.comment.text = text
            r.comment.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        }
        segBar.update()

        val chunkPart = if (job.chunkTotal > 0) " · chunk ${job.chunkCur}/${job.chunkTotal}" else ""
        val finishPart = if (job.running && job.etaMs > 0) " · kész kb. ${fmtClock(now + job.etaMs)}" else ""
        overallLabel.text = "Teljes: ${job.overall}%$chunkPart$finishPart"
        val end = if (job.running) now else job.endMs.takeIf { it > 0 } ?: now
        val etaPart = when {
            !job.running -> ""
            job.etaMs > 0 -> "\nHátralévő: ${fmtDur(job.etaMs)} · kész kb. ${fmtClock(now + job.etaMs)}"
            else -> "\nHátralévő: számítás…"
        }
        timeLabel.text = "Eltelt: ${fmtDur(end - job.startMs)}$etaPart"

        when {
            job.running -> {
                status.text = job.message
                status.setTextColor(Ui.color(this, if (job.cooling) R.color.warn else R.color.textPrimary))
            }
            job.finished -> {
                status.text = "✓ Kész – ${job.resultCount} feliratblokk" +
                    (if (job.partial) " (részleges)" else "") + "\n${job.resultName}"
                status.setTextColor(Ui.color(this, R.color.ok))
            }
            job.cancelled -> {
                status.text = "Megszakítva."
                status.setTextColor(Ui.color(this, R.color.warn))
            }
            job.error != null -> {
                status.text = "Hiba: ${job.error}"
                status.setTextColor(Ui.color(this, R.color.warn))
            }
        }

        // Ha a képernyő végignézte a futást, a végén magától felkínálja a mentést
        if (job.finished && !job.saved && watchedRun && !saveAsked && job.resultSrt != null) {
            saveAsked = true
            Haptics.click(this)
            saveSrtLauncher.launch(job.resultName)
        }
    }

    // ------------------------------------------------------------ választók

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
        wizardSrc?.let { if (it.isNotBlank()) return it }
        if (prefs.meetingMode || !::srcGroup.isInitialized) return "hu"
        val checked = srcGroup.checkedRadioButtonId
        val idx = (0 until srcGroup.childCount).indexOfFirst { srcGroup.getChildAt(it).id == checked }
        return if (idx == 1) "hu" else "en"
    }

    private fun selectedDir(): String {
        if (prefs.meetingMode) return Prefs.DIR_NONE
        val wantTr = if (::translateSwitch.isInitialized) translateSwitch.isChecked
        else intent.getBooleanExtra(EXTRA_TRANSLATE, true)
        if (!wantTr) return Prefs.DIR_NONE
        val src = selectedSrc()
        val tgt = wizardTgt.ifBlank { "hu" }
        return if (src == tgt) Prefs.DIR_NONE else "${src}_${tgt}"
    }

    private fun startProcess() {
        val uri = selectedUri
        if (uri == null) {
            Toast.makeText(this, "Előbb válassz fájlt.", Toast.LENGTH_SHORT).show()
            return
        }
        if (SrtJob.running) return
        val dir = if (prefs.meetingMode) Prefs.DIR_NONE else selectedDir()
        val needTranslate = dir != Prefs.DIR_NONE && !prefs.meetingMode
        watchedRun = false
        saveAsked = false
        // A munkát az előtér-szolgáltatás végzi: másik appra váltva / zárolt képernyővel is megy tovább.
        SrtJobService.start(this, uri, selectedMode(), selectedSrc(), dir, needTranslate, useDiarization)
        main.removeCallbacks(tick)
        main.post(tick)
        render()
    }

    private fun fmtDur(ms: Long) = SrtJob.fmtDur(ms)
    private fun fmtClock(atMs: Long) = SrtJob.fmtClock(atMs)
}
