package hu.angolmagyar.fordito

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions

class MainActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUTO_START = "auto_start"
        const val EXTRA_PROFILE = "profile"
        private const val TIPS_VERSION = 29
    }

    private lateinit var prefs: Prefs
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var column: LinearLayout

    // Futás kártya élő elemei (a tick frissíti őket, nem kell újraépíteni)
    private var timerText: TextView? = null
    private lateinit var meterText: TextView
    private lateinit var meter: ProgressBar
    private lateinit var hintText: TextView
    private var captionText: TextView? = null

    // Állapot
    private var translatorReady = false
    private var downloading = false
    private var downloadMsg = ""
    private var downloadPct = 0

    // Pending műveletek
    private var pendingMode: String? = null
    private var pendingTest = false
    private var shownTestResult: String? = null

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val mode = pendingMode
            val test = pendingTest
            pendingMode = null
            pendingTest = false
            if (hasMic()) {
                if (mode != null) proceed(mode, test)
            } else {
                toast(getString(R.string.m_no_mic))
            }
        }

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val data = res.data
            if (res.resultCode == RESULT_OK && data != null) {
                launchService(TranslateService.MODE_INTERNAL, false, res.resultCode, data)
            } else {
                toast(getString(R.string.m_projection_denied))
            }
        }

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            handler.postDelayed(this, 500)
        }
    }

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private fun hasMic() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasOverlay() = Settings.canDrawOverlays(this)

    private fun voskReady() = Engines.missingForLive(this, prefs) == null

    private fun batteryOk(): Boolean = try {
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    } catch (_: Exception) {
        false
    }

    private fun allReady() = voskReady() && translatorReady && hasOverlay() && hasMic()

    // ------------------------------------------------------------------ életciklus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        publishShortcuts()

        column = Ui.column(this).apply {
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }
        val scroll = Ui.scrollOf(this, column)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        if (!prefs.onboarded && savedInstanceState == null && intent.getStringExtra(EXTRA_AUTO_START) == null) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
        }
        handleAutoStart(intent)
    }

    /** Hosszú nyomás az app ikonon: Mikrofon / Belső hang / Átiratok. */
    private fun publishShortcuts() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) return
        try {
            val sm = getSystemService(android.content.pm.ShortcutManager::class.java) ?: return
            fun sc(id: String, label: String, mode: String?): android.content.pm.ShortcutInfo {
                val intent = Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    if (mode != null) putExtra(EXTRA_AUTO_START, mode)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                if (mode == null) {
                    // Átiratok – külön activity
                    return android.content.pm.ShortcutInfo.Builder(this, id)
                        .setShortLabel(label)
                        .setLongLabel(label)
                        .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_translate))
                        .setIntent(
                            Intent(this, TranscriptActivity::class.java).apply {
                                action = Intent.ACTION_VIEW
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                        )
                        .build()
                }
                return android.content.pm.ShortcutInfo.Builder(this, id)
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_translate))
                    .setIntent(intent)
                    .build()
            }
            val filmName = getString(R.string.profile_film)
            val filmShortcut = android.content.pm.ShortcutInfo.Builder(this, "profile_film")
                .setShortLabel(filmName)
                .setLongLabel(filmName)
                .setIcon(android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_stat_translate))
                .setIntent(
                    Intent(this, MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        putExtra(EXTRA_PROFILE, filmName)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                )
                .build()
            sm.dynamicShortcuts = listOf(
                sc("mic", getString(R.string.m_mode_mic), TranslateService.MODE_MIC),
                sc("internal", getString(R.string.m_mode_internal), TranslateService.MODE_INTERNAL),
                filmShortcut,
                sc("transcripts", getString(R.string.tr_title), null)
            )
        } catch (_: Exception) {
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutoStart(intent)
    }

    override fun onResume() {
        super.onResume()
        checkTranslatorModel()
        rebuild(firstBuild)
        firstBuild = false
        handler.post(ticker)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(ticker)
    }

    private fun handleAutoStart(i: Intent?) {
        if (i == null) return
        val profileName = i.getStringExtra(EXTRA_PROFILE)
        if (profileName != null) {
            i.removeExtra(EXTRA_PROFILE)
            handler.post {
                val p = Profiles.find(this, prefs, profileName)
                if (p == null) return@post
                val mode = Profiles.apply(prefs, p)
                if (allReady()) proceed(mode, false) else toast(getString(R.string.m_finish_setup))
            }
            return
        }
        val mode = i.getStringExtra(EXTRA_AUTO_START) ?: return
        i.removeExtra(EXTRA_AUTO_START)
        handler.post { if (allReady()) proceed(mode, false) else toast(getString(R.string.m_finish_setup)) }
    }

    private fun checkTranslatorModel() {
        RemoteModelManager.getInstance()
            .isModelDownloaded(TranslateRemoteModel.Builder(TranslateLanguage.HUNGARIAN).build())
            .addOnSuccessListener { ok ->
                if (ok != translatorReady) {
                    translatorReady = ok
                    rebuild(false)
                }
            }
    }

    // ------------------------------------------------------------------------- UI

    private var meterShown = false
    private var firstBuild = true

    private fun modeName(mode: String?) = getString(
        when (mode) {
            TranslateService.MODE_INTERNAL -> R.string.m_mode_internal
            TranslateService.MODE_PTT -> R.string.m_mode_ptt
            else -> R.string.m_mode_mic
        }
    )

    /** @param animate a kártyák belépő animációja (csak szerkezetváltáskor; frissítéskor nincs villogás) */
    private fun rebuild(animate: Boolean = true) {
        column.removeAllViews()
        meterShown = false
        timerText = null
        captionText = null

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
        }
        header.addView(
            Ui.title(this, getString(R.string.m_title), 26f),
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(Ui.iconButton(this, R.drawable.ic_settings, getString(R.string.m_settings)) {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        column.addView(header)

        // Státusz sáv
        val dirLabel = when {
            prefs.isConversation() -> getString(R.string.m_dir_conv) +
                if (prefs.autoLangSwitch) " " + getString(R.string.m_dir_auto) else ""
            prefs.isHuToEn() -> "HU → EN"
            prefs.needsTranslation() -> "${prefs.sourceLang.uppercase()} → ${prefs.targetLang.uppercase()}"
            else -> getString(R.string.m_dir_none)
        }
        val sidesNow = Engines.sides(prefs)
        val modelLabel = when {
            sidesNow.hu && !sidesNow.other -> "Magyar (Android)"
            sidesNow.hu && sidesNow.other ->
                prefs.effectiveSpec(this).label.substringBefore("(").trim() + " + magyar"
            else -> prefs.effectiveSpec(this).label.substringBefore("(").trim()
        }
        val extras = buildList {
            if (prefs.ttsEnabled) add("TTS")
            if (prefs.eco) add("eco")
            if (prefs.meetingMode) add(getString(R.string.m_extra_meeting))
        }.joinToString(" · ").let { if (it.isBlank()) "" else " · $it" }
        column.addView(Ui.small(this, "$dirLabel  ·  $modelLabel  ·  ${prefs.delayMs} ms$extras"))

        if (prefs.tipsSeenVersion < TIPS_VERSION && !AppState.running) {
            val tip = Ui.card(this)
            tip.addView(Ui.title(this, getString(R.string.m_tip_title), 16f))
            tip.addView(Ui.small(this, getString(R.string.m_tip_body)))
            tip.addView(Ui.button(this, getString(R.string.m_tip_ok), true) {
                prefs.tipsSeenVersion = TIPS_VERSION
                rebuild()
            })
            column.addView(tip)
        }

        errorCard()

        if (AppState.running) {
            runningCard()
        } else {
            readyCards()
        }

        // Alsó műveletek
        val tools = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        }
        tools.addView(Ui.button(this, getString(R.string.tr_title), false) {
            startActivity(Intent(this, TranscriptActivity::class.java))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        })
        tools.addView(Ui.button(this, getString(R.string.m_srt_from_file), true) {
            startActivity(Intent(this, FileSubtitleActivity::class.java))
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        })
        column.addView(tools)
        column.addView(Ui.button(this, getString(R.string.log_title), false) { LogDialog.show(this) })

        if (animate) Ui.animateIn(column) else Ui.settle(column)
    }

    private fun stepRow(done: Boolean, title: String, desc: String, action: String?, onAction: (() -> Unit)?) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(4))
        }
        val head = TextView(this).apply {
            text = (if (done) "✔  " else "○  ") + title
            textSize = 16f
            setTextColor(Ui.color(this@MainActivity, if (done) R.color.ok else R.color.textPrimary))
        }
        row.addView(head)
        if (desc.isNotBlank()) row.addView(Ui.small(this, desc))
        if (!done && action != null && onAction != null) row.addView(Ui.button(this, action, true, onAction))
        wizardHost.addView(row)
    }

    private lateinit var wizardHost: LinearLayout

    private fun wizardCard() {
        val card = Ui.card(this)
        card.addView(Ui.title(this, getString(R.string.m_wizard_title), 18f))
        card.addView(Ui.small(this, getString(R.string.m_wizard_sub)))
        wizardHost = card

        val spec = prefs.effectiveSpec(this)
        if (downloading) {
            val txt = Ui.body(this, downloadMsg)
            card.addView(txt)
            card.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = downloadPct
                isIndeterminate = downloadPct <= 0
            })
        }
        stepRow(
            voskReady() && translatorReady, getString(R.string.m_step1),
            getString(R.string.m_step1_desc, spec.label),
            if (downloading) null else getString(R.string.m_download)
        ) { startDownload() }
        stepRow(
            hasOverlay(), getString(R.string.m_step2),
            getString(R.string.m_step2_desc), getString(R.string.m_allow)
        ) { openOverlaySettings() }
        stepRow(
            hasMic(), getString(R.string.m_step3),
            getString(R.string.m_step3_desc), getString(R.string.m_allow)
        ) { requestPerms() }
        stepRow(
            batteryOk(), getString(R.string.m_step4),
            getString(R.string.m_step4_desc), getString(R.string.m_open)
        ) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        android.net.Uri.parse("package:$packageName")
                    )
                )
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }

        card.addView(Ui.button(this, "Előkészítés ellenőrzése", false) {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        })
        column.addView(card)
    }

    private fun readyCards() {
        val last = prefs.lastMode

        // Profilok: egy koppintás = beállítások + indítás
        column.addView(Ui.small(this, getString(R.string.m_profiles)).apply {
            setPadding(0, dp(14), 0, dp(2))
        })
        val chipRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (p in Profiles.all(this, prefs)) {
            val b = Ui.smallButton(this, p.name) {
                val mode = Profiles.apply(prefs, p)
                proceed(mode, false)
            }
            b.setOnLongClickListener {
                if (!p.builtIn) confirmDeleteProfile(p.name)
                !p.builtIn
            }
            if (p.name == prefs.lastProfile) {
                b.setTextColor(Ui.color(this, R.color.primary))
                b.setTypeface(b.typeface, android.graphics.Typeface.BOLD)
            }
            chipRow.addView(b, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = dp(8) })
        }
        chipRow.addView(Ui.smallButton(this, getString(R.string.m_profile_save)) { saveProfileDialog() })
        column.addView(android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipRow)
        })

        // Mit szeretnél? – ikonos csempék, mindegyik saját varázslóval.
        // Modellek nélkül is látszanak, csak szürkék; koppintásra a letöltéshez visznek.
        val modelsOk = voskReady() && translatorReady
        fun open(task: String) {
            val needsLive = task == TaskSetupActivity.TASK_LIVE || task == TaskSetupActivity.TASK_INTERPRETER
            when {
                !modelsOk -> {
                    toast("Előbb töltsd le a modelleket.")
                    startActivity(Intent(this, ModelsActivity::class.java))
                }
                needsLive && (!hasMic() || !hasOverlay()) ->
                    startActivity(Intent(this, SetupWizardActivity::class.java))
                else -> startActivity(
                    Intent(this, TaskSetupActivity::class.java).putExtra(TaskSetupActivity.EXTRA_TASK, task)
                )
            }
        }
        if (!modelsOk) {
            val card = Ui.card(this)
            card.addView(Ui.title(this, "Első lépés: modellek", 18f))
            card.addView(Ui.small(this,
                if (Engines.device(this).canRunLarge)
                    "Ajánlott: nagy angol felismerő + fordító (~190 MB)."
                else "Ajánlott: kis angol felismerő + fordító (~100 MB)."))
            card.addView(Ui.button(this, "Ajánlott letöltése", true) {
                startActivity(Intent(this, ModelsActivity::class.java).putExtra(ModelsActivity.EXTRA_AUTO, true))
            })
            card.addView(Ui.button(this, "Modellek kiválasztása", false) { startDownload() })
            column.addView(card)
        }
        column.addView(Ui.small(this, "Mit szeretnél?").apply { setPadding(0, dp(14), 0, dp(2)) })
        column.addView(Ui.tileRow(
            this,
            Ui.tile(this, R.drawable.ic_play, "Élő fordítás", "film, videó, előadás felirata", modelsOk) { open(TaskSetupActivity.TASK_LIVE) },
            Ui.tile(this, R.drawable.ic_swap, "Tolmács", "magyar ⇄ angol beszélgetés", modelsOk) { open(TaskSetupActivity.TASK_INTERPRETER) }
        ))
        column.addView(Ui.tileRow(
            this,
            Ui.tile(this, R.drawable.ic_clipboard, "Átirat", "fájlból vagy felvételből", modelsOk) { open(TaskSetupActivity.TASK_TRANSCRIBE) },
            Ui.tile(this, R.drawable.ic_subtitles, "Film felirat", "angol videóból magyar .srt", modelsOk) { open(TaskSetupActivity.TASK_SUBTITLE) }
        ))

        // Gyors indítás: a legutóbbi beállításokkal
        column.addView(Ui.small(this, "Gyors indítás a legutóbbi beállításokkal").apply { setPadding(0, dp(16), 0, dp(2)) })
        column.addView(Ui.modeCard(
            this, R.drawable.ic_play, getString(R.string.m_card_internal), getString(R.string.m_card_internal_sub),
            last == TranslateService.MODE_INTERNAL
        ) { proceed(TranslateService.MODE_INTERNAL, false) })

        column.addView(Ui.modeCard(
            this, R.drawable.ic_mic, getString(R.string.m_card_mic), getString(R.string.m_card_mic_sub),
            last == TranslateService.MODE_MIC
        ) { proceed(TranslateService.MODE_MIC, false) })

        column.addView(Ui.button(this, getString(R.string.m_quick_test), false) {
            proceed(TranslateService.MODE_MIC, true)
        })

        val result = AppState.testResult
        if (result != null) {
            val rc = Ui.card(this)
            rc.addView(Ui.title(this, getString(R.string.m_test_result), 16f))
            rc.addView(Ui.body(this, result))
            rc.addView(Ui.button(this, getString(R.string.common_close), false) { AppState.testResult = null; rebuild(false) })
            column.addView(rc)
            shownTestResult = result
        }
    }

    private fun saveProfileDialog() {
        val input = androidx.appcompat.widget.AppCompatEditText(this).apply {
            hint = getString(R.string.m_profile_name_hint)
            setSingleLine(true)
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.m_profile_save)
            .setMessage(R.string.m_profile_save_msg)
            .setView(box)
            .setPositiveButton(R.string.common_save) { _, _ ->
                val name = input.text?.toString()?.trim() ?: ""
                if (name.isNotEmpty()) {
                    Profiles.saveCurrent(prefs, name, prefs.lastMode)
                    prefs.lastProfile = name
                    rebuild(false)
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun confirmDeleteProfile(name: String) {
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.m_profile_delete, name))
            .setPositiveButton(R.string.common_delete) { _, _ ->
                Profiles.delete(prefs, name)
                rebuild(false)
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun elapsedLabel(): String {
        if (AppState.sessionStartedAt <= 0) return "0:00"
        val sec = ((android.os.SystemClock.elapsedRealtime() - AppState.sessionStartedAt) / 1000).toInt()
        return if (sec >= 3600) String.format("%d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60)
        else String.format("%d:%02d", sec / 60, sec % 60)
    }

    /** Futó kártya állapotsora: irány és felismerő motor(ok). */
    private fun engineLine(): String {
        val s = Engines.sides(prefs)
        val parts = ArrayList<String>()
        if (s.other) parts.add(prefs.effectiveSpec(this).label.substringBefore("(").trim())
        if (s.hu) {
            parts.add(
                if (Engines.voskSpecFor(this, prefs, "hu") != null) "magyar: saját modell"
                else "magyar: Android"
            )
        }
        val dir = when {
            prefs.isConversation() ->
                if (prefs.autoLangSwitch) "Tolmács · auto irány" else "Tolmács · rögzített irány"
            prefs.isHuToEn() -> "HU → EN"
            prefs.needsTranslation() -> "${prefs.sourceLang.uppercase()} → ${prefs.targetLang.uppercase()}"
            else -> "Csak átirat"
        }
        return dir + " · " + parts.joinToString(" + ")
    }

    private fun runningCard() {
        meterShown = true
        val card = Ui.card(this)
        val timer = Ui.title(this, "", 18f).apply {
            setTextColor(Ui.color(this@MainActivity, R.color.ok))
        }
        timerText = timer
        timer.text = getString(R.string.m_running, modeName(AppState.mode), elapsedLabel())
        card.addView(timer)
        card.addView(Ui.small(this, engineLine()))
        meterText = Ui.small(this, getString(R.string.m_level))
        card.addView(meterText)
        meter = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        card.addView(meter)
        val cap = Ui.body(this, AppState.lastCaptionHu.ifBlank { AppState.lastCaptionEn }).apply {
            setPadding(0, dp(8), 0, dp(4))
        }
        captionText = cap
        card.addView(cap)
        hintText = Ui.small(this, "").apply {
            setTextColor(Ui.color(this@MainActivity, R.color.warn))
        }
        card.addView(hintText)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row.addView(Ui.button(this, getString(if (AppState.paused) R.string.m_resume else R.string.m_pause), false) {
            startService(
                Intent(this, TranslateService::class.java).setAction(TranslateService.ACTION_TOGGLE_PAUSE)
            )
            handler.postDelayed({ rebuild(false) }, 200)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(6)
            }
        })
        row.addView(Ui.button(this, getString(R.string.m_stop), true) {
            startService(Intent(this, TranslateService::class.java).setAction(TranslateService.ACTION_STOP))
            handler.postDelayed({ rebuild(false) }, 300)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
        })
        card.addView(row)
        column.addView(card)
    }

    private fun errorCard() {
        val err = AppState.lastError ?: return
        val card = Ui.card(this)
        card.addView(Ui.title(this, getString(R.string.m_error_title), 16f).apply {
            setTextColor(Ui.color(this@MainActivity, R.color.err))
        })
        card.addView(Ui.body(this, err.message))

        when (err.code) {
            AppState.ERR_NO_OVERLAY ->
                card.addView(Ui.button(this, getString(R.string.m_allow), true) { openOverlaySettings() })
            AppState.ERR_NO_MIC ->
                card.addView(Ui.button(this, getString(R.string.m_allow), true) { requestPerms() })
            AppState.ERR_NO_MODEL, AppState.ERR_NO_TRANSLATOR ->
                card.addView(Ui.button(this, getString(R.string.m_download_models), true) { startDownload() })
            AppState.ERR_PROJECTION, AppState.ERR_AUDIO ->
                card.addView(Ui.button(this, getString(R.string.m_try_mic), true) {
                    AppState.lastError = null
                    proceed(TranslateService.MODE_MIC, false)
                })
        }
        card.addView(Ui.button(this, "Önellenőrzés", false) {
            startActivity(Intent(this, DiagnosticsActivity::class.java))
        })
        card.addView(Ui.button(this, getString(R.string.log_title), false) { LogDialog.show(this) })
        card.addView(Ui.button(this, getString(R.string.m_hide), false) { AppState.lastError = null; rebuild(false) })
        column.addView(card)
    }

    // ------------------------------------------------------------------ időzítő

    private fun tick() {
        // Átállás futó/nem futó állapot között -> újraépítés
        val needRunning = AppState.running
        if (needRunning != meterShown) {
            rebuild()
            return
        }
        if (needRunning) {
            meter.progress = (AppState.level * 100).toInt()
            hintText.text = AppState.hint ?: ""
            timerText?.text = getString(R.string.m_running, modeName(AppState.mode), elapsedLabel())
            captionText?.text = AppState.lastCaptionHu.ifBlank { AppState.lastCaptionEn }
        }
        val tr = AppState.testResult
        if (tr != shownTestResult) {
            shownTestResult = tr
            rebuild(false)
        }
    }

    // ------------------------------------------------------------------- letöltés

    /** A modellek letöltése a külön Modellek képernyőn történik. */
    private fun startDownload() {
        startActivity(Intent(this, ModelsActivity::class.java))
    }

    private var rebuildPosted = false
    private fun rebuildSoon() {
        if (rebuildPosted) return
        rebuildPosted = true
        handler.postDelayed({
            rebuildPosted = false
            if (!isFinishing) rebuild(false)
        }, 400)
    }

    private fun finishDownload(msg: String) {
        runOnUiThread {
            downloading = false
            toast(msg)
            checkTranslatorModel()
            rebuild(false)
        }
    }

    // ------------------------------------------------------------------- indítás

    private fun openOverlaySettings() {
        startActivity(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
        )
    }

    private fun requestPerms(mode: String? = null, test: Boolean = false) {
        pendingMode = mode
        pendingTest = test
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        permLauncher.launch(perms.toTypedArray())
    }

    private fun proceed(mode: String, test: Boolean) {
        if (AppState.running) {
            toast(getString(R.string.m_already_running))
            return
        }
        AppState.lastError = null
        if (!voskReady()) {
            AppState.lastError = AppError(AppState.ERR_NO_MODEL, getString(R.string.m_need_models))
            rebuild(false)
            return
        }
        if (!hasOverlay()) {
            openOverlaySettings()
            toast(getString(R.string.m_allow_overlay))
            return
        }
        if (!hasMic()) {
            requestPerms(mode, test)
            return
        }
        if (mode == TranslateService.MODE_INTERNAL && !test) {
            // Egyszeri tájékoztatás a belső hang korlátairól
            if (!prefs.hintShown) {
                prefs.hintShown = true
                AlertDialog.Builder(this)
                    .setTitle(R.string.m_internal_title)
                    .setMessage(R.string.m_internal_msg)
                    .setPositiveButton(R.string.m_tip_ok) { _, _ -> startProjection() }
                    .show()
                return
            }
            startProjection()
        } else {
            launchService(mode, test, 0, null)
        }
    }

    private fun startProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun launchService(mode: String, test: Boolean, resultCode: Int, data: Intent?) {
        val intent = Intent(this, TranslateService::class.java).apply {
            action = TranslateService.ACTION_START
            putExtra(TranslateService.EXTRA_MODE, mode)
            putExtra(TranslateService.EXTRA_TEST, test)
            putExtra(
                TranslateService.EXTRA_MODEL_PATH,
                ModelManager.dir(this@MainActivity, prefs.effectiveSpec(this@MainActivity)).absolutePath
            )
            if (data != null) {
                putExtra(TranslateService.EXTRA_RESULT_CODE, resultCode)
                putExtra(TranslateService.EXTRA_DATA, data)
            }
        }
        ContextCompat.startForegroundService(this, intent)
        toast(getString(if (test) R.string.m_test_started else R.string.m_started))
        handler.postDelayed({ rebuild(false) }, 600)
    }
}
