package hu.angolmagyar.fordito

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import hu.angolmagyar.fordito.core.review.ReviewIntelligence
import kotlin.math.abs

/**
 * Kész felirat átnézése és javítása: blokkonként szerkeszthető szöveg és időzítés, a gyanús blokkok
 * (üres, ismétlődő, túl gyors, hibás idő) jelölve vannak és külön szűrhetők.
 * A mentés a videó mellé (vagy kézzel választott helyre) készít egy ".szerk.srt" fájlt.
 */
class SrtEditorActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_IDX = "idx"
        private const val EXTRA_SUSP = "susp"
        private const val PAGE = 20

        private const val EXTRA_PATH = "path"

        private fun launch(ctx: Context, i: Intent) {
            val o = androidx.core.app.ActivityOptionsCompat.makeCustomAnimation(ctx, R.anim.screen_in, R.anim.screen_hold)
            ctx.startActivity(i, o.toBundle())
        }

        fun open(ctx: Context, queueIndex: Int, onlySuspicious: Boolean = false) {
            launch(ctx, Intent(ctx, SrtEditorActivity::class.java).putExtra(EXTRA_IDX, queueIndex).putExtra(EXTRA_SUSP, onlySuspicious))
        }

        /** Korábban elkészült (belső tárban lévő) SRT megnyitása az előzményekből. */
        fun openFile(ctx: Context, path: String) {
            launch(ctx, Intent(ctx, SrtEditorActivity::class.java).putExtra(EXTRA_PATH, path))
        }
    }

    private class RowViews(val idx: Int, val start: EditText, val end: EditText, val text: EditText)

    private var item: SrtJob.QItem? = null
    private val cues = ArrayList<SrtExporter.Cue>()
    /** Gazdag mód: az angol eredeti is megvan (frissen készült felirat), így újrafordítható. */
    private var rich = false
    private var hasTarget = false
    private val dropped = ArrayList<SrtExporter.Cue>()
    private var busy = false
    private var worstOnly = false
    private val undo = ArrayList<List<SrtExporter.Cue>>()
    private val redo = ArrayList<List<SrtExporter.Cue>>()
    private val ui = android.os.Handler(android.os.Looper.getMainLooper())
    private var video: android.widget.VideoView? = null
    private var playerBox: LinearLayout? = null
    private var seek: android.widget.SeekBar? = null
    private var timeLabel: TextView? = null
    private var caption: TextView? = null
    private var playBtn: android.widget.Button? = null
    private var playUntil = -1L
    private var seeking = false
    private var videoReady = false
    private val poll = object : Runnable {
        override fun run() { pollPlayer(); ui.postDelayed(this, 250) }
    }
    private var orig: List<SrtExporter.Cue> = emptyList()
    private lateinit var toolsBox: LinearLayout
    private var page = 0
    private var onlySuspicious = false
    private var dirty = false
    private val rows = ArrayList<RowViews>()
    private val cardOf = HashMap<Int, View>()
    private var activeIdx = -1

    private lateinit var scroll: ScrollView
    private lateinit var listBox: LinearLayout
    private lateinit var summary: TextView
    private lateinit var pageLabel: TextView

    private fun dp(v: Int) = Ui.dp(this, v)

    private val saveAsLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/x-subrip")) { uri ->
            val qi = item
            if (uri != null && qi != null) {
                try {
                    contentResolver.openOutputStream(uri)?.use { o -> o.write((qi.srt ?: "").toByteArray(Charsets.UTF_8)) }
                    dirty = false
                    Toast.makeText(this, "Elmentve.", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Mentési hiba: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val idx = intent.getIntExtra(EXTRA_IDX, -1)
        val path = intent.getStringExtra(EXTRA_PATH)
        val qi = if (path != null) {
            try {
                val f = java.io.File(path)
                SrtJob.QItem(android.net.Uri.EMPTY, f.name).also { q ->
                    q.srt = f.readText(Charsets.UTF_8)
                    q.srtName = f.name
                    q.localFile = f.name
                }
            } catch (_: Exception) { null }
        } else SrtJob.queue.getOrNull(idx)
        val srt = qi?.srt
        if (qi == null || srt == null) {
            Toast.makeText(this, "Nincs szerkeszthető felirat.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        item = qi
        val rc = qi.cues
        if (rc != null && rc.isNotEmpty()) {
            rich = true
            cues.addAll(rc)
            dropped.addAll(qi.dropped)
            hasTarget = cues.any { it.target.isNotBlank() } && qi.mode != SrtExporter.Mode.SOURCE_ONLY
            orig = rc.toList()
        } else cues.addAll(SubFormat.parseSrt(srt))

        val column = Ui.column(this).apply { setPadding(dp(16), dp(16), dp(16), dp(32)) }
        scroll = Ui.scrollOf(this, column)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val pb = buildPlayer(qi)
        playerBox = pb
        root.addView(pb)
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        column.addView(Ui.button(this, "← Vissza", false) { confirmLeave() })
        column.addView(Ui.title(this, "Felirat szerkesztése"))
        column.addView(Ui.small(this, qi.name))
        summary = Ui.small(this, "")
        column.addView(summary)
        toolsBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(toolsBox)
        column.addView(SwitchCompat(this).apply {
            text = "Csak a gyanús blokkok"
            minHeight = dp(48)
            isChecked = intent.getBooleanExtra(EXTRA_SUSP, false).also { onlySuspicious = it }
            setOnCheckedChangeListener { _, c ->
                applyRows()
                onlySuspicious = c
                page = 0
                rebuild()
            }
        })
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        nav.addView(Ui.smallButton(this, "◀") { applyRows(); page--; rebuild() })
        pageLabel = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(Ui.color(this@SrtEditorActivity, R.color.textPrimary))
        }
        nav.addView(pageLabel, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        nav.addView(Ui.smallButton(this, "▶") { applyRows(); page++; rebuild() })
        column.addView(nav)
        listBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(listBox)
        column.addView(Ui.button(this, "Mentés", true) { save() })
        column.addView(Ui.button(this, "Mentés másként…", false) {
            applyRows()
            commitToItem()
            saveAsLauncher.launch(nameForEdited())
        })
        rebuild()
    }

    /** Beépített lejátszó: a kijelölt blokk képe + hangja mellett javíthatod a szöveget. */
    private fun buildPlayer(qi: SrtJob.QItem): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = View.GONE }
        if (qi.uri == android.net.Uri.EMPTY) return box
        val vv = android.widget.VideoView(this)
        video = vv
        val frame = android.widget.FrameLayout(this)
        frame.setBackgroundColor(0xFF000000.toInt())
        frame.addView(vv, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(190), Gravity.CENTER
        ))
        val cap = TextView(this).apply {
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            setShadowLayer(4f, 0f, 0f, 0xFF000000.toInt())
            setPadding(dp(12), dp(4), dp(12), dp(8))
        }
        caption = cap
        frame.addView(cap, android.widget.FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM
        ))
        box.addView(frame)
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), 0, dp(8), 0) }
        val pb = Ui.smallButton(this, "▶") { togglePlay() }
        playBtn = pb
        bar.addView(pb)
        val sk = android.widget.SeekBar(this)
        seek = sk
        sk.setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                if (fromUser) video?.seekTo(p)
            }
            override fun onStartTrackingTouch(s: android.widget.SeekBar?) { seeking = true }
            override fun onStopTrackingTouch(s: android.widget.SeekBar?) { seeking = false }
        })
        bar.addView(sk, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val tl = TextView(this).apply { textSize = 12f; setTextColor(Ui.color(this@SrtEditorActivity, R.color.textSecondary)) }
        timeLabel = tl
        bar.addView(tl)
        box.addView(bar)
        vv.setOnPreparedListener { mp ->
            videoReady = true
            sk.max = mp.duration
            box.visibility = View.VISIBLE
            mp.setVolume(1f, 1f)
            rebuild()
        }
        vv.setOnErrorListener { _, _, _ ->
            videoReady = false
            box.visibility = View.GONE
            true
        }
        try { vv.setVideoURI(qi.uri) } catch (_: Exception) { box.visibility = View.GONE }
        return box
    }

    private fun togglePlay() {
        val v = video ?: return
        playUntil = -1L
        if (v.isPlaying) v.pause() else v.start()
        updatePlayBtn()
    }

    private fun updatePlayBtn() { playBtn?.text = if (video?.isPlaying == true) "⏸" else "▶" }

    private fun playSpan(startMs: Long, endMs: Long) {
        val v = video ?: return
        v.seekTo((startMs - 400).coerceAtLeast(0).toInt())
        playUntil = endMs + 400
        v.start()
        updatePlayBtn()
    }

    private fun pollPlayer() {
        val v = video ?: return
        if (!videoReady) return
        val pos = try { v.currentPosition.toLong() } catch (_: Exception) { return }
        if (!seeking) seek?.progress = pos.toInt()
        timeLabel?.text = " " + SrtExporter.formatTime(pos).take(8)
        if (playUntil in 0..pos && v.isPlaying) { v.pause(); playUntil = -1L; updatePlayBtn() }
        val cue = cues.firstOrNull { pos >= it.startMs && pos <= it.endMs }
        caption?.text = if (cue == null) "" else if (rich && hasTarget) cue.target else cue.source
        // lejátszás közben az éppen szóló blokk kiemelődik, és a lista követi
        val ai = if (v.isPlaying && cue != null) cues.indexOf(cue) else -1
        if (ai != activeIdx) {
            cardOf[activeIdx]?.setBackgroundColor(0)
            activeIdx = ai
            cardOf[ai]?.let { c ->
                c.setBackgroundColor(android.graphics.Color.argb(48, 47, 128, 237))
                try { scroll.smoothScrollTo(0, (c.top + listBox.top - dp(140)).coerceAtLeast(0)) } catch (e: Exception) { DiagLog.swallow("SrtEditorActivity", e) }
            }
        }
        updatePlayBtn()
    }

    override fun onResume() { super.onResume(); ui.post(poll) }
    override fun onPause() {
        ui.removeCallbacks(poll)
        try { video?.pause() } catch (e: Exception) { DiagLog.swallow("SrtEditorActivity", e) }
        super.onPause()
    }
    override fun onDestroy() {
        ui.removeCallbacks(poll)
        try { video?.stopPlayback() } catch (e: Exception) { DiagLog.swallow("SrtEditorActivity", e) }
        super.onDestroy()
    }

    private fun nameForEdited(): String {
        val base = (item?.srtName ?: "felirat.srt").removeSuffix(".srt")
        return "$base.szerk.srt"
    }

    private fun confirmLeave() {
        applyRows()
        if (!dirty) { finish(); return }
        AlertDialog.Builder(this)
            .setTitle("Nem mentett módosítások")
            .setMessage("Kilépsz mentés nélkül?")
            .setPositiveButton("Kilépés") { _, _ -> finish() }
            .setNegativeButton("Maradok", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        confirmLeave()
    }

    /** A képernyőn lévő sorok tartalmát visszaírja a blokklistába. */
    private fun pushUndo() {
        undo.add(cues.toList())
        if (undo.size > 50) undo.removeAt(0)
        redo.clear()
    }

    private fun undoStep() {
        applyRows()
        if (undo.isEmpty()) { toast("Nincs mit visszavonni."); return }
        redo.add(cues.toList())
        val prev = undo.removeAt(undo.size - 1)
        cues.clear(); cues.addAll(prev)
        dirty = true
        rebuild()
    }

    private fun redoStep() {
        applyRows()
        if (redo.isEmpty()) { toast("Nincs mit újra végrehajtani."); return }
        undo.add(cues.toList())
        val next = redo.removeAt(redo.size - 1)
        cues.clear(); cues.addAll(next)
        dirty = true
        rebuild()
    }

    private fun applyRows() {
        var snap = false
        for (r in rows) {
            if (r.idx !in cues.indices) continue
            val old = cues[r.idx]
            val s = SubFormat.parseTime(r.start.text.toString()) ?: old.startMs
            val e = SubFormat.parseTime(r.end.text.toString()) ?: old.endMs
            val t = r.text.text.toString().trim()
            val cur = if (rich && hasTarget) old.target else old.source
            if (s != old.startMs || e != old.endMs || t != cur.trim()) {
                if (!snap) { pushUndo(); snap = true }
                if (t != cur.trim()) AuditLog.add(this, auditKey(), hu.angolmagyar.fordito.core.review.ReviewAuditEntry(
                    System.currentTimeMillis(), r.idx, cur.trim(), t, "Kézi szerkesztés", true, "manual"))
                cues[r.idx] = if (rich && hasTarget) old.copy(startMs = s, endMs = e, target = t)
                else old.copy(startMs = s, endMs = e, source = t)
                dirty = true
            }
        }
    }

    private fun commitToItem() {
        val qi = item ?: return
        if (rich) {
            qi.cues = cues.toList()
            qi.dropped = dropped.toList()
            qi.srt = SrtBuild.build(this, cues, qi.mode)
            qi.cueCount = cues.size
        } else {
            qi.srt = SubFormat.buildRaw(cues)
            qi.cueCount = cues.count { c -> c.source.isNotBlank() }
        }
    }

    /** A kézi javításokból tanul: gyakori javításból szabályt javasol, a javított sorokat a fordítási memóriába teszi. */
    private fun learn() {
        if (!rich || !hasTarget) return
        try {
            val edited = ArrayList<SrtExporter.Cue>()
            val raw = Prefs(this).raw()
            val stats = RuleStats.parse(raw.getString("ruleStats", "") ?: "")
            val byStart = orig.associateBy { it.startMs to it.source }
            for (c in cues) {
                val o = byStart[c.startMs to c.source] ?: continue
                if (o.target.trim() != c.target.trim() && c.target.isNotBlank()) {
                    edited.add(c)
                    RuleSuggest.diff(o.target, c.target)?.let { RuleStats.record(stats, it.first, it.second) }
                }
            }
            if (edited.isEmpty()) return
            TranslationMemoryStore.record(this, edited)
            orig = cues.toList()
            val cands = RuleStats.candidates(stats, Projects.huRules(this))
            raw.edit().putString("ruleStats", RuleStats.serialize(stats)).apply()
            if (cands.isNotEmpty()) {
                AlertDialog.Builder(this)
                    .setTitle("Gyakori javítások")
                    .setMessage("Ezeket többször javítottad:\n\n" + cands.take(8).joinToString("\n") { it.replace("=", " → ") } +
                        "\n\nSzabállyá teszem őket, hogy a következő fordításnál automatikusan javuljanak?")
                    .setPositiveButton("Igen") { _, _ ->
                        for (k in cands) {
                            val i = k.indexOf('=')
                            if (i > 0) Projects.addHuRule(this, k.substring(0, i), k.substring(i + 1))
                        }
                        toast("${cands.size} szabály elmentve.")
                    }
                    .setNegativeButton("Most nem", null)
                    .show()
            }
        } catch (e: Exception) { DiagLog.swallow("SrtEditorActivity", e) }
    }

    private fun save() {
        applyRows()
        commitToItem()
        learn()
        val qi = item ?: return
        val bytes = (qi.srt ?: "").toByteArray(Charsets.UTF_8)
        val where = if (qi.uri != android.net.Uri.EMPTY && OutputPrefs.saveMode(this) == OutputPrefs.SAVE_BESIDE)
            OutputWriter.saveBesideVideo(this, qi.uri, nameForEdited(), OutputWriter.MIME_SRT, bytes)
        else null
        val tree = OutputPrefs.defaultTree(this)
        val w2 = where ?: if (tree != null) OutputWriter.saveInTree(this, tree, nameForEdited(), OutputWriter.MIME_SRT, bytes)
            ?.let { l -> "$l/${nameForEdited()}" } else null
        if (w2 != null) {
            dirty = false
            qi.detail = "Szerkesztett változat mentve $w2"
            Toast.makeText(this, "Mentve $w2", Toast.LENGTH_LONG).show()
            SrtJob.changed()
        } else {
            saveAsLauncher.launch(nameForEdited())
        }
    }

    /** Gazdag mód eszközei: újrafordítás offline / online, gyanús sorok javítása, kiszűrt blokkok visszaállítása. */
    private fun buildTools(suspCount: Int) {
        toolsBox.removeAllViews()
        val ur = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        ur.addView(Ui.smallButton(this, "↶ Visszavonás") { undoStep() })
        ur.addView(Ui.smallButton(this, "↷ Újra") { redoStep() })
        toolsBox.addView(ur)
        if (cues.any { it.speaker.isNotBlank() }) toolsBox.addView(Ui.smallButton(this, "Beszélők nevei…") { renameSpeakers() })
        if (!rich) return
        toolsBox.addView(Ui.smallButton(this, "Szótár újraalkalmazása + javított sorok újrafordítása") { reapplyGlossary() })
        if (hasTarget) {
            toolsBox.addView(Ui.smallButton(this, "Gyanús sorok javítása (online)") {
                applyRows()
                val bad = cues.indices.filter { i ->
                    LineQuality.flags(cues.getOrNull(i - 1), cues[i], cues.getOrNull(i + 1), true).isNotEmpty()
                }
                if (bad.isEmpty()) toast("Nincs gyanús sor.") else retranslate(bad, OnlinePrefs.ONLINE)
            })
            toolsBox.addView(Ui.smallButton(this, "Az egész újrafordítása (online)") { applyRows(); retranslate(null, OnlinePrefs.ONLINE) })
            toolsBox.addView(Ui.smallButton(this, "Az egész újrafordítása (offline)") { applyRows(); retranslate(null, OnlinePrefs.OFF) })
        }
        val reviewThr = (Math.exp(QualityRules.REVIEW_LP.toDouble()) * 100).toInt() + 1
        val review = ReviewIntelligence.analyze(cues.map { cue ->
            val pct = if (cue.lp.isFinite() && cue.lp <= 0f) (Math.exp(cue.lp.toDouble()) * 100).toInt().coerceIn(0, 100) else null
            ReviewIntelligence.Line(
                text = if (hasTarget && cue.target.isNotBlank()) cue.target else cue.source,
                confidencePercent = pct,
                startMs = cue.startMs,
                endMs = cue.endMs,
                source = if (hasTarget) cue.source else null,
                target = if (hasTarget) cue.target else null,
                weakWords = cue.wordConfidence.any { TokenQuality.needsReview(it) }
            )
        }, lowConfidencePercent = reviewThr)
        // minőség-kártya: animált pontgyűrű + összegzés; koppintásra megnyílik a részletes lap
        val qCard = Ui.card(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            isClickable = true
            setOnClickListener { applyRows(); showReviewSheet(review) }
        }
        val qRing = QualityRingView(this)
        qCard.addView(qRing, LinearLayout.LayoutParams(dp(60), dp(60)))
        val qText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        qText.addView(Ui.text(this, "Minőség · ${review.flaggedLines} jelölt sor", 15f, R.color.textPrimary, true))
        qText.addView(Ui.small(this, QualityReport.build(cues).lines().joinToString(" · ")))
        qText.addView(Ui.small(this, "Koppints a részletekhez ›"))
        qCard.addView(qText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        toolsBox.addView(qCard)
        qRing.setScore(ReviewSheet.score(review))
        toolsBox.addView(Ui.smallButton(this, if (worstOnly) "Mind a sor megjelenítése" else "Legrosszabb 10 sor") {
            applyRows(); worstOnly = !worstOnly; page = 0; rebuild()
        })
        if (dropped.isNotEmpty()) {
            toolsBox.addView(Ui.smallButton(this, "Kiszűrt blokkok visszaállítása (${dropped.size})") { restoreDropped() })
        }
    }


    private fun auditKey(): String = item?.name.orEmpty()

    /** Prémium ellenőrző lap: a jelzések kártyái, szűrők, célzott újrafelismerés, napló, benchmark. */
    private fun showReviewSheet(review: ReviewIntelligence.ReviewSummary) {
        ReviewSheet.show(this, review, cues.toList(), rich && hasTarget, Benchmark.hasReference(this, auditKey()),
            object : ReviewSheet.Actions {
                override fun jump(lineIndex: Int) = jumpToLine(lineIndex)
                override fun recheckSuspicious() = startTargetedReview(review)
                override fun showAudit() = showAuditDialog()
                override fun showBenchmark() = showBenchmarkDialog()
                override fun saveReference() {
                    Benchmark.saveReference(this@SrtEditorActivity, auditKey(), cues)
                    toast("Referenciának mentve. Újrafuttatás után a Benchmark összeveti az eredményt.")
                }
            })
    }

    private fun jumpToLine(index: Int) {
        applyRows()
        onlySuspicious = false
        worstOnly = false
        page = index / PAGE
        rebuild()
        scroll.post {
            val card = cardOf[index]
            if (card != null) scroll.smoothScrollTo(0, (card.top + listBox.top - dp(100)).coerceAtLeast(0))
        }
    }

    /** A kritikus/magas jelzésű sorok hangszakaszának újrafelismerése; a változás csak jóváhagyással kerül be. */
    private fun startTargetedReview(review: ReviewIntelligence.ReviewSummary) {
        if (busy) return
        val qi = item ?: return
        if (qi.uri == android.net.Uri.EMPTY) { toast("Ehhez az eredeti videó/hang kell (régi, fájl nélküli elem)."); return }
        val lines = TargetedReview.pickLines(review)
        if (lines.isEmpty()) { toast("Nincs kritikus vagy magas prioritású jelzés."); return }
        busy = true
        summary.text = "Célzott újrafelismerés előkészítése (${lines.size} sor)…"
        val snapshot = cues.toList()
        val lang = qi.dir.substringBefore('_').takeIf { it.length == 2 } ?: "en"
        val cancel = java.util.concurrent.atomic.AtomicBoolean(false)
        Thread {
            var err: String? = null
            var props: List<TargetedReview.Proposal> = emptyList()
            try {
                props = TargetedReview.run(this, qi.uri, snapshot, lines, lang, cancel) { m -> runOnUiThread { summary.text = m } }
            } catch (e: Throwable) {
                err = e.message ?: e.javaClass.simpleName
                DiagLog.log("Célzott újrafelismerés hiba: $err")
            }
            val errMsg = err
            val result = props
            runOnUiThread {
                busy = false
                if (errMsg != null) toast("Újrafelismerési hiba: $errMsg")
                else showProposals(result)
                rebuild()
            }
        }.start()
    }

    private fun showProposals(all: List<TargetedReview.Proposal>) {
        val changed = all.filter { it.changed }
        if (changed.isEmpty()) { toast("Az újrafelismerés nem talált eltérést – a sorok maradnak."); return }
        val labels = changed.map { p ->
            val first = p.first + 1
            "${if (p.first == p.last) "$first." else "$first–${p.last + 1}."} sor ${if (p.better) "✓ jobb" else "• eltér"}\n" +
                "régi: ${p.oldText().take(90)}\núj:   ${p.newText().take(90)}"
        }.toTypedArray()
        val checked = BooleanArray(changed.size) { changed[it].better }
        AlertDialog.Builder(this)
            .setTitle("Újrafelismerési javaslatok")
            .setMultiChoiceItems(labels, checked) { _, which, on -> checked[which] = on }
            .setPositiveButton("Kiválasztottak alkalmazása") { _, _ ->
                val chosen = changed.filterIndexed { i, _ -> checked[i] }
                if (chosen.isEmpty()) return@setPositiveButton
                val before = cues.toList()
                pushUndo()
                val out = TargetedReview.apply(before, chosen)
                val showT = rich && hasTarget
                cues.clear(); cues.addAll(out)
                dirty = true
                AuditLog.recordDiff(this, auditKey(), before, out, "Célzott újrafelismerés", "targeted-review", showT)
                // az új sorok fordítását pótoljuk, ha kell
                if (hasTarget && cues.any { it.target.isBlank() && it.source.isNotBlank() }) {
                    val todo = cues.indices.filter { cues[it].target.isBlank() && cues[it].source.isNotBlank() }
                    retranslate(todo, if (OnlinePrefs.usable(this)) OnlinePrefs.AUTO else OnlinePrefs.OFF)
                } else rebuild()
            }
            .setNegativeButton("Elvetés", null)
            .show()
    }

    private fun showAuditDialog() {
        val entries = AuditLog.load(this, auditKey()).takeLast(40).reversed()
        if (entries.isEmpty()) { toast("Még nincs rögzített módosítás ehhez a fájlhoz."); return }
        val fmt = java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
        val labels = entries.map { e ->
            "${fmt.format(java.util.Date(e.timestampEpochMs))} · ${if (e.lineIndex >= 0) "${e.lineIndex + 1}. sor" else "több sor"} · ${e.reason}\n" +
                "${e.beforeText.take(70)} → ${e.afterText.take(70)}"
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Módosítási napló (koppints: visszaállítás)")
            .setItems(labels) { _, which ->
                val e = entries[which]
                if (e.lineIndex !in cues.indices) { toast("Ez a bejegyzés nem állítható vissza egy sorra."); return@setItems }
                val showT = rich && hasTarget
                val cur = cues[e.lineIndex]
                val curText = if (showT) cur.target else cur.source
                if (curText.trim() != e.afterText.trim()) { toast("A sor azóta másképp módosult, nem állítom vissza."); return@setItems }
                applyRows(); pushUndo()
                cues[e.lineIndex] = if (showT) cur.copy(target = e.beforeText) else cur.copy(source = e.beforeText)
                dirty = true
                AuditLog.add(this, auditKey(), hu.angolmagyar.fordito.core.review.ReviewAuditEntry(
                    System.currentTimeMillis(), e.lineIndex, e.afterText, e.beforeText, "Visszaállítás", true, "undo-from-log"))
                rebuild()
            }
            .setNegativeButton("Bezárás", null)
            .setNeutralButton("Napló törlése") { _, _ -> AuditLog.clear(this, auditKey()) }
            .show()
    }

    private fun showBenchmarkDialog() {
        val audioMs = cues.maxOfOrNull { it.endMs } ?: 0L
        val elapsed = if (SrtJob.endMs > SrtJob.startMs) SrtJob.endMs - SrtJob.startMs else 0L
        val rep = Benchmark.evaluate(this, auditKey(), cues, elapsed, audioMs)
        if (rep == null) { toast("Nincs referencia ehhez a fájlhoz."); return }
        val hist = Benchmark.history(this, auditKey()).takeLast(6)
        val msg = StringBuilder()
        msg.append("WER: ${"%.1f".format(rep.bench.wordErrorRate * 100)}% (${rep.result.errors} hiba / ${rep.result.referenceWords} szó)\n")
        msg.append("  helyettesítés ${rep.result.substitutions} · törlés ${rep.result.deletions} · beszúrás ${rep.result.insertions}\n")
        msg.append("Kritikus eltérés: ${rep.bench.criticalErrors} (szám ${rep.numberDiffs}, tagadás ${rep.negationDiffs})\n")
        if (elapsed > 0 && audioMs > 0) msg.append("Valós idejű szorzó: ${"%.2f".format(rep.bench.realtimeFactor)}×\n")
        msg.append("Verzió: ${rep.bench.version}\n")
        if (hist.isNotEmpty()) {
            msg.append("\nElőzmények:\n")
            for (h in hist) msg.append("  ${h.version}: WER ${"%.1f".format(h.wordErrorRate * 100)}% · kritikus ${h.criticalErrors}\n")
        }
        msg.append("\nA WER felismerési mérőszám, nem fordítás- vagy jogi hitelességi mutató.")
        AlertDialog.Builder(this)
            .setTitle("Benchmark")
            .setMessage(msg.toString())
            .setPositiveButton("Eredmény rögzítése") { _, _ -> Benchmark.record(this, rep.bench); toast("Rögzítve.") }
            .setNeutralButton("Referencia törlése") { _, _ -> Benchmark.deleteReference(this, auditKey()) }
            .setNegativeButton("Bezárás", null)
            .show()
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_LONG).show()

    private fun restoreDropped() {
        applyRows()
        pushUndo()
        cues.addAll(dropped)
        cues.sortBy { it.startMs }
        dropped.clear()
        dirty = true
        rebuild()
    }

    /** A kézi javításból szabályt készít a magyar szótárba ("régi=új"), így a következő fordításoknál is él. */
    private fun makeRule(i: Int, newText: String) {
        val old = cues.getOrNull(i)?.target ?: return
        val d = RuleSuggest.diff(old, newText.trim())
        if (d == null) { toast("Előbb javítsd a sort úgy, hogy egy-két szó változzon."); return }
        AlertDialog.Builder(this)
            .setTitle("Javítás szabállyá")
            .setMessage("„${d.first}” → „${d.second}”\n\nMinden további fordításnál ez a csere lesz alkalmazva.")
            .setPositiveButton("Mentés") { _, _ ->
                val added = Projects.addHuRule(this, d.first, d.second)
                toast(if (added) "Szabály elmentve." else "Ez a szabály már megvan.")
            }
            .setNegativeButton("Mégse", null)
            .show()
    }

    /** A (felhasználói + automatikus) szótár újra az egész feliratra; a megváltozott forrású sorok újrafordulnak. */
    private fun reapplyGlossary() {
        if (busy) return
        applyRows()
        val prefs = Prefs(this)
        val en = Fixes.parse(prefs.enFixes + "\n" + VocabLearner.autoRules(prefs))
        val hu = Fixes.parse(Projects.huRules(this))
        val changed = ArrayList<Int>()
        var huChanged = 0
        pushUndo()
        for (k in cues.indices) {
            val c = cues[k]
            val ns = Fixes.apply(c.source, en)
            val nt = if (hasTarget && hu.isNotEmpty()) Fixes.apply(c.target, hu) else c.target
            if (ns != c.source) changed.add(k)
            else if (nt != c.target) huChanged++
            if (ns != c.source || nt != c.target) cues[k] = c.copy(source = ns, target = if (ns != c.source) c.target else nt)
        }
        dirty = true
        if (changed.isEmpty()) { toast(if (huChanged > 0) "$huChanged sor magyar szövege javítva." else "A szótár nem talált javítanivalót."); rebuild(); return }
        if (!hasTarget) { toast("${changed.size} sor szövege javítva."); rebuild(); return }
        toast("${changed.size} sor forrása javult – újrafordítom.")
        retranslate(changed, OnlinePrefs.mode(this).let { if (it == OnlinePrefs.OFF || !OnlinePrefs.usable(this)) OnlinePrefs.OFF else it })
    }

    /** A beszélők átnevezése (pl. S1 → Nina) az egész felirat összes blokkjában. */
    private fun renameSpeakers() {
        applyRows()
        val ids = cues.map { it.speaker }.filter { it.isNotBlank() }.distinct()
        if (ids.isEmpty()) { toast("Nincs beszélő a feliratban."); return }
        val pad = dp(16)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, 0) }
        col.addView(Ui.small(this, "Írd be a nevet; üresen hagyva marad a jelenlegi."))
        val fields = ids.map { id ->
            col.addView(Ui.body(this, id))
            android.widget.EditText(this).apply { hint = "pl. Nina"; setSingleLine(true) }.also { col.addView(it) }
        }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Beszélők nevei")
            .setView(ScrollView(this).apply { addView(col) })
            .setPositiveButton("Alkalmaz") { _, _ ->
                val map = ids.zip(fields).associate { (id, f) -> id to f.text.toString().trim() }.filter { it.value.isNotEmpty() }
                if (map.isNotEmpty()) {
                    pushUndo()
                    for (k in cues.indices) cues[k].speaker.let { s -> map[s]?.let { cues[k] = cues[k].copy(speaker = it) } }
                    dirty = true
                    // a nevek a következő feldolgozáshoz is megmaradnak
                    try {
                        val cur = Prefs(this).speakerNames
                        val add = map.entries.joinToString("\n") { "${it.key}=${it.value}" }
                        Prefs(this).speakerNames = if (cur.isBlank()) add else cur.trimEnd() + "\n" + add
                    } catch (e: Exception) { DiagLog.swallow("SrtEditorActivity", e) }
                    rebuild()
                }
            }
            .setNegativeButton("Mégse", null)
            .show()
    }

    /** Egy sor több fordítása (jelenlegi / online / offline) egymás mellett; a választott bekerül a sorba. */
    private fun compare(i: Int) {
        if (busy) return
        val qi = item ?: return
        applyRows()
        if (i !in cues.indices) return
        busy = true
        summary.text = "Összehasonlítás…"
        val snap = cues.toList()
        val dir = qi.dir
        Thread {
            val opts = LinkedHashMap<String, String>()
            opts["Jelenlegi"] = snap[i].target
            if (OnlinePrefs.usable(this)) try {
                val cfg = OnlinePrefs.config(
                    this, SrtJobService.langName(dir.substringBefore("_", "en")), SrtJobService.langName(dir.substringAfter("_", "hu"))
                )
                if (cfg != null) {
                    val a = (i - 4).coerceAtLeast(0)
                    val en = snap.subList(a, i + 1).map { it.source }
                    val hu = snap.subList(a, i + 1).map { it.target }.toMutableList()
                    hu[hu.size - 1] = ""
                    val bad = OnlineTranslator(cfg).translate(en, hu, listOf(en.size - 1), { _, _ -> }, { false })
                    if (bad.isEmpty() && hu.last().isNotBlank()) {
                        val nm = when (cfg.provider) {
                            OnlineTranslator.Provider.DEEPL -> "DeepL"
                            OnlineTranslator.Provider.ANTHROPIC -> "Claude"
                            else -> "Online"
                        }
                        opts[nm] = hu.last().trim()
                    }
                }
            } catch (_: Throwable) {}
            val rec = FileRecognizer(this, Prefs(this), java.util.concurrent.atomic.AtomicBoolean(false))
            try {
                val off = rec.retranslate(snap, listOf(i), dir, OnlinePrefs.OFF) { _, _, _ -> }
                opts["Offline (ML Kit)"] = off.getOrNull(i)?.target.orEmpty()
            } catch (_: Throwable) {
            } finally {
                try { rec.close() } catch (e: Exception) { DiagLog.swallow("SrtEditorActivity", e) }
            }
            // azonos szövegű változatok összevonása
            val seen = HashSet<String>()
            val list = opts.entries.filter { it.value.isNotBlank() && seen.add(it.value.trim()) }
            runOnUiThread {
                busy = false
                rebuild()
                if (list.size < 2) { toast("Nincs másik változat (az online fordítás nincs beállítva?)."); return@runOnUiThread }
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Melyik fordítás legyen?")
                    .setItems(list.map { "${it.key}:\n${it.value}" }.toTypedArray()) { _, which ->
                        val pick = list[which].value
                        if (i in cues.indices && pick != cues[i].target) {
                            pushUndo()
                            cues[i] = cues[i].copy(target = pick)
                            dirty = true
                            try { TranslationMemoryStore.record(this, listOf(cues[i])) } catch (e: Exception) { DiagLog.swallow("SrtEditorActivity", e) }
                            rebuild()
                        }
                    }
                    .setNegativeButton("Mégse", null)
                    .show()
            }
        }.start()
    }

    private fun retranslate(indices: List<Int>?, mode: Int, group: Boolean = false) {
        if (busy) return
        val qi = item ?: return
        if (mode != OnlinePrefs.OFF && !OnlinePrefs.usable(this)) {
            toast("Az online fordítás nincs beállítva (Fájl képernyő → Online fordítás).")
            return
        }
        busy = true
        summary.text = "Fordítás folyamatban…"
        val snapshot = cues.toList()
        val dir = qi.dir
        Thread {
            var err: String? = null
            var out: List<SrtExporter.Cue>? = null
            var note = ""
            val rec = FileRecognizer(this, Prefs(this), java.util.concurrent.atomic.AtomicBoolean(false))
            rec.groupSubset = group
            try {
                rec.onNote = { m -> note = m }
                out = rec.retranslate(snapshot, indices, dir, mode) { d, t, label ->
                    runOnUiThread { summary.text = "$label: $d/$t" }
                }
            } catch (e: Throwable) {
                err = e.message ?: e.javaClass.simpleName
            } finally {
                try { rec.close() } catch (e: Exception) { DiagLog.swallow("SrtEditorActivity", e) }
            }
            val result = out
            val errMsg = err
            val noteMsg = note
            runOnUiThread {
                busy = false
                if (result != null) {
                    pushUndo()
                    AuditLog.recordDiff(this, auditKey(), cues.toList(), result,
                        "Újrafordítás (${if (mode == OnlinePrefs.OFF) "offline" else "online/auto"})", "retranslate", true)
                    cues.clear(); cues.addAll(result)
                    dirty = true
                    if (noteMsg.isNotBlank()) toast(noteMsg)
                }
                if (errMsg != null) toast("Fordítási hiba: $errMsg")
                rebuild()
            }
        }.start()
    }

    private fun box(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(8).toFloat()
        setColor(Ui.color(this@SrtEditorActivity, R.color.bgElevated))
        setStroke(dp(1), Ui.color(this@SrtEditorActivity, R.color.stroke))
    }

    private fun edit(text: String, mono: Boolean, multi: Boolean): EditText = EditText(this).apply {
        setText(text)
        textSize = if (mono) 13f else 15f
        if (mono) typeface = Typeface.MONOSPACE
        setTextColor(Ui.color(this@SrtEditorActivity, R.color.textPrimary))
        background = box()
        setPadding(dp(10), dp(8), dp(10), dp(8))
        if (multi) {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            minLines = 2
            gravity = Gravity.TOP
        } else {
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { dirty = true }
        })
    }


    /** Balra húzás → blokk törlése (gesztusvezérlés az SRT szerkesztőben). */
    private fun attachSwipeToDelete(card: View, cueIndex: Int) {
        var startX = 0f
        var startY = 0f
        var dragging = false
        card.setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startX = ev.rawX
                    startY = ev.rawY
                    dragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - startX
                    val dy = ev.rawY - startY
                    if (!dragging && abs(dx) > abs(dy) && abs(dx) > dp(16)) {
                        dragging = true
                        v.parent?.requestDisallowInterceptTouchEvent(true)
                    }
                    if (dragging) {
                        v.translationX = dx.coerceAtMost(0f)
                        v.alpha = (1f + dx / (v.width.coerceAtLeast(1))).coerceIn(0.3f, 1f)
                        true
                    } else false
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        val dx = ev.rawX - startX
                        if (dx < -dp(96)) {
                            Haptics.click(this)
                            applyRows()
                            if (cueIndex in cues.indices) {
                                pushUndo()
                                cues.removeAt(cueIndex)
                                dirty = true
                            }
                            rebuild()
                        } else {
                            v.animate().translationX(0f).alpha(1f).setDuration(160).start()
                        }
                        v.parent?.requestDisallowInterceptTouchEvent(false)
                        true
                    } else false
                }
                else -> false
            }
        }
    }

    /** Forrásszöveg szókonfidencia-jelölése: narancs = ellenőrizendő; a szöveget nem módosítja. */
    private fun confidencePreview(c: SrtExporter.Cue): TextView = TextView(this).apply {
        val words = c.source.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val span = android.text.SpannableStringBuilder()
        words.forEachIndexed { index, word ->
            if (index > 0) span.append(" ")
            val start = span.length
            span.append(word)
            val score = c.wordConfidence.getOrNull(index)
            if (score != null && TokenQuality.needsReview(score)) {
                span.setSpan(android.text.style.ForegroundColorSpan(Ui.color(this@SrtEditorActivity, R.color.warn)), start, span.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                span.setSpan(android.text.style.UnderlineSpan(), start, span.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        text = if (c.wordConfidence.isEmpty()) c.source else span
        textSize = 13f
        setTextColor(Ui.color(this@SrtEditorActivity, R.color.textSecondary))
        if (c.wordConfidence.isNotEmpty()) contentDescription = "Forrásszöveg. Narancssárga aláhúzás: alacsony tokenkonfidenciájú szó."
    }

    private fun rebuild() {
        // gyanús blokkok
        val flags = arrayOfNulls<String>(cues.size)
        var prev: SrtExporter.Cue? = null
        var suspCount = 0
        for ((i, c) in cues.withIndex()) {
            flags[i] = if (rich) LineQuality.flags(prev, c, cues.getOrNull(i + 1), hasTarget).joinToString(", ").ifEmpty { null }
            else SubFormat.suspicious(prev, c)
            if (flags[i] != null) suspCount++
            prev = c
        }
        buildTools(suspCount)
        val worst = if (rich && worstOnly) QualityReport.build(cues).worst else null
        // A "Legrosszabb 10 sor" valóban súlyosság szerint jelenjen meg; a normál nézet marad időrendi.
        val view = if (worst != null) {
            worst.filter { i -> !onlySuspicious || flags[i] != null }
        } else {
            cues.indices.filter { !onlySuspicious || flags[it] != null }
        }
        val pages = ((view.size + PAGE - 1) / PAGE).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        summary.text = "${cues.size} blokk · $suspCount gyanús" + if (dirty) " · módosítva" else ""
        pageLabel.text = "${page + 1}/$pages. oldal"

        rows.clear()
        cardOf.clear()
        activeIdx = -1
        listBox.removeAllViews()
        if (view.isEmpty()) {
            listBox.addView(Ui.small(this, if (onlySuspicious) "Nincs gyanús blokk." else "Nincs blokk."))
        }
        for (i in view.drop(page * PAGE).take(PAGE)) {
            val c = cues[i]
            val card = Ui.card(this)
            val flag = flags[i]
            card.addView(TextView(this).apply {
                text = "#${i + 1}" + if (flag != null) "  ⚠ $flag" else ""
                textSize = 13f
                setTextColor(Ui.color(this@SrtEditorActivity, if (flag != null) R.color.warn else R.color.textSecondary))
            })
            val times = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val st = edit(SrtExporter.formatTime(c.startMs), true, false)
            val en = edit(SrtExporter.formatTime(c.endMs), true, false)
            times.addView(st, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            times.addView(TextView(this).apply {
                text = "  →  "
                setTextColor(Ui.color(this@SrtEditorActivity, R.color.textSecondary))
            })
            times.addView(en, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(times, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
            if (rich && hasTarget) {
                card.addView(confidencePreview(c), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) })
            } else if (c.wordConfidence.isNotEmpty()) {
                card.addView(confidencePreview(c), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) })
            }
            if (rich && hasTarget) {
                // Párhuzamos eredeti/fordítás nézet: az angol forrás közvetlenül a magyar
                // szöveg felett jelenik meg, így a fordítás ellenőrizhető anélkül, hogy
                // el kellene hagyni a szerkesztőt. A forrás szándékosan csak olvasható.
                val sourceBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        setColor(Ui.color(this@SrtEditorActivity,  R.color.card))
                    }
                }
                sourceBox.addView(TextView(this).apply {
                    text = "ANGOL EREDETI"
                    textSize = 10f
                    setTypeface(typeface, Typeface.BOLD)
                    letterSpacing = 0.08f
                    setTextColor(Ui.color(this@SrtEditorActivity, R.color.textSecondary))
                })
                sourceBox.addView(TextView(this).apply {
                    text = c.source
                    textSize = 14f
                    setTextColor(Ui.color(this@SrtEditorActivity, R.color.textPrimary))
                    setPadding(0, dp(4), 0, 0)
                    setTextIsSelectable(true)
                })
                card.addView(sourceBox, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) })
            }
            if (rich && hasTarget) {
                card.addView(TextView(this).apply {
                    text = "MAGYAR FORDÍTÁS · szerkeszthető"
                    textSize = 10f
                    letterSpacing = 0.08f
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(Ui.color(this@SrtEditorActivity, R.color.accentGlow))
                }, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) })
            }
            val tx = edit(if (rich && hasTarget) c.target else c.source, false, true)
            card.addView(tx, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) })
            if (videoReady) {
                card.addView(Ui.smallButton(this, "▶ Meghallgat / megnéz") { applyRows(); playSpan(c.startMs, c.endMs) })
            }
            if (rich && hasTarget) {
                card.addView(Ui.smallButton(this, "Újrafordítás (online)") { retranslate(listOf(i), OnlinePrefs.ONLINE) })
                card.addView(Ui.smallButton(this, "Fordítások összehasonlítása") { compare(i) })
                card.addView(Ui.smallButton(this, "Szakasz újrafordítása egyben (±4 sor)") {
                    applyRows()
                    retranslate(((i - 4).coerceAtLeast(0)..(i + 4).coerceAtMost(cues.size - 1)).toList(), OnlinePrefs.ONLINE, true)
                })
                card.addView(Ui.smallButton(this, "Javítás szabállyá") { makeRule(i, tx.text.toString()) })
            }
            card.addView(Ui.smallButton(this, "Blokk törlése") {
                applyRows()
                if (i in cues.indices) { pushUndo(); cues.removeAt(i); dirty = true }
                rebuild()
            })
            attachSwipeToDelete(card, i)
            listBox.addView(card)
            cardOf[i] = card
            tx.setOnFocusChangeListener { _, has -> if (has) try { video?.pause(); updatePlayBtn() } catch (e: Exception) { DiagLog.swallow("SrtEditorActivity", e) } }
            rows.add(RowViews(i, st, en, tx))
        }
        scroll.post { scroll.scrollTo(0, 0) }
    }
}
