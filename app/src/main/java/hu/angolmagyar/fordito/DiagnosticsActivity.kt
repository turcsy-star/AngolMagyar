package hu.angolmagyar.fordito

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel

/**
 * Előkészítés / önellenőrzés: mi hiányzik az induláshoz, és mindegyik mellett a megoldás gombja.
 * Első indításkor magától megnyílik.
 */
class DiagnosticsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var column: LinearLayout
    private var translatorOk: Boolean? = null

    private fun dp(v: Int) = Ui.dp(this, v)

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { build() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        prefs.onboarded = true
        column = Ui.column(this).apply { setPadding(dp(16), dp(16), dp(16), dp(32)) }
        val scroll = Ui.scrollOf(this, column)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        build()
    }

    override fun onResume() {
        super.onResume()
        checkTranslator()
        build()
    }

    private fun checkTranslator() {
        try {
            val mgr = RemoteModelManager.getInstance()
            mgr.isModelDownloaded(TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build())
                .addOnSuccessListener { en ->
                    mgr.isModelDownloaded(TranslateRemoteModel.Builder(TranslateLanguage.HUNGARIAN).build())
                        .addOnSuccessListener { hu ->
                            val ok = en == true && hu == true
                            if (translatorOk != ok) {
                                translatorOk = ok
                                build()
                            }
                        }
                }
        } catch (_: Exception) {
        }
    }

    private class Row(val title: String, val ok: Boolean?, val detail: String, val action: String?, val run: (() -> Unit)?)

    private fun rows(): List<Row> {
        val list = ArrayList<Row>()
        val openModels = { startActivity(Intent(this, ModelsActivity::class.java)) }

        val missing = Engines.missingForLive(this, prefs)
        list.add(Row(
            "Felismerő modellek", missing == null,
            missing ?: "Az aktuális beállításhoz minden felismerő megvan.",
            "Modellek", openModels
        ))

        val huSys = SystemAsrSupport.onDeviceAvailable(this)
        val huVosk = ModelManager.isInstalled(this, ModelManager.HU_LARGE)
        list.add(Row(
            "Magyar felismerő",
            if (huVosk || huSys) true else false,
            when {
                huVosk -> "Saját magyar modell telepítve."
                huSys -> "Android beépített felismerő elérhető (az offline magyar csomagot a Modellek képernyőn ellenőrizheted)."
                !SystemAsrSupport.audioSourceSupported() -> "Android 13 vagy újabb kell."
                else -> "Nincs eszközön futó felismerő. A Google szolgáltatások hiányozhatnak."
            },
            "Modellek", openModels
        ))

        list.add(Row(
            "Fordítómodell (angol ↔ magyar)", translatorOk,
            when (translatorOk) {
                true -> "Letöltve, offline működik."
                false -> "Nincs letöltve."
                null -> "Ellenőrzés…"
            },
            "Modellek", openModels
        ))

        val overlay = Settings.canDrawOverlays(this)
        list.add(Row(
            "Megjelenítés más appok fölött", overlay,
            if (overlay) "Engedélyezve." else "Enélkül nem látszik a felirat a film fölött.",
            "Engedélyezés"
        ) {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })

        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        list.add(Row(
            "Mikrofon", mic,
            if (mic) "Engedélyezve." else "A hangfelismeréshez kell.",
            "Engedélyezés"
        ) { permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) })

        if (Build.VERSION.SDK_INT >= 33) {
            val notif = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            list.add(Row(
                "Értesítések", notif,
                if (notif) "Engedélyezve." else "A futás közbeni értesítéshez kell.",
                "Engedélyezés"
            ) { permLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) })
        }

        val battery = try {
            (getSystemService(POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(packageName)
        } catch (_: Exception) {
            false
        }
        list.add(Row(
            "Akkumulátor-optimalizálás kikapcsolva", battery,
            if (battery) "Rendben." else "Nélküle a rendszer pár perc után leállíthatja a háttérben futó appot.",
            "Beállítás"
        ) {
            BatterySettings.open(this)
        })

        val dev = Engines.device(this)
        list.add(Row(
            "Készülék", null,
            dev.summary + if (Engines.onUnmeteredNetwork(this)) " · Wi-Fi" else if (Engines.online(this)) " · mobilnet" else " · nincs net",
            null, null
        ))
        return list
    }

    private fun build() {
        column.removeAllViews()
        column.addView(Ui.button(this, "← Vissza", false) { finish() })
        column.addView(Ui.title(this, "Előkészítés"))
        val rows = rows()
        val bad = rows.count { it.ok == false }
        column.addView(
            Ui.body(
                this,
                if (bad == 0) "✓ Minden rendben, indulhat." else "$bad dolog hiányzik az induláshoz."
            ).apply {
                setTextColor(Ui.color(this@DiagnosticsActivity, if (bad == 0) R.color.ok else R.color.warn))
            }
        )
        for (r in rows) {
            val card = Ui.card(this)
            val mark = when (r.ok) {
                true -> "✔  "
                false -> "✖  "
                null -> "•  "
            }
            card.addView(TextView(this).apply {
                text = mark + r.title
                textSize = 16f
                setTextColor(
                    Ui.color(
                        this@DiagnosticsActivity,
                        when (r.ok) { true -> R.color.ok; false -> R.color.err; null -> R.color.textPrimary }
                    )
                )
            })
            card.addView(Ui.small(this, r.detail))
            if (r.ok != true && r.action != null && r.run != null) {
                card.addView(Ui.button(this, r.action, true) { r.run.invoke() })
            }
            column.addView(card)
        }
        column.addView(Ui.button(this, "Adatvédelem és mentés", false) {
            startActivity(Intent(this, PrivacyActivity::class.java))
        })
        column.addView(Ui.button(this, "Kész", true) { finish() })
    }
}
