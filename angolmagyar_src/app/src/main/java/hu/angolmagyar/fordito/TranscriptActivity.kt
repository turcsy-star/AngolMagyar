package hu.angolmagyar.fordito

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max

/**
 * Mentett átiratok listája: kártyák (dátum, hossz, első sor, címke), keresés az összes átiratban,
 * hosszú nyomásra menü, törlés visszavonással (kuka). A részletek: [TranscriptDetailActivity].
 */
class TranscriptActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private val ui = Handler(Looper.getMainLooper())
    private val bg = Executors.newSingleThreadExecutor()

    private lateinit var listView: ListView
    private lateinit var emptyView: TextView
    private lateinit var searchBox: AppCompatEditText
    private lateinit var undoBar: LinearLayout
    private lateinit var undoText: TextView

    private var infos: List<TranscriptRepo.Info> = emptyList()
    private var hits: List<TranscriptRepo.Hit> = emptyList()
    private var query = ""
    private var kindFilter = 0 // 0 = mind, 1 = élő, 2 = fájlból
    private var searchSeq = 0
    private var trashedNames: List<String> = emptyList()

    private val hideUndo = Runnable { undoBar.visibility = View.GONE }
    private val searchRunnable = Runnable { runSearch() }

    private val detailLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
            val name = res.data?.getStringExtra(TranscriptDetailActivity.EXTRA_TRASHED)
            if (name != null) showUndo(listOf(name), getString(R.string.tr_deleted))
        }

    private fun dp(v: Int) = Ui.dp(this, v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val root = FrameLayout(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(content, FrameLayout.LayoutParams(-1, -1))
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
        header.addView(
            Ui.title(this, getString(R.string.tr_title)).apply { setPadding(dp(12), 0, 0, 0) },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(Ui.iconButton(this, R.drawable.ic_star, getString(R.string.tr_favorites)) { showFavorites() })
        header.addView(
            Ui.iconButton(this, R.drawable.ic_more, getString(R.string.common_more)) {}.also { b ->
                b.setOnClickListener { showMainMenu(b) }
            },
            LinearLayout.LayoutParams(dp(46), dp(46)).apply { marginStart = dp(8) }
        )
        content.addView(header)
        content.addView(Ui.heroHeader(this, "LIBRARY", "Mentett átiratok", "Keresd vissza, szűrd és kezeld a korábbi beszélgetéseidet."), LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(4), dp(16), dp(8)) })

        // ---- keresés
        searchBox = AppCompatEditText(this).apply {
            hint = getString(R.string.tr_search_hint)
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString()?.trim() ?: ""
                    ui.removeCallbacks(searchRunnable)
                    ui.postDelayed(searchRunnable, 300)
                }
            })
        }
        content.addView(
            searchBox,
            LinearLayout.LayoutParams(-1, -2).apply {
                setMargins(dp(16), dp(4), dp(16), dp(4))
            }
        )

        // ---- szűrő: Mind / Élő / Fájlból
        val kindRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val kindButtons = ArrayList<android.widget.Button>()
        fun paintKinds() {
            kindButtons.forEachIndexed { i, b ->
                b.alpha = if (i == kindFilter) 1f else 0.55f
                b.setTypeface(null, if (i == kindFilter) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
        listOf("Mind", "Élő", "Fájlból").forEachIndexed { i, n ->
            val b = Ui.smallButton(this, n) {
                kindFilter = i
                paintKinds()
                refresh()
            }
            kindButtons.add(b)
            kindRow.addView(b, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = dp(8) })
        }
        paintKinds()
        content.addView(
            kindRow,
            LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(16), dp(2), dp(16), dp(6)) }
        )

        emptyView = Ui.body(this, getString(R.string.tr_empty)).apply {
            setPadding(dp(20), dp(24), dp(20), dp(24))
            visibility = View.GONE
        }
        content.addView(emptyView)

        listView = ListView(this).apply {
            divider = android.graphics.drawable.ColorDrawable(0)
            dividerHeight = dp(10)
            clipToPadding = false
            setPadding(dp(16), dp(4), dp(16), dp(24))
            selector = android.graphics.drawable.ColorDrawable(0)
            adapter = RowsAdapter()
            setOnItemClickListener { _, _, pos, _ -> onRowClick(pos) }
            setOnItemLongClickListener { _, v, pos, _ -> onRowLongClick(v, pos); true }
        }
        content.addView(listView, LinearLayout.LayoutParams(-1, 0, 1f))

        // ---- Visszavonás sáv (a kukából visszaállít)
        undoBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(dp(18), dp(8), dp(8), dp(8))
            elevation = dp(8).toFloat()
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Ui.color(this@TranscriptActivity, R.color.textPrimary))
                cornerRadius = dp(14).toFloat()
            }
        }
        undoText = TextView(this).apply {
            setTextColor(Ui.color(this@TranscriptActivity, R.color.bg))
            textSize = 14f
        }
        undoBar.addView(undoText, LinearLayout.LayoutParams(0, -2, 1f))
        undoBar.addView(Ui.smallButton(this, getString(R.string.tr_undo)) { undo() })
        root.addView(
            undoBar,
            FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
                setMargins(dp(16), 0, dp(16), dp(16))
            }
        )
    }

    override fun onResume() {
        super.onResume()
        reload()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        bg.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ adatok

    private fun reload() {
        bg.execute {
            val list = TranscriptRepo.listInfos(this)
            ui.post {
                infos = list
                refresh()
            }
        }
    }

    private fun runSearch() {
        val q = query
        if (q.isEmpty() || q.startsWith("#")) {
            hits = emptyList()
            refresh()
            return
        }
        val seq = ++searchSeq
        bg.execute {
            val res = TranscriptRepo.search(this, q)
            ui.post {
                if (seq == searchSeq) {
                    hits = res
                    refresh()
                }
            }
        }
    }

    private fun searching() = query.isNotEmpty() && !query.startsWith("#")

    /** A fájlból készült átiratok neve "jegyzokonyv_"-vel kezdődik, a többi élő felvétel. */
    private fun isFromFile(i: TranscriptRepo.Info) = i.file.name.startsWith("jegyzokonyv_")

    private fun visibleInfos(): List<TranscriptRepo.Info> {
        val base = when (kindFilter) {
            1 -> infos.filter { !isFromFile(it) }
            2 -> infos.filter { isFromFile(it) }
            else -> infos
        }
        if (query.startsWith("#")) {
            val tag = TranscriptRepo.fold(query.substring(1))
            return base.filter { tag.isEmpty() || TranscriptRepo.fold(it.tag).contains(tag) }
        }
        return base
    }

    private fun refresh() {
        (listView.adapter as BaseAdapter).notifyDataSetChanged()
        val empty = if (searching()) hits.isEmpty() else visibleInfos().isEmpty()
        emptyView.text = getString(if (searching() || query.startsWith("#")) R.string.tr_no_hits else R.string.tr_empty)
        emptyView.visibility = if (empty) View.VISIBLE else View.GONE
    }

    // ------------------------------------------------------------------ lista

    private inner class RowsAdapter : BaseAdapter() {
        override fun getCount() = if (searching()) hits.size else visibleInfos().size
        override fun getItem(position: Int): Any =
            if (searching()) hits[position] else visibleInfos()[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val card = (convertView as? LinearLayout) ?: Ui.card(this@TranscriptActivity).apply {
                alpha = 1f
                translationY = 0f
                layoutParams = android.widget.AbsListView.LayoutParams(-1, -2)
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            card.removeAllViews()
            val item = getItem(position)
            if (item is TranscriptRepo.Info) {
                bindInfo(card, item)
            } else if (item is TranscriptRepo.Hit) {
                bindHit(card, item)
            }
            return card
        }
    }

    private fun formatDur(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s % 60) else String.format("%d:%02d", m, s % 60)
    }

    private fun bindInfo(card: LinearLayout, i: TranscriptRepo.Info) {
        card.addView(Ui.title(this, i.title, 16f))
        val parts = mutableListOf(i.whenLabel)
        if (i.durationMs > 0) parts.add(formatDur(i.durationMs))
        parts.add(getString(R.string.tr_entries, i.entries))
        if (i.hasAudio) parts.add(getString(R.string.tr_has_audio))
        card.addView(Ui.small(this, parts.joinToString("  ·  ")))
        if (i.tag.isNotBlank()) {
            card.addView(Ui.small(this, "#" + i.tag).apply {
                setTextColor(Ui.color(this@TranscriptActivity, R.color.primary))
            })
        }
        if (i.firstLine.isNotBlank()) {
            card.addView(Ui.body(this, i.firstLine).apply {
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
                setPadding(0, dp(6), 0, 0)
            })
        }
    }

    private fun bindHit(card: LinearLayout, h: TranscriptRepo.Hit) {
        card.addView(Ui.small(this, h.title + "  ·  " + formatDur(h.stampMs)).apply {
            setTextColor(Ui.color(this@TranscriptActivity, R.color.primary))
        })
        card.addView(Ui.body(this, h.snippet).apply {
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
    }

    private fun onRowClick(pos: Int) {
        val item = if (searching()) hits.getOrNull(pos) else visibleInfos().getOrNull(pos)
        val intent = Intent(this, TranscriptDetailActivity::class.java)
        when (item) {
            is TranscriptRepo.Info -> intent.putExtra(TranscriptDetailActivity.EXTRA_FILE, item.file.name)
            is TranscriptRepo.Hit -> {
                intent.putExtra(TranscriptDetailActivity.EXTRA_FILE, item.file.name)
                intent.putExtra(TranscriptDetailActivity.EXTRA_SEEK_MS, item.stampMs)
                intent.putExtra(TranscriptDetailActivity.EXTRA_QUERY, query)
            }
            else -> return
        }
        detailLauncher.launch(intent)
    }

    private fun onRowLongClick(anchor: View, pos: Int) {
        val item = visibleInfos().getOrNull(pos) ?: return
        if (searching()) return
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, getString(R.string.tr_rename_tag))
        pm.menu.add(0, 2, 1, getString(R.string.common_delete))
        pm.setOnMenuItemClickListener { mi ->
            when (mi.itemId) {
                1 -> renameDialog(item)
                2 -> deleteOne(item.file)
            }
            true
        }
        pm.show()
    }

    // ------------------------------------------------------------------ műveletek

    private fun showMainMenu(anchor: View) {
        val pm = PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, getString(R.string.tr_delete_all))
        pm.setOnMenuItemClickListener {
            confirmDeleteAll()
            true
        }
        pm.show()
    }

    private fun renameDialog(i: TranscriptRepo.Info) {
        val (title, tag) = TranscriptRepo.readMeta(i.file)
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
                    i.file,
                    t.text?.toString() ?: "",
                    (g.text?.toString() ?: "").trim().removePrefix("#")
                )
                reload()
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun deleteOne(f: File) {
        val name = f.name
        bg.execute {
            TranscriptRepo.trash(this, f)
            ui.post {
                reload()
                showUndo(listOf(name), getString(R.string.tr_deleted))
            }
        }
    }

    private fun confirmDeleteAll() {
        val n = infos.size
        if (n == 0) {
            Toast.makeText(this, R.string.tr_nothing_to_delete, Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.tr_delete_all)
            .setMessage(getString(R.string.tr_delete_all_confirm, n))
            .setPositiveButton(R.string.common_delete) { _, _ ->
                val files = infos.map { it.file }
                bg.execute {
                    files.forEach { TranscriptRepo.trash(this, it) }
                    ui.post {
                        reload()
                        showUndo(files.map { it.name }, getString(R.string.tr_deleted_n, files.size))
                    }
                }
            }
            .setNegativeButton(R.string.common_cancel, null)
            .show()
    }

    private fun showUndo(names: List<String>, msg: String) {
        trashedNames = names
        undoText.text = msg
        undoBar.visibility = View.VISIBLE
        ui.removeCallbacks(hideUndo)
        ui.postDelayed(hideUndo, 7000)
    }

    private fun undo() {
        val names = trashedNames
        trashedNames = emptyList()
        undoBar.visibility = View.GONE
        ui.removeCallbacks(hideUndo)
        bg.execute {
            names.forEach { TranscriptRepo.restore(this, it) }
            ui.post { reload() }
        }
    }

    private fun showFavorites() {
        val items = Favorites.list(this)
        if (items.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.tr_favorites)
                .setMessage(R.string.tr_fav_empty)
                .setPositiveButton(R.string.common_close, null)
                .show()
            return
        }
        val labels = items.map { it.stamp + "\n" + it.source + "\n" + it.target }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.tr_favorites)
            .setItems(labels) { _, which ->
                val fav = items[which]
                AlertDialog.Builder(this)
                    .setMessage(fav.source + "\n\n" + fav.target)
                    .setPositiveButton(R.string.common_share) { _, _ ->
                        val send = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, fav.source + "\n" + fav.target)
                        }
                        startActivity(Intent.createChooser(send, getString(R.string.common_share)))
                    }
                    .setNegativeButton(R.string.common_delete) { _, _ ->
                        Favorites.remove(this, fav)
                    }
                    .setNeutralButton(R.string.common_close, null)
                    .show()
            }
            .setNeutralButton(R.string.tr_fav_share_all) { _, _ ->
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, Favorites.asText(items))
                }
                startActivity(Intent.createChooser(send, getString(R.string.common_share)))
            }
            .setPositiveButton(R.string.common_close, null)
            .show()
    }
}
