package hu.angolmagyar.fordito

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Adatvédelem: mi marad a telefonon, mi kerül ki; mentés, visszaállítás, törlés. */
class PrivacyActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private fun dp(v: Int) = Ui.dp(this, v)

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
            if (uri == null) return@registerForActivityResult
            Thread {
                try {
                    val n = contentResolver.openOutputStream(uri)?.use { BackupHelper.export(this, it) } ?: 0
                    runOnUiThread { Toast.makeText(this, "Mentve ($n átirat + beállítások).", Toast.LENGTH_LONG).show() }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "Mentési hiba: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            Thread {
                try {
                    val n = contentResolver.openInputStream(uri)?.use { BackupHelper.import(this, it) } ?: 0
                    runOnUiThread {
                        Toast.makeText(
                            this, "Visszaállítva ($n átirat). Indítsd újra az appot a beállításokhoz.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } catch (e: Exception) {
                    runOnUiThread { Toast.makeText(this, "Visszaállítási hiba: ${e.message}", Toast.LENGTH_LONG).show() }
                }
            }.start()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val column = Ui.column(this).apply { setPadding(dp(16), dp(16), dp(16), dp(32)) }
        val scroll = Ui.scrollOf(this, column)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        column.addView(Ui.button(this, "← Vissza", false) { finish() })
        column.addView(Ui.title(this, "Adatvédelem és mentés"))

        val c1 = Ui.card(this)
        c1.addView(Ui.title(this, "Mi marad a telefonon?", 16f))
        c1.addView(Ui.body(this,
            "• A hang feldolgozása a telefonon történik (angol: Vosk; magyar: az Android beépített " +
                "felismerője, offline csomaggal).\n• Az átiratok és a hangfelvételek csak a telefon belső tárában vannak.\n" +
                "• A fordítás offline (ML Kit)."))
        column.addView(c1)

        val c2 = Ui.card(this)
        c2.addView(Ui.title(this, "Mi kerül ki a hálózatra?", 16f))
        c2.addView(Ui.body(this,
            "• Modellek letöltése (Vosk, ML Kit), ez csak letöltés, hang nem megy ki.\n" +
                "• Csak ha te engedélyezed: online magyar felismerés, ilyenkor a hang a Google szervereire kerül."))
        c2.addView(SwitchCompat(this).apply {
            text = "Online magyar felismerés engedélyezése (tartalék)"
            isChecked = prefs.allowOnlineAsr
            minHeight = dp(52)
            setOnCheckedChangeListener { _, c -> prefs.allowOnlineAsr = c }
        })
        column.addView(c2)

        val c3 = Ui.card(this)
        c3.addView(Ui.title(this, "Mentés és visszaállítás", 16f))
        c3.addView(Ui.small(this, "Egyetlen zip: beállítások + minden átirat. Telefoncseréhez vagy biztonsági másolatnak."))
        c3.addView(Ui.button(this, "Mentés fájlba…", true) {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            exportLauncher.launch("angolmagyar_mentes_$stamp.zip")
        })
        c3.addView(Ui.button(this, "Visszaállítás fájlból…", false) {
            importLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
        })
        column.addView(c3)

        val c4 = Ui.card(this)
        c4.addView(Ui.title(this, "Törlés", 16f))
        c4.addView(Ui.button(this, "Minden átirat törlése", false) {
            AlertDialog.Builder(this)
                .setTitle("Minden átirat törlése?")
                .setMessage("Az átiratok és a kuka tartalma végleg törlődik. A beállítások megmaradnak.")
                .setPositiveButton("Törlés") { _, _ ->
                    val n = BackupHelper.deleteAllTranscripts(this)
                    Toast.makeText(this, "$n fájl törölve.", Toast.LENGTH_LONG).show()
                }
                .setNegativeButton("Mégse", null)
                .show()
        })
        column.addView(c4)
    }
}
