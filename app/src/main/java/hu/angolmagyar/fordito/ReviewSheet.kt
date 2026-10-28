package hu.angolmagyar.fordito

import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import hu.angolmagyar.fordito.core.review.ReviewIntelligence
import hu.angolmagyar.fordito.core.review.ReviewIntelligence.Kind
import hu.angolmagyar.fordito.core.review.ReviewIntelligence.Severity

/** Körgyűrűs minőségpont (0–100), animált kitöltéssel; a szín a ponttal változik. */
class QualityRingView(ctx: Context) : View(ctx) {
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Typeface.DEFAULT_BOLD }
    private val box = RectF()
    private var shown = 0f
    private var target = 0
    private val stroke = Ui.dp(ctx, 7).toFloat()

    fun setScore(score: Int, animate: Boolean = true) {
        target = score.coerceIn(0, 100)
        if (!animate) { shown = target.toFloat(); invalidate(); return }
        ValueAnimator.ofFloat(shown, target.toFloat()).apply {
            duration = 700
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val pad = stroke / 2f + 1f
        box.set(pad, pad, w - pad, h - pad)
        track.strokeWidth = stroke; arc.strokeWidth = stroke
        track.color = ColorUtils.setAlphaComponent(Ui.color(context, R.color.stroke), 200)
        arc.color = Ui.color(context, when {
            shown >= 85f -> R.color.ok
            shown >= 60f -> R.color.warn
            else -> R.color.err
        })
        c.drawArc(box, 0f, 360f, false, track)
        c.drawArc(box, -90f, 360f * shown / 100f, false, arc)
        txt.color = Ui.color(context, R.color.textPrimary)
        txt.textSize = h * 0.30f
        c.drawText(shown.toInt().toString(), w / 2f, h / 2f + txt.textSize * 0.34f, txt)
    }
}

/**
 * Prémium minőségellenőrző lap: animált pontszám, súlyosság-szűrők és színes kártyák.
 * Csak jelez – a szöveget soha nem írja át; a kártyára koppintás a sorra ugrik.
 */
object ReviewSheet {

    interface Actions {
        fun jump(lineIndex: Int)
        fun recheckSuspicious()
        fun showAudit()
        fun showBenchmark()
        fun saveReference()
    }

    private fun sevColor(c: Context, s: Severity): Int = Ui.color(c, when (s) {
        Severity.CRITICAL -> R.color.err
        Severity.HIGH -> R.color.warn
        Severity.MEDIUM -> R.color.primary
        else -> R.color.textSecondary
    })

    private fun sevLabel(s: Severity) = when (s) {
        Severity.CRITICAL -> "Kritikus"; Severity.HIGH -> "Magas"; Severity.MEDIUM -> "Közepes"
        Severity.LOW -> "Alacsony"; Severity.INFO -> "Info"
    }

    private fun kindLabel(k: Kind) = when (k) {
        Kind.LOW_CONFIDENCE -> "Bizonytalan felismerés"; Kind.NUMBER -> "Szám"; Kind.NEGATION -> "Tagadás"
        Kind.DATE_OR_TIME -> "Dátum/idő"; Kind.NAME_CANDIDATE -> "Név"; Kind.SHORT_FRAGMENT -> "Töredék"
        Kind.REPEATED_TEXT -> "Ismétlődés"; Kind.EMPTY_TEXT -> "Üres sor"; Kind.BAD_TIMING -> "Időzítés"
    }

    /** 0–100: a sorok súlyozott tisztasága (a legsúlyosabb jelzés számít soronként). */
    fun score(summary: ReviewIntelligence.ReviewSummary): Int {
        if (summary.totalLines == 0) return 100
        val perLine = summary.findings.groupBy { it.lineIndex }.values.sumOf { items ->
            when (items.maxOf { it.severity }) {
                Severity.CRITICAL -> 1.0; Severity.HIGH -> 0.6; Severity.MEDIUM -> 0.3
                Severity.LOW -> 0.1; Severity.INFO -> 0.0
            }
        }
        return (100.0 * (1.0 - perLine / summary.totalLines)).toInt().coerceIn(0, 100)
    }

    private fun pill(ctx: Context, label: String, color: Int, filled: Boolean): TextView = TextView(ctx).apply {
        text = label
        textSize = 12f
        setTypeface(typeface, Typeface.BOLD)
        setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 4), Ui.dp(ctx, 10), Ui.dp(ctx, 4))
        setTextColor(if (filled) Ui.color(ctx, R.color.onPrimary) else color)
        background = GradientDrawable().apply {
            cornerRadius = Ui.dp(ctx, 20).toFloat()
            setColor(if (filled) color else ColorUtils.setAlphaComponent(color, 36))
        }
    }

    fun show(
        act: Activity, summary: ReviewIntelligence.ReviewSummary, cues: List<SrtExporter.Cue>,
        showTarget: Boolean, hasReference: Boolean, actions: Actions
    ) {
        val dlg = Dialog(act)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val root = FrameLayout(act)
        val sheet = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            val r = Ui.dp(act, 26).toFloat()
            background = GradientDrawable().apply {
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(Ui.color(act, R.color.bgElevated))
            }
            setPadding(Ui.dp(act, 18), Ui.dp(act, 10), Ui.dp(act, 18), Ui.dp(act, 14))
            elevation = Ui.dp(act, 8).toFloat()
        }
        // fogantyú
        sheet.addView(View(act).apply {
            background = GradientDrawable().apply {
                cornerRadius = 8f; setColor(ColorUtils.setAlphaComponent(Ui.color(act, R.color.stroke), 255))
            }
        }, LinearLayout.LayoutParams(Ui.dp(act, 42), Ui.dp(act, 4)).apply {
            gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = Ui.dp(act, 12)
        })

        // fejléc: pontgyűrű + összegzés
        val head = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val ring = QualityRingView(act)
        head.addView(ring, LinearLayout.LayoutParams(Ui.dp(act, 76), Ui.dp(act, 76)))
        val ht = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(Ui.dp(act, 14), 0, 0, 0) }
        ht.addView(Ui.title(act, "Minőségellenőrzés", 20f))
        ht.addView(Ui.small(act, "Ellenőrzési index · ${summary.totalLines} blokk · ${summary.flaggedLines} jelölt"))
        ht.addView(Ui.small(act, "${summary.criticalFindings} kritikus · ${summary.highFindings} magas prioritás"))
        ht.addView(Ui.small(act, "A pont a jelzések súlyozása, nem mért felismerési pontosság. A szöveget nem írja át."))
        head.addView(ht, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        sheet.addView(head)

        // műveletek
        val acts = HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false }
        val actRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, Ui.dp(act, 10), 0, Ui.dp(act, 4)) }
        fun addAct(label: String, f: () -> Unit) {
            actRow.addView(Ui.smallButton(act, label) { dlg.dismiss(); f() }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = Ui.dp(act, 8) })
        }
        addAct("🎯 Gyanús sorok újrafelismerése") { actions.recheckSuspicious() }
        addAct("🕘 Módosítási napló") { actions.showAudit() }
        addAct(if (hasReference) "📊 Benchmark" else "📌 Mentés referenciának") {
            if (hasReference) actions.showBenchmark() else actions.saveReference()
        }
        acts.addView(actRow)
        sheet.addView(acts)

        // szűrők
        val selected = HashSet<Severity>()
        val chips = HorizontalScrollView(act).apply { isHorizontalScrollBarEnabled = false }
        val chipRow = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, Ui.dp(act, 4), 0, Ui.dp(act, 8)) }
        chips.addView(chipRow)
        sheet.addView(chips)

        val listBox = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(act).apply { addView(listBox); isVerticalScrollBarEnabled = false }
        sheet.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        val byLine = summary.findings.groupBy { it.lineIndex }.toSortedMap()
        val counts = Severity.values().reversed().associateWith { s ->
            byLine.values.count { items -> items.maxOf { it.severity } == s }
        }

        fun rebuildList() {
            listBox.removeAllViews()
            val rows = byLine.entries.filter { (_, items) ->
                selected.isEmpty() || items.maxOf { it.severity } in selected
            }.sortedWith(compareByDescending<Map.Entry<Int, List<ReviewIntelligence.Finding>>> { e -> e.value.maxOf { it.severity }.ordinal }
                .thenBy { it.key })
            if (rows.isEmpty()) {
                listBox.addView(Ui.small(act, if (summary.findings.isEmpty())
                    "✓ Nincs külön jelzett elem. Ez nem bizonyítja, hogy az átirat hibátlan." else "Nincs ilyen súlyosságú jelzés."
                ).apply { setPadding(0, Ui.dp(act, 18), 0, Ui.dp(act, 18)); gravity = Gravity.CENTER })
                return
            }
            for ((idx, items) in rows.take(120)) {
                val sev = items.maxOf { it.severity }
                val col = sevColor(act, sev)
                val card = LinearLayout(act).apply {
                    orientation = LinearLayout.HORIZONTAL
                    background = GradientDrawable().apply {
                        cornerRadius = Ui.dp(act, 16).toFloat()
                        setColor(ColorUtils.blendARGB(Ui.color(act, R.color.card), col, 0.05f))
                        setStroke(Ui.dp(act, 1), ColorUtils.setAlphaComponent(col, 90))
                    }
                    isClickable = true
                    setOnClickListener { Haptics.tick(act); dlg.dismiss(); actions.jump(idx) }
                }
                card.addView(View(act).apply {
                    background = GradientDrawable().apply {
                        val rr = Ui.dp(act, 16).toFloat()
                        cornerRadii = floatArrayOf(rr, rr, 0f, 0f, 0f, 0f, rr, rr)
                        setColor(col)
                    }
                }, LinearLayout.LayoutParams(Ui.dp(act, 5), ViewGroup.LayoutParams.MATCH_PARENT))
                val col2 = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(Ui.dp(act, 14), Ui.dp(act, 12), Ui.dp(act, 14), Ui.dp(act, 12))
                }
                val top = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                top.addView(Ui.text(act, "${idx + 1}. sor", 14f, R.color.textPrimary, true))
                top.addView(pill(act, sevLabel(sev), col, true), LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginStart = Ui.dp(act, 8) })
                col2.addView(top)
                val c = cues.getOrNull(idx)
                val line = c?.let { if (showTarget && it.target.isNotBlank()) it.target else it.source }.orEmpty().replace('\n', ' ')
                col2.addView(Ui.body(act, line.take(110)).apply { maxLines = 2; setPadding(0, Ui.dp(act, 6), 0, Ui.dp(act, 6)) })
                val tags = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
                for (k in items.map { it.kind }.distinct().take(4)) {
                    val ks = items.filter { it.kind == k }.maxOf { it.severity }
                    tags.addView(pill(act, kindLabel(k), sevColor(act, ks), false), LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = Ui.dp(act, 6) })
                }
                col2.addView(tags)
                col2.addView(Ui.small(act, items.sortedByDescending { it.severity.ordinal }.map { it.explanation }.distinct().take(2)
                    .joinToString(" ")).apply { setPadding(0, Ui.dp(act, 6), 0, 0) })
                card.addView(col2, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                listBox.addView(card, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = Ui.dp(act, 10) })
            }
            if (rows.size > 120) listBox.addView(Ui.small(act, "…és még ${rows.size - 120} sor (szűrj súlyosság szerint)."))
            Ui.animateIn(listBox, 22)
        }

        fun rebuildChips() {
            chipRow.removeAllViews()
            fun chip(label: String, sev: Severity?, n: Int) {
                val on = if (sev == null) selected.isEmpty() else sev in selected
                val color = if (sev == null) Ui.color(act, R.color.primary) else sevColor(act, sev)
                val v = pill(act, "$label · $n", color, on).apply {
                    setOnClickListener {
                        Haptics.tick(act)
                        if (sev == null) selected.clear() else if (!selected.add(sev)) selected.remove(sev)
                        rebuildChips(); rebuildList()
                    }
                }
                chipRow.addView(v, LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { marginEnd = Ui.dp(act, 8) })
            }
            chip("Mind", null, byLine.size)
            for (s in listOf(Severity.CRITICAL, Severity.HIGH, Severity.MEDIUM, Severity.LOW)) {
                val n = counts[s] ?: 0
                if (n > 0) chip(sevLabel(s), s, n)
            }
        }

        rebuildChips(); rebuildList()
        root.addView(sheet, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
            (act.resources.displayMetrics.heightPixels * 0.82f).toInt(), Gravity.BOTTOM))
        dlg.setContentView(root)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(0x00000000))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setGravity(Gravity.BOTTOM)
            setDimAmount(0.45f)
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        root.setOnClickListener { dlg.dismiss() }
        sheet.setOnClickListener { /* a lap nem zár */ }
        dlg.show()
        sheet.translationY = act.resources.displayMetrics.heightPixels.toFloat()
        sheet.animate().translationY(0f).setDuration(320).setInterpolator(DecelerateInterpolator(1.6f)).start()
        ring.setScore(score(summary))
    }
}
