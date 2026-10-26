package hu.angolmagyar.fordito

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView

/**
 * Az élő képernyő két panelja: felül az eredeti szöveg, alatta a fordítás. Minden sor előtt a beszélő
 * színes jelölése; ha a beszélő más nyelvre vált, a nyelv neve is megjelenik. Alul a célnyelv-választó.
 */
class LivePanels(private val ctx: Context, private val prefs: Prefs, private val full: Boolean = false) {

    val root = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    private val origText: TextView
    private val trText: TextView
    private val origScroll: ScrollView
    private val trScroll: ScrollView
    private val trTitle: TextView
    private val panelViews = ArrayList<View>()
    private val bubbleBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val bubbleScroll = ScrollView(ctx)
    private class Bubble(val header: TextView, val tr: TextView, val orig: TextView)
    private val bubbles = HashMap<Long, Bubble>()
    private var bubbleMode = true
    private lateinit var modeBtn: TextView
    private var shown: List<LiveLine>? = null
    private var lastCount = 0
    private var lastTr = ""

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun panel(title: TextView, body: TextView): ScrollView {
        root.addView(title)
        panelViews.add(title)
        val sv = ScrollView(ctx).apply {
            isVerticalScrollBarEnabled = true
            setPadding(dp(10), dp(8), dp(10), dp(8))
            setBackgroundColor(Color.argb(28, 128, 128, 128))
            setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
            addView(body)
        }
        val lp = if (full) LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        else LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
        lp.bottomMargin = dp(8)
        root.addView(sv, lp)
        panelViews.add(sv)
        return sv
    }

    /** Élő hangszint-oszlopok a mikrofon fölött (a hangszintet maga olvassa, simán animálva). */
    private val bars = LevelBars(ctx)

    init {
        root.addView(bars, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(36)).apply {
            bottomMargin = dp(6)
        })
        origText = Ui.body(ctx, "").apply { textSize = if (full) 22f else 16f }
        trText = Ui.body(ctx, "").apply { textSize = if (full) 28f else 18f }
        val t1 = Ui.small(ctx, "📝 Eredeti szöveg").apply { setTypeface(typeface, Typeface.BOLD) }
        trTitle = Ui.small(ctx, "").apply { setTypeface(typeface, Typeface.BOLD) }
        origScroll = panel(t1, origText)
        trScroll = panel(trTitle, trText)
        refreshTitle()

        bubbleScroll.apply {
            setPadding(dp(4), dp(6), dp(4), dp(6))
            setOnTouchListener { v, _ -> v.parent?.requestDisallowInterceptTouchEvent(true); false }
            addView(bubbleBox)
        }
        val blp = if (full) LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        else LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(340))
        blp.bottomMargin = dp(8)
        root.addView(bubbleScroll, blp)
        bubbleMode = try { prefs.raw().getBoolean("liveBubbles", true) } catch (_: Exception) { true }
        modeBtn = Ui.smallButton(ctx, "") {
            bubbleMode = !bubbleMode
            try { prefs.raw().edit().putBoolean("liveBubbles", bubbleMode).apply() } catch (_: Exception) {}
            applyMode()
        }
        root.addView(modeBtn, 1)
        applyMode()

        root.addView(Ui.small(ctx, "🎯 Fordítás nyelve (a beszélőt automatikusan felismerem):"))
        val spin = Spinner(ctx)
        val items = LangCatalog.ALL
        spin.adapter = ArrayAdapter(
            ctx, android.R.layout.simple_spinner_dropdown_item,
            items.map { "${it.third} – ${it.second}" }
        )
        val cur = items.indexOfFirst { it.first == LangCatalog.norm(prefs.polyTarget) }
        spin.setSelection(if (cur >= 0) cur else 0, false)
        spin.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                val code = items[pos].first
                if (LangCatalog.norm(prefs.polyTarget) != code) {
                    prefs.polyTarget = code
                    refreshTitle()
                }
            }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
        root.addView(spin)
    }

    /** Betűméret szorzó (teljes képernyős mód: csípéssel / gombbal állítható). */
    fun scale(f: Float) {
        val k = f.coerceIn(0.6f, 2.5f)
        origText.textSize = (if (full) 22f else 16f) * k
        trText.textSize = (if (full) 28f else 18f) * k
    }

    private fun applyMode() {
        modeBtn.text = if (bubbleMode) "▤ Váltás: két panel" else "💬 Váltás: beszélő-buborékok"
        bubbleScroll.visibility = if (bubbleMode) View.VISIBLE else View.GONE
        for (v in panelViews) v.visibility = if (bubbleMode) View.GONE else View.VISIBLE
    }

    private fun speakerHeader(lines: List<LiveLine>, i: Int): String {
        val l = lines[i]
        val sb = StringBuilder()
        sb.append(if (l.speaker > 0) "👤 ${l.speaker}. beszélő" else "👤 ?")
        if (l.lang.isNotBlank()) sb.append("  ").append(LangCatalog.flag(l.lang)).append(' ').append(LangCatalog.nameHu(l.lang))
        if (LiveLog.langSwitched(lines, i)) sb.append("  🔀 másik nyelven")
        return sb.toString()
    }

    private fun addBubble(lines: List<LiveLine>, i: Int) {
        val l = lines[i]
        val col = colorFor(l.speaker)
        val right = l.speaker > 0 && l.speaker % 2 == 0
        val maxW = (ctx.resources.displayMetrics.widthPixels * 0.78f).toInt()
        val box = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = dp(18).toFloat()
                setColor((col and 0x00FFFFFF) or (0x2E shl 24))
                setStroke(dp(1), (col and 0x00FFFFFF) or (0x90 shl 24))
            }
        }
        val header = Ui.small(ctx, speakerHeader(lines, i)).apply {
            setTextColor(col); setTypeface(typeface, Typeface.BOLD); textSize = 12f; maxWidth = maxW
        }
        val tr = Ui.body(ctx, "").apply { textSize = if (full) 24f else 17f; setTypeface(typeface, Typeface.BOLD); maxWidth = maxW }
        val orig = Ui.small(ctx, l.original).apply { textSize = if (full) 16f else 13f; maxWidth = maxW }
        box.addView(header); box.addView(tr); box.addView(orig)
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            gravity = if (right) android.view.Gravity.END else android.view.Gravity.START
            topMargin = dp(6)
        }
        box.alpha = 0f
        box.translationY = dp(14).toFloat()
        bubbleBox.addView(box, lp)
        box.animate().alpha(1f).translationY(0f).setDuration(320).start()
        bubbles[l.id] = Bubble(header, tr, orig)
    }

    private fun updateBubbles(lines: List<LiveLine>) {
        if (bubbles.size > lines.size || (lines.isNotEmpty() && bubbles.isNotEmpty() && !bubbles.containsKey(lines.first().id) && lines.size <= bubbles.size)) {
            bubbleBox.removeAllViews(); bubbles.clear()
        }
        var changed = false
        for (i in lines.indices) {
            val l = lines[i]
            if (!bubbles.containsKey(l.id)) { addBubble(lines, i); changed = true }
            val b = bubbles[l.id] ?: continue
            val txt = if (l.translated.isBlank()) "…" else l.translated
            val cur = b.tr.text.toString()
            if (cur != txt) {
                // az új fordítás betűnként "begépelődik"
                Anim.typeInto(b.tr, txt, if (txt.startsWith(cur) && cur != "…") cur.length else 0, 520L)
                changed = true
            }
            b.header.text = speakerHeader(lines, i)
        }
        if (changed) bubbleScroll.post { bubbleScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun refreshTitle() {
        trTitle.text = "🌍 Fordítás – ${LangCatalog.nameHu(prefs.polyTarget)}"
    }

    /** Újrarajzolja a paneleket, ha a napló megváltozott. */
    fun update(lines: List<LiveLine>) {
        if (lines === shown) return
        shown = lines
        val o = SpannableStringBuilder()
        val t = SpannableStringBuilder()
        if (lines.isEmpty()) {
            o.append("Beszélj vagy játssz le valamit – bármilyen nyelven…")
            t.append("A fordítás itt jelenik meg.")
        }
        for ((i, l) in lines.withIndex()) {
            val tag = tagFor(lines, i)
            if (tag != null) {
                appendTag(o, tag.first, tag.second)
                appendTag(t, tag.first, tag.second)
            }
            o.append(l.original).append("\n")
            t.append(if (l.translated.isBlank()) "…" else l.translated).append("\n")
        }
        val grew = lines.isNotEmpty() && (lines.size != lastCount || lines.last().translated != lastTr)
        updateBubbles(lines)
        lastCount = lines.size
        lastTr = lines.lastOrNull()?.translated ?: ""
        origText.text = o
        trText.text = t
        if (grew) {
            // az új/frissült fordítás finoman beúszik
            trText.alpha = 0.35f
            trText.animate().alpha(1f).setDuration(380).start()
        }
        origScroll.post { origScroll.fullScroll(View.FOCUS_DOWN) }
        trScroll.post { trScroll.fullScroll(View.FOCUS_DOWN) }
    }

    /** (felirat, szín) – csak ha új beszélő kezdődik vagy nyelvváltás van. */
    private fun tagFor(lines: List<LiveLine>, i: Int): Pair<String, Int>? {
        val l = lines[i]
        val newSpeaker = LiveLog.speakerChanged(lines, i)
        val switched = LiveLog.langSwitched(lines, i)
        if (!newSpeaker && !switched) return null
        val sb = StringBuilder()
        if (l.speaker > 0) sb.append("👤 ").append(l.speaker).append(". beszélő") else sb.append("👤 ?")
        if (l.lang.isNotBlank()) sb.append(" · ").append(LangCatalog.nameHu(l.lang))
        if (switched) sb.append("  🔀 másik nyelven szól")
        return sb.toString() to colorFor(l.speaker)
    }

    private fun appendTag(sb: SpannableStringBuilder, text: String, color: Int) {
        val start = sb.length
        sb.append(text).append("\n")
        sb.setSpan(ForegroundColorSpan(color), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(RelativeSizeSpan(0.8f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun colorFor(speaker: Int): Int {
        val pal = intArrayOf(
            0xFF2F80ED.toInt(), 0xFFE67E22.toInt(), 0xFF27AE60.toInt(),
            0xFFD63384.toInt(), 0xFF8E44AD.toInt(), 0xFF16A085.toInt()
        )
        return if (speaker <= 0) 0xFF888888.toInt() else pal[(speaker - 1) % pal.size]
    }
}

/** Hangszint-oszlopok: a [AppState.level] értékét képkockánként olvassa, az oszlopok sima lecsengéssel követik. */
private class LevelBars(ctx: Context) : View(ctx) {
    private val n = 28
    private val h = FloatArray(n) { 0.06f }
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private val rect = android.graphics.RectF()
    private var t = 0f
    private val anim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = android.animation.ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener {
            t += 0.18f
            val lvl = AppState.level.coerceIn(0f, 1f)
            for (i in 0 until n - 1) h[i] = h[i + 1]
            val wobble = 0.5f + 0.5f * kotlin.math.sin(t * 2.3f + 1.7f * kotlin.math.sin(t))
            h[n - 1] = (0.06f + lvl * (0.55f + 0.45f * wobble)).coerceIn(0.06f, 1f)
            invalidate()
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }
    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val hh = height.toFloat()
        val slot = w / n
        val bw = slot * 0.6f
        val col = Ui.color(context, R.color.primary)
        for (i in 0 until n) {
            val bh = h[i] * hh
            val x = i * slot + (slot - bw) / 2f
            rect.set(x, (hh - bh) / 2f, x + bw, (hh + bh) / 2f)
            paint.color = (col and 0x00FFFFFF) or ((80 + (175 * h[i]).toInt().coerceAtMost(175)) shl 24)
            c.drawRoundRect(rect, bw / 2f, bw / 2f, paint)
        }
    }
}
