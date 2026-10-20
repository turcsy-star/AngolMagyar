package hu.angolmagyar.fordito

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionSupport
import android.speech.RecognitionSupportCallback
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Modellek: jelölőnégyzetes választó, készülék-felmérés alapján javasolt kiválasztással,
 * "OK" gomb – Wi-Fin egyből letölt, mobilneten rákérdez.
 */
class ModelsActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout
    private val main = Handler(Looper.getMainLooper())

    /** Kijelölt (letöltendő) elemek: Vosk spec id-k és "mlkit". */
    private val selected = LinkedHashSet<String>()
    private var firstBuild = true
    private var mlInstalled = false
    private var mlChecked = false
    private var huStatus = "Ellenőrzés…"
    private var huInstalled = false
    private var huCanDownload = false
    private var downloading = false
    private var progressBar: ProgressBar? = null
    private var progressText: TextView? = null

    private fun dp(v: Int) = Ui.dp(this, v)

    private data class Item(val spec: ModelManager.Spec, val mb: Int, val note: String)

    private val items: List<Item> by lazy {
        listOf(
            Item(ModelManager.SMALL, 40, "gyors, takarékos"),
            Item(ModelManager.LARGE, 130, "pontosabb, filmekhez ajánlott"),
            Item(ModelManager.DE_SMALL, 44, ""),
            Item(ModelManager.FR_SMALL, 40, ""),
            Item(ModelManager.ES_SMALL, 38, ""),
            Item(ModelManager.IT_SMALL, 47, ""),
            Item(ModelManager.UK_SMALL, 137, "")
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        root = Ui.column(this).apply { setPadding(dp(16), dp(16), dp(16), dp(32)) }
        val scroll = Ui.scrollOf(this, root)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }
        build()
        refreshStatuses()
    }

    override fun onResume() {
        super.onResume()
        if (!downloading) refreshStatuses()
    }

    // ----------------------------------------------------------------- állapotok

    private fun refreshStatuses() {
        // ML Kit fordítómodell: mindkét nyelv letöltve?
        try {
            val mgr = RemoteModelManager.getInstance()
            val en = mgr.isModelDownloaded(TranslateRemoteModel.Builder(TranslateLanguage.ENGLISH).build())
            en.addOnSuccessListener { enOk ->
                val hu = mgr.isModelDownloaded(TranslateRemoteModel.Builder(TranslateLanguage.HUNGARIAN).build())
                hu.addOnSuccessListener { huOk ->
                    mlInstalled = enOk == true && huOk == true
                    mlChecked = true
                    if (!downloading) build()
                }
            }
        } catch (_: Exception) {
        }
        checkHungarian()
    }

    private fun checkHungarian() {
        if (!SystemAsrSupport.audioSourceSupported()) {
            huStatus = "Android 13 vagy újabb kell."
            return
        }
        if (!SystemAsrSupport.onDeviceAvailable(this)) {
            huStatus = "Ezen a telefonon nincs eszközön futó beszédfelismerő (Google szolgáltatás hiányzik vagy tiltott)."
            huInstalled = false
            huCanDownload = false
            if (!downloading) build()
            return
        }
        try {
            val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
            r.checkRecognitionSupport(intent, mainExecutor, object : RecognitionSupportCallback {
                override fun onSupportResult(s: RecognitionSupport) {
                    val inst = s.installedOnDeviceLanguages.any { it.startsWith("hu") }
                    val pend = s.pendingOnDeviceLanguages.any { it.startsWith("hu") }
                    val sup = s.supportedOnDeviceLanguages.any { it.startsWith("hu") }
                    huInstalled = inst
                    huCanDownload = sup && !inst
                    huStatus = when {
                        inst -> "✓ Telepítve, offline működik."
                        pend -> "Letöltés folyamatban… (pár perc, majd frissítsd az állapotot)"
                        sup -> "Letölthető (kb. 50–100 MB)."
                        else -> "Ez a felismerő nem támogatja a magyart offline."
                    }
                    try { r.destroy() } catch (_: Exception) {}
                    if (!downloading) build()
                }

                override fun onError(error: Int) {
                    huStatus = "Állapot nem kérdezhető le (hiba $error). Próbáld a letöltést."
                    huCanDownload = true
                    try { r.destroy() } catch (_: Exception) {}
                    if (!downloading) build()
                }
            })
        } catch (t: Throwable) {
            huStatus = "Állapot nem kérdezhető le: ${t.message}"
        }
    }

    private fun triggerHuDownload() {
        if (Build.VERSION.SDK_INT < 33) return
        try {
            val r = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hu-HU")
            r.triggerModelDownload(intent)
            Toast.makeText(
                this,
                "A magyar csomag letöltése elindult a háttérben. Pár perc múlva frissítsd az állapotot.",
                Toast.LENGTH_LONG
            ).show()
            main.postDelayed({
                try { r.destroy() } catch (_: Exception) {}
                refreshStatuses()
            }, 4000)
        } catch (t: Throwable) {
            Toast.makeText(this, "Nem indítható: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------------ felépítés

    private fun recommendedIds(): List<String> {
        val d = Engines.device(this)
        val ids = ArrayList<String>()
        ids.add(if (d.canRunLarge) ModelManager.LARGE.id else ModelManager.SMALL.id)
        ids.add("mlkit")
        return ids
    }

    private fun build() {
        root.removeAllViews()
        val dev = Engines.device(this)
        val rec = recommendedIds()

        if (firstBuild) {
            firstBuild = false
            for (id in rec) {
                if (id == "mlkit") { if (!mlInstalled) selected.add(id) }
                else if (!ModelManager.isInstalled(this, ModelManager.byId(id))) selected.add(id)
            }
        }

        root.addView(Ui.button(this, "← Vissza", false) { finish() })
        root.addView(Ui.title(this, "Modellek"))

        // --- ajánlás
        val recCard = Ui.card(this)
        recCard.addView(Ui.title(this, "Ajánlás ehhez a telefonhoz", 17f))
        recCard.addView(Ui.small(this, dev.summary))
        val big = if (dev.canRunLarge)
            "Ez a telefon bőven elbírja a nagy angol modellt (pontosabb felirat). Takarékos módban az app magától a kicsire vált."
        else
            "Ehhez a telefonhoz a kis angol modell ajánlott (gyors, kevésbé melegít)."
        recCard.addView(Ui.body(this, big))
        recCard.addView(Ui.button(this, "Ajánlott kijelölése", false) {
            selected.clear()
            for (id in rec) {
                if (id == "mlkit") { if (!mlInstalled) selected.add(id) }
                else if (!ModelManager.isInstalled(this, ModelManager.byId(id))) selected.add(id)
            }
            build()
        })
        root.addView(recCard)

        // --- Vosk felismerők
        val vCard = Ui.card(this)
        vCard.addView(Ui.title(this, "Felismerők (offline)", 17f))
        vCard.addView(Ui.small(this, "Jelöld be, amit használni szeretnél, majd nyomj az OK gombra."))
        for (it in items) {
            val installed = ModelManager.isInstalled(this, it.spec)
            val cb = CheckBox(this).apply {
                val name = it.spec.label.substringBefore("(").trim()
                text = buildString {
                    append(name).append(" (~").append(it.mb).append(" MB)")
                    if (it.note.isNotBlank()) append(" – ").append(it.note)
                    if (installed) append("  ✓ telepítve")
                }
                minHeight = dp(48)
                isChecked = installed || selected.contains(it.spec.id)
                isEnabled = !installed && !downloading
                setOnCheckedChangeListener { _, c ->
                    if (c) selected.add(it.spec.id) else selected.remove(it.spec.id)
                    updateOkButton()
                }
            }
            vCard.addView(cb)
        }
        // használt angol modell
        val enInstalledSmall = ModelManager.isInstalled(this, ModelManager.SMALL)
        val enInstalledLarge = ModelManager.isInstalled(this, ModelManager.LARGE)
        if (enInstalledSmall || enInstalledLarge) {
            vCard.addView(Ui.body(this, "Használt angol modell:"))
            val g = RadioGroup(this)
            val rs = RadioButton(this).apply {
                id = View.generateViewId(); text = "Kicsi (gyors)"; isEnabled = enInstalledSmall
                isChecked = prefs.modelId != "large"
                setOnClickListener { prefs.modelId = "small" }
            }
            val rl = RadioButton(this).apply {
                id = View.generateViewId(); text = "Nagy (pontosabb)"; isEnabled = enInstalledLarge
                isChecked = prefs.modelId == "large"
                setOnClickListener { prefs.modelId = "large" }
            }
            g.addView(rs); g.addView(rl)
            vCard.addView(g)
        }
        root.addView(vCard)

        // --- magyar felismerő
        val hCard = Ui.card(this)
        hCard.addView(Ui.title(this, "Magyar felismerő", 17f))
        hCard.addView(Ui.small(this,
            "A magyar beszédfelismerést az Android beépített (Google) felismerője végzi, mert " +
                "a Vosk-nak nincs magyar modellje. A csomag a telefonon fut, a hang nem kerül ki."))
        hCard.addView(Ui.body(this, huStatus))
        if (huCanDownload && !huInstalled) {
            hCard.addView(Ui.button(this, "Magyar csomag letöltése", true) { triggerHuDownload() })
        }
        hCard.addView(Ui.button(this, "Állapot frissítése", false) {
            huStatus = "Ellenőrzés…"
            build()
            checkHungarian()
        })
        hCard.addView(Ui.button(this, "Hangbeviteli beállítások megnyitása", false) {
            try {
                startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            } catch (_: Exception) {
                Toast.makeText(this, "Nem nyitható meg.", Toast.LENGTH_SHORT).show()
            }
        })
        hCard.addView(SwitchCompat(this).apply {
            text = "Online magyar felismerés engedélyezése (tartalék; a hang a Google-hoz kerül)"
            isChecked = prefs.allowOnlineAsr
            minHeight = dp(52)
            setOnCheckedChangeListener { _, c -> prefs.allowOnlineAsr = c }
        })
        root.addView(hCard)

        // --- fordítás
        val tCard = Ui.card(this)
        tCard.addView(Ui.title(this, "Fordítás (ML Kit)", 17f))
        val tcb = CheckBox(this).apply {
            text = "Angol ↔ Magyar fordítómodell (~60 MB)" + if (mlInstalled) "  ✓ telepítve" else if (!mlChecked) "  (ellenőrzés…)" else ""
            minHeight = dp(48)
            isChecked = mlInstalled || selected.contains("mlkit")
            isEnabled = !mlInstalled && !downloading
            setOnCheckedChangeListener { _, c ->
                if (c) selected.add("mlkit") else selected.remove("mlkit")
                updateOkButton()
            }
        }
        tCard.addView(tcb)
        root.addView(tCard)

        // --- letöltés
        val dCard = Ui.card(this)
        val unmetered = Engines.onUnmeteredNetwork(this)
        dCard.addView(Ui.small(this,
            if (unmetered) "Wi-Fi kapcsolat: a letöltés egyből indulhat."
            else if (Engines.online(this)) "Nincs Wi-Fi (mobilnet) – letöltés előtt rákérdezek."
            else "Nincs internet."))
        val (bar, label) = Ui.progressRow(this)
        progressBar = bar
        progressText = label
        bar.visibility = View.GONE
        label.visibility = View.GONE
        dCard.addView(bar)
        dCard.addView(label)
        okButton = Ui.button(this, okLabel(), true) { onOk() }
        dCard.addView(okButton!!)
        root.addView(dCard)
    }

    private var okButton: android.widget.Button? = null

    private fun pendingIds(): List<String> = selected.filter { id ->
        if (id == "mlkit") !mlInstalled else !ModelManager.isInstalled(this, ModelManager.byId(id))
    }

    private fun totalMb(ids: List<String>): Int = ids.sumOf { id ->
        if (id == "mlkit") 60 else items.firstOrNull { it.spec.id == id }?.mb ?: 0
    }

    private fun okLabel(): String {
        val p = pendingIds()
        return if (p.isEmpty()) "OK" else "OK – letöltés (~${totalMb(p)} MB)"
    }

    private fun updateOkButton() {
        okButton?.text = okLabel()
    }

    private fun onOk() {
        if (downloading) return
        val ids = pendingIds()
        if (ids.isEmpty()) {
            finish()
            return
        }
        if (!Engines.online(this)) {
            Toast.makeText(this, "Nincs internetkapcsolat.", Toast.LENGTH_LONG).show()
            return
        }
        if (Engines.onUnmeteredNetwork(this)) {
            startDownload(ids)
        } else {
            AlertDialog.Builder(this)
                .setTitle("Nincs Wi-Fi")
                .setMessage("Mobilnetes letöltés: kb. ${totalMb(ids)} MB. Folytassam?")
                .setPositiveButton("Letöltés mobilneten") { _, _ -> startDownload(ids) }
                .setNegativeButton("Mégse", null)
                .show()
        }
    }

    private fun startDownload(ids: List<String>) {
        downloading = true
        build()
        progressBar?.visibility = View.VISIBLE
        progressText?.visibility = View.VISIBLE
        okButton?.isEnabled = false
        val app = applicationContext
        Thread {
            val failed = ArrayList<String>()
            for ((idx, id) in ids.withIndex()) {
                try {
                    if (id == "mlkit") {
                        post(idx, ids.size, 0, "Fordítómodell letöltése…")
                        downloadTranslator()
                    } else {
                        val spec = ModelManager.byId(id)
                        ModelManager.download(app, spec) { pct, msg -> post(idx, ids.size, pct, msg) }
                        if (id == ModelManager.LARGE.id) prefs.modelId = "large"
                    }
                } catch (e: Exception) {
                    failed.add("$id: ${e.message}")
                }
            }
            main.post {
                downloading = false
                selected.clear()
                if (failed.isEmpty()) {
                    Toast.makeText(this, "Kész, minden letöltődött.", Toast.LENGTH_LONG).show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Letöltési hiba")
                        .setMessage(failed.joinToString("\n"))
                        .setPositiveButton("OK", null)
                        .show()
                }
                build()
                refreshStatuses()
            }
        }.start()
    }

    private fun post(idx: Int, total: Int, pct: Int, msg: String) {
        main.post {
            progressBar?.isIndeterminate = false
            progressBar?.progress = pct.coerceIn(0, 100)
            progressText?.text = "(${idx + 1}/$total) $msg"
        }
    }

    private fun downloadTranslator() {
        val t = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.HUNGARIAN)
                .build()
        )
        val latch = CountDownLatch(1)
        var err: String? = null
        t.downloadModelIfNeeded(DownloadConditions.Builder().build())
            .addOnSuccessListener { latch.countDown() }
            .addOnFailureListener { e -> err = e.message ?: "ismeretlen hiba"; latch.countDown() }
        val done = latch.await(10, TimeUnit.MINUTES)
        t.close()
        if (!done) throw IllegalStateException("időtúllépés")
        err?.let { throw IllegalStateException(it) }
    }
}
