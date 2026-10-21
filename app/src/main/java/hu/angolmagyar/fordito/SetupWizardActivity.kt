package hu.angolmagyar.fordito

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Első indítás: egy képernyő = egy engedély. Nagy "Engedélyezés" és "Tovább" gomb, nincs görgetés.
 * Az utolsó lépés a modellek letöltése (ajánlott csomag egy koppintással).
 */
class SetupWizardActivity : AppCompatActivity() {

    private enum class Step { MIC, NOTIF, OVERLAY, BATTERY, MODELS }

    private lateinit var prefs: Prefs
    private lateinit var host: LinearLayout
    private var steps: List<Step> = emptyList()
    private var idx = 0

    private fun dp(v: Int) = Ui.dp(this, v)

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { next() }

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

    private fun done(s: Step) = when (s) {
        Step.MIC -> hasMic()
        Step.NOTIF -> hasNotif()
        Step.OVERLAY -> Settings.canDrawOverlays(this)
        Step.BATTERY -> hasBattery()
        Step.MODELS -> Engines.missingForLive(this, prefs) == null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        prefs.onboarded = true // egyszer fut le automatikusan
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
        // beállításokból visszatérve: ha megvan az engedély, megyünk tovább
        if (steps.isNotEmpty() && idx < steps.size && steps[idx] != Step.MODELS && done(steps[idx])) next()
    }

    private fun next() {
        idx++
        if (idx >= steps.size) {
            finish()
            return
        }
        show()
    }

    private fun show() {
        host.removeAllViews()
        val s = steps[idx]
        val (icon, title, text, primary) = when (s) {
            Step.MIC -> Four("🎤", "Mikrofon", "A beszéd felismeréséhez kell a mikrofon.", "Engedélyezés")
            Step.NOTIF -> Four("🔔", "Értesítések", "Futás közben ez tartja életben az appot, és innen állíthatod le.", "Engedélyezés")
            Step.OVERLAY -> Four("🎬", "Felirat a film fölött", "Engedd meg, hogy az app más appok (pl. videó) fölött mutassa a feliratot.", "Engedélyezés")
            Step.BATTERY -> Four("🔋", "Háttérben futás", "Kapcsold ki az akkumulátor-optimalizálást, hogy a rendszer ne állítsa le az appot hosszú film közben.", "Beállítás")
            Step.MODELS -> {
                val big = Engines.device(this).canRunLarge
                Four(
                    "⬇️", "Letöltendő modellek",
                    if (big) "Ajánlott csomag: nagy angol felismerő és fordítómodell (~190 MB). Wi-Fin egyből indul."
                    else "Ajánlott csomag: kis angol felismerő és fordítómodell (~100 MB). Wi-Fin egyből indul.",
                    "Letöltés most"
                )
            }
        }
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
            setPadding(0, dp(8), 0, dp(32))
        })
        host.addView(Ui.button(this, primary, true) { act(s) })
        host.addView(Ui.button(this, if (s == Step.MODELS) "Később" else "Tovább", false) { next() })
    }

    private data class Four(val a: String, val b: String, val c: String, val d: String)

    private fun act(s: Step) {
        when (s) {
            Step.MIC -> permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
            Step.NOTIF -> if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) else next()
            Step.OVERLAY -> startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
            Step.BATTERY -> try {
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
            Step.MODELS -> {
                startActivity(Intent(this, ModelsActivity::class.java).putExtra(ModelsActivity.EXTRA_AUTO, true))
                finish()
            }
        }
    }
}
