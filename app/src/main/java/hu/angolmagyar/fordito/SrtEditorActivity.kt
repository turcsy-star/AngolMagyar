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
import android.view.View
import android.view.ViewGroup
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

/**
 * Kész felirat átnézése és javítása: blokkonként szerkeszthető szöveg és időzítés, a gyanús blokkok
 * (üres, ismétlődő, túl gyors, hibás idő) jelölve vannak és külön szűrhetők.
 * A mentés a videó mellé (vagy kézzel választott helyre) készít egy ".szerk.srt" fájlt.
 */
class SrtEditorActivity : AppCompatActivity() {

    companion object {
        private const val EXTRA_IDX = "idx"
        private const val PAGE = 20

        fun open(ctx: Context, queueIndex: Int) {
            ctx.startActivity(Intent(ctx, SrtEditorActivity::class.java).putExtra(EXTRA_IDX, queueIndex))
        }
    }

    private class RowViews(val idx: Int, val start: EditText, val end: EditText, val text: EditText)

    private var item: SrtJob.QItem? = null
    private val cues = ArrayList<SrtExporter.Cue>()
    private var page = 0
    private var onlySuspicious = false
    private var dirty = false
    private val rows = ArrayList<RowViews>()

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
        val qi = SrtJob.queue.getOrNull(idx)
        val srt = qi?.srt
        if (qi == null || srt == null) {
            Toast.makeText(this, "Nincs szerkeszthető felirat.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        item = qi
        cues.addAll(SubFormat.parseSrt(srt))

        val column = Ui.column(this).apply { setPadding(dp(16), dp(16), dp(16), dp(32)) }
        scroll = Ui.scrollOf(this, column)
        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val b = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(b.left, b.top, b.right, b.bottom)
            insets
        }

        column.addView(Ui.button(this, "← Vissza", false) { confirmLeave() })
        column.addView(Ui.title(this, "Felirat szerkesztése"))
        column.addView(Ui.small(this, qi.name))
        summary = Ui.small(this, "")
        column.addView(summary)
        column.addView(SwitchCompat(this).apply {
            text = "Csak a gyanús blokkok"
            minHeight = dp(48)
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
    private fun applyRows() {
        for (r in rows) {
            if (r.idx !in cues.indices) continue
            val old = cues[r.idx]
            val s = SubFormat.parseTime(r.start.text.toString()) ?: old.startMs
            val e = SubFormat.parseTime(r.end.text.toString()) ?: old.endMs
            val t = r.text.text.toString().trim()
            if (s != old.startMs || e != old.endMs || t != old.source.trim()) {
                cues[r.idx] = old.copy(startMs = s, endMs = e, source = t)
                dirty = true
            }
        }
    }

    private fun commitToItem() {
        val qi = item ?: return
        qi.srt = SubFormat.buildRaw(cues)
        qi.cueCount = cues.count { c -> c.source.isNotBlank() }
    }

    private fun save() {
        applyRows()
        commitToItem()
        val qi = item ?: return
        val bytes = (qi.srt ?: "").toByteArray(Charsets.UTF_8)
        val where = if (OutputPrefs.saveMode(this) == OutputPrefs.SAVE_BESIDE)
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

    private fun rebuild() {
        // gyanús blokkok
        val flags = arrayOfNulls<String>(cues.size)
        var prev: SrtExporter.Cue? = null
        var suspCount = 0
        for ((i, c) in cues.withIndex()) {
            flags[i] = SubFormat.suspicious(prev, c)
            if (flags[i] != null) suspCount++
            prev = c
        }
        val view = cues.indices.filter { !onlySuspicious || flags[it] != null }
        val pages = ((view.size + PAGE - 1) / PAGE).coerceAtLeast(1)
        page = page.coerceIn(0, pages - 1)
        summary.text = "${cues.size} blokk · $suspCount gyanús" + if (dirty) " · módosítva" else ""
        pageLabel.text = "${page + 1}/$pages. oldal"

        rows.clear()
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
            val tx = edit(c.source, false, true)
            card.addView(tx, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) })
            card.addView(Ui.smallButton(this, "Blokk törlése") {
                applyRows()
                if (i in cues.indices) { cues.removeAt(i); dirty = true }
                rebuild()
            })
            listBox.addView(card)
            rows.add(RowViews(i, st, en, tx))
        }
        scroll.post { scroll.scrollTo(0, 0) }
    }
}
