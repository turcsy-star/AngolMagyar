package hu.angolmagyar.fordito

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import kotlin.math.abs

/**
 * Lebegő felirat más appok fölött.
 * - fent vékony hangszintcsík; koppintásra vezérlősáv (szünet, A−, A+, bezárás)
 * - függőlegesen húzható, a pozíciót megjegyzi
 * - a régebbi magyar sorok fokozatosan halványulnak
 */
class Overlay(
    private val ctx: Context,
    private val prefs: Prefs,
    private val cb: Callbacks
) {

    interface Callbacks {
        fun onTogglePause()
        fun onSizeDelta(delta: Int)
        fun onClose()
        fun onPttDown() {}
        fun onPttUp() {}
        fun onSwitchDirection() {}
        fun onDelayDelta(deltaMs: Int) {}
        fun onToggleEnglish() {}
        fun onSaveFavorite() {}
    }

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private val root = LinearLayout(ctx)
    private val levelBar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
    private val buttons = LinearLayout(ctx)
    private val row1 = LinearLayout(ctx)
    private val row2 = LinearLayout(ctx)
    private val warnView = TextView(ctx)
    private val enView = TextView(ctx)
    private val huView = TextView(ctx)
    private lateinit var pauseBtn: ImageView
    private var pinchAcc = 1f
    private var pinching = false
    private val bgShape = GradientDrawable()

    private lateinit var lp: WindowManager.LayoutParams
    private var added = false
    private var expanded = false
    private var lastEn = ""
    private var lastLines: List<String> = emptyList()

    private val collapse = Runnable { setExpanded(false) }

    private fun dp(v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    private fun chipBg() = GradientDrawable().apply {
        cornerRadius = dp(20).toFloat()
        setColor(0x33FFFFFF)
    }

    private fun pressFeedback(v: View) {
        Haptics.tick(ctx)
        v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(60).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
        }.start()
    }

    /** Szöveges chip (leírással a kisegítő technológiáknak). */
    private fun chip(label: String, desc: String, onClick: () -> Unit): TextView = TextView(ctx).apply {
        text = label
        contentDescription = desc
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER
        minWidth = dp(44)
        minHeight = dp(40)
        setPadding(dp(10), 0, dp(10), 0)
        background = chipBg()
        setOnClickListener {
            pressFeedback(this)
            onClick()
            scheduleCollapse()
        }
    }

    /** Ikon chip. */
    private fun iconChip(iconRes: Int, desc: String, onClick: () -> Unit): ImageView = ImageView(ctx).apply {
        setImageResource(iconRes)
        setColorFilter(Color.WHITE)
        contentDescription = desc
        scaleType = ImageView.ScaleType.CENTER
        minimumWidth = dp(44)
        minimumHeight = dp(40)
        background = chipBg()
        setOnClickListener {
            pressFeedback(this)
            onClick()
            scheduleCollapse()
        }
    }

    private fun chipLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(dp(48), dp(40)).apply { marginEnd = dp(6); topMargin = dp(4) }

    @SuppressLint("ClickableViewAccessibility")
    fun add() {
        if (added) return

        bgShape.cornerRadius = dp(18).toFloat()
        root.background = InsetDrawable(bgShape, dp(10), 0, dp(10), 0)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(24), dp(10), dp(24), dp(12))
        root.elevation = dp(8).toFloat()

        levelBar.max = 100
        levelBar.progressTintList = ColorStateList.valueOf(0xFF34D399.toInt())
        levelBar.progressBackgroundTintList = ColorStateList.valueOf(0x22FFFFFF)
        levelBar.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(5))
        levelBar.scaleY = 1.2f

        buttons.orientation = LinearLayout.VERTICAL
        row1.orientation = LinearLayout.HORIZONTAL
        row1.gravity = Gravity.CENTER_VERTICAL
        row2.orientation = LinearLayout.HORIZONTAL
        row2.gravity = Gravity.CENTER_VERTICAL

        // 1. sor: szünet, push-to-talk, irány, angol sor, kedvenc, (hely), bezárás
        pauseBtn = iconChip(R.drawable.ic_pause, ctx.getString(R.string.ov_pause)) { cb.onTogglePause() }
        row1.addView(pauseBtn, chipLp())
        // Push-to-talk: nyomva tartásra hallgat
        val pttChip = iconChip(R.drawable.ic_mic, ctx.getString(R.string.ov_ptt)) {}
        pttChip.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    cb.onPttDown()
                    pttChip.setColorFilter(0xFF4CD08A.toInt())
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    cb.onPttUp()
                    pttChip.setColorFilter(Color.WHITE)
                    true
                }
                else -> false
            }
        }
        row1.addView(pttChip, chipLp())
        row1.addView(iconChip(R.drawable.ic_swap, ctx.getString(R.string.ov_switch)) { cb.onSwitchDirection() }, chipLp())
        row1.addView(chip("EN", ctx.getString(R.string.ov_toggle_en)) { cb.onToggleEnglish() }, chipLp())
        row1.addView(iconChip(R.drawable.ic_star, ctx.getString(R.string.ov_favorite)) { cb.onSaveFavorite() }, chipLp())
        row1.addView(View(ctx), LinearLayout.LayoutParams(0, 1, 1f))
        row1.addView(iconChip(R.drawable.ic_close, ctx.getString(R.string.ov_close)) { cb.onClose() }, chipLp())

        // 2. sor: betűméret, késleltetés
        row2.addView(chip("A−", ctx.getString(R.string.ov_a_minus)) { cb.onSizeDelta(-2) }, chipLp())
        row2.addView(chip("A+", ctx.getString(R.string.ov_a_plus)) { cb.onSizeDelta(2) }, chipLp())
        row2.addView(
            chip("−0.1s", ctx.getString(R.string.ov_delay_minus)) { cb.onDelayDelta(-100) },
            LinearLayout.LayoutParams(dp(64), dp(40)).apply { marginEnd = dp(6); topMargin = dp(4) }
        )
        row2.addView(
            chip("+0.1s", ctx.getString(R.string.ov_delay_plus)) { cb.onDelayDelta(100) },
            LinearLayout.LayoutParams(dp(64), dp(40)).apply { marginEnd = dp(6); topMargin = dp(4) }
        )
        buttons.addView(row1)
        buttons.addView(row2)
        buttons.visibility = View.GONE

        warnView.setTextColor(0xFFFFB454.toInt())
        warnView.textSize = 12f
        warnView.visibility = View.GONE

        enView.maxLines = 2
        enView.ellipsize = TextUtils.TruncateAt.START

        huView.setTypeface(huView.typeface, android.graphics.Typeface.BOLD)
        huView.maxLines = prefs.maxOverlayLines.coerceIn(2, 6)

        root.addView(levelBar)
        root.addView(buttons)
        root.addView(warnView)
        root.addView(enView)
        root.addView(huView)

        val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                pinching = true
                pinchAcc = 1f
                return true
            }

            override fun onScale(d: ScaleGestureDetector): Boolean {
                pinchAcc *= d.scaleFactor
                if (pinchAcc > 1.12f) {
                    cb.onSizeDelta(2)
                    pinchAcc = 1f
                } else if (pinchAcc < 0.89f) {
                    cb.onSizeDelta(-2)
                    pinchAcc = 1f
                }
                return true
            }

            override fun onScaleEnd(d: ScaleGestureDetector) {
                pinching = false
            }
        })

        root.setOnTouchListener(object : View.OnTouchListener {
            private var startY = 0f
            private var lastY = 0f
            private var downAt = 0L
            private var dragged = false
            private var lastTapMs = 0L
            private var multi = false

            override fun onTouch(v: View, e: MotionEvent): Boolean {
                scaleDetector.onTouchEvent(e)
                when (e.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startY = e.rawY
                        lastY = e.rawY
                        downAt = System.currentTimeMillis()
                        dragged = false
                        multi = false
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> multi = true
                    MotionEvent.ACTION_MOVE -> {
                        if (pinching || multi) return true
                        if (!dragged && abs(e.rawY - startY) > dp(10)) dragged = true
                        if (dragged) {
                            val dy = e.rawY - lastY
                            lastY = e.rawY
                            val ny = if (prefs.atTop) lp.y + dy else lp.y - dy
                            lp.y = ny.toInt().coerceAtLeast(0)
                            if (added) wm.updateViewLayout(root, lp)
                        }
                    }
                    MotionEvent.ACTION_UP -> {
                        if (multi || pinching) {
                            // csippentés vége – nem koppintás
                        } else if (dragged) {
                            prefs.overlayY = lp.y
                        } else if (System.currentTimeMillis() - downAt >= 550) {
                            // Hosszú nyomás: az utolsó mondat mentése kedvencek közé + vágólapra
                            Haptics.click(ctx)
                            cb.onSaveFavorite()
                        } else {
                            val now = System.currentTimeMillis()
                            if (now - lastTapMs < 320) {
                                // Dupla koppintás → szünet / folytatás
                                Haptics.tick(ctx)
                                cb.onTogglePause()
                                lastTapMs = 0L
                            } else {
                                setExpanded(!expanded)
                                lastTapMs = now
                            }
                        }
                    }
                }
                return true
            }
        })

        val top = prefs.atTop
        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    (if (prefs.touchThrough) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else 0),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = (if (top) Gravity.TOP else Gravity.BOTTOM) or Gravity.CENTER_HORIZONTAL
            y = if (prefs.overlayY >= 0) prefs.overlayY else if (top) dp(48) else dp(96)
        }

        applyStyle()

        try {
            wm.addView(root, lp)
            added = true
        } catch (_: Exception) {
            // Nincs overlay-engedély, vagy a rendszer elutasította.
        }
    }

    /** A beállítások (méret, szín, kontraszt, angol sor) újraalkalmazása. */
    fun applyStyle() {
        val hc = prefs.highContrast
        val size = prefs.textSizeSp + if (hc) 2 else 0
        huView.textSize = size.toFloat()
        huView.setTextColor(if (hc) Color.WHITE else prefs.textColor)
        if (hc) huView.setShadowLayer(6f, 0f, 0f, Color.BLACK) else huView.setShadowLayer(0f, 0f, 0f, 0)

        enView.textSize = (size * 0.62f).coerceAtLeast(11f)
        enView.setTextColor(if (hc) Color.WHITE else 0xFFBBBBBB.toInt())

        renderContent()
    }

    /** en: angol (halvány) sor; lines: magyar sorok régitől az újig. */
    fun setContent(en: String, lines: List<String>) {
        val max = prefs.maxOverlayLines.coerceIn(2, 6)
        val trimmed = if (lines.size > max) lines.takeLast(max) else lines
        val changed = en != lastEn || trimmed != lastLines
        lastEn = en
        lastLines = trimmed
        renderContent()
        if (changed && added) {
            huView.alpha = 0.55f
            huView.animate().alpha(1f).setDuration(180).start()
            if (enView.visibility == View.VISIBLE) {
                enView.alpha = 0.55f
                enView.animate().alpha(0.85f).setDuration(180).start()
            }
        }
    }

    fun setWarning(text: String?) {
        if (text.isNullOrBlank()) {
            warnView.visibility = View.GONE
        } else {
            warnView.text = text
            warnView.visibility = View.VISIBLE
        }
        updateBg()
    }

    fun setPaused(paused: Boolean) {
        if (!added) return
        pauseBtn.setImageResource(if (paused) R.drawable.ic_play else R.drawable.ic_pause)
        pauseBtn.contentDescription = ctx.getString(if (paused) R.string.ov_resume else R.string.ov_pause)
    }

    fun setLevel(level: Float) {
        if (!added) return
        levelBar.progress = (level.coerceIn(0f, 1f) * 100).toInt()
    }

    fun setPttActive(active: Boolean) {
        // vizuális visszajelzés a szintcsíkon
        levelBar.progressTintList = android.content.res.ColorStateList.valueOf(
            if (active) 0xFF4CD08A.toInt() else 0xFF888888.toInt()
        )
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        if (!added) return
        added = false
        try {
            wm.removeView(root)
        } catch (_: Exception) {
        }
    }

    private fun renderContent() {
        val grav = when (prefs.hAlign) {
            Prefs.ALIGN_LEFT -> Gravity.START or Gravity.CENTER_VERTICAL
            Prefs.ALIGN_RIGHT -> Gravity.END or Gravity.CENTER_VERTICAL
            else -> Gravity.CENTER
        }
        enView.gravity = grav
        huView.gravity = grav
        val showEn = prefs.showEnglish && lastEn.isNotBlank()
        enView.text = lastEn
        enView.visibility = if (showEn) View.VISIBLE else View.GONE

        val base = if (prefs.highContrast) Color.WHITE else prefs.textColor
        val sb = SpannableStringBuilder()
        lastLines.forEachIndexed { i, line ->
            val age = lastLines.size - 1 - i
            val alpha = when (age) {
                0 -> 255
                1 -> 170
                else -> 110
            }
            val start = sb.length
            sb.append(line)
            sb.setSpan(
                ForegroundColorSpan((base and 0x00FFFFFF) or (alpha shl 24)),
                start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            if (i < lastLines.size - 1) sb.append('\n')
        }
        huView.text = sb
        huView.visibility = if (lastLines.isEmpty()) View.GONE else View.VISIBLE
        updateBg()
    }

    private fun updateBg() {
        val empty = lastLines.isEmpty() && lastEn.isBlank() && warnView.visibility != View.VISIBLE
        val base = if (prefs.highContrast) 245 else prefs.bgAlphaPct.coerceIn(10, 100) * 255 / 100
        // enyhe kékes fekete – cinema
        bgShape.setColor(Color.argb(if (empty) base * 40 / 100 else base, 8, 10, 18))
    }

    private fun setExpanded(e: Boolean) {
        expanded = e
        buttons.visibility = if (e) View.VISIBLE else View.GONE
        if (e) scheduleCollapse() else handler.removeCallbacks(collapse)
    }

    private fun scheduleCollapse() {
        handler.removeCallbacks(collapse)
        handler.postDelayed(collapse, prefs.autoCollapseMs.toLong())
    }
}
