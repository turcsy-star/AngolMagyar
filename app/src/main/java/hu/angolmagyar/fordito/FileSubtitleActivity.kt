package hu.angolmagyar.fordito

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Hang- vagy videófájlokból SRT felirat / átirat készítése – varázsló-szerűen:
 *  1) beállítás: fájlok listája (több is lehet, sorban dolgozza fel), mentés helye, teljesítmény;
 *  2) futás: egy összesítő (eltelt / hátra / kész), a lépés-táblázat és a darab-lista, plus a prioritás;
 *  3) kész: összegzés fájlonként.
 * A munkát a SrtJobService végzi a háttérben; ez a képernyő csak megjeleníti.
 *
 * Támogatott: mp3, m4a, wav, ogg, flac, mp4, mkv, webm (MediaExtractor dekódolható formátumok).
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
        const val EXTRA_AUTOSTART = "autostart" // fájlválasztó azonnal
        const val EXTRA_TGT = "tgt" // célnyelv: hu/en/de/…
        const val EXTRA_KIND = "kind" // SrtJobService.KIND_TRANSCRIPT / KIND_SUBTITLE

        private val PICK_TYPES = arrayOf(
            "audio/*", "video/*",
            "audio/mpeg", "audio/mp4", "audio/x-wav", "audio/ogg",
            "video/mp4", "video/x-matroska", "video/webm"
        )
    }

    private var autoStart = false
    private var fromWizard = false
    /** Varázslóból jövő forrásnyelv (en/de/fr/…); ha üres, a UI választó érvényes. */
    private var wizardSrc: String? = null
    private var wizardTgt: String = "hu"
    private var lastRenderMs = 0L
    private var renderPosted = false
    /** A haladás-értesítések másodpercenként legfeljebb egyszer rajzolják újra a képernyőt (nyugodt, olvasható számok). */
    private val jobListener: () -> Unit = {
        val now = System.currentTimeMillis()
        val since = now - lastRenderMs
        if (since >= 1000) {
            lastRenderMs = now
            render()
        } else if (!renderPosted) {
            renderPosted = true
            main.postDelayed({
                renderPosted = false
                lastRenderMs = System.currentTimeMillis()
                render()
            }, 1000 - since)
        }
    }

    /** Hátralévő idő simítása: a kijelzett érték másodpercenként csökken, a becslés csak lassan húzza el. */
    private val etaMem = HashMap<String, Pair<Long, Long>>()
    private fun smoothRem(key: String, rem: Long, now: Long): Long {
        val prev = etaMem[key]
        if (prev != null && prev.second == now) return prev.first // ugyanabban a rajzolásban többször kérdezhetjük
        val out = if (prev == null) rem else {
            val predicted = prev.first - (now - prev.second)
            if (predicted <= 0) rem else (predicted * 0.85 + rem * 0.15).toLong()
        }
        etaMem[key] = out to now
        return out
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!SrtJob.batchRunning) return
            lastRenderMs = System.currentTimeMillis()
            render()
            main.postDelayed(this, 1000)
        }
    }
    /** Ez a képernyő látta futni a feladatot → a végén magától felkínálja a mentést / bezárást. */
    private var watchedRun = false
    private var saveAsked = false
    private var closeScheduled = false
    private var pendingSaveItem: SrtJob.QItem? = null

    private lateinit var srcGroup: RadioGroup
    private lateinit var translateSwitch: SwitchCompat
    private lateinit var modeGroup: RadioGroup

    private lateinit var prefs: Prefs
    /** Beszélők megkülönböztetése (S1/S2…); tárgyalás módban mindig be van kapcsolva. */
    private var useDiarization = false
    private val main = Handler(Looper.getMainLooper())
    private var kind = SrtJobService.KIND_SUBTITLE

    // --- képernyők
    private lateinit var setupGroup: LinearLayout
    private lateinit var runGroup: LinearLayout
    private lateinit var progressSection: LinearLayout
    private lateinit var resultSection: LinearLayout

    // --- 1. beállítás
    private lateinit var setupQueueBox: LinearLayout
    private lateinit var pickBtn: android.widget.Button
    private lateinit var saveBox: LinearLayout
    private lateinit var startBtn: android.widget.Button
    private lateinit var engineInfo: TextView

    // --- 2. futás
    private lateinit var fileLabel: TextView
    private lateinit var langLine: TextView
    private lateinit var barTotal: BarRow
    private lateinit var posText: TextView
    private var glossEn: android.widget.EditText? = null
    private var glossHu: android.widget.EditText? = null
    private var glossAuto: android.widget.EditText? = null

    private val exportGlossary = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) try {
            glossEn?.let { prefs.enFixes = it.text.toString() }
            glossHu?.let { prefs.huFixes = it.text.toString() }
            val o = org.json.JSONObject()
                .put("enFixes", prefs.enFixes).put("huFixes", prefs.huFixes).put("vocab", prefs.customVocab)
                .put("cast", Projects.get(this, "cast")).put("speakerNames", prefs.speakerNames)
            contentResolver.openOutputStream(uri)?.use { it.write(o.toString(2).toByteArray(Charsets.UTF_8)) }
            Toast.makeText(this, "Szótár exportálva.", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Az exportálás nem sikerült: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private val importGlossary = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) try {
            val txt = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) } ?: return@registerForActivityResult
            val o = org.json.JSONObject(txt)
            fun merge(old: String, add: String): String {
                val have = old.lines().map { it.trim().lowercase() }.toSet()
                val extra = add.lines().map { it.trim() }.filter { it.isNotEmpty() && it.lowercase() !in have }
                return if (extra.isEmpty()) old else (old.trimEnd() + "\n" + extra.joinToString("\n")).trim()
            }
            prefs.enFixes = merge(glossEn?.text?.toString() ?: prefs.enFixes, o.optString("enFixes"))
            prefs.huFixes = merge(glossHu?.text?.toString() ?: prefs.huFixes, o.optString("huFixes"))
            prefs.customVocab = merge(prefs.customVocab, o.optString("vocab"))
            Projects.set(this, "cast", merge(Projects.get(this, "cast"), o.optString("cast")))
            prefs.speakerNames = merge(prefs.speakerNames, o.optString("speakerNames"))
            glossEn?.setText(prefs.enFixes)
            glossHu?.setText(prefs.huFixes)
            Toast.makeText(this, "Szótár importálva (összefésülve a meglévővel).", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Az importálás nem sikerült: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    private lateinit var engineLine: TextView
    private lateinit var pipeline: PipelineView
    private lateinit var wave: WaveView
    private lateinit var checkView: CheckView
    private var shownEnv: FloatArray? = null
    private var shownRanges: LongArray? = null
    private var checkPlayed = false
    private var typedLen = 0
    private lateinit var tileElapsed: TextView
    private lateinit var tileRem: TextView
    private lateinit var tileFin: TextView
    private lateinit var stepsBox: LinearLayout
    private lateinit var coolBanner: TextView
    private lateinit var stallBanner: TextView
    private lateinit var stallReport: View
    private lateinit var chunkSection: LinearLayout
    private lateinit var chunkTitle: TextView
    private lateinit var chunkMore: TextView
    private lateinit var previewBox: LinearLayout
    private lateinit var previewText: TextView
    private lateinit var flow: TranscriptFlow
    private lateinit var runQueueBox: LinearLayout
    private lateinit var cancelBtn: android.widget.Button
    private lateinit var perfRunCard: LinearLayout

    // --- 3. kész
    private lateinit var resultTitle: TextView
    private lateinit var resultText: TextView

    private val bgHints = ArrayList<LinearLayout>()
    private val perfSeeks = ArrayList<android.widget.SeekBar>()
    private val perfLabels = ArrayList<TextView>()
    private val perfDescs = ArrayList<TextView>()
    private val tRows = ArrayList<TRow>()
    private var setupSig = ""
    private var runSig = ""

    /** A lépés-táblázat egy sora: jelző, név+megjegyzés, idő, hátra, %, és az aktív lépés vékony sávja. */
    private class TRow(
        val root: LinearLayout, val icon: TextView, val name: TextView, val note: TextView,
        val time: TextView, val eta: TextView, val pct: TextView, val bar: ProgressBar
    )

    /** A darab-lista egy sora: jelző, darab, beszédhossz, idő, hátra, %, sáv és megjegyzés. */
    private class CRow(
        val root: LinearLayout, val icon: TextView, val name: TextView, val len: TextView,
        val time: TextView, val eta: TextView, val pct: TextView, val bar: ProgressBar, val note: TextView
    )

    private val cRows = ArrayList<CRow>()

    /** Legfeljebb ennyi darab-sor látszik; többnél az aktív darabok körüli ablak (és a darab-térkép). */
    private val CHUNK_ROWS = 8

    /** Egy folyamatsor: cím + százalék, sáv, és egy alsó megjegyzés (eltelt / hátra). */
    private class BarRow(ctx: Context) {
        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = Ui.dp(ctx, 14) }
        }
        val title = TextView(ctx).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.color(ctx, R.color.textPrimary))
            maxLines = 2
        }
        val pct = TextView(ctx).apply {
            textSize = 15f
            typeface = Typeface.MONOSPACE
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.color(ctx, R.color.textPrimary))
        }
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Ui.color(ctx, R.color.stroke))
        }
        val sub = TextView(ctx).apply {
            textSize = 13f
            setTextColor(Ui.color(ctx, R.color.textSecondary))
        }

        init {
            val head = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            head.addView(pct)
            root.addView(head)
            root.addView(bar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 10)
            ).apply { topMargin = Ui.dp(ctx, 6) })
            root.addView(sub)
        }

        fun color(c: Int) {
            bar.progressTintList = android.content.res.ColorStateList.valueOf(c)
            bar.indeterminateTintList = android.content.res.ColorStateList.valueOf(c)
        }

        /** @param p 0–100, vagy -1 = nem ismert (pörgő sáv) */
        fun set(t: String, p: Int, s: String) {
            title.text = t
            if (p < 0) {
                bar.isIndeterminate = true
                pct.text = "…"
            } else {
                bar.isIndeterminate = false
                bar.progress = p.coerceIn(0, 100)
                pct.text = "${p.coerceIn(0, 100)}%"
            }
            sub.text = s
            sub.visibility = if (s.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    // ------------------------------------------------------------ választók (fájl, mentés)

    private val openLauncher =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (!uris.isNullOrEmpty()) {
                val items = ArrayList<SrtJob.QItem>()
                for (uri in uris) {
                    try {
                        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    } catch (e: Exception) { DiagLog.swallow("FileSubtitleActivity", e) }
                    val name = OutputWriter.displayName(this, uri) ?: uri.lastPathSegment ?: "fájl"
                    items.add(SrtJob.QItem(uri, name))
                }
                SrtJob.addItems(items)
                // fut a feldolgozás: a szolgáltatás magától veszi a sor következő elemét
            }
            render()
        }

    private val saveSrtLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
            val item = pendingSaveItem
            val srt = item?.srt
            if (uri != null && item != null && srt != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(srt.toByteArray(Charsets.UTF_8))
                    }
                    item.saved = true
                    item.detail = "Mentve a telefonra (kézzel választott helyre)"
                    Toast.makeText(this, "SRT elmentve.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Mentési hiba: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
            pendingSaveItem = null
            render()
        }

    private fun takeTree(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: Exception) { DiagLog.swallow("FileSubtitleActivity", e) }
    }

    private val pickDefaultTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                takeTree(uri)
                OutputPrefs.setDefaultTree(this, uri)
                OutputPrefs.setSaveMode(this, OutputPrefs.SAVE_DEFAULT)
            }
            refreshWhere()
        }

    /** A belső tárba mentett korábbi feliratok listája; bármelyik megnyitható szerkesztésre. */
    private fun showHistory() {
        val files = (TranscriptStore.dir(this).listFiles { f -> f.name.startsWith("felirat_") && f.extension == "srt" }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()).take(30)
        if (files.isEmpty()) {
            Toast.makeText(this, "Még nincs korábbi felirat.", Toast.LENGTH_SHORT).show()
            return
        }
        val fmt = java.text.SimpleDateFormat("yyyy.MM.dd HH:mm", java.util.Locale.getDefault())
        val labels = files.map { f ->
            val blocks = try { f.readText(Charsets.UTF_8).split("\n\n").count { it.isNotBlank() } } catch (_: Exception) { 0 }
            "${fmt.format(java.util.Date(f.lastModified()))} · $blocks blokk"
        }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Korábbi feliratok")
            .setItems(labels) { _, i -> SrtEditorActivity.openFile(this, files[i].absolutePath) }
            .setNegativeButton("Bezár", null)
            .show()
    }

    private fun pickFiles() {
        openLauncher.launch(PICK_TYPES)
    }

    /** Mappa kiválasztása: a benne (és almappáiban) lévő, még felirat nélküli videók a sorba kerülnek. */
    private val pickScanTree =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) scanFolder(uri)
        }

    private fun scanFolder(tree: Uri) {
        takeTree(tree)
        Toast.makeText(this, "Mappa átvizsgálása…", Toast.LENGTH_SHORT).show()
        Thread {
            val found = FolderScan.scan(this, tree)
            main.post {
                if (found.isEmpty()) {
                    Toast.makeText(
                        this, "Nincs feldolgozatlan videó / hangfájl a mappában (mindegyik mellett van már .srt).",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    SrtJob.addItems(found.map { SrtJob.QItem(it.uri, it.name) })
                    Toast.makeText(this, "${found.size} feldolgozatlan fájl hozzáadva a sorhoz.", Toast.LENGTH_LONG).show()
                }
                render()
            }
        }.start()
    }

    private lateinit var profileBox: LinearLayout
    private lateinit var trModeBox: LinearLayout

    /** Összecsukható szakasz (alapból zárva): ritkán kellő beállítások a kezdőképernyő nyugalmáért. */
    private fun collapsible(title: String, content: View, open: Boolean = false): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.visibility = if (open) View.VISIBLE else View.GONE
        lateinit var btn: android.widget.Button
        btn = Ui.button(this, (if (open) "▾ " else "▸ ") + title, false) {
            val show = content.visibility != View.VISIBLE
            content.visibility = if (show) View.VISIBLE else View.GONE
            btn.text = (if (show) "▾ " else "▸ ") + title
        }
        box.addView(btn)
        box.addView(content)
        return box
    }

    /** Projekt (sorozat) + szereplők / téma / beszélők viszonya: a felismerést és a fordítást is pontosítja. */
    private lateinit var projBox: LinearLayout

    private fun makeProjectCard(): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.title(this, "Projekt és szereplők", 16f))
        card.addView(Ui.small(this, "A nevek pontosabbak lesznek a felismerésben és a fordításban. Projektenként (pl. egy sorozat) külön tárolódnak."))
        projBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(projBox)
        refreshProject()
        return card
    }

    private fun refreshProject() {
        projBox.removeAllViews()
        val names = listOf("(nincs projekt – általános)") + Projects.list(this)
        val spin = android.widget.Spinner(this)
        spin.adapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, names)
        val cur = Projects.current(this)
        spin.setSelection(if (cur.isEmpty()) 0 else names.indexOf(cur).coerceAtLeast(0))
        var first = true
        spin.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: android.widget.AdapterView<*>?, v: View?, pos: Int, id: Long) {
                if (first) { first = false; return }
                Projects.setCurrent(this@FileSubtitleActivity, if (pos == 0) "" else names[pos])
                refreshProject()
            }
            override fun onNothingSelected(p: android.widget.AdapterView<*>?) {}
        }
        projBox.addView(spin)
        projBox.addView(Ui.smallButton(this, "Új projekt…") {
            val et = android.widget.EditText(this).apply { hint = "pl. Stepsiblings" }
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Új projekt")
                .setView(et)
                .setPositiveButton("Létrehoz") { _, _ -> Projects.add(this, et.text.toString()); refreshProject() }
                .setNegativeButton("Mégse", null)
                .show()
        })
        fun field(label: String, key: String, hint: String) {
            projBox.addView(Ui.body(this, label))
            projBox.addView(android.widget.EditText(this).apply {
                setText(Projects.get(this@FileSubtitleActivity, key))
                this.hint = hint
                textSize = 14f
                minLines = 1
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                setTextColor(Ui.color(this@FileSubtitleActivity, R.color.textPrimary))
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(e: android.text.Editable?) {
                        Projects.set(this@FileSubtitleActivity, key, e?.toString() ?: "")
                    }
                })
            })
        }
        field("Szereplők, helyek nevei (vesszővel)", "cast", "Nina, Jake, Kowalski")
        field("Téma, műfaj", "topic", "családi vígjáték, iskolai környezet")
        field("Beszélők viszonya, stílus", "style", "Nina és Jake testvérek: tegeződnek; a tanárt magázzák")
    }

    /** Fordítás módja: offline (ML Kit) / online (LLM) / online + offline tartalék. */
    private fun makeTranslateModeCard(): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.title(this, "Fordítás módja", 16f))
        trModeBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(trModeBox)
        card.addView(Ui.button(this, "Online szolgáltatás beállítása…", false) {
            OnlineSettingsDialog.show(this) { refreshTrMode() }
        })
        refreshTrMode()
        return card
    }

    private fun refreshTrMode() {
        if (!::trModeBox.isInitialized) return
        trModeBox.removeAllViews()
        val cur = OnlinePrefs.mode(this)
        val usable = OnlinePrefs.usable(this)
        fun choose(m: Int) {
            if (m != OnlinePrefs.OFF && !usable) {
                OnlineSettingsDialog.show(this) {
                    if (OnlinePrefs.usable(this)) OnlinePrefs.setMode(this, m)
                    refreshTrMode()
                }
                return
            }
            OnlinePrefs.setMode(this, m)
            refreshTrMode()
        }
        trModeBox.addView(optionRow("Offline", "Az eszközön fut (ML Kit + szabályok), internet nélkül is.", cur == OnlinePrefs.OFF) { choose(OnlinePrefs.OFF) })
        trModeBox.addView(optionRow("Online (pontosabb)", "Nyelvi modell fordít, a szomszédos sorok ismeretében; a hibás sor angolul marad.", cur == OnlinePrefs.ONLINE) { choose(OnlinePrefs.ONLINE) })
        trModeBox.addView(optionRow("Online + offline tartalék", "Online fordít; ami nem sikerül, azt az offline fordító pótolja.", cur == OnlinePrefs.AUTO) { choose(OnlinePrefs.AUTO) })
        if (cur != OnlinePrefs.OFF && !usable) trModeBox.addView(Ui.small(this, "Az online fordítás még nincs beállítva – offline fordítás fut."))
    }

    /** Profilok (gyors vázlat … éjszakai) + formázási kapcsolók + szótár. */
    private fun makeProfileCard(): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.title(this, "3. Profil és formázás", 16f))
        profileBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(profileBox)
        val advBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        advBox.addView(SwitchCompat(this).apply {
            text = "Olvasható felirat: hosszú blokkok szétvágása, időzítés, kiegyensúlyozott sortörés"
            minHeight = dp(52)
            isChecked = SubPrefs.reflow(this@FileSubtitleActivity)
            setOnCheckedChangeListener { _, c -> SubPrefs.setReflow(this@FileSubtitleActivity, c) }
        })
        advBox.addView(SwitchCompat(this).apply {
            text = "Jobb fordítás: mondatokba fogott (kontextusos) fordítás"
            minHeight = dp(52)
            isChecked = SubPrefs.contextTranslate(this@FileSubtitleActivity)
            setOnCheckedChangeListener { _, c -> SubPrefs.setContextTranslate(this@FileSubtitleActivity, c) }
        })
        advBox.addView(SwitchCompat(this).apply {
            text = "Tegező párbeszéd: magázó alakok javítása (Segítsen → Segíts)"
            minHeight = dp(52)
            isChecked = SubPrefs.informalHu(this@FileSubtitleActivity)
            setOnCheckedChangeListener { _, c -> SubPrefs.setInformalHu(this@FileSubtitleActivity, c) }
        })
        advBox.addView(SwitchCompat(this).apply {
            text = "Hallucináció-szűrő: ismétlődő és magányos, értelmetlen sorok kidobása"
            minHeight = dp(52)
            isChecked = SubPrefs.hallucFilter(this@FileSubtitleActivity)
            setOnCheckedChangeListener { _, c -> SubPrefs.setHallucFilter(this@FileSubtitleActivity, c) }
        })
        advBox.addView(SwitchCompat(this).apply {
            text = "Beszélők színezése az SRT-ben"
            minHeight = dp(52)
            isChecked = SubPrefs.speakerColors(this@FileSubtitleActivity)
            setOnCheckedChangeListener { _, c -> SubPrefs.setSpeakerColors(this@FileSubtitleActivity, c) }
        })
        advBox.addView(Ui.button(this, "Szótár (nevek, javítások)…", false) { showGlossary() })
        card.addView(collapsible("Formázás és szűrők", advBox))
        refreshProfiles()
        return card
    }

    private fun refreshProfiles() {
        if (!::profileBox.isInitialized) return
        profileBox.removeAllViews()
        val cur = SubPrefs.profile(this)
        for (p in Presets.ALL) {
            profileBox.addView(optionRow(p.label, Presets.describe(this, p), cur == p.id) {
                Presets.apply(this, p.id)
                syncPerf()
                refreshProfiles()
            })
        }
        if (cur == "custom") profileBox.addView(Ui.small(this, "Egyedi beállítás (a prioritást kézzel állítottad)."))
    }

    /** Felhasználói javítólista a felismerés és a fordítás szövegére ("régi=új" soronként). */
    private fun showGlossary() {
        val pad = dp(16)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, 0)
        }
        fun field(hint: String, value: String) = android.widget.EditText(this).apply {
            setText(value)
            this.hint = hint
            minLines = 4
            gravity = Gravity.TOP
            textSize = 14f
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            setTextColor(Ui.color(this@FileSubtitleActivity, R.color.textPrimary))
        }
        col.addView(Ui.small(this,
            "Soronként: régi=új (kis/nagybetű nem számít, egész szóra illeszt). Pl.: nina skye=Nina Skye. " +
                "A javítás a felismerés után és a fordítás után is lefut."))
        col.addView(Ui.body(this, "Angol szöveg javításai"))
        val en = field("pl. step siblings=stepsiblings", prefs.enFixes)
        col.addView(en)
        col.addView(Ui.body(this, "Magyar szöveg javításai"))
        val hu = field("pl. mostoha testvér=mostohatestvér", prefs.huFixes)
        col.addView(hu)
        glossEn = en
        glossHu = hu
        col.addView(SwitchCompat(this).apply {
            text = "A szótár magától fejlődik (a háromszor ugyanúgy javított szó szabály lesz)"
            minHeight = dp(52)
            isChecked = VocabLearner.auto(prefs)
            setOnCheckedChangeListener { _, c -> VocabLearner.setAuto(prefs, c) }
        })
        col.addView(Ui.body(this, "Automatikus szabályok (magától tanulta; szerkeszthető, törölhető)"))
        val autoF = field("üres – a program tölti", VocabLearner.autoRules(prefs))
        col.addView(autoF)
        glossAuto = autoF
        val learned = VocabLearner.autoLog(prefs)
        if (learned.isNotEmpty()) col.addView(Ui.small(this, "Automatikusan tanult: " + learned.takeLast(6).joinToString("; ") +
            "\n(Törléshez töröld a sort a fenti listából.)"))
        col.addView(Ui.smallButton(this, "Szótár exportálása (fájlba)") { exportGlossary.launch("szotar.json") })
        col.addView(Ui.smallButton(this, "Szótár importálása (fájlból)") { importGlossary.launch(arrayOf("*/*")) })
        val sv = android.widget.ScrollView(this).apply { addView(col) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Szótár")
            .setView(sv)
            .setPositiveButton("Mentés") { _, _ ->
                prefs.enFixes = en.text.toString()
                prefs.huFixes = hu.text.toString()
                glossAuto?.let { VocabLearner.setAutoRules(prefs, it.text.toString()) }
            }
            .setNegativeButton("Mégse", null)
            .show()
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onDestroy() {
        try { flow.stop() } catch (e: Exception) { DiagLog.swallow("FileSubtitleActivity", e) }
        try { seekPlayer?.release() } catch (e: Exception) { DiagLog.swallow("FileSubtitleActivity", e) }
        // Ideiglenes tárgyalás mód: kilépéskor visszaállítjuk az eredeti beállításokat.
        if (isFinishing && ::prefs.isInitialized && prefs.meetingMode && prefs.meetingTemporary) {
            MeetingMode.clear(prefs)
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ felépítés

    /** Félbehagyott (pl. az app leállása miatt megszakadt) feldolgozás folytatásának felajánlása. */
    /** Éjszakai sor indítása, ha van eltett fájl. Igaz, ha megjelent a kérdés. */
    private fun offerNight(): Boolean {
        val items = NightMode.pending(this)
        if (items.isEmpty() || SrtJob.batchRunning) return false
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("🌙 Éjszakai feldolgozás")
            .setMessage("${items.size} fájl vár. Indítsam a jelenlegi beállításokkal?")
            .setPositiveButton("Indítás") { _, _ ->
                SrtJob.addItems(items.map { SrtJob.QItem(it.first, it.second) })
                NightMode.clear(this)
                startProcess()
            }
            .setNegativeButton("Később", null)
            .setNeutralButton("Elvetés") { _, _ -> NightMode.clear(this) }
            .show()
        return true
    }

    private fun offerResume() {
        try {
            if (offerNight()) return
            if (SrtJob.batchRunning || SrtJob.queue.isNotEmpty()) return
            val raw = prefs.raw().getString("pendingJob", null) ?: return
            val o = org.json.JSONObject(raw)
            val uri = android.net.Uri.parse(o.getString("uri"))
            val name = o.optString("name")
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Félbehagyott feldolgozás")
                .setMessage("„$name” feldolgozása megszakadt (pl. az app leállt). A már kész darabok megvannak, onnan folytathatom.")
                .setPositiveButton("Folytatás") { _, _ ->
                    prefs.raw().edit().remove("pendingJob").apply()
                    SrtJob.addItems(listOf(SrtJob.QItem(uri, name)))
                    startProcess()
                }
                .setNegativeButton("Elvetés") { _, _ -> prefs.raw().edit().remove("pendingJob").apply() }
                .show()
        } catch (e: Exception) { DiagLog.swallow("FileSubtitleActivity", e) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        main.post { offerResume() }
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
        kind = intent.getStringExtra(EXTRA_KIND)
            ?: if (meeting || titleText == "Átirat fájlból") SrtJobService.KIND_TRANSCRIPT else SrtJobService.KIND_SUBTITLE
        column.addView(Ui.title(this, if (meeting) "Tárgyalás – magyar átirat" else (titleText ?: "Felirat fájlból (SRT)")))
        if (meeting) {
            column.addView(Ui.small(this, MeetingMode.statusText(this)))
            column.addView(Ui.small(this, DiarizerFactory.onnxStatus(this)))
            column.addView(Ui.small(this, "Beszélők: S1/S2… az SRT-ben (energia-alapú, ONNX ha telepítve)"))
        }

        setupGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        runGroup = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(setupGroup)
        column.addView(runGroup)
        buildSetup(meeting)
        buildRun()

        handleShare(intent)
        Anim.staggerIn(setupGroup)

        // Ha a varázsló fájlválasztóval nyitotta, azonnal felajánljuk (csak az első létrehozáskor)
        if (autoStart && savedInstanceState == null && !SrtJob.batchRunning && SrtJob.queue.isEmpty()) {
            main.post { pickFiles() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
        main.post { offerResume() }
    }

    /** Más appból (Galéria, Fájlok…) a Megosztás menüvel érkező videók / hangok a sorba kerülnek. */
    private fun handleShare(i: Intent?) {
        if (i == null) return
        val uris = ArrayList<Uri>()
        @Suppress("DEPRECATION")
        when (i.action) {
            Intent.ACTION_SEND -> (i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))?.let { uris.add(it) }
            Intent.ACTION_SEND_MULTIPLE -> i.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.let { uris.addAll(it) }
            else -> return
        }
        // ugyanazt a megosztást ne vegyük fel kétszer (pl. képernyőforgatás után)
        i.action = null
        if (uris.isEmpty()) return
        val items = uris.map { u ->
            SrtJob.QItem(u, OutputWriter.displayName(this, u) ?: u.lastPathSegment ?: "fájl")
        }
        SrtJob.addItems(items)
        if (prefs.raw().getBoolean("shareAuto", true) && !SrtJob.batchRunning && !prefs.meetingMode) {
            Toast.makeText(this, "${items.size} fájl: indul az utolsó beállításokkal.", Toast.LENGTH_LONG).show()
            startProcess()
        } else {
            Toast.makeText(this, "${items.size} fájl a sorban. Állítsd be a nyelvet, majd indítsd az SRT készítését.", Toast.LENGTH_LONG).show()
            render()
        }
    }

    private lateinit var quickBtn: android.widget.Button
    private lateinit var estLine: TextView
    private var estKey = ""
    private val durCache = HashMap<String, Long>()

    /** Indítás előtt: becsült futásidő a telepített Whisper-modellenként (a mért sebesség alapján). */
    private fun updateEstimate() {
        val items = SrtJob.queue.filter { it.state == SrtJob.Q_WAIT }
        if (items.isEmpty()) { estKey = ""; estLine.visibility = View.GONE; return }
        val key = items.joinToString("|") { it.uri.toString() }
        if (key == estKey) return
        estKey = key
        Thread {
            var total = 0L
            for (q in items) {
                total += durCache.getOrPut(q.uri.toString()) {
                    try {
                        val r = android.media.MediaMetadataRetriever()
                        r.setDataSource(this, q.uri)
                        val d = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                        r.release()
                        d
                    } catch (_: Exception) { 0L }
                }
            }
            val sp = getSharedPreferences("srt_hist", MODE_PRIVATE)
            val measured = HashMap<String, Long>()
            for ((k, v) in sp.all) if (k.startsWith("mpw_") && v is Long) measured[k.removePrefix("mpw_")] = v
            val cur = Engines.whisperSpec(this)?.id ?: ""
            val legacy = sp.getLong("msPerWindow", 0L)
            if (measured.isEmpty() && legacy > 0 && cur.isNotEmpty()) measured[cur] = legacy
            val parts = ModelManager.installedSpecs(this).filter { it in ModelManager.WHISPER }.mapNotNull { m ->
                val ms = TimeEstimate.estimateMs(total, TimeEstimate.msPerWindow(m.id, measured))
                if (ms < 0) null else (if (m.id == cur) "▶ " else "") + m.label.substringBefore(" (") + ": " + TimeEstimate.label(ms)
            }
            runOnUiThread {
                if (parts.isEmpty() || total <= 0) estLine.visibility = View.GONE
                else {
                    estLine.text = "⏱ Becsült idő – " + parts.joinToString(" · ")
                    estLine.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    private fun buildSetup(meeting: Boolean) {
        val g = setupGroup
        // egy gombos indítás: fájl(ok) kiválasztása után az utoljára használt beállításokkal rögtön indul
        quickBtn = Ui.button(this, "Fájlok kiválasztása…", true) {
            if (SrtJob.queue.any { it.state == SrtJob.Q_WAIT }) startProcess() else pickFiles()
        }
        g.addView(quickBtn)
        estLine = Ui.small(this, "").apply { visibility = View.GONE }
        g.addView(estLine)
        g.addView(Ui.small(this, "Hang- vagy videófájlból offline átirat + opcionális fordítás, SRT kimenettel."))
        if (!meeting) {
            g.addView(SwitchCompat(this).apply {
                text = getString(R.string.f_diarize)
                minHeight = dp(52)
                isChecked = intent.getBooleanExtra(EXTRA_DIARIZE, false)
                useDiarization = isChecked
                setOnCheckedChangeListener { _, checked -> useDiarization = checked }
            })
        }

        // Mód + nyelv: varázslóból jövet összefoglaló, különben teljes választó
        modeGroup = RadioGroup(this)
        val outIdx = if (intent.hasExtra(EXTRA_OUTPUT)) intent.getIntExtra(EXTRA_OUTPUT, 2) else prefs.raw().getInt("lastOut", 2)
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
            ?: prefs.raw().getString("lastSrc", null)
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
                else prefs.raw().getBoolean("lastTr", prefs.translateDir != Prefs.DIR_NONE)
            }
        }
        engineInfo = Ui.small(this, "")
        if (fromWizard && !meeting) {
            val tgtName = SrtJobService.langName(wizardTgt)
            val langName = SrtJobService.langName(presetSrc).replaceFirstChar { it.uppercase() }
            val summary = when (outIdx) {
                0 -> "$langName átirat (fordítás nélkül)"
                1 -> "$langName videó → $tgtName felirat"
                else -> "$langName + $tgtName felirat"
            }
            val card = Ui.card(this)
            card.addView(Ui.title(this, "Felirat", 16f))
            card.addView(Ui.body(this, summary))
            card.addView(engineInfo)
            g.addView(card)
            // Rejtett, de a selectedMode/selectedSrc olvassa
            modeGroup.visibility = View.GONE
            if (::srcGroup.isInitialized) srcGroup.visibility = View.GONE
            if (::translateSwitch.isInitialized) translateSwitch.visibility = View.GONE
        } else {
            val modeCard = Ui.card(this)
            modeCard.addView(Ui.title(this, "Kimenet", 16f))
            modeCard.addView(modeGroup)
            g.addView(modeCard)
            if (!meeting) {
                val langCard = Ui.card(this)
                langCard.addView(Ui.title(this, "A hang nyelve", 16f))
                langCard.addView(srcGroup)
                langCard.addView(translateSwitch)
                langCard.addView(engineInfo)
                g.addView(langCard)
                srcGroup.setOnCheckedChangeListener { _, _ -> refreshEngineInfo() }
            }
        }
        refreshEngineInfo()

        // 1. Fájlok
        val filesCard = Ui.card(this)
        filesCard.addView(Ui.title(this, "1. Fájlok", 16f))
        setupQueueBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        filesCard.addView(setupQueueBox)
        pickBtn = Ui.button(this, "Fájlok kiválasztása…", true) { pickFiles() }
        filesCard.addView(pickBtn)
        filesCard.addView(Ui.button(this, "Mappa átvizsgálása (feldolgozatlan videók)…", false) { pickScanTree.launch(null) })
        filesCard.addView(Ui.button(this, "Korábbi feliratok (előzmények)…", false) { showHistory() })
        g.addView(filesCard)

        // 2. Mentés helye
        val whereCard = Ui.card(this)
        whereCard.addView(Ui.title(this, "2. Mentés helye", 16f))
        saveBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        whereCard.addView(saveBox)
        whereCard.addView(SwitchCompat(this).apply {
            text = "Kész után zárja be ezt a képernyőt"
            minHeight = dp(48)
            isChecked = OutputPrefs.autoClose(this@FileSubtitleActivity)
            setOnCheckedChangeListener { _, c -> OutputPrefs.setAutoClose(this@FileSubtitleActivity, c) }
        })
        g.addView(whereCard)
        refreshWhere()

        g.addView(makeTranslateModeCard())
        val projCard = makeProjectCard()
        val profCard = makeProfileCard()

        // Teljesítmény (a futás közben is ugyanez marad az egyetlen beállítás) – a haladó részben
        Perf.load(this)
        val adv = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        adv.addView(projCard)
        adv.addView(profCard)
        adv.addView(makePerfCard())
        adv.addView(SwitchCompat(this).apply {
            text = "Hangminőség-előszűrés (zúgás szűrése, halk felvétel felhangosítása)"
            minHeight = dp(52)
            isChecked = prefs.raw().getBoolean("audioPrep", true)
            setOnCheckedChangeListener { _, c -> prefs.raw().edit().putBoolean("audioPrep", c).apply() }
        })
        adv.addView(SwitchCompat(this).apply {
            text = "Megosztásból érkező fájl azonnal induljon (az utolsó beállításokkal)"
            minHeight = dp(52)
            isChecked = prefs.raw().getBoolean("shareAuto", true)
            setOnCheckedChangeListener { _, c -> prefs.raw().edit().putBoolean("shareAuto", c).apply() }
        })
        adv.addView(SwitchCompat(this).apply {
            text = "Pontosító második menet a bizonytalan szakaszokra (lassabb)"
            minHeight = dp(52)
            isChecked = SubPrefs.secondPass(this@FileSubtitleActivity)
            setOnCheckedChangeListener { _, c -> SubPrefs.setSecondPass(this@FileSubtitleActivity, c) }
        })
        adv.addView(SwitchCompat(this).apply {
            text = "Fordítási memória: ugyanaz a mondat mindig ugyanúgy forduljon"
            minHeight = dp(52)
            isChecked = TranslationMemoryStore.enabled(this@FileSubtitleActivity)
            setOnCheckedChangeListener { _, c -> TranslationMemoryStore.setEnabled(this@FileSubtitleActivity, c) }
        })
        adv.addView(Ui.smallButton(this, "Fordítási memória törlése") {
            TranslationMemoryStore.clear(this)
            Toast.makeText(this, "Törölve.", Toast.LENGTH_SHORT).show()
        })
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            adv.addView(SwitchCompat(this).apply {
                text = "Dinamikus színek (a háttérképből, Android 12+; újraindítás után teljes)"
                minHeight = dp(52)
                isChecked = Ui.dynamicColors(this@FileSubtitleActivity)
                setOnCheckedChangeListener { _, c -> Ui.setDynamicColors(this@FileSubtitleActivity, c) }
            })
        }
        adv.addView(makeBgHint())
        g.addView(collapsible("Haladó beállítások (projekt, profil, teljesítmény, háttérfutás)", adv))

        startBtn = Ui.button(this, "SRT készítése", true) { startProcess() }
        g.addView(startBtn)
        g.addView(Ui.button(this, "🌙 Éjszakára ütemezés (töltőn, Wi-Fin)", false) {
            val n = NightMode.schedule(this, SrtJob.queue.filter { it.state == SrtJob.Q_WAIT })
            Toast.makeText(this,
                if (n > 0) "$n fájl ütemezve: töltőre téve értesítést kapsz, koppintással indul."
                else "Nincs várakozó fájl (vagy az ütemezés nem sikerült).", Toast.LENGTH_LONG).show()
        })
    }

    private fun buildRun() {
        val g = runGroup

        // ---------- futás: csak a folyamat (minden érték egyetlen helyen szerepel)
        progressSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = Ui.card(this)
        val sec = Ui.color(this, R.color.textSecondary)
        fileLabel = Ui.title(this, "", 16f)
        head.addView(fileLabel)
        langLine = Ui.small(this, "")
        head.addView(langLine)

        // összesítő: egyetlen sáv + eltelt / hátra / kész (a lépés- és darab-sorok ezeket nem ismétlik)
        pipeline = PipelineView(this)
        head.addView(pipeline, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })
        wave = WaveView(this)
        head.addView(wave, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12); bottomMargin = dp(2) })
        // a hullám alatt: melyik hangszakaszon dolgozik éppen
        wave.onSeek = { ms -> playAudioAt(ms) }
        posText = Ui.small(this, "").apply { gravity = Gravity.CENTER_HORIZONTAL }
        head.addView(posText, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })
        engineLine = Ui.small(this, "").apply { gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        head.addView(engineLine, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(6) })
        // összhaladás: egyetlen csík
        barTotal = BarRow(this)
        barTotal.color(Ui.color(this, R.color.primary))
        head.addView(barTotal.root)
        val tiles = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(10), 0, 0)
        }
        fun tile(caption: String, align: Int): TextView {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            col.addView(TextView(this).apply {
                text = caption; textSize = 11f; setTextColor(sec); this.gravity = align
            })
            val value = TextView(this).apply {
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(Ui.color(this@FileSubtitleActivity, R.color.textPrimary))
                this.gravity = align
            }
            col.addView(value)
            tiles.addView(col)
            return value
        }
        tileElapsed = tile("Eltelt", Gravity.START)
        tileRem = tile("Hátra", Gravity.CENTER_HORIZONTAL)
        tileFin = tile("Kész kb.", Gravity.END)
        head.addView(tiles)

        coolBanner = Ui.body(this, "").apply {
            setTextColor(Ui.color(this@FileSubtitleActivity, R.color.warn))
            visibility = View.GONE
        }
        head.addView(coolBanner)
        stallBanner = Ui.small(this, "").apply {
            setTextColor(Ui.color(this@FileSubtitleActivity, R.color.warn))
            visibility = View.GONE
        }
        head.addView(stallBanner)
        stallReport = Ui.smallButton(this, "Hibajelentés küldése (napló)") { DiagLog.share(this) }.apply {
            visibility = View.GONE
        }
        head.addView(stallReport)

        // lépés-táblázat: Lépés | Idő | Hátra | % (a képernyő szélességéhez igazítva, nincs oldalra görgetés)
        stepsBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        var recogWrap: View? = null
        stepsBox.addView(headerRow(20, "Lépés", "Idő" to S_TIME, "Hátra" to S_ETA, "%" to S_PCT))
        tRows.clear()
        for (i in 0 until SrtJob.WEIGHTS.size) {
            val wrap = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(5), 0, dp(5))
            }
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val icon = TextView(this).apply {
                textSize = 15f; gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(20), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val name = TextView(this).apply {
                textSize = 14f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            val note = TextView(this).apply {
                textSize = 11f
                setTextColor(sec)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                visibility = View.GONE
            }
            val nameCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(name); addView(note)
            }
            val time = monoCell(S_TIME, sec)
            val eta = monoCell(S_ETA, sec)
            val pct = monoCell(S_PCT, Ui.color(this, R.color.textPrimary))
            line.addView(icon); line.addView(nameCol); line.addView(time); line.addView(eta); line.addView(pct)
            val bar = thinBar()
            wrap.addView(line)
            wrap.addView(bar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4)
            ).apply { topMargin = dp(4); marginStart = dp(20) })
            stepsBox.addView(wrap)
            if (i == SrtJob.ST_RECOG) recogWrap = wrap
            tRows.add(TRow(wrap, icon, name, note, time, eta, pct, bar))
        }
        head.addView(stepsBox)

        // darabok: részletes lista (beszédhossz, idő, hátra, haladás, tempó) – a darab-számlálók a Felismerés sor alatt vannak
        chunkSection = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(2), 0, dp(6))
            visibility = View.GONE
        }
        chunkTitle = TextView(this).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Ui.color(this@FileSubtitleActivity, R.color.textPrimary))
        }
        chunkSection.addView(chunkTitle)
        chunkSection.addView(headerRow(
            20, "Darab", "Beszéd" to C_LEN, "Idő" to C_TIME, "Hátra" to C_ETA, "%" to C_PCT
        ).apply { setPadding(0, dp(10), 0, dp(2)) })
        cRows.clear()
        for (k in 0 until CHUNK_ROWS) {
            val c = buildChunkRow(sec)
            cRows.add(c)
            chunkSection.addView(c.root)
        }
        chunkMore = Ui.small(this, "")
        chunkSection.addView(chunkMore)
        // a darabok a 4. lépés (Felismerés + fordítás) alatt, a lépés-táblázaton belül
        stepsBox.addView(chunkSection, stepsBox.indexOfChild(recogWrap) + 1)
        progressSection.addView(head)

        // élő előnézet: az utoljára felismert sorok (így azonnal látszik, jó nyelven halad-e)
        previewBox = Ui.card(this).apply { visibility = View.GONE }
        previewBox.addView(Ui.title(this, "Élő átirat – ahogy a hangból szöveg lesz", 15f))
        previewText = TextView(this).apply {
            textSize = 12.5f
            setTextColor(Ui.color(this@FileSubtitleActivity, R.color.textPrimary))
            setTextIsSelectable(true)
        }
        previewBox.addView(previewText)
        flow = TranscriptFlow(previewText)
        // közvetlenül a hullám pozíciója alá
        head.addView(previewBox, head.indexOfChild(posText) + 1)
        g.addView(progressSection)

        // ---------- kész: összegzés
        resultSection = Ui.card(this)
        checkView = CheckView(this)
        resultSection.addView(checkView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })
        resultTitle = Ui.title(this, "", 18f)
        resultText = Ui.body(this, "")
        resultSection.addView(resultTitle)
        resultSection.addView(resultText)
        resultSection.addView(Ui.button(this, "Új feldolgozás", true) {
            SrtJob.clearFinished()
            watchedRun = false
            saveAsked = false
            closeScheduled = false
            render()
        })
        resultSection.addView(Ui.smallButton(this, "Hibajelentés / napló küldése…") { DiagLog.share(this) })
        resultSection.addView(Ui.button(this, "Bezárás", false) { finish() })
        g.addView(resultSection)

        // ---------- fájlok állapota (futás közben is bővíthető)
        val qCard = Ui.card(this)
        qCard.addView(Ui.title(this, "Fájlok", 16f))
        runQueueBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        qCard.addView(runQueueBox)
        qCard.addView(Ui.smallButton(this, "+ Fájl hozzáadása a sorhoz") { pickFiles() }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(8)
        })
        g.addView(qCard)

        // ---------- az egyetlen futás közbeni beállítás: a prioritás
        perfRunCard = makePerfCard()
        g.addView(perfRunCard)
        g.addView(makeBgHint())

        cancelBtn = Ui.button(this, "Megszakítás", false) {
            SrtJobService.cancel(this)
            Toast.makeText(this, "Megszakítás…", Toast.LENGTH_SHORT).show()
        }
        g.addView(cancelBtn)
    }

    // ------------------------------------------------ haladás-táblázatok: közös elemek

    private val S_TIME = 48
    private val S_ETA = 52
    private val S_PCT = 36
    private val C_LEN = 44
    private val C_TIME = 48
    private val C_ETA = 52
    private val C_PCT = 38

    /** Jobbra igazított, fix szélességű, egysoros számcella. */
    private fun monoCell(w: Int, color: Int) = TextView(this).apply {
        textSize = 12f
        typeface = Typeface.MONOSPACE
        gravity = Gravity.END
        maxLines = 1
        setTextColor(color)
        layoutParams = LinearLayout.LayoutParams(dp(w), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun thinBar() = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Ui.color(this@FileSubtitleActivity, R.color.stroke))
        visibility = View.GONE
    }

    private fun tint(bar: ProgressBar, c: Int) {
        val l = android.content.res.ColorStateList.valueOf(c)
        bar.progressTintList = l
        bar.indeterminateTintList = l
    }

    /** Fejlécsor: [lead] üres hely (jelző), a rugalmas első oszlop, majd a fix szélességű oszlopok. */
    private var seekPlayer: android.media.MediaPlayer? = null

    /** A hullámra koppintva a forrásfájl hangja az adott ponttól ~6 másodpercig megszólal. */
    private fun playAudioAt(ms: Long) {
        val q = SrtJob.queue.firstOrNull { it.state == SrtJob.Q_RUN } ?: SrtJob.queue.firstOrNull() ?: return
        try {
            seekPlayer?.release()
            val mp = android.media.MediaPlayer()
            mp.setDataSource(this, q.uri)
            mp.setOnPreparedListener { m ->
                m.seekTo(ms.toInt())
                m.start()
                main.postDelayed({ try { if (m.isPlaying) m.pause() } catch (e: Exception) { DiagLog.swallow("FileSubtitleActivity", e) } }, 6000)
            }
            mp.setOnErrorListener { _, _, _ -> true }
            mp.prepareAsync()
            seekPlayer = mp
        } catch (_: Exception) {
            Toast.makeText(this, "Nem sikerült lejátszani ezt a pontot.", Toast.LENGTH_SHORT).show()
        }
    }

    /** Melyik hangszakaszt dolgozza fel éppen (pl. "Éppen: 4:45 – 10:46 · Darab 1/2"). */
    private fun positionLabel(job: SrtJob): String {
        val st = job.chunkState
        val r = job.waveRanges
        if (st.isEmpty() || r.size < st.size * 2) return ""
        val fr = job.chunkFracCopy()
        val act = st.indices.filter { st[it] == 1 }
        if (act.isEmpty()) return if (st.all { it == 2 }) "Kész: a teljes hang feldolgozva" else "Várakozás a következő szakaszra…"
        return act.take(2).joinToString("   ") { i ->
            val a = r[2 * i]
            val b = r[2 * i + 1]
            val cur = a + ((b - a) * fr.getOrElse(i) { 0.0 }.coerceIn(0.0, 1.0)).toLong()
            "Éppen: ${fmtDur(cur)} (${fmtDur(a)} – ${fmtDur(b)}) · ${i + 1}/${st.size}. darab"
        } + if (act.size > 2) " …" else ""
    }

    private fun headerRow(lead: Int, first: String, vararg cols: Pair<String, Int>): LinearLayout {
        val sec = Ui.color(this, R.color.textSecondary)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(2))
        }
        row.addView(View(this), LinearLayout.LayoutParams(dp(lead), 1))
        row.addView(TextView(this).apply {
            text = first; textSize = 11f; setTextColor(sec)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        for ((t, w) in cols) {
            row.addView(TextView(this).apply {
                text = t; textSize = 11f; setTextColor(sec); gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(dp(w), ViewGroup.LayoutParams.WRAP_CONTENT)
            })
        }
        return row
    }

    private fun buildChunkRow(sec: Int): CRow {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(4), 0, dp(4))
        }
        val line = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = TextView(this).apply {
            textSize = 14f; gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(20), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val name = TextView(this).apply {
            textSize = 13f
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val len = monoCell(C_LEN, sec)
        val time = monoCell(C_TIME, sec)
        val eta = monoCell(C_ETA, sec)
        val pct = monoCell(C_PCT, Ui.color(this, R.color.textPrimary))
        line.addView(icon); line.addView(name); line.addView(len); line.addView(time); line.addView(eta); line.addView(pct)
        val bar = thinBar()
        val note = TextView(this).apply {
            textSize = 11f
            setTextColor(sec)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(dp(20), 0, 0, 0)
            visibility = View.GONE
        }
        root.addView(line)
        root.addView(bar, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(4)
        ).apply { topMargin = dp(3); marginStart = dp(20) })
        root.addView(note)
        return CRow(root, icon, name, len, time, eta, pct, bar, note)
    }

    /** Tempó: hány másodpercnyi munka jut 1 másodpercnyi beszédre (magyar tizedesvesszővel). */
    private fun fmtRatio(tookMs: Long, speechMs: Long): String =
        String.format(java.util.Locale.forLanguageTag("hu-HU"), "%.1f", tookMs.toDouble() / speechMs.coerceAtLeast(1L))

    /** Teljesítmény-csúszka; több példány (beállítás + futás) is szinkronban marad. */
    private fun makePerfCard(): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.title(this, "Prioritás (teljesítmény)", 16f))
        val label = Ui.body(this, "")
        card.addView(label)
        val seek = android.widget.SeekBar(this).apply {
            max = Perf.MAX - Perf.MIN
            progress = Perf.level - Perf.MIN
        }
        val desc = Ui.small(this, "")
        card.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        card.addView(desc)
        card.addView(Ui.small(this,
            "Futás közben is átállítható (a prioritás kb. 1 mp-en belül él, a szálszám a következő darabtól). " +
                "Magasabb szinten gyorsabb, de melegebb a telefon és hamarabb fogy az akku. A számítás a processzoron fut."))
        perfSeeks.add(seek)
        perfLabels.add(label)
        perfDescs.add(desc)
        seek.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) {
                    Perf.set(this@FileSubtitleActivity, p + Perf.MIN)
                    SubPrefs.setProfile(this@FileSubtitleActivity, "custom")
                    refreshProfiles()
                    syncPerf()
                }
            }
            override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {}
        })
        syncPerf()
        return card
    }

    private fun syncPerf() {
        for (s in perfSeeks) if (s.progress != Perf.level - Perf.MIN) s.progress = Perf.level - Perf.MIN
        for (l in perfLabels) l.text = "Szint: ${Perf.level}/5 – ${Perf.name()}"
        for (d in perfDescs) d.text = Perf.describe()
    }

    private fun makeBgHint(): LinearLayout {
        val hint = Ui.card(this)
        hint.addView(Ui.title(this, "Háttérben is fusson", 16f))
        hint.addView(Ui.small(this,
            "A feldolgozás előtérszolgáltatásként fut: másik appra válthatsz, vagy zárolhatod a telefont. " +
                "Egyes telefonok az akkumulátor-optimalizálással ettől még lelassíthatják vagy leállíthatják – " +
                "kapcsold ki az apphoz (Korlátozás nélkül)."))
        hint.addView(Ui.button(this, "Akkumulátor-optimalizálás kikapcsolása", false) {
            BatterySettings.open(this)
        })
        bgHints.add(hint)
        return hint
    }

    // ------------------------------------------------------------ mentés helye (2 választás)

    /** Választható sor: a kiválasztott kerete színes, jelölője kitöltött – jól látszik, melyik aktív. */
    private fun optionRow(title: String, sub: String, selected: Boolean, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                if (selected) {
                    setColor(androidx.core.graphics.ColorUtils.setAlphaComponent(Ui.color(this@FileSubtitleActivity, R.color.primary), 40))
                    setStroke(dp(2), Ui.color(this@FileSubtitleActivity, R.color.primary))
                } else {
                    setColor(android.graphics.Color.TRANSPARENT)
                    setStroke(dp(1), Ui.color(this@FileSubtitleActivity, R.color.stroke))
                }
            }
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
            setOnClickListener { Haptics.click(this@FileSubtitleActivity); onClick() }
        }
        row.addView(TextView(this).apply {
            text = if (selected) "◉" else "○"
            textSize = 22f
            setTextColor(Ui.color(this@FileSubtitleActivity, if (selected) R.color.primary else R.color.textSecondary))
        }, LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(this).apply {
            text = if (selected) "$title  ✓ kiválasztva" else title
            textSize = 15f
            setTypeface(typeface, if (selected) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(Ui.color(this@FileSubtitleActivity, R.color.textPrimary))
        })
        texts.addView(Ui.small(this, sub))
        row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        return row
    }

    private fun refreshWhere() {
        if (!::saveBox.isInitialized) return
        saveBox.removeAllViews()
        val mode = OutputPrefs.saveMode(this)
        val beside = mode == OutputPrefs.SAVE_BESIDE
        val allFiles = OutputWriter.hasAllFiles()
        val canAllFiles = OutputWriter.allFilesDeclared(this)
        val tree = OutputPrefs.defaultTree(this)

        saveBox.addView(optionRow(
            "A videó mellé (alapértelmezett)",
            "A program megnézi, honnan töltötted be a videót, és oda menti a feliratot." +
                if (allFiles) "\n✓ teljes fájlhozzáférés megvan"
                else if (canAllFiles) "\n✗ ehhez teljes fájlhozzáférés kell – nélküle az alapértelmezett mappába ment"
                else "\nA videó mappájához mentéskor egyszer engedélyt kérek (mappa-hozzáférés); nélküle az alapértelmezett mappába ment",
            beside
        ) {
            OutputPrefs.setSaveMode(this, OutputPrefs.SAVE_BESIDE)
            refreshWhere()
        })
        if (beside && !allFiles && canAllFiles && android.os.Build.VERSION.SDK_INT >= 30) {
            saveBox.addView(Ui.button(this, "Teljes fájlhozzáférés megadása", false) {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    try { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    catch (e: Exception) { DiagLog.swallow("FileSubtitleActivity", e) }
                }
            })
        }

        saveBox.addView(optionRow(
            "Alapértelmezett mappába",
            if (tree != null) "✓ ${OutputWriter.folderLabel(tree)}" else "✗ még nincs mappa kiválasztva",
            !beside
        ) {
            OutputPrefs.setSaveMode(this, OutputPrefs.SAVE_DEFAULT)
            if (OutputPrefs.defaultTree(this) == null) pickDefaultTree.launch(null) else refreshWhere()
        })
        if (!beside) {
            saveBox.addView(Ui.button(this, if (tree != null) "Mappa módosítása…" else "Mappa kiválasztása…", false) {
                pickDefaultTree.launch(null)
            })
        }
    }

    // ------------------------------------------------------------------ életciklus

    override fun onStart() {
        super.onStart()
        SrtJob.addListener(jobListener)
        refreshWhere()
        refreshEngineInfo()
        render()
        if (SrtJob.batchRunning) {
            main.removeCallbacks(tick)
            main.post(tick)
        }
    }

    override fun onStop() {
        SrtJob.removeListener(jobListener)
        main.removeCallbacks(tick)
        super.onStop()
    }

    // ------------------------------------------------------------------ megjelenítés

    private fun fmtDur(ms: Long) = SrtJob.fmtDur(ms)

    private fun refreshEngineInfo() {
        if (!::engineInfo.isInitialized) return
        val src = selectedSrc()
        val asr = try { Engines.asrFor(this, prefs, src) } catch (_: Exception) { null }
        val lang = SrtJobService.langName(src)
        engineInfo.text = when {
            asr == null -> ""
            asr.kind == Engines.K_SYSTEM && src != "hu" ->
                "⚠ A hang nyelve: $lang – ehhez nincs offline felismerő modell. Töltsd le a Whisper modellt a Modellek képernyőn."
            asr.kind == Engines.K_SYSTEM -> "Felismerő: Android beépített felismerő (magyar) · a hang nyelve: $lang"
            else -> "Felismerő: ${asr.label} · a hang nyelve: $lang"
        }
    }

    private fun stateIcon(s: Int) = when (s) {
        SrtJob.Q_RUN -> "▶"
        SrtJob.Q_DONE -> "✓"
        SrtJob.Q_ERR -> "✗"
        SrtJob.Q_STOP -> "⏸"
        else -> "○"
    }

    private fun stateColor(s: Int) = Ui.color(
        this, when (s) {
            SrtJob.Q_DONE -> R.color.ok
            SrtJob.Q_ERR -> R.color.err
            SrtJob.Q_STOP -> R.color.warn
            SrtJob.Q_RUN -> R.color.primary
            else -> R.color.textSecondary
        }
    )

    /** A fájllista felépítése. Csak akkor épül újra, ha a tartalma változott (különben a gombok elveszítenék az érintést). */
    private fun renderQueue(box: LinearLayout, canRemove: Boolean, sigOld: String): String {
        val items = SrtJob.queue.toList()
        val sig = items.joinToString("|") { "${System.identityHashCode(it)}:${it.state}:${it.saved}:${it.detail.hashCode()}" } +
            "#$canRemove"
        if (sig == sigOld) return sig
        box.removeAllViews()
        if (items.isEmpty()) {
            box.addView(Ui.small(this, "Még nincs kiválasztva fájl."))
            return sig
        }
        for (item in items) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(6), 0, dp(6))
            }
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            line.addView(TextView(this).apply {
                text = stateIcon(item.state)
                textSize = 16f
                gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(stateColor(item.state))
            }, LinearLayout.LayoutParams(dp(28), ViewGroup.LayoutParams.WRAP_CONTENT))
            line.addView(TextView(this).apply {
                text = item.name
                textSize = 14.5f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.MIDDLE
                setTextColor(Ui.color(this@FileSubtitleActivity, R.color.textPrimary))
                if (item.state == SrtJob.Q_RUN) setTypeface(typeface, Typeface.BOLD)
            }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            if (item.state == SrtJob.Q_WAIT && canRemove) {
                line.addView(Ui.smallButton(this, "✕") { SrtJob.removeWaiting(item); render() })
            }
            row.addView(line)
            if (item.detail.isNotBlank()) {
                row.addView(Ui.small(this, item.detail).apply { setPadding(dp(28), dp(2), 0, 0) })
            }
            if (item.state == SrtJob.Q_DONE && item.srt != null) {
                val nSusp = try { item.cues?.let { c -> LineQuality.summarize(c).count { it.isNotEmpty() } } ?: 0 } catch (_: Exception) { 0 }
                if (nSusp > 0) {
                    row.addView(Ui.smallButton(this, "⚠ $nSusp gyanús sor átnézése (meghallgatással)…") {
                        SrtEditorActivity.open(this, SrtJob.queue.indexOf(item), true)
                    }.apply { (layoutParams as? LinearLayout.LayoutParams)?.apply { topMargin = dp(6); marginStart = dp(28) } })
                }
                row.addView(Ui.smallButton(this, "Átnézés / szerkesztés…") {
                    SrtEditorActivity.open(this, SrtJob.queue.indexOf(item))
                }.apply { (layoutParams as? LinearLayout.LayoutParams)?.apply { topMargin = dp(6); marginStart = dp(28) } })
            }
            if (item.state == SrtJob.Q_DONE && item.localFile.isNotBlank()) {
                row.addView(Ui.smallButton(this, "Megnyitás lejátszóval (felirattal)…") {
                    PlayerLauncher.open(this, item.uri, java.io.File(TranscriptStore.dir(this), item.localFile))
                }.apply { (layoutParams as? LinearLayout.LayoutParams)?.apply { topMargin = dp(6); marginStart = dp(28) } })
            }
            if (item.state == SrtJob.Q_DONE && !item.saved && item.srt != null) {
                row.addView(Ui.smallButton(this, "SRT mentése a telefonra…") {
                    pendingSaveItem = item
                    saveSrtLauncher.launch(item.srtName.ifBlank { "felirat.srt" })
                }.apply { (layoutParams as? LinearLayout.LayoutParams)?.apply { topMargin = dp(6); marginStart = dp(28) } })
            }
            box.addView(row)
        }
        return sig
    }

    /** A közös állapot (SrtJob) alapján újrarajzolja a képernyőt. Csak a főszálon hívjuk. */
    private fun render() {
        if (!::runGroup.isInitialized) return
        val job = SrtJob
        val running = job.batchRunning
        val results = !running && job.queue.any { it.state != SrtJob.Q_WAIT && it.state != SrtJob.Q_RUN }
        val showRun = running || results
        if (running) watchedRun = true

        setupGroup.visibility = if (showRun) View.GONE else View.VISIBLE
        runGroup.visibility = if (showRun) View.VISIBLE else View.GONE
        progressSection.visibility = if (running) View.VISIBLE else View.GONE
        resultSection.visibility = if (results) View.VISIBLE else View.GONE
        perfRunCard.visibility = if (running) View.VISIBLE else View.GONE
        cancelBtn.visibility = if (running) View.VISIBLE else View.GONE
        val ignoring = BatterySettings.isIgnoring(this)
        for (h in bgHints) h.visibility = if (ignoring || results) View.GONE else View.VISIBLE
        syncPerf()

        if (!showRun) {
            val waiting = job.queue.count { it.state == SrtJob.Q_WAIT }
            setupSig = renderQueue(setupQueueBox, true, setupSig)
            pickBtn.text = if (waiting == 0) "Fájlok kiválasztása…" else "További fájl hozzáadása…"
            quickBtn.text = if (waiting == 0) "Fájlok kiválasztása…"
            else "▶ Indítás az utolsó beállításokkal" + if (waiting > 1) " ($waiting fájl)" else ""
            updateEstimate()
            startBtn.isEnabled = waiting > 0
            startBtn.alpha = if (waiting > 0) 1f else 0.45f
            startBtn.text = when {
                waiting == 0 -> "SRT készítése"
                waiting == 1 -> "SRT készítése"
                else -> "SRT készítése ($waiting fájl, sorban)"
            }
            return
        }

        runSig = renderQueue(runQueueBox, running, runSig)
        if (running) renderProgress()
        if (results) renderResults()
    }

    private fun renderProgress() {
        val job = SrtJob
        val now = System.currentTimeMillis()
        val cur = job.current
        val secondary = Ui.color(this, R.color.textSecondary)
        val primary = Ui.color(this, R.color.textPrimary)
        val okC = Ui.color(this, R.color.ok)
        val warnC = Ui.color(this, R.color.warn)
        val errC = Ui.color(this, R.color.err)
        val recogC = SrtJob.COLORS[SrtJob.ST_RECOG]

        fileLabel.text = when {
            cur == null -> "Indítás…"
            job.fileTotal > 1 -> "Fájl ${job.fileNo}/${job.fileTotal} · ${cur.name}"
            else -> cur.name
        }
        langLine.text = buildString {
            if (job.engineLabel.isNotEmpty()) append("Felismerő: ${job.engineLabel}")
            if (job.langLabel.isNotEmpty()) {
                if (isNotEmpty()) append(" · ")
                append("nyelv: ${job.langLabel}")
            }
            if (job.speechInfo.isNotEmpty()) {
                if (isNotEmpty()) append("\n")
                append(job.speechInfo)
            }
        }
        langLine.visibility = if (langLine.text.isEmpty()) View.GONE else View.VISIBLE

        // --- teljes folyamat: az ETA és a befejezés órája csak itt szerepel
        val elapsedAll = now - job.startMs
        // hátralévő idő a teljes folyamatra (ms), -1 = még nem becsülhető
        val totRem: Long = when {
            job.etaAtMs > now && job.curStep >= SrtJob.ST_RECOG -> smoothRem("t", job.etaMs, now)
            job.overall >= 1 && elapsedAll >= 2000 -> smoothRem("t", elapsedAll * (100 - job.overall) / job.overall, now)
            else -> -1L
        }
        val basis = when {
            job.etaAtMs > now && job.curStep >= SrtJob.ST_RECOG && job.etaBasis.isNotEmpty() -> "Becslés: ${job.etaBasis}"
            job.curStep < SrtJob.ST_RECOG && job.overall >= 1 -> "Durva becslés – a felismerés indulása után pontosodik"
            else -> ""
        }
        barTotal.set("Teljes folyamat", job.overall, basis)
        val env = job.waveEnv
        wave.visibility = View.VISIBLE
        if (env.isNotEmpty() && shownEnv !== env) {
            wave.setEnvelope(env, job.waveTotalMs)
            shownEnv = env
        }
        val wr = job.waveRanges
        if (shownRanges !== wr) { wave.setRanges(wr); shownRanges = wr }
        if (job.chunkState.isNotEmpty()) {
            wave.setProgress(job.chunkState.copyOf(), job.chunkFracCopy())
        }
        posText.text = positionLabel(job)
        engineLine.text = SrtJob.engineText().let { t ->
            if (SrtJob.names.isNotEmpty()) t + (if (t.isNotEmpty()) "\n" else "") + "Nevek: " + SrtJob.names.joinToString(", ") else t
        }
        engineLine.visibility = if (engineLine.text.isNotEmpty()) View.VISIBLE else View.GONE
        pipeline.set(IntArray(SrtJob.WEIGHTS.size) { job.steps[it].state }, job.steps.getOrNull(job.curStep)?.pct ?: 0)
        tileElapsed.text = fmtDur(elapsedAll)
        tileRem.text = if (totRem >= 0) "kb. ${fmtDur(totRem)}" else "számítás…"
        tileFin.text = if (totRem >= 0) SrtJob.fmtClock(now + totRem) else "–"

        // --- lépés-táblázat (lépésenként: idő, hátra, %; a darabok külön listában vannak)
        var unitsRem = 0.0
        for (i in 0 until SrtJob.WEIGHTS.size) {
            val st = job.steps[i]
            unitsRem += when (st.state) {
                SrtJob.DONE -> 0.0
                SrtJob.ACTIVE -> SrtJob.WEIGHTS[i] * (100 - st.pct.coerceIn(0, 100)) / 100.0
                else -> SrtJob.WEIGHTS[i].toDouble()
            }
        }
        val perUnit = if (totRem >= 0 && unitsRem > 0) totRem / unitsRem else -1.0
        var finKnown = true      // a sorban álló lépések becslése csak akkor értelmes, ha az aktív lépés ideje ismert
        for (i in 0 until SrtJob.WEIGHTS.size) {
            val v = tRows[i]
            val st = job.steps[i]
            val color = SrtJob.COLORS[i]
            v.name.text = "${i + 1}. ${job.label(i)}"
            when (st.state) {
                SrtJob.DONE -> { v.icon.text = "✓"; v.icon.setTextColor(okC); v.name.setTextColor(primary); v.name.setTypeface(null, Typeface.NORMAL) }
                SrtJob.ACTIVE -> { v.icon.text = "●"; v.icon.setTextColor(color); v.name.setTextColor(primary); v.name.setTypeface(null, Typeface.BOLD) }
                SrtJob.ERROR -> { v.icon.text = "✗"; v.icon.setTextColor(errC); v.name.setTextColor(errC); v.name.setTypeface(null, Typeface.BOLD) }
                SrtJob.STOPPED -> { v.icon.text = "⏸"; v.icon.setTextColor(warnC); v.name.setTextColor(warnC); v.name.setTypeface(null, Typeface.NORMAL) }
                else -> { v.icon.text = "○"; v.icon.setTextColor(secondary); v.name.setTextColor(secondary); v.name.setTypeface(null, Typeface.NORMAL) }
            }
            // megjegyzés: a felismerésnél a darab-számlálók (egyetlen helyen), egyébként a lépés saját megjegyzése
            var noteTxt = if (st.state == SrtJob.DONE || st.state == SrtJob.ACTIVE) st.comment else ""
            if (i == SrtJob.ST_RECOG && st.state == SrtJob.ACTIVE && job.chunkState.isNotEmpty()) {
                val work = job.chunkState.count { it == 1 }
                val wait = job.chunkState.count { it == 0 }
                noteTxt = "kész ${job.chunkDone}/${job.chunkTotal} darab · dolgozik $work · vár $wait"
            }
            // ugyanaz a felismerő-név már a fejlécben látszik
            if (noteTxt == job.engineLabel) noteTxt = ""
            v.note.text = noteTxt
            v.note.visibility = if (noteTxt.isBlank()) View.GONE else View.VISIBLE
            tint(v.bar, color)
            v.bar.visibility = if (st.state == SrtJob.ACTIVE) View.VISIBLE else View.GONE
            when (st.state) {
                SrtJob.DONE -> {
                    v.time.text = fmtDur(st.durMs)
                    v.eta.text = "–"
                    v.pct.text = "100%"
                }
                SrtJob.ACTIVE -> {
                    val p = st.pct
                    val el = now - job.stepStartMs
                    val own = when {
                        i == SrtJob.ST_RECOG && job.etaAtMs > now -> smoothRem("r", job.etaMs, now)
                        p >= 1 && el >= 2000 -> smoothRem("s$i", el * (100 - p) / p, now)
                        perUnit >= 0 -> (SrtJob.WEIGHTS[i] * (100 - p.coerceAtLeast(0)) / 100.0 * perUnit).toLong()
                        else -> -1L
                    }
                    v.time.text = fmtDur(el)
                    if (own >= 0) v.eta.text = fmtDur(own) else { finKnown = false; v.eta.text = "…" }
                    if (p < 0) { v.bar.isIndeterminate = true; v.pct.text = "…" }
                    else { v.bar.isIndeterminate = false; v.bar.progress = p.coerceIn(0, 100); v.pct.text = "${p.coerceIn(0, 100)}%" }
                }
                else -> {
                    v.time.text = if (st.state == SrtJob.ERROR || st.state == SrtJob.STOPPED) fmtDur(st.durMs) else ""
                    v.pct.text = ""
                    v.eta.text = if (finKnown && perUnit >= 0 && st.state == SrtJob.PENDING) fmtDur((SrtJob.WEIGHTS[i] * perUnit).toLong()) else "–"
                }
            }
        }

        // --- darabok: részletes lista
        val n = job.chunkState.size
        chunkSection.visibility = if (n > 0) View.VISIBLE else View.GONE
        if (n > 0) {
            val infos = job.chunkInfo
            // átlagos tempó a kész darabokból (ismételt kijelzés nélkül, csak a címben)
            var sumTook = 0L
            var sumSpeech = 0L
            for (k in 0 until n) {
                val inf = infos.getOrNull(k) ?: continue
                if (job.chunkState[k] == 2 && inf.tookMs >= 2000 && inf.speechMs > 0) { sumTook += inf.tookMs; sumSpeech += inf.speechMs }
            }
            chunkTitle.text = if (sumSpeech > 0) "Darabok · átlagos tempó: ${fmtRatio(sumTook, sumSpeech)}× a beszéd hossza"
            else "Darabok"

            // melyik darabok látszanak: kevésnél mind, soknál az aktívak + a legutóbbi kész + a következő várakozók
            val picked: List<Int> = if (n <= CHUNK_ROWS) (0 until n).toList() else {
                val act = (0 until n).filter { job.chunkState[it] == 1 }.take(CHUNK_ROWS)
                val left = CHUNK_ROWS - act.size
                val doneIdx = (0 until n).filter { job.chunkState[it] == 2 }.takeLast(left / 2)
                val pendIdx = (0 until n).filter { job.chunkState[it] == 0 }.take(left - doneIdx.size)
                (act + doneIdx + pendIdx).sorted()
            }
            for (r in cRows.indices) {
                val c = cRows[r]
                val i = picked.getOrNull(r)
                if (i == null) { c.root.visibility = View.GONE; continue }
                c.root.visibility = View.VISIBLE
                val inf = infos.getOrNull(i)
                c.name.text = "Darab ${i + 1}"
                c.len.text = if (inf != null && inf.speechMs > 0) fmtDur(inf.speechMs) else "–"
                when (job.chunkState[i]) {
                    2 -> {
                        c.icon.text = "✓"; c.icon.setTextColor(okC); c.name.setTextColor(primary)
                        c.time.text = if (inf != null && inf.tookMs > 0) fmtDur(inf.tookMs) else "–"
                        c.eta.text = "–"
                        c.pct.text = "100%"
                        c.bar.visibility = View.GONE
                        val parts = ArrayList<String>()
                        if (inf != null && inf.cues >= 0) parts.add("${inf.cues} blokk")
                        if (inf != null && inf.tookMs >= 2000 && inf.speechMs > 0) parts.add("tempó ${fmtRatio(inf.tookMs, inf.speechMs)}×")
                        c.note.text = parts.joinToString(" · ")
                        c.note.visibility = if (parts.isEmpty()) View.GONE else View.VISIBLE
                    }
                    1 -> {
                        val pc = ((inf?.frac ?: 0.0) * 100).toInt().coerceIn(0, 99)
                        val el = if (inf != null && inf.startMs > 0) now - inf.startMs else 0L
                        val rem = if (pc >= 1 && el >= 2000) smoothRem("c${i + 1}", el * (100 - pc) / pc, now) else -1L
                        c.icon.text = "●"; c.icon.setTextColor(if (job.cooling) warnC else recogC)
                        c.name.setTextColor(primary)
                        c.time.text = fmtDur(el)
                        c.eta.text = if (rem >= 0) fmtDur(rem) else "…"
                        c.pct.text = "$pc%"
                        tint(c.bar, recogC)
                        c.bar.isIndeterminate = false
                        c.bar.progress = pc
                        c.bar.visibility = View.VISIBLE
                        // mit csinál éppen (felismerés %, fordítás x/y mondat…) – csak itt látszik
                        val what = job.phases[i].orEmpty()
                        c.note.text = what
                        c.note.visibility = if (what.isBlank()) View.GONE else View.VISIBLE
                    }
                    else -> {
                        c.icon.text = "○"; c.icon.setTextColor(secondary); c.name.setTextColor(secondary)
                        c.time.text = ""; c.eta.text = ""; c.pct.text = ""
                        c.bar.visibility = View.GONE
                        c.note.visibility = View.GONE
                    }
                }
            }
            val hidden = n - picked.size
            chunkMore.text = "… még $hidden darab nincs kiírva (kész ${job.chunkDone} · vár ${job.chunkState.count { it == 0 }})"
            chunkMore.visibility = if (hidden > 0) View.VISIBLE else View.GONE
            // sok darabnál a térkép adja az áttekintést
        }

        // hűtési szünet
        coolBanner.visibility = if (job.cooling) View.VISIBLE else View.GONE
        if (job.cooling) coolBanner.text = job.waitReason.ifBlank { "Szünet (hűtés)" }

        // "megállt a háttérben?" jelzés
        val stalled = !job.cooling && job.running && now - job.lastProgressMs > SrtJobService.STALL_WARN_MS
        stallBanner.visibility = if (stalled) View.VISIBLE else View.GONE
        stallReport.visibility = if (stalled || job.running) View.VISIBLE else View.GONE
        if (stalled) {
            stallBanner.text = "⚠ Már ${fmtDur(now - job.lastProgressMs)} ideje nincs új haladás. " +
                "Ha a telefon a háttérben lelassította az appot, kapcsold ki az akkumulátor-optimalizálást (lent), " +
                "vagy tartsd előtérben a képernyőt."
        }

        // élő előnézet
        val shownFlow = flow.update(
            job.previewCues,
            Ui.color(this, R.color.textPrimary),
            Ui.color(this, R.color.primary)
        )
        previewBox.visibility = if (shownFlow) View.VISIBLE else View.GONE
    }

    private fun renderResults() {
        val items = SrtJob.queue.filter { it.state != SrtJob.Q_WAIT }
        val ok = items.count { it.state == SrtJob.Q_DONE }
        val err = items.count { it.state == SrtJob.Q_ERR }
        val stop = items.count { it.state == SrtJob.Q_STOP }
        resultTitle.text = when {
            err == 0 && stop == 0 -> "✓ Kész"
            ok == 0 && err > 0 && stop == 0 -> "✗ Hiba"
            else -> "Befejezve"
        }
        resultTitle.setTextColor(Ui.color(this, when {
            err == 0 && stop == 0 -> R.color.ok
            ok == 0 && err > 0 -> R.color.err
            else -> R.color.warn
        }))
        val allGood = err == 0 && stop == 0 && ok > 0
        checkView.visibility = if (allGood) View.VISIBLE else View.GONE
        if (allGood && !checkPlayed) { checkPlayed = true; checkView.play() }
        if (!allGood) checkPlayed = false
        val parts = ArrayList<String>()
        parts.add("$ok fájl kész")
        if (err > 0) parts.add("$err hibás")
        if (stop > 0) parts.add("$stop megszakítva")
        val blocks = items.filter { it.state == SrtJob.Q_DONE }.sumOf { it.cueCount }
        resultText.text = parts.joinToString(", ") +
            (if (ok > 0) " · összesen $blocks feliratblokk" else "") +
            (items.firstOrNull { it.state == SrtJob.Q_DONE && it.cues != null }?.let { q ->
                try { "\n" + QualityReport.build(q.cues!!).lines().joinToString(" · ") } catch (_: Exception) { "" }
            } ?: "") +
            (if (SrtJob.skippedParts > 0) "\n⚠ Az utolsó fájlnál ${SrtJob.skippedParts} darab részben kimaradt (a felismerés elakadt) – a naplóban a részletek." else "")

        // helyzethez igazított tippek
        try {
            val tips = ArrayList<String>()
            for (q in items) {
                if (q.state == SrtJob.Q_ERR) tips.addAll(Tips.forError(q.detail))
                if (q.state == SrtJob.Q_DONE) {
                    val flagged = q.cues?.let { c -> LineQuality.summarize(c).count { it.isNotEmpty() } } ?: 0
                    tips.addAll(Tips.forResult(q.cueCount, flagged, SrtJobService.langName(q.dir.substringBefore("_", "en"))))
                }
            }
            if (tips.isNotEmpty()) resultText.append("\n\n💡 " + tips.distinct().take(3).joinToString("\n💡 "))
        } catch (e: Exception) { DiagLog.swallow("FileSubtitleActivity", e) }

        // automatikus bezárás: minden kész fájl el van mentve
        if (watchedRun && !closeScheduled && OutputPrefs.autoClose(this) &&
            items.none { it.state == SrtJob.Q_DONE && !it.saved } && err == 0
        ) {
            closeScheduled = true
            main.postDelayed({ if (!isFinishing) finish() }, 2500)
        }
        // ha az első kész fájlnak nincs hova mentenie, magától felkínálja a mentést
        if (watchedRun && !saveAsked) {
            val unsaved = items.firstOrNull { it.state == SrtJob.Q_DONE && !it.saved && it.srt != null }
            saveAsked = true
            if (unsaved != null) {
                Haptics.click(this)
                pendingSaveItem = unsaved
                saveSrtLauncher.launch(unsaved.srtName.ifBlank { "felirat.srt" })
            }
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
        if (SrtJob.batchRunning) return
        if (SrtJob.queue.none { it.state == SrtJob.Q_WAIT }) {
            Toast.makeText(this, "Előbb válassz fájlt.", Toast.LENGTH_SHORT).show()
            return
        }
        val dir = if (prefs.meetingMode) Prefs.DIR_NONE else selectedDir()
        val needTranslate = dir != Prefs.DIR_NONE && !prefs.meetingMode
        watchedRun = true
        saveAsked = false
        closeScheduled = false
        // A munkát az előtér-szolgáltatás végzi: másik appra váltva / zárolt képernyővel is megy tovább.
        val mode = selectedMode()
        try {
            prefs.raw().edit().putInt("lastOut", mode.ordinal.let { if (it == 0) 0 else if (it == 1) 1 else 2 })
                .putString("lastSrc", selectedSrc())
                .putBoolean("lastTr", needTranslate).apply()
        } catch (e: Exception) { DiagLog.swallow("FileSubtitleActivity", e) }
        val tgtLang = if (needTranslate && mode != SrtExporter.Mode.SOURCE_ONLY) dir.substringAfter("_", selectedSrc()) else selectedSrc()
        SrtJobService.start(this, mode, selectedSrc(), dir, needTranslate, useDiarization, kind, tgtLang)
        main.removeCallbacks(tick)
        main.post(tick)
        render()
    }
}
