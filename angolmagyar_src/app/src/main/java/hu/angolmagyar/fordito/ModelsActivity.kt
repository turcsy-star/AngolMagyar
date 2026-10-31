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
import android.view.WindowManager
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
    private var platProgress: hu.angolmagyar.fordito.ui.processing.PlatinumProgressView? = null
    private var pauseBtn: android.widget.Button? = null
    private var cancelBtn: android.widget.Button? = null
    private var showMoreLangs = false

    // letöltési sor
    private var paused = false
    private var pausing = false
    private var cancelled = false
    @Volatile private var queue: List<String> = emptyList()
    @Volatile private var queueTotal = 0
    @Volatile private var completedBytes = 0L
    @Volatile private var totalEstBytes = 0L
    @Volatile private var currentIsMl = false
    private var ctl = ModelManager.DlControl()
    private var progPct = 0
    private var progMsg = ""
    private var dlListener: ((Int, String, Boolean, Boolean) -> Unit)? = null

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
        attachDlListener()
        if (ModelDownloadService.running) {
            downloading = true
            paused = ModelDownloadService.paused
            progPct = ModelDownloadService.pct
            progMsg = ModelDownloadService.message
            build()
        }
        if (intent.getBooleanExtra(EXTRA_AUTO, false) && savedInstanceState == null) {
            main.postDelayed({ if (!downloading && !isDestroyed) onOk() }, 700)
        }
    }

    companion object {
        const val EXTRA_AUTO = "auto_download"
    }

    override fun onDestroy() {
        // Letöltés a ModelDownloadService-ben fut – képernyőzár nem szakítja meg.
        dlListener?.let { ModelDownloadService.removeListener(it) }
        dlListener = null
        super.onDestroy()
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
        } catch (e: Exception) { DiagLog.swallow("ModelsActivity", e) }
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
                    try { r.destroy() } catch (e: Exception) { DiagLog.swallow("ModelsActivity", e) }
                    if (!downloading) build()
                }

                override fun onError(error: Int) {
                    huStatus = "Állapot nem kérdezhető le (hiba $error). Próbáld a letöltést."
                    huCanDownload = true
                    try { r.destroy() } catch (e: Exception) { DiagLog.swallow("ModelsActivity", e) }
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
                try { r.destroy() } catch (e: Exception) { DiagLog.swallow("ModelsActivity", e) }
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
        if (WhisperNative.available() && ModelManager.WHISPER.none { ModelManager.isInstalled(this, it) }) {
            ids.add(ModelManager.WH_LARGE.id)
        }
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
        root.addView(Ui.heroHeader(this, "AI MODEL CENTER", "Felismerő modellek", "Offline beszédfelismerés és fordítás. Válassz modellt a készüléked teljesítményéhez igazítva."))
        root.addView(Ui.sectionLabel(this, "Elérhető motorok"))

        // --- Vosk felismerők
        val vCard = Ui.card(this)
        vCard.addView(Ui.title(this, "Felismerők (offline)", 17f))
        vCard.addView(Ui.small(this, "Jelöld be, amit használni szeretnél, majd nyomj az OK gombra."))
        val dev2 = dev
        for ((idx, it) in items.withIndex()) {
            if (idx >= 2 && !showMoreLangs) continue
            val installed = ModelManager.isInstalled(this, it.spec)
            val part = if (installed) 0L else ModelManager.partialBytes(this, it.spec)
            val cb = CheckBox(this).apply {
                val name = it.spec.label.substringBefore("(").trim()
                val note = if (it.spec.id == ModelManager.LARGE.id && dev2.canRunLarge)
                    "pontosabb, ajánlott ennek a telefonnak" else it.note
                text = buildString {
                    append(name).append(" (~").append(it.mb).append(" MB)")
                    if (note.isNotBlank()) append(" – ").append(note)
                    if (installed) append("  ✓ telepítve")
                    else if (part > 0) append("  ⏸ félbehagyva, folytatható")
                }
                minHeight = dp(48)
                textSize = 15f
                setTextColor(android.content.res.ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_enabled),
                        intArrayOf(-android.R.attr.state_enabled)
                    ),
                    intArrayOf(
                        Ui.color(this@ModelsActivity, R.color.textPrimary),
                        Ui.color(this@ModelsActivity, R.color.textSecondary)
                    )
                ))
                isChecked = installed || selected.contains(it.spec.id)
                isEnabled = !installed && !downloading
                setOnCheckedChangeListener { _, c ->
                    if (c) selected.add(it.spec.id) else selected.remove(it.spec.id)
                    updateOkButton()
                }
            }
            vCard.addView(cb)
        }
        vCard.addView(Ui.button(this, if (showMoreLangs) "Kevesebb nyelv" else "További nyelvek (német, francia, spanyol…)", false) {
            showMoreLangs = !showMoreLangs
            build()
        })
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

        // --- Whisper (offline, magyarul is)
        val wCard = Ui.card(this)
        wCard.addView(Ui.title(this, "Whisper felismerő (offline, magyarul is)", 17f))
        if (!WhisperNative.available()) {
            wCard.addView(Ui.small(this,
                "A Whisper ezen a telefonon nem használható: " + WhisperNative.error().ifBlank { "natív könyvtár hiányzik" }))
        } else {
            wCard.addView(Ui.small(this,
                "Ugyanaz a technika, mint a PC-s változatban: a hang a telefonon marad, a magyar felismerés pontos. " +
                    "A nagyobb modell pontosabb, de lassabb és több memóriát kér. Ajánlott: " +
                    "large-v3 (a legpontosabb)."))
            for (spec in ModelManager.WHISPER) {
                val installed = ModelManager.isInstalled(this, spec)
                val part = if (installed) 0L else ModelManager.partialBytes(this, spec)
                val rec = spec.id == ModelManager.WH_LARGE.id
                val cb = CheckBox(this).apply {
                    text = buildString {
                        append(spec.label)
                        if (rec) append(" – ajánlott")
                        if (installed) append("  ✓ telepítve")
                        else if (part > 0) append("  ⏸ félbehagyva, folytatható")
                    }
                    minHeight = dp(48)
                    textSize = 15f
                    setTextColor(android.content.res.ColorStateList(
                        arrayOf(
                            intArrayOf(android.R.attr.state_enabled),
                            intArrayOf(-android.R.attr.state_enabled)
                        ),
                        intArrayOf(
                            Ui.color(this@ModelsActivity, R.color.textPrimary),
                            Ui.color(this@ModelsActivity, R.color.textSecondary)
                        )
                    ))
                    isChecked = installed || selected.contains(spec.id)
                    isEnabled = !installed && !downloading
                    setOnCheckedChangeListener { _, c ->
                        if (c) selected.add(spec.id) else selected.remove(spec.id)
                        updateOkButton()
                    }
                }
                wCard.addView(cb)
            }
            val instW = ModelManager.WHISPER.filter { ModelManager.isInstalled(this, it) }
            if (instW.isNotEmpty()) {
                wCard.addView(Ui.body(this, "Használt Whisper modell:"))
                val cur = ModelManager.bestWhisper(this, AsrPrefs.whisperId(this))?.id
                val gw = RadioGroup(this)
                for (spec in instW) {
                    gw.addView(RadioButton(this).apply {
                        id = View.generateViewId()
                        text = spec.label.substringBefore("(").trim()
                        isChecked = spec.id == cur
                        setOnClickListener { AsrPrefs.setWhisperId(this@ModelsActivity, spec.id) }
                    })
                }
                wCard.addView(gw)
                wCard.addView(Ui.body(this, "Melyik felismerő dolgozzon a fájlokon?"))
                val ge = RadioGroup(this)
                val engs = listOf(
                    AsrPrefs.AUTO to "Automatikus (Whisper, ha van)",
                    AsrPrefs.WHISPER to "Mindig Whisper",
                    AsrPrefs.VOSK to "Vosk (gyors, pontatlanabb)"
                )
                for ((v, label) in engs) {
                    ge.addView(RadioButton(this).apply {
                        id = View.generateViewId()
                        text = label
                        isChecked = AsrPrefs.engine(this@ModelsActivity) == v
                        setOnClickListener { AsrPrefs.setEngine(this@ModelsActivity, v) }
                    })
                }
                wCard.addView(ge)
                wCard.addView(SwitchCompat(this).apply {
                    text = "Pontosabb keresés (beam 5, kb. 2–3× lassabb)"
                    isChecked = AsrPrefs.beam(this@ModelsActivity) > 1
                    minHeight = dp(52)
                    setOnCheckedChangeListener { _, c -> AsrPrefs.setBeam(this@ModelsActivity, if (c) 5 else 1) }
                })
            }
        }
        root.addView(wCard)

        // --- magyar felismerő
        val hCard = Ui.card(this)
        hCard.addView(Ui.title(this, "Magyar felismerő", 17f))
        val whisperOk = try { Engines.whisperSpec(this) != null } catch (_: Exception) { false }
        if (whisperOk) {
            // Ha a Whisper működik, nincs szükség a Google-csomagra: ne jelenjen meg félrevezető "nem támogatja" üzenet.
            hCard.addView(Ui.body(this, "✓ A magyar felismerést a Whisper végzi – offline, a telefonon."))
            hCard.addView(Ui.small(this,
                "Nincs szükség másik csomagra. Az Android beépített (Google) felismerője csak tartalék, " +
                    "ha a Whisper nem érhető el; ilyenkor a Modellek képernyő jelzi."))
        } else {
            hCard.addView(Ui.small(this,
                "A Whisper modell nincs telepítve, ezért a magyar felismerést az Android beépített (Google) " +
                    "felismerője végezné (tartalék). A csomag a telefonon fut, a hang nem kerül ki. " +
                    "Pontosabb és egységes megoldás: töltsd le a Whisper modellt fent."))
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
        }
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
        if (!downloading) {
            val unmetered = Engines.onUnmeteredNetwork(this)
            dCard.addView(Ui.small(this,
                if (unmetered) "Wi-Fi: a letöltés egyből indul."
                else if (Engines.online(this)) "Nincs Wi-Fi (mobilnet) – letöltés előtt rákérdezek, és bármikor szüneteltethető."
                else "Nincs internet."))
        }
        val (bar, label) = Ui.progressRow(this)
        progressBar = bar
        progressText = label
        if (downloading) {
            bar.isIndeterminate = progPct < 0
            if (progPct >= 0) bar.progress = progPct
            label.text = progMsg
        } else {
            bar.visibility = View.GONE
            label.visibility = View.GONE
        }
        dCard.addView(bar)
        dCard.addView(label)
        platProgress = null
        if (downloading && Prefs(this).platinumTheme) {
            // Platina haladássáv a sima helyett (a régi sáv és felirat rejtve marad, de tovább frissül)
            bar.visibility = View.GONE
            label.visibility = View.GONE
            platProgress = hu.angolmagyar.fordito.ui.processing.PlatinumProgressView(this).also {
                it.set(progPct, progMsg)
                dCard.addView(it, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (10 * resources.displayMetrics.density).toInt() })
            }
        }
        if (downloading) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            pauseBtn = Ui.button(this, "", true) { onPauseResume() }
            cancelBtn = Ui.button(this, "Mégse", false) { onCancel() }
            row.addView(pauseBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(6) })
            row.addView(cancelBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
            dCard.addView(row)
            updatePauseUi()
            okButton = null
        } else {
            okButton = Ui.button(this, okLabel(), true) { onOk() }
            dCard.addView(okButton!!)
        }
        root.addView(dCard)
    }

    private var okButton: android.widget.Button? = null

    private fun pendingIds(): List<String> = selected.filter { id ->
        if (id == "mlkit") !mlInstalled else !ModelManager.isInstalled(this, ModelManager.byId(id))
    }

    private fun totalMb(ids: List<String>): Int = ids.sumOf { id ->
        if (id == "mlkit") 60
        else items.firstOrNull { it.spec.id == id }?.mb ?: ModelManager.byId(id).approxMb
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
        queue = ids.sortedBy { if (it == "mlkit") 1 else 0 }
        queueTotal = queue.size
        completedBytes = 0L
        totalEstBytes = totalMb(queue) * MB
        downloading = true
        cancelled = false
        pausing = false
        paused = false
        progPct = -1
        progMsg = "Indítás… (előtérben, képernyőzár után is folytatódik)"
        build()
        attachDlListener()
        ModelDownloadService.start(this, queue)
    }

    private fun attachDlListener() {
        dlListener?.let { ModelDownloadService.removeListener(it) }
        val listener: (Int, String, Boolean, Boolean) -> Unit = { p, msg, isPaused, finished ->
            main.post {
                if (isDestroyed) return@post
                progPct = p
                progMsg = msg
                paused = isPaused
                pausing = false
                progressBar?.apply {
                    isIndeterminate = p < 0
                    if (p >= 0) progress = p.coerceIn(0, 100)
                }
                progressText?.text = msg
                platProgress?.set(p, msg)
                updatePauseUi()
                if (finished) {
                    downloading = false
                    val fail = ModelDownloadService.failedMsg
                    if (fail != null) {
                        AlertDialog.Builder(this@ModelsActivity)
                            .setTitle("Letöltési hiba")
                            .setMessage(fail + "\n\nNyomd meg újra az OK-t a folytatáshoz.")
                            .setPositiveButton("OK", null)
                            .show()
                    } else {
                        Toast.makeText(this@ModelsActivity, msg, Toast.LENGTH_LONG).show()
                    }
                    selected.clear()
                    queue = emptyList()
                    build()
                    refreshStatuses()
                }
            }
        }
        dlListener = listener
        ModelDownloadService.addListener(listener)
        if (ModelDownloadService.running) {
            downloading = true
            paused = ModelDownloadService.paused
            progPct = ModelDownloadService.pct
            progMsg = ModelDownloadService.message
        }
    }

    private val MB = 1024L * 1024L

    private fun estBytes(id: String): Long =
        (if (id == "mlkit") 60 else items.firstOrNull { it.spec.id == id }?.mb ?: 0) * MB

    private fun shortName(id: String): String =
        if (id == "mlkit") "Fordítómodell"
        else ModelManager.byId(id).label.substringBefore("(").trim()

    private fun fmtEta(sec: Long): String = when {
        sec < 60 -> "kb. $sec mp"
        sec < 3600 -> "kb. ${(sec + 59) / 60} perc"
        else -> "kb. ${sec / 3600} ó ${(sec % 3600 + 59) / 60} p"
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: return "ismeretlen hiba"
        return when {
            m.contains("abort", true) || m.contains("reset", true) || m.contains("timed out", true) ||
                m.contains("hiányos", true) || m.contains("Unable to resolve", true) ->
                "megszakadt a kapcsolat – nyomd meg újra az OK-t, onnan folytatja"
            else -> m
        }
    }

    private fun runQueue() {
        paused = false
        pausing = false
        ctl = ModelManager.DlControl()
        val c = ctl
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        updatePauseUi()
        val app = applicationContext
        Thread {
            val failed = ArrayList<String>()
            var lastT = 0L
            var lastB = 0L
            var speed = 0.0
            while (true) {
                val id = queue.firstOrNull() ?: break
                val est = estBytes(id)
                currentIsMl = id == "mlkit"
                main.post { updatePauseUi() }
                val idx = queueTotal - queue.size + 1
                try {
                    if (id == "mlkit") {
                        post(-1, "($idx/$queueTotal) Fordítómodell letöltése… (nem szüneteltethető)")
                        downloadTranslator()
                    } else {
                        val spec = ModelManager.byId(id)
                        ModelManager.download(app, spec, c, { done, total ->
                            val now = System.currentTimeMillis()
                            if (lastT == 0L) {
                                lastT = now; lastB = done
                            } else if (now - lastT >= 1000) {
                                val inst = (done - lastB) * 1000.0 / (now - lastT)
                                speed = if (speed == 0.0) inst else speed * 0.7 + inst * 0.3
                                lastT = now; lastB = done
                            }
                            val cur = if (total > 0) total else est
                            val overall = completedBytes + done
                            val overallTotal = (totalEstBytes - est + cur).coerceAtLeast(1L)
                            val pct = (overall * 100 / overallTotal).toInt()
                            val sp = if (speed > 0) String.format(java.util.Locale.US, "%.1f MB/s", speed / MB) else "…"
                            val eta = if (speed > 50_000) " · " + fmtEta(((overallTotal - overall) / speed).toLong().coerceAtLeast(1)) else ""
                            post(pct, "($idx/$queueTotal) ${shortName(id)} · ${done / MB} / ${cur / MB} MB · $sp$eta")
                        }, { stage -> post(-1, "($idx/$queueTotal) ${shortName(id)}: $stage") })
                        if (id == ModelManager.LARGE.id) prefs.modelId = "large"
                    }
                    completedBytes += est
                    queue = queue.drop(1)
                } catch (e: ModelManager.PausedException) {
                    break
                } catch (e: Exception) {
                    failed.add("${shortName(id)}: ${friendly(e)}")
                    completedBytes += est
                    queue = queue.drop(1)
                }
            }
            main.post { onQueueStopped(failed) }
        }.start()
    }

    private fun onQueueStopped(failed: List<String>) {
        if (isDestroyed) return
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (cancelled) {
            downloading = false
            queue = emptyList()
            Toast.makeText(this, "Megszakítva. A félbehagyott rész megmarad, később folytatható.", Toast.LENGTH_LONG).show()
            build()
            refreshStatuses()
            return
        }
        if (queue.isNotEmpty() && ctl.paused) {
            paused = true
            pausing = false
            progMsg = progMsg.substringBefore(" · ") + " · szüneteltetve"
            progressText?.text = progMsg
            platProgress?.set(progPct, progMsg)
            updatePauseUi()
            return
        }
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

    private fun updatePauseUi() {
        val pb = pauseBtn ?: return
        pb.text = if (paused) "▶ Folytatás" else if (pausing) "Szünetel…" else "⏸ Szünet"
        pb.isEnabled = paused || (!pausing && !currentIsMl)
        cancelBtn?.isEnabled = paused || (!pausing && !currentIsMl)
    }

    private fun onPauseResume() {
        if (paused) {
            if (!Engines.online(this)) {
                Toast.makeText(this, "Nincs internetkapcsolat.", Toast.LENGTH_LONG).show()
                return
            }
            pausing = false
            ModelDownloadService.resume(this)
            updatePauseUi()
        } else if (!pausing) {
            pausing = true
            ModelDownloadService.pause(this)
            updatePauseUi()
        }
    }

    private fun onCancel() {
        cancelled = true
        ModelDownloadService.cancel(this)
        downloading = false
        paused = false
        queue = emptyList()
        Toast.makeText(this, "Megszakítva. A félbehagyott rész megmarad, később folytatható.", Toast.LENGTH_LONG).show()
        build()
        refreshStatuses()
    }

    private fun post(pct: Int, msg: String) {
        progPct = pct
        progMsg = msg
        main.post {
            if (isDestroyed) return@post
            progressBar?.let {
                if (pct < 0) it.isIndeterminate = true
                else { it.isIndeterminate = false; it.progress = pct.coerceIn(0, 100) }
            }
            progressText?.text = msg
            platProgress?.set(pct, msg)
        }
    }

    private fun downloadTranslator() {
        MlKitModels.ensure(
            listOf(TranslateLanguage.ENGLISH, TranslateLanguage.HUNGARIAN), { cancelled },
            { el -> post(-1, "Fordítómodell letöltése… ${el / 60}:${"%02d".format(el % 60)}") }
        )
    }
}
