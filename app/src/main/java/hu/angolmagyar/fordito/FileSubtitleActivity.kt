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
 *  2) futás: csak a folyamat (aktuális darab, felismerés, teljes) és a prioritás látszik;
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
    private lateinit var status: TextView
    private lateinit var barChunk: BarRow
    private lateinit var barRecog: BarRow
    private lateinit var barTotal: BarRow
    private lateinit var stepsBox: LinearLayout
    private lateinit var coolBanner: TextView
    private lateinit var stallBanner: TextView
    private lateinit var stallReport: View
    private lateinit var chunkMap: ChunkMapView
    private lateinit var chunkLegend: TextView
    private lateinit var previewBox: LinearLayout
    private lateinit var previewText: TextView
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

    /** A folyamat-táblázat egy sora (lépés vagy alsor): jelző, név+megjegyzés, idő, hátra, kész, sáv, %. */
    private class TRow(
        val root: LinearLayout, val icon: TextView, val name: TextView, val note: TextView,
        val time: TextView, val eta: TextView, val fin: TextView, val bar: ProgressBar, val pct: TextView
    )

    /** A táblázat sorai: lépés-index, -1 = a felismerés alatti "Darab" alsor. */
    private val TABLE_SLOTS = intArrayOf(0, 1, 2, 3, -1, 4)

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
                    } catch (_: Exception) {
                    }
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
        } catch (_: Exception) {
        }
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

    /** Profilok (gyors vázlat … éjszakai) + formázási kapcsolók + szótár. */
    private fun makeProfileCard(): LinearLayout {
        val card = Ui.card(this)
        card.addView(Ui.title(this, "3. Profil és formázás", 16f))
        profileBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        card.addView(profileBox)
        card.addView(SwitchCompat(this).apply {
            text = "Olvasható felirat: hosszú blokkok szétvágása, időzítés, kiegyensúlyozott sortörés"
            minHeight = dp(52)
            isChecked = SubPrefs.reflow(this@FileSubtitleActivity)
            setOnCheckedChangeListener { _, c -> SubPrefs.setReflow(this@FileSubtitleActivity, c) }
        })
        card.addView(SwitchCompat(this).apply {
            text = "Jobb fordítás: mondatokba fogott (kontextusos) fordítás"
            minHeight = dp(52)
            isChecked = SubPrefs.contextTranslate(this@FileSubtitleActivity)
            setOnCheckedChangeListener { _, c -> SubPrefs.setContextTranslate(this@FileSubtitleActivity, c) }
        })
        card.addView(SwitchCompat(this).apply {
            text = "Beszélők színezése az SRT-ben"
            minHeight = dp(52)
            isChecked = SubPrefs.speakerColors(this@FileSubtitleActivity)
            setOnCheckedChangeListener { _, c -> SubPrefs.setSpeakerColors(this@FileSubtitleActivity, c) }
        })
        card.addView(Ui.button(this, "Szótár (nevek, javítások)…", false) { showGlossary() })
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
        val sv = android.widget.ScrollView(this).apply { addView(col) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Szótár")
            .setView(sv)
            .setPositiveButton("Mentés") { _, _ ->
                prefs.enFixes = en.text.toString()
                prefs.huFixes = hu.text.toString()
            }
            .setNegativeButton("Mégse", null)
            .show()
    }

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onDestroy() {
        // Ideiglenes tárgyalás mód: kilépéskor visszaállítjuk az eredeti beállításokat.
        if (isFinishing && ::prefs.isInitialized && prefs.meetingMode && prefs.meetingTemporary) {
            MeetingMode.clear(prefs)
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ felépítés

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

        // Ha a varázsló fájlválasztóval nyitotta, azonnal felajánljuk (csak az első létrehozáskor)
        if (autoStart && savedInstanceState == null && !SrtJob.batchRunning && SrtJob.queue.isEmpty()) {
            main.post { pickFiles() }
        }
    }

    private fun buildSetup(meeting: Boolean) {
        val g = setupGroup
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

        g.addView(makeProfileCard())

        // Teljesítmény (a futás közben is ugyanez marad az egyetlen beállítás)
        Perf.load(this)
        g.addView(makePerfCard())

        g.addView(makeBgHint())

        startBtn = Ui.button(this, "SRT készítése", true) { startProcess() }
        g.addView(startBtn)
    }

    private fun buildRun() {
        val g = runGroup

        // ---------- futás: csak a folyamat
        progressSection = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val head = Ui.card(this)
        fileLabel = Ui.title(this, "", 16f)
        head.addView(fileLabel)
        langLine = Ui.small(this, "")
        head.addView(langLine)
        status = Ui.small(this, "")
        head.addView(status)

        barChunk = BarRow(this)
        barRecog = BarRow(this)
        barTotal = BarRow(this)
        barRecog.color(SrtJob.COLORS[SrtJob.ST_RECOG])
        barTotal.color(Ui.color(this, R.color.primary))
        head.addView(barChunk.root)
        head.addView(barRecog.root)
        head.addView(barTotal.root)

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

        chunkMap = ChunkMapView(this)
        head.addView(chunkMap, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })
        chunkLegend = Ui.small(this, "").apply { visibility = View.GONE }
        head.addView(chunkLegend)

        // folyamat-táblázat (mint a PC-s Whisper felületen): Lépés | Idő | Hátra | Kész | Haladás | %
        stepsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val sec = Ui.color(this, R.color.textSecondary)
        fun hCell(t: String, w: Int, g: Int) = TextView(this).apply {
            text = t; textSize = 11f; setTextColor(sec); gravity = g
            layoutParams = LinearLayout.LayoutParams(dp(w), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val hdr = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 0, 0, dp(2))
        }
        hdr.addView(hCell("", 22, Gravity.START))
        hdr.addView(hCell("Lépés", 130, Gravity.START))
        hdr.addView(hCell("Idő", 46, Gravity.END))
        hdr.addView(hCell("Hátra", 56, Gravity.END))
        hdr.addView(hCell("Kész", 50, Gravity.END))
        hdr.addView(hCell("Haladás", 76, Gravity.CENTER))
        hdr.addView(hCell("%", 40, Gravity.END))
        stepsBox.addView(hdr)
        for (slot in TABLE_SLOTS) {
            val sub = slot < 0
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val icon = TextView(this).apply {
                textSize = 15f; gravity = Gravity.CENTER
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val name = TextView(this).apply {
                textSize = if (sub) 13f else 14f
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
                layoutParams = LinearLayout.LayoutParams(dp(130), ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(name); addView(note)
            }
            fun mono(w: Int) = TextView(this).apply {
                textSize = 12f; typeface = Typeface.MONOSPACE; gravity = Gravity.END
                setTextColor(sec)
                layoutParams = LinearLayout.LayoutParams(dp(w), ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            val time = mono(46)
            val eta = mono(56)
            val fin = mono(50)
            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Ui.color(this@FileSubtitleActivity, R.color.stroke))
                layoutParams = LinearLayout.LayoutParams(dp(68), dp(8)).apply { marginStart = dp(8); marginEnd = dp(0) }
            }
            val pct = mono(40).apply {
                setTextColor(Ui.color(this@FileSubtitleActivity, R.color.textPrimary))
            }
            row.addView(icon); row.addView(nameCol); row.addView(time); row.addView(eta); row.addView(fin)
            row.addView(bar); row.addView(pct)
            stepsBox.addView(row)
            tRows.add(TRow(row, icon, name, note, time, eta, fin, bar, pct))
        }
        val tableScroll = android.widget.HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            setPadding(0, dp(12), 0, 0)
            addView(stepsBox)
        }
        head.addView(tableScroll)
        progressSection.addView(head)

        // élő előnézet: az utoljára felismert sorok (így azonnal látszik, jó nyelven halad-e)
        previewBox = Ui.card(this).apply { visibility = View.GONE }
        previewBox.addView(Ui.title(this, "Élő előnézet (az utolsó felismert sorok)", 15f))
        previewText = TextView(this).apply {
            textSize = 12.5f
            typeface = Typeface.MONOSPACE
            setTextColor(Ui.color(this@FileSubtitleActivity, R.color.textPrimary))
            setTextIsSelectable(true)
        }
        previewBox.addView(previewText)
        progressSection.addView(previewBox)
        g.addView(progressSection)

        // ---------- kész: összegzés
        resultSection = Ui.card(this)
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
        val tree = OutputPrefs.defaultTree(this)

        saveBox.addView(optionRow(
            "A videó mellé (alapértelmezett)",
            "A program megnézi, honnan töltötted be a videót, és oda menti a feliratot." +
                if (allFiles) "\n✓ teljes fájlhozzáférés megvan"
                else "\n✗ ehhez teljes fájlhozzáférés kell – nélküle az alapértelmezett mappába ment",
            beside
        ) {
            OutputPrefs.setSaveMode(this, OutputPrefs.SAVE_BESIDE)
            refreshWhere()
        })
        if (beside && !allFiles && android.os.Build.VERSION.SDK_INT >= 30) {
            saveBox.addView(Ui.button(this, "Teljes fájlhozzáférés megadása", false) {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:$packageName")))
                } catch (_: Exception) {
                    try { startActivity(Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                    catch (_: Exception) {}
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
                row.addView(Ui.smallButton(this, "Átnézés / szerkesztés…") {
                    SrtEditorActivity.open(this, SrtJob.queue.indexOf(item))
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
        }
        langLine.visibility = if (langLine.text.isEmpty()) View.GONE else View.VISIBLE
        status.text = job.message
        status.setTextColor(Ui.color(this, if (job.cooling) R.color.warn else R.color.textSecondary))

        // Hátralévő idő és várható befejezés: eltelt idő és haladás alapján (lineáris becslés)
        fun etaText(elapsed: Long, pct: Int, key: String): String {
            if (pct >= 100) return ""
            if (pct < 1 || elapsed < 2000) return " · Hátra: számítás…"
            val rem = smoothRem(key, elapsed * (100 - pct) / pct, now)
            return " · Hátra: kb. ${fmtDur(rem)} · Kész: ${SrtJob.fmtClock(now + rem)} körül"
        }
        /** A futás-becslés (etaAtMs) simított változata. */
        fun etaFromJob(key: String): String {
            val rem = smoothRem(key, job.etaMs, now)
            return " · Hátra: kb. ${fmtDur(rem)} · Kész: ${SrtJob.fmtClock(now + rem)} körül"
        }

        // --- 1. aktuális darab (vagy az aktuális lépés, amíg még nincs darab)
        if (job.curStep == SrtJob.ST_RECOG && job.chunkTotal > 0 && job.curChunkNo > 0) {
            val el = if (job.curChunkStartMs > 0) now - job.curChunkStartMs else 0L
            val pc = job.curChunkPct
            barChunk.color(SrtJob.COLORS[SrtJob.ST_RECOG])
            barChunk.set(
                "Aktuális darab · ${job.curChunkNo}/${job.chunkTotal}",
                pc,
                "Eltelt: ${fmtDur(el)}" + etaText(el, pc, "c${job.curChunkNo}")
            )
        } else {
            val idx = job.curStep.coerceAtLeast(0)
            val st = job.steps[idx]
            barChunk.color(SrtJob.COLORS[idx.coerceIn(0, SrtJob.COLORS.size - 1)])
            val p = if (st.state == SrtJob.DONE) 100 else st.pct
            val el = now - job.stepStartMs
            barChunk.set(
                "Aktuális lépés · ${job.label(idx)}",
                p,
                "Eltelt: ${fmtDur(el)}" + (if (p >= 0) etaText(el, p, "s$idx") else "")
            )
        }

        // --- 2. felismerés (az összes darab)
        val rs = job.steps[SrtJob.ST_RECOG]
        val rp = when (rs.state) {
            SrtJob.DONE -> 100
            SrtJob.ACTIVE -> rs.pct.coerceAtLeast(0)
            else -> 0
        }
        val recEta = when {
            rs.state != SrtJob.ACTIVE -> ""
            job.etaAtMs > now -> etaFromJob("r")
            else -> etaText(now - job.stepStartMs, rp, "r")
        }
        barRecog.set(
            job.label(SrtJob.ST_RECOG),
            rp,
            (if (job.chunkTotal > 0) "Kész: ${job.chunkDone}/${job.chunkTotal} darab" else
                if (rs.state == SrtJob.PENDING) "még nem indult" else "") + recEta
        )

        // --- 3. teljes folyamat: a felismerés mért tempója, előtte a teljes haladásból becsülve
        val elapsedAll = now - job.startMs
        val etaPart = when {
            job.etaAtMs > now && job.curStep >= SrtJob.ST_RECOG ->
                etaFromJob("t")
            else -> etaText(elapsedAll, job.overall, "t")
        }
        val basis = when {
            job.etaAtMs > now && job.curStep >= SrtJob.ST_RECOG && job.etaBasis.isNotEmpty() -> "\n(${job.etaBasis})"
            job.curStep < SrtJob.ST_RECOG && job.overall >= 1 -> "\n(durva becslés, a felismerés indulása után pontosodik)"
            else -> ""
        }
        barTotal.set("Teljes folyamat", job.overall, "Eltelt: ${fmtDur(elapsedAll)}$etaPart$basis")

        // --- folyamat-táblázat
        val secondary = Ui.color(this, R.color.textSecondary)
        val primary = Ui.color(this, R.color.textPrimary)
        // hátralévő idő a teljes folyamatra (ms), -1 = még nem becsülhető
        val totRem: Long = when {
            job.etaAtMs > now && job.curStep >= SrtJob.ST_RECOG -> smoothRem("t", job.etaMs, now)
            job.overall >= 1 && elapsedAll >= 2000 -> smoothRem("t", elapsedAll * (100 - job.overall) / job.overall, now)
            else -> -1L
        }
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
        var cumDone = job.startMs
        var finAt = now          // a sorban álló lépések várható befejezési ideje (halmozódik)
        var finKnown = true
        for ((r, slot) in TABLE_SLOTS.withIndex()) {
            val v = tRows[r]
            if (slot < 0) {
                // alsor: az aktuális darab
                val show = job.curStep == SrtJob.ST_RECOG && job.chunkTotal > 0 && job.curChunkNo > 0
                v.root.visibility = if (show) View.VISIBLE else View.GONE
                if (!show) continue
                val pc = job.curChunkPct.coerceIn(0, 100)
                val el = if (job.curChunkStartMs > 0) now - job.curChunkStartMs else 0L
                val rem = if (pc >= 1 && el >= 2000) smoothRem("c${job.curChunkNo}", el * (100 - pc) / pc, now) else -1L
                v.icon.text = "↳"; v.icon.setTextColor(secondary)
                v.name.text = "Darab ${job.curChunkNo}/${job.chunkTotal}"
                v.name.setTextColor(primary); v.name.setTypeface(null, Typeface.NORMAL)
                v.note.text = "kész: ${job.chunkDone}/${job.chunkTotal}"; v.note.visibility = View.VISIBLE
                v.time.text = fmtDur(el)
                v.eta.text = if (rem >= 0) fmtDur(rem) else "…"
                v.fin.text = if (rem >= 0) SrtJob.fmtClock(now + rem) else "–"
                v.bar.isIndeterminate = false
                v.bar.progressTintList = android.content.res.ColorStateList.valueOf(SrtJob.COLORS[SrtJob.ST_RECOG])
                v.bar.progress = pc
                v.pct.text = "$pc%"
                continue
            }
            val i = slot
            val st = job.steps[i]
            val color = SrtJob.COLORS[i]
            v.root.visibility = View.VISIBLE
            v.name.text = "${i + 1}. ${job.label(i)}"
            when (st.state) {
                SrtJob.DONE -> { v.icon.text = "✓"; v.icon.setTextColor(Ui.color(this, R.color.ok)); v.name.setTextColor(primary); v.name.setTypeface(null, Typeface.NORMAL) }
                SrtJob.ACTIVE -> { v.icon.text = "●"; v.icon.setTextColor(color); v.name.setTextColor(primary); v.name.setTypeface(null, Typeface.BOLD) }
                SrtJob.ERROR -> { v.icon.text = "✗"; v.icon.setTextColor(Ui.color(this, R.color.err)); v.name.setTextColor(Ui.color(this, R.color.err)); v.name.setTypeface(null, Typeface.BOLD) }
                SrtJob.STOPPED -> { v.icon.text = "⏸"; v.icon.setTextColor(Ui.color(this, R.color.warn)); v.name.setTextColor(Ui.color(this, R.color.warn)); v.name.setTypeface(null, Typeface.NORMAL) }
                else -> { v.icon.text = "○"; v.icon.setTextColor(secondary); v.name.setTextColor(secondary); v.name.setTypeface(null, Typeface.NORMAL) }
            }
            val noteTxt = if (st.state == SrtJob.DONE || st.state == SrtJob.ACTIVE) st.comment else ""
            v.note.text = noteTxt
            v.note.visibility = if (noteTxt.isBlank()) View.GONE else View.VISIBLE
            v.bar.progressTintList = android.content.res.ColorStateList.valueOf(color)
            when (st.state) {
                SrtJob.DONE -> {
                    cumDone += st.durMs
                    v.time.text = fmtDur(st.durMs)
                    v.eta.text = "-"
                    v.fin.text = SrtJob.fmtClock(cumDone)
                    v.bar.isIndeterminate = false; v.bar.progress = 100
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
                    if (own >= 0) { finAt = now + own; v.eta.text = fmtDur(own); v.fin.text = SrtJob.fmtClock(finAt) }
                    else { finKnown = false; v.eta.text = "…"; v.fin.text = "–" }
                    if (p < 0) { v.bar.isIndeterminate = true; v.pct.text = "…" }
                    else { v.bar.isIndeterminate = false; v.bar.progress = p.coerceIn(0, 100); v.pct.text = "${p.coerceIn(0, 100)}%" }
                }
                else -> {
                    v.time.text = ""
                    v.bar.isIndeterminate = false; v.bar.progress = 0
                    v.pct.text = ""
                    if (finKnown && perUnit >= 0 && st.state == SrtJob.PENDING) {
                        val est = (SrtJob.WEIGHTS[i] * perUnit).toLong()
                        finAt += est
                        v.eta.text = fmtDur(est)
                        v.fin.text = SrtJob.fmtClock(finAt)
                    } else { v.eta.text = "–"; v.fin.text = "–" }
                }
            }
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

        // darab-térkép
        val showChunks = job.chunkState.isNotEmpty()
        chunkMap.visibility = if (showChunks) View.VISIBLE else View.GONE
        chunkLegend.visibility = if (showChunks) View.VISIBLE else View.GONE
        if (showChunks) {
            chunkMap.update()
            chunkLegend.text = "■ kész ${job.chunkDone}   ■ dolgozik ${job.chunkState.count { it == 1 }}   ■ vár ${job.chunkState.count { it == 0 }}"
        }

        // élő előnézet
        val pv = job.preview
        previewBox.visibility = if (pv.isNotEmpty()) View.VISIBLE else View.GONE
        previewText.text = pv.joinToString("\n")
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
        val parts = ArrayList<String>()
        parts.add("$ok fájl kész")
        if (err > 0) parts.add("$err hibás")
        if (stop > 0) parts.add("$stop megszakítva")
        val blocks = items.filter { it.state == SrtJob.Q_DONE }.sumOf { it.cueCount }
        resultText.text = parts.joinToString(", ") +
            (if (ok > 0) " · összesen $blocks feliratblokk" else "") +
            (if (SrtJob.skippedParts > 0) "\n⚠ Az utolsó fájlnál ${SrtJob.skippedParts} darab részben kimaradt (a felismerés elakadt) – a naplóban a részletek." else "")

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
        val tgtLang = if (needTranslate && mode != SrtExporter.Mode.SOURCE_ONLY) dir.substringAfter("_", selectedSrc()) else selectedSrc()
        SrtJobService.start(this, mode, selectedSrc(), dir, needTranslate, useDiarization, kind, tgtLang)
        main.removeCallbacks(tick)
        main.post(tick)
        render()
    }
}
