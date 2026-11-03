package hu.angolmagyar.fordito
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
class SetupWizardActivity : AppCompatActivity() {
    private enum class Step { MIC, NOTIF, OVERLAY, BATTERY, MODELS }
    private lateinit var prefs: Prefs
    private lateinit var host: LinearLayout
    private var steps: List<Step> = emptyList()
    private var idx = 0
    private fun dp(v: Int) = Ui.dp(this, v)
    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { next() }
    private val ui = Handler(Looper.getMainLooper())
    private var dlBar: ProgressBar? = null
    private var dlInfo: TextView? = null
    private var dlPct: TextView? = null
    private var dlPause: AppCompatButton? = null
    private var dlRetry: AppCompatButton? = null
    private var polling = false
    private var sawRunning = false
    private var startedAt = 0L
    private var finishing = false
    private val poll = object : Runnable {
        override fun run() {
            if (!polling) return
            pollDownload()
            ui.postDelayed(this, 500)
        }
    }
    private fun hasMic() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
    private fun hasNotif() = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED
    private fun hasBattery() = try {
        (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
    } catch (_: Exception) {
        true
    }
    private fun modelsReady() = ModelManager.bestWhisper(this, null) != null
    private fun done(s: Step) = when (s) {
        Step.MIC -> hasMic()
        Step.NOTIF -> hasNotif()
        Step.OVERLAY -> Settings.canDrawOverlays(this)
        Step.BATTERY -> hasBattery()
        Step.MODELS -> modelsReady()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        prefs.onboarded = true
        host = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        setContentView(host)
        ViewCompat.setOnApplyWindowInsetsListener(host) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left + dp(24), b.top + dp(24), b.right + dp(24), b.bottom + dp(24))
            insets
        }
        steps = Step.values().filter { !done(it) }
        if (steps.isEmpty()) {
            finish()
            return
        }
        show()
    }
    override fun onResume() {
        super.onResume()
        if (steps.isEmpty() || idx >= steps.size) return
        if (steps[idx] == Step.MODELS) startPolling()
        else if (done(steps[idx])) next()
    }
    override fun onPause() {
        stopPolling()
        super.onPause()
    }
    private fun startPolling() {
        if (polling) return
        polling = true
        ui.post(poll)
    }
    private fun stopPolling() {
        polling = false
        ui.removeCallbacks(poll)
    }
    private fun next() {
        stopPolling()
        idx++
        if (idx >= steps.size) {
            finish()
            return
        }
        show()
        if (steps[idx] == Step.MODELS) startPolling()
    }
    private fun show() {
        host.removeAllViews()
        val s = steps[idx]
        if (s == Step.MODELS) {
            showModels()
            return
        }
        val (icon, title, text, primary) = when (s) {
            Step.MIC -> Four("🎤", "Mikrofon", "A beszéd felismeréséhez kell a mikrofon.", "Engedélyezés")
            Step.NOTIF -> Four("🔔", "Értesítések", "Futás közben ez tartja életben az appot, és innen állíthatod le.", "Engedélyezés")
            Step.OVERLAY -> Four("🎬", "Felirat a film fölött", "Engedd meg, hogy az app más appok (pl. videó) fölött mutassa a feliratot.", "Engedélyezés")
            Step.BATTERY -> Four("🔋", "Háttérben futás", "Kapcsold ki az akkumulátor-optimalizálást, hogy a rendszer ne állítsa le az appot hosszú film közben.", "Beállítás")
            Step.MODELS -> Four("", "", "", "")
        }
        addHeader(icon, title, text)
        host.addView(Ui.button(this, primary, true) { act(s) })
        host.addView(Ui.button(this, "Tovább", false) { next() })
    }
    private fun addHeader(icon: String, title: String, text: String) {
        host.addView(TextView(this).apply {
            this.text = "${idx + 1} / ${steps.size}"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Ui.color(this@SetupWizardActivity, R.color.textSecondary))
        })
        host.addView(TextView(this).apply {
            this.text = icon
            textSize = 64f
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(16))
        })
        host.addView(Ui.title(this, title, 26f).apply { gravity = Gravity.CENTER })
        host.addView(Ui.body(this, text).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(24))
        })
    }
    // ---- Modellek: nincs választás, a telepítő magától letölti a szükségeset, és mutatja az állását ----
    private fun showModels() {
        addHeader(
            "⬇️", "Modellek letöltése",
            "A pontos felismeréshez és fordításhoz egyszeri letöltés kell (kb. 1,2 GB). " +
                "Magától elindul, utána internet nélkül is működik. Wi-Fin ajánlott."
        )
        val (bar, label) = Ui.progressRow(this)
        bar.isIndeterminate = true
        label.gravity = Gravity.CENTER
        host.addView(bar)
        host.addView(label)
        val pct = Ui.title(this, "", 22f).apply { gravity = Gravity.CENTER }
        host.addView(pct)
        dlBar = bar
        dlInfo = label
        dlPct = pct
        host.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(1, dp(16)) })
        val pause = Ui.button(this, "Szünet", false) {
            if (ModelDownloadService.paused) ModelDownloadService.resume(this) else ModelDownloadService.pause(this)
        }
        val retry = Ui.button(this, "Újrapróbálom", true) { beginDownload() }
        retry.visibility = View.GONE
        pause.visibility = View.GONE
        dlPause = pause
        dlRetry = retry
        host.addView(retry)
        host.addView(pause)
        host.addView(Ui.button(this, "Folytatás a háttérben", false) {
            Toast.makeText(this, "A letöltés a háttérben megy tovább (értesítésben látod).", Toast.LENGTH_LONG).show()
            finish()
        })
        beginDownload()
    }
    private fun beginDownload() {
        sawRunning = ModelDownloadService.running
        startedAt = SystemClock.elapsedRealtime()
        dlRetry?.visibility = View.GONE
        dlBar?.isIndeterminate = true
        if (ModelDownloadService.running) return
        if (!Engines.online(this)) {
            showProblem("Nincs internetkapcsolat. Kapcsolódj Wi-Fi-hez vagy mobilnethez, majd nyomd meg az Újrapróbálom gombot.")
            return
        }
        dlInfo?.text = "Letöltés indítása…"
        try {
            ModelDownloadService.start(this, ModelManager.DEFAULT_IDS)
        } catch (t: Throwable) {
            showProblem("A letöltés nem indítható: ${t.message ?: t.javaClass.simpleName}")
        }
    }
    private fun showProblem(msg: String) {
        dlBar?.isIndeterminate = false
        dlBar?.progress = 0
        dlPct?.text = ""
        dlInfo?.text = msg
        dlPause?.visibility = View.GONE
        dlRetry?.visibility = View.VISIBLE
    }
    private fun pollDownload() {
        if (finishing || steps.isEmpty() || idx >= steps.size || steps[idx] != Step.MODELS) return
        val running = ModelDownloadService.running
        val paused = ModelDownloadService.paused
        val finished = ModelDownloadService.done
        val failed = ModelDownloadService.failedMsg
        if (running) sawRunning = true
        val waited = SystemClock.elapsedRealtime() - startedAt
        when {
            running -> {
                val p = ModelDownloadService.pct
                dlRetry?.visibility = View.GONE
                dlPause?.visibility = View.VISIBLE
                dlPause?.text = if (paused) "Folytatás" else "Szünet"
                val msg = ModelDownloadService.message
                if (p in 1..100) {
                    dlBar?.isIndeterminate = false
                    dlBar?.progress = p
                    dlPct?.text = "$p %"
                } else {
                    dlBar?.isIndeterminate = !paused
                    dlPct?.text = ""
                }
                dlInfo?.text = if (msg.isBlank()) "Letöltés…" else msg
            }
            sawRunning && finished && failed != null -> showProblem("Letöltési hiba: $failed")
            sawRunning && finished && message().startsWith("Megszakítva") ->
                showProblem("A letöltés megszakadt. A félbehagyott rész megmarad, folytatható.")
            (sawRunning && finished) || modelsReady() -> finishSetup()
            waited > 10_000L -> showProblem("A letöltés nem indult el. Ellenőrizd az internetet és az értesítési engedélyt, majd próbáld újra.")
        }
    }
    private fun message(): String = ModelDownloadService.message
    private fun finishSetup() {
        if (finishing) return
        finishing = true
        stopPolling()
        dlBar?.isIndeterminate = false
        dlBar?.progress = 100
        dlPct?.text = "100 %"
        dlInfo?.text = "Kész ✓ – minden letöltődött."
        Toast.makeText(this, "A modellek letöltve, mehet a munka.", Toast.LENGTH_LONG).show()
        ui.postDelayed({ finish() }, 900)
    }
    private data class Four(val a: String, val b: String, val c: String, val d: String)
    private fun act(s: Step) {
        when (s) {
            Step.MIC -> permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            Step.NOTIF -> if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) else next()
            Step.OVERLAY -> startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            Step.BATTERY -> BatterySettings.open(this)
            Step.MODELS -> Unit
        }
    }
}
