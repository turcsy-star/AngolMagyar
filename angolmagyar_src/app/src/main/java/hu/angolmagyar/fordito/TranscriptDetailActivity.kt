package hu.angolmagyar.fordito

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.io.OutputStream
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Egy átirat részletei: sorok időbélyeggel és beszélővel, keresés (kiemeléssel),
 * sor szerkesztése (a gyakori javításokat megtanulja), beszélő hozzárendelése/átnevezése,
 * hanglejátszás a sor időpontjától (ha van mentett hang), export (SRT, VTT, MD, TXT, DOCX, PDF),
 * összefoglaló döntésekkel és teendőkkel.
 */
class TranscriptDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FILE = "file"
        const val EXTRA_SEEK_MS = "seek_ms"
        const val EXTRA_QUERY = "query"
        const val EXTRA_TRASHED = "trashed"
    }

    private lateinit var prefs: Prefs
    private lateinit var file: File
    private val ui = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()

    private val cues = mutableListOf<SrtExporter.Cue>()
    private var rawMode = false
    private var rawText = ""
    private var audio: File? = null

    private lateinit var listView: ListView
    private lateinit var titleView: TextView
    private lateinit var infoView: TextView
    private lateinit var searchBox: AppCompatEditText
    private var shown: List<Int> = emptyList()
    private var filterQuery = ""
    private var highlightQuery = ""

    private var player: MediaPlayer? = null
    private var playingIdx = -1
    private var playSeq = 0
    private val stopRun = Runnable { stopPlay() }

    private var pendingWriter: ((OutputStream) -> Unit)? = null

    private val createLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            val writer = pendingWriter
            pendingWriter = null
            if (uri == null || writer == null) return@registerForActivityResult
            bg.execute {
                try {
                    contentResolver.openOutputStream(uri)?.use { writer(it) }
                    ui.post { toast(R.string.td_saved) }
                } catch (e: Exception) {
                    ui.post { Toast.makeText(this, getString(R.string.td_save_error, e.message ?: ""), Toast.LENGTH_LONG).show() }
                }
            }
        }

    private fun dp(v: Int) = Ui.dp(this, v)
    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    // ------------------------------------------------------------------ életciklus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val name = intent.getStringExtra(EXTRA_FILE)
        if (name == null) {
            finish()
            return
        }
        file = File(TranscriptStore.dir(this), name)
        if (!file.exists()) {
            finish()
            return
        }
        highlightQuery = intent.getStringExtra(EXTRA_QUERY) ?: ""

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        setContentView(root)
        val side = max(0, (resources.displayMetrics.widthPixels - dp(640)) / 2)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(b.left + side, b.top, b.right + side, b.bottom)
            insets
        }

        // ---- fejléc
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(4))
        }
        header.addView(Ui.iconButton(this, R.drawable.ic_back, getString(R.string.common_back)) { finish() })
        titleView = Ui.title(this, "", 18f).apply {
            setPadding(dp(12), 0, dp(8), 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setOnClickListener { renameDialog() }
        }
        header.addView(titleView, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val exportBtn = Ui.smallButton(this, getString(R.string.td_export)) {}
        exportBtn.setOnClickListener { showExportMenu(exportBtn) }
        header.addView(exportBtn)
        val moreBtn = Ui.iconButton(this, R.drawable.ic_more, getString(R.string.common_more)) {}
        moreBtn.setOnClickListener { showMoreMenu(moreBtn) }
        header.addView(moreBtn, LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(8) })
        root.addView(header)

        infoView = Ui.small(this, "").apply { setPadding(dp(20), 0, dp(20), dp(4)) }
        root.addView(infoView)

        searchBox = AppCompatEditText(this).apply {
            hint = getString(R.string.td_search_hint)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setText(highlightQuery)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    filterQuery = s?.toString()?.trim() ?: ""
                    highlightQuery = filterQuery
                    applyFilter()
                }
            })
        }
        // a címből érkező keresés csak kiemel, nem szűr
        filterQuery = ""
        root.addView(
            searchBox,
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(2), dp(16), dp(4)) }
        )

        listView = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(Ui.color(this@TranscriptDetailActivity, R.color.stroke))
            dividerHeight = 1
            clipToPadding = false
            setPadding(dp(16), 0, dp(16), dp(24))
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            adapter = CueAdapter()
            setOnItemClickListener { _, _, pos, _ -> shown.getOrNull(pos)?.let { showCueActions(it) } }
        }
        root.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))

        load()
    }

    override fun onPause() {
        super.onPause()
        stopPlay()
    }

    override fun onDestroy() {
        stopPlay()
        ui.removeCallbacksAndMessages(null)
        bg.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ betöltés / mentés

    private fun displayTitle(): String {
        val t = TranscriptRepo.readMeta(file).first
        return t.ifBlank { file.nameWithoutExtension.removePrefix("atirat_") }
    }

    private fun load() {
        val seek = intent.getLongExtra(EXTRA_SEEK_MS, -1L)
        bg.execute {
            val text = try {
                file.readText()
            } catch (e: Exception) {
                ""
            }
            val raw = file.name.startsWith("jegyzokonyv_") || file.name.startsWith("felirat_") ||
                !Regex("(?m)^\\[\\d{2}:\\d{2}:\\d{2}\\]").containsMatchIn(text)
            val parsed = SrtExporter.parseCuesFromTranscript(text)
            val a = TranscriptStore.audioFile(file)
            ui.post {
                rawText = text
                rawMode = raw
                cues.clear()
                cues.addAll(parsed)
                audio = if (a.exists() && a.length() > 0 && !raw) a else null
                titleView.text = displayTitle()
                applyFilter()
                updateInfo()
                if (seek >= 0 && !raw) {
                    val idx = cues.indexOfLast { it.startMs <= seek }
                    if (idx >= 0) {
                        val pos = shown.indexOf(idx)
                        if (pos >= 0) listView.setSelection(pos)
                    }
                }
            }
        }
    }

    private fun updateInfo() {
        val last = cues.maxOfOrNull { it.startMs } ?: 0L
        val s = last / 1000
        val dur = String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
        val parts = mutableListOf(getString(R.string.tr_entries, cues.size), dur)
        if (!rawMode) {
            // Gyors, teljes átiratra vonatkozó összesítés; a soronkénti audit továbbra is heurisztikus.
            val reviewCount = cues.count { cue ->
                cue.source.isNotBlank() && cue.target.isNotBlank() &&
                    cue.source != "…" && cue.target != "…" &&
                    TranslationQualityAudit.inspect(cue.source, cue.target).needsReview
            }
            if (reviewCount > 0) parts.add("◇ Ellenőrzendő sor: $reviewCount")
            else if (cues.any { it.source.isNotBlank() && it.target.isNotBlank() }) parts.add("✓ Alapellenőrzés kész")
        }
        if (audio != null) parts.add(getString(R.string.tr_has_audio))
        if (rawMode) parts.add(getString(R.string.td_raw_mode))
        infoView.text = parts.joinToString("  ·  ")
    }

    /** A cue-lista mentése a fájlba (háttérszálon). */
    private fun persist() {
        if (rawMode) return
        val snap = cues.toList()
        bg.execute {
            try {
                file.writeText(SrtExporter.toTranscriptText(snap))
            } catch (e: Exception) {
                ui.post { Toast.makeText(this, getString(R.string.td_save_error, e.message ?: ""), Toast.LENGTH_LONG).show() }
            }
        }
    }

    private fun applyFilter() {
        val q = TranscriptRepo.foldKeep(filterQuery)
        shown = if (q.isEmpty()) {
            cues.indices.toList()
        } else {
            cues.indices.filter { i ->
                val c = cues[i]
                TranscriptRepo.foldKeep(c.source + " " + c.target + " " + c.speaker).contains(q)
            }
        }
        (listView.adapter as BaseAdapter).notifyDataSetChanged()
    }

    // ------------------------------------------------------------------ lista

    private fun highlight(text: String): CharSequence {
        val q = TranscriptRepo.foldKeep(highlightQuery)
        if (q.isEmpty() || text.isEmpty()) return text
        val ft = TranscriptRepo.foldKeep(text)
        val sp = SpannableString(text)
        var idx = ft.indexOf(q)
        while (idx >= 0) {
            sp.setSpan(
                BackgroundColorSpan(0x80FFD54F.toInt()), idx, idx + q.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            idx = ft.indexOf(q, idx + q.length)
        }
        return sp
    }

    private inner class CueAdapter : BaseAdapter() {
        override fun getCount() = shown.size
        override fun getItem(position: Int): Any = shown[position]
        override fun getItemId(position: Int) = shown[position].toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val idx = shown[position]
            val c = cues[idx]
            val row = (convertView as? LinearLayout) ?: LinearLayout(this@TranscriptDetailActivity).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = AbsListView.LayoutParams(-1, -2)
                setPadding(dp(2), dp(10), dp(2), dp(10))
            }
            row.removeAllViews()
            row.setBackgroundColor(if (idx == playingIdx) 0x226366F1 else 0)

            val top = LinearLayout(this@TranscriptDetailActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            top.addView(TextView(this@TranscriptDetailActivity).apply {
                text = TranscriptStore.stamp(c.startMs)
                textSize = 12f
                setTextColor(Ui.color(this@TranscriptDetailActivity, R.color.primary))
            })
            if (c.speaker.isNotBlank()) {
                top.addView(TextView(this@TranscriptDetailActivity).apply {
                    text = c.speaker
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(Ui.color(this@TranscriptDetailActivity, R.color.textPrimary))
                    setPadding(dp(10), 0, 0, 0)
                })
            }
            top.addView(View(this@TranscriptDetailActivity), LinearLayout.LayoutParams(0, 1, 1f))
            if (audio != null) {
                val playing = idx == playingIdx
                top.addView(AppCompatImageButton(this@TranscriptDetailActivity).apply {
                    setImageResource(if (playing) R.drawable.ic_stop else R.drawable.ic_play)
                    setColorFilter(Ui.color(this@TranscriptDetailActivity, R.color.primary))
                    setBackgroundColor(0)
                    isFocusable = false
                    contentDescription = getString(R.string.td_action_play)
                    setOnClickListener { togglePlay(idx) }
                }, LinearLayout.LayoutParams(dp(40), dp(40)))
            }
            row.addView(top)

            val main = c.target.ifBlank { c.source }
            row.addView(TextView(this@TranscriptDetailActivity).apply {
                text = highlight(if (main == "…") "" else main)
                textSize = 15f
                setTextColor(Ui.color(this@TranscriptDetailActivity, R.color.textPrimary))
            })
            if (c.source.isNotBlank() && c.source != "…" && c.source != main) {
                row.addView(TextView(this@TranscriptDetailActivity).apply {
                    text = highlight(c.source)
                    textSize = 12.5f
                    setTypeface(typeface, android.graphics.Typeface.ITALIC)
                    setTextColor(Ui.color(this@TranscriptDetailActivity, R.color.textSecondary))
                    setPadding(0, dp(2), 0, 0)
                })
            }
            // Nem módosítjuk a fordítást: csak azokat a sorokat jelöljük, amelyek emberi ellenőrzést érdemelhetnek.
            if (c.source.isNotBlank() && c.target.isNotBlank() && c.source != "…" && c.target != "…") {
                val audit = TranslationQualityAudit.inspect(c.source, c.target)
                if (audit.needsReview) {
                    row.addView(TextView(this@TranscriptDetailActivity).apply {
                        text = "◇ Ellenőrzés javasolt · " + audit.findings.joinToString(" · ") { it.message }
                        textSize = 11.5f
                        setTextColor(0xFFB7791F.toInt())
                        setPadding(dp(8), dp(5), dp(8), dp(5))
                        setBackgroundColor(0x14D69E2E)
                    })
                }
            }
            return row
        }
    }

    // ------------------------------------------------------------------ sor műveletek

    private fun showCueActions(idx: Int) {
        val items = mutableListOf<Pair<String, () -> Unit>>()
        if (!rawMode) {
            items.add(getString(R.string.td_action_edit) to { editCue(idx) })
            items.add(getString(R.string.td_action_speaker_one) to { pickSpeaker(idx, false) })
            items.add(getString(R.string.td_action_speaker_from) to { pickSpeaker(idx, true) })
        }
        items.add(getString(R.string.td_action_copy) to { copyCue(idx) })
        if (audio != null) items.add(getString(R.string.td_action_play) to { togglePlay(idx) })
        AlertDialog.Builder(this)
            .setItems(items.map { it.first }.toTypedArray()) { _, which -> items[which].second() }
            .show()
    }

    private fun copyCue(idx: Int) {
        val c = cues[idx]
        val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(
            android.content.ClipData.newPlainText("transcript", (c.source + "\n" + c.target).trim())
        )
        toast(R.string.td_copied)
    }

    private fun editCue(idx: Int) {
        val old = cues[idx]
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        fun edit(hintRes: Int, value: String) = AppCompatEditText(this).apply {
            hint = getString(hintRes)
            setText(if (value == "…") "" else value)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        }
        val spk = edit(R.string.td_speaker_hint, old.speaker)
        val tgt = edit(R.string.td_hint_target, old.target)
        val src = edit(R.string.td_hint_source, old.source)
        box.addView(spk)
        box.addView(tgt)
        box.addView(src)
        AlertDialog.Builder(this)
            .setTitle(R.string.td_action_edit)
            .setView(ScrollView(this).apply { addView(box) })
            .setPositiveButton(R.string.common_save) { _, _ ->
                val ns = src.text?.toString()?.trim() ?: ""
                val nt = tgt.text?.toString()?.trim() ?: ""
                val sp = (spk.text?.toString() ?: "").replace("{", "").replace("}", "").trim()
                cues[idx] = old.copy(source = ns, target = nt, speaker = sp)
                persist()
                applyFilter()
                val learned = VocabLearner.record(prefs, old.source, ns, false) +
                    VocabLearner.record(prefs, old.target, nt, true)
                if (learned.isNotEmpty()) askLearn(learned, 0)
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun askLearn(list: List<VocabLearner.Suggestion>, i: Int) {
        if (i >= list.size) return
        val s = list[i]
        AlertDialog.Builder(this)
            .setTitle(R.string.td_learn_title)
            .setMessage(
                getString(
                    R.string.td_learn_msg, s.from, s.to,
                    getString(if (s.target) R.string.td_learn_list_target else R.string.td_learn_list_source)
                )
            )
            .setPositiveButton(R.string.td_learn_add) { _, _ ->
                VocabLearner.accept(prefs, s)
                askLearn(list, i + 1)
            }
            .setNegativeButton(R.string.td_learn_skip) { _, _ ->
                VocabLearner.dismiss(prefs, s)
                askLearn(list, i + 1)
            }
            .setCancelable(false)
            .show()
    }

    // ------------------------------------------------------------------ beszélők

    private fun speakerNames(): List<String> =
        cues.map { it.speaker }.filter { it.isNotBlank() }.distinct()

    private fun pickSpeaker(idx: Int, fromHere: Boolean) {
        val names = speakerNames()
        val labels = names + listOf(getString(R.string.td_speaker_new), getString(R.string.td_speaker_none))
        AlertDialog.Builder(this)
            .setTitle(R.string.td_speaker_title)
            .setItems(labels.toTypedArray()) { _, which ->
                when {
                    which < names.size -> assignSpeaker(idx, names[which], fromHere)
                    which == names.size -> askName(getString(R.string.td_speaker_new), "") { assignSpeaker(idx, it, fromHere) }
                    else -> assignSpeaker(idx, "", fromHere)
                }
            }
            .show()
    }

    private fun assignSpeaker(idx: Int, name: String, fromHere: Boolean) {
        val clean = name.replace("{", "").replace("}", "").trim()
        var end = idx + 1
        if (fromHere) {
            val current = cues[idx].speaker
            while (end < cues.size) {
                val sp = cues[end].speaker
                if (sp.isNotBlank() && sp != current) break
                end++
            }
        }
        for (k in idx until end) cues[k] = cues[k].copy(speaker = clean)
        persist()
        applyFilter()
    }

    private fun askName(title: String, initial: String, onOk: (String) -> Unit) {
        val input = AppCompatEditText(this).apply {
            setText(initial)
            setSingleLine(true)
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(-1, -2))
        }
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(box)
            .setPositiveButton(R.string.common_save) { _, _ ->
                val v = input.text?.toString()?.trim() ?: ""
                if (v.isNotEmpty()) onOk(v)
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun renameSpeakerEverywhere() {
        val names = speakerNames()
        if (names.isEmpty()) {
            toast(R.string.td_no_speakers)
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.td_speaker_rename_all)
            .setItems(names.toTypedArray()) { _, which ->
                val from = names[which]
                askName(getString(R.string.td_rename_to, from), from) { to ->
                    val clean = to.replace("{", "").replace("}", "").trim()
                    for (k in cues.indices) if (cues[k].speaker == from) cues[k] = cues[k].copy(speaker = clean)
                    persist()
                    applyFilter()
                }
            }
            .show()
    }

    // ------------------------------------------------------------------ hanglejátszás

    private fun togglePlay(idx: Int) {
        if (playingIdx == idx) {
            stopPlay()
            return
        }
        val a = audio ?: run {
            toast(R.string.td_no_audio)
            return
        }
        stopPlay()
        val c = cues[idx]
        val seq = ++playSeq
        playingIdx = idx
        (listView.adapter as BaseAdapter).notifyDataSetChanged()
        try {
            val mp = MediaPlayer()
            player = mp
            mp.setDataSource(a.absolutePath)
            mp.setOnPreparedListener { p ->
                if (seq == playSeq) p.seekTo(c.startMs.toInt())
            }
            mp.setOnSeekCompleteListener { p ->
                if (seq == playSeq) {
                    p.start()
                    val dur = minOf(c.endMs - c.startMs, 30_000L).coerceAtLeast(2_000L) + 1_200L
                    ui.postDelayed(stopRun, dur)
                }
            }
            mp.setOnCompletionListener { if (seq == playSeq) stopPlay() }
            mp.setOnErrorListener { _, _, _ ->
                if (seq == playSeq) {
                    stopPlay()
                    toast(R.string.td_audio_error)
                }
                true
            }
            mp.prepareAsync()
        } catch (e: Exception) {
            stopPlay()
            toast(R.string.td_audio_error)
        }
    }

    private fun stopPlay() {
        ui.removeCallbacks(stopRun)
        playSeq++
        try {
            player?.release()
        } catch (_: Exception) {
        }
        player = null
        if (playingIdx >= 0) {
            playingIdx = -1
            if (::listView.isInitialized) (listView.adapter as BaseAdapter).notifyDataSetChanged()
        }
    }

    // ------------------------------------------------------------------ cím / címke

    private fun renameDialog() {
        val (title, tag) = TranscriptRepo.readMeta(file)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
        }
        val t = AppCompatEditText(this).apply {
            setText(title)
            hint = getString(R.string.tr_hint_title)
            setSingleLine(true)
        }
        val g = AppCompatEditText(this).apply {
            setText(tag)
            hint = getString(R.string.tr_hint_tag)
            setSingleLine(true)
        }
        box.addView(t)
        box.addView(g)
        AlertDialog.Builder(this)
            .setTitle(R.string.tr_rename_tag)
            .setView(box)
            .setPositiveButton(R.string.common_save) { _, _ ->
                TranscriptRepo.writeMeta(
                    file, t.text?.toString() ?: "",
                    (g.text?.toString() ?: "").trim().removePrefix("#")
                )
                titleView.text = displayTitle()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    // ------------------------------------------------------------------ menük

    private fun showMoreMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        var id = 0
        val actions = mutableListOf<() -> Unit>()
        fun add(res: Int, enabled: Boolean = true, action: () -> Unit) {
            val mi = pm.menu.add(0, id++, 0, getString(res))
            mi.isEnabled = enabled
            actions.add(action)
        }
        add(R.string.td_summary) { showSummary() }
        add(R.string.td_share) { shareText() }
        add(R.string.td_clean, !rawMode) { cleanAll() }
        add(R.string.td_speaker_rename_all, !rawMode) { renameSpeakerEverywhere() }
        add(R.string.td_edit_all) { editAll() }
        add(R.string.tr_rename_tag) { renameDialog() }
        add(R.string.common_delete) { confirmDelete() }
        pm.setOnMenuItemClickListener { mi ->
            actions.getOrNull(mi.itemId)?.invoke()
            true
        }
        pm.show()
    }

    private fun showExportMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        var id = 0
        val actions = mutableListOf<() -> Unit>()
        fun add(res: Int, action: () -> Unit) {
            pm.menu.add(0, id++, 0, getString(res))
            actions.add(action)
        }
        add(R.string.td_export_srt_both) { exportSrt(SrtExporter.Mode.BOTH) }
        add(R.string.td_export_srt_target) { exportSrt(SrtExporter.Mode.TARGET_ONLY) }
        add(R.string.td_export_vtt) { exportVtt() }
        add(R.string.td_export_ass) { exportAss() }
        add(R.string.td_export_md) { exportMd() }
        add(R.string.td_export_txt) { exportTxt() }
        add(R.string.td_export_docx) { exportDocx() }
        add(R.string.td_export_pdf) { exportPdf() }
        pm.setOnMenuItemClickListener { mi ->
            actions.getOrNull(mi.itemId)?.invoke()
            true
        }
        pm.show()
    }

    // ------------------------------------------------------------------ export

    private fun baseName(): String =
        displayTitle().replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").trim('_').ifBlank { "atirat" }

    private fun hasTargets(snap: List<SrtExporter.Cue>) =
        snap.any { it.target.isNotBlank() && it.target != "…" }

    private fun saveAs(fileName: String, writer: (OutputStream) -> Unit) {
        if (cues.isEmpty()) {
            toast(R.string.td_empty_file)
            return
        }
        pendingWriter = writer
        createLauncher.launch(fileName)
    }

    /** Export előtti minőségkapu: hibákat és figyelmeztetéseket mutat, automatikusan nem javít. */
    private fun confirmSubtitleExport(snap: List<SrtExporter.Cue>, mode: SrtExporter.Mode, onContinue: () -> Unit) {
        val report = SrtQualityCheck.inspect(snap, mode)
        if (report.issues.isEmpty()) {
            onContinue()
            return
        }
        val summary = buildString {
            append("Hibák: ").append(report.errorCount)
            append(" · Figyelmeztetések: ").append(report.warningCount).append("\n\n")
            report.issues.take(8).forEach { issue ->
                append(if (issue.severity == SrtQualityCheck.Severity.ERROR) "⛔ " else "⚠ ")
                append("#").append(issue.cueIndex).append(" – ").append(issue.message).append('\n')
            }
            if (report.issues.size > 8) append("… és további ").append(report.issues.size - 8).append(" jelzés")
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Export előtti minőségellenőrzés")
            .setMessage(summary)
            .setNegativeButton("Mégse", null)
            .setNeutralButton("Hibák listája") { _, _ ->
                val labels = report.issues.map { issue ->
                    val marker = if (issue.severity == SrtQualityCheck.Severity.ERROR) "⛔" else "⚠"
                    "$marker  #${issue.cueIndex} – ${issue.message}"
                }
                AlertDialog.Builder(this)
                    .setTitle("Érintett feliratsorok (${report.issues.size})")
                    .setItems(labels.toTypedArray()) { _, which ->
                        jumpToCue(report.issues[which].cueIndex)
                    }
                    .setNegativeButton("Bezárás", null)
                    .show()
            }
        if (report.canExport) {
            dialog.setPositiveButton("Export folytatása") { _, _ -> onContinue() }
        } else {
            dialog.setPositiveButton("Export mégis") { _, _ -> onContinue() }
        }
        dialog.show()
    }

    /** A jelentés 1-alapú sorszámát a szűrt lista tényleges pozíciójára képezi le. */
    private fun jumpToCue(cueNumber: Int) {
        if (!::listView.isInitialized || cues.isEmpty()) return
        val cueIndex = (cueNumber - 1).coerceIn(0, cues.lastIndex)
        if (cueIndex !in shown) {
            // A hibás sor akkor is elérhető legyen, ha az aktuális keresés elrejti.
            if (::searchBox.isInitialized && filterQuery.isNotEmpty()) searchBox.setText("")
            else applyFilter()
        }
        val visiblePosition = shown.indexOf(cueIndex)
        if (visiblePosition < 0) return
        listView.smoothScrollToPosition(visiblePosition)
        listView.post {
            val child = listView.getChildAt(visiblePosition - listView.firstVisiblePosition)
            child?.requestFocus()
        }
    }

    private fun exportSrt(mode: SrtExporter.Mode) {
        val snap = cues.toList()
        val m = if (hasTargets(snap)) mode else SrtExporter.Mode.SOURCE_ONLY
        confirmSubtitleExport(snap, m) {
            saveAs(baseName() + ".srt") { out ->
                out.write(SrtExporter.build(snap, m).toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun exportVtt() {
        val snap = cues.toList()
        val m = if (hasTargets(snap)) SrtExporter.Mode.BOTH else SrtExporter.Mode.SOURCE_ONLY
        confirmSubtitleExport(snap, m) {
            saveAs(baseName() + ".vtt") { out ->
                out.write(SrtExporter.buildVtt(snap, m).toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun exportAss() {
        val snap = cues.toList()
        val m = if (hasTargets(snap)) SrtExporter.Mode.BOTH else SrtExporter.Mode.SOURCE_ONLY
        confirmSubtitleExport(snap, m) {
            saveAs(baseName() + ".ass") { out ->
                out.write(SrtExporter.buildAss(snap, m).toByteArray(Charsets.UTF_8))
            }
        }
    }

    /** A minőségkapu minden szöveges és dokumentum-export előtt lefut. */
    private fun confirmDocumentExport(snap: List<SrtExporter.Cue>, onContinue: () -> Unit) {
        val mode = if (hasTargets(snap)) SrtExporter.Mode.BOTH else SrtExporter.Mode.SOURCE_ONLY
        confirmSubtitleExport(snap, mode, onContinue)
    }

    private fun exportMd() {
        val snap = cues.toList()
        val title = displayTitle()
        confirmDocumentExport(snap) {
            saveAs(baseName() + ".md") { out ->
                out.write(ExportHelper.toMarkdown(snap, title).toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun exportTxt() {
        val snap = cues.toList()
        val title = displayTitle()
        confirmDocumentExport(snap) {
            saveAs(baseName() + "-jegyzokonyv.txt") { out ->
                out.write(ExportHelper.toPlainMinutes(snap, title).toByteArray(Charsets.UTF_8))
            }
        }
    }

    private fun exportDocx() {
        val snap = cues.toList()
        val title = displayTitle()
        val map = SpeakerNames.fromPrefs(prefs)
        confirmDocumentExport(snap) {
            saveAs(baseName() + ".docx") { out ->
                val summary = MeetingSummary.generate(snap, title, map)
                ExportHelper.writeDocx(out, snap, title, summary, hasTargets(snap))
            }
        }
    }

    private fun exportPdf() {
        val snap = cues.toList()
        val title = displayTitle()
        val map = SpeakerNames.fromPrefs(prefs)
        confirmDocumentExport(snap) {
            saveAs(baseName() + ".pdf") { out ->
                val summary = MeetingSummary.generate(snap, title, map)
                ExportHelper.writePdf(out, snap, title, summary, hasTargets(snap))
            }
        }
    }

    private fun shareText() {
        val text = if (rawMode) rawText else SrtExporter.toTranscriptText(cues)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, displayTitle())
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, getString(R.string.td_share)))
    }

    // ------------------------------------------------------------------ összefoglaló

    private fun showSummary() {
        if (cues.isEmpty()) {
            toast(R.string.td_empty_file)
            return
        }
        val title = displayTitle()
        val summary = MeetingSummary.generate(cues.toList(), title, SpeakerNames.fromPrefs(prefs))
        val msg = StringBuilder()
        msg.append(getString(R.string.td_sum_duration, summary.durationLabel)).append('\n')
        msg.append(getString(R.string.td_sum_entries, summary.cueCount)).append("\n")
        if (summary.decisions.isNotEmpty()) {
            msg.append("\n").append(getString(R.string.td_sum_decisions)).append('\n')
            summary.decisions.take(6).forEach { msg.append("✔ ").append(it).append('\n') }
        }
        if (summary.actions.isNotEmpty()) {
            msg.append("\n").append(getString(R.string.td_sum_actions)).append('\n')
            summary.actions.take(8).forEach { msg.append("☐ ").append(it).append('\n') }
        }
        if (summary.keyLines.isNotEmpty()) {
            msg.append("\n").append(getString(R.string.td_sum_key)).append('\n')
            summary.keyLines.take(5).forEach { msg.append("• ").append(it).append('\n') }
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.td_summary)
            .setMessage(msg.toString().trim())
            .setPositiveButton(R.string.td_sum_save) { _, _ ->
                saveAs(baseName() + "-osszefoglalo.txt") { out ->
                    out.write(summary.fullText.toByteArray(Charsets.UTF_8))
                }
            }
            .setNeutralButton(R.string.common_share) { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, title)
                    putExtra(Intent.EXTRA_TEXT, summary.fullText)
                }
                startActivity(Intent.createChooser(send, getString(R.string.common_share)))
            }
            .setNegativeButton(R.string.common_close, null)
            .show()
    }

    // ------------------------------------------------------------------ tisztítás, szerkesztés, törlés

    private fun cleanAll() {
        val cur = SrtExporter.toTranscriptText(cues)
        val cleaned = TextPolish.cleanTranscript(
            cur, Fixes.parse(prefs.enFixes), Fixes.parse(prefs.huFixes)
        )
        val parsed = SrtExporter.parseCuesFromTranscript(cleaned)
        cues.clear()
        cues.addAll(parsed)
        persist()
        applyFilter()
        toast(R.string.td_cleaned)
    }

    private fun editAll() {
        val current = if (rawMode) rawText else SrtExporter.toTranscriptText(cues)
        val input = AppCompatEditText(this).apply {
            setText(current)
            minLines = 12
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.td_edit_all)
            .setView(ScrollView(this).apply { addView(input) })
            .setPositiveButton(R.string.common_save) { _, _ ->
                val text = input.text?.toString() ?: return@setPositiveButton
                bg.execute {
                    try {
                        file.writeText(text)
                    } catch (_: Exception) {
                    }
                    ui.post { load() }
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setMessage(R.string.td_delete_confirm)
            .setPositiveButton(R.string.common_delete) { _, _ ->
                stopPlay()
                val name = file.name
                bg.execute {
                    TranscriptRepo.trash(this, file)
                    ui.post {
                        setResult(RESULT_OK, Intent().putExtra(EXTRA_TRASHED, name))
                        finish()
                    }
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }
}
