package hu.angolmagyar.fordito

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin

/**
 * A feliratkészítés folyamata egy pillantásra: Hang → Szakaszolás → Felismerés → Fordítás → Felirat.
 * A kész lépés zöld pipa, az aktív lépés körül hullám terjed, a következő lépés felé pontok áramlanak,
 * a hátralévők szürkék. A lépések állapotát a SrtJob lépései adják ([set]).
 */
class PipelineView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val labels = arrayOf("Előkészítés", "Hang", "Betöltés", "Felismerés + fordítás", "Mentés")
    private var states = IntArray(labels.size)
    private var pct = 0
    private var phase = 0f
    private val doneAt = LongArray(5)
    private var initialized = false
    private val pairs = arrayOf("Hello" to "Szia", "Thanks" to "Kösz", "Yes" to "Igen", "Good morning" to "Jó reggelt")

    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 10.5f * d }

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedFraction; if (hasActive() || popping()) invalidate() }
    }

    private fun hasActive() = states.any { it == SrtJob.ACTIVE }
    private fun popping(): Boolean { val now = System.currentTimeMillis(); return doneAt.any { now - it < 600 } }

    fun set(newStates: IntArray, activePct: Int) {
        val n = minOf(newStates.size, states.size)
        var changed = pct != activePct
        val now = System.currentTimeMillis()
        for (i in 0 until n) if (states[i] != newStates[i]) {
            // kész lett → kipattanó animáció (az első beállításkor már kész lépéseknél nincs)
            if (initialized && newStates[i] == SrtJob.DONE) doneAt[i] = now
            states[i] = newStates[i]; changed = true
        }
        initialized = true
        pct = activePct
        if (changed) invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (118 * d).toInt())
    }

    /** Az aktív lépés kis "mi történik most" animációja a csomópont fölött. */
    private fun mini(c: Canvas, step: Int, cx: Float, baseY: Float, col: Int) {
        p.style = Paint.Style.FILL
        p.color = col
        when (step) {
            0 -> for (k in 0 until 3) { // előkészítés: sorban táguló pontok
                val f = ((phase * 3f - k) % 3f + 3f) % 3f
                val rr = (2f + 2.5f * (1f - kotlin.math.abs(f - 0.5f).coerceAtMost(1f))) * d
                c.drawCircle(cx + (k - 1) * 9f * d, baseY - 6f * d, rr, p)
            }
            1 -> for (k in 0 until 5) { // hang: pergő oszlopok
                val h = (5f + 11f * (0.5f + 0.5f * sin(phase * 2f * Math.PI.toFloat() * 2f + k * 1.3f))) * d
                c.drawRoundRect(cx + (k - 2) * 7f * d - 1.6f * d, baseY - h, cx + (k - 2) * 7f * d + 1.6f * d, baseY, 2f * d, 2f * d, p)
            }
            2 -> for (k in 0 until 5) { // betöltés: sorban megtelő négyzetek
                val on = phase * 6f > k + 0.5f
                p.color = if (on) col else 0x55888AA0
                c.drawRoundRect(cx + (k - 2) * 8f * d - 3f * d, baseY - 9f * d, cx + (k - 2) * 8f * d + 3f * d, baseY - 3f * d, 1.5f * d, 1.5f * d, p)
            }
            3 -> { // felismerés + fordítás: az angol szó magyarrá alakul
                val pr = pairs[((System.currentTimeMillis() / 1600) % pairs.size).toInt()]
                text.typeface = Typeface.DEFAULT_BOLD
                val oldC = text.color
                val showFirst = phase < 0.5f
                val a = if (showFirst) (kotlin.math.min(phase * 6f, 1f)) else (1f - kotlin.math.min((phase - 0.5f) * 4f, 1f)).let { 1f - it }
                text.color = (col and 0x00FFFFFF) or (((if (showFirst) 1f - (phase * 2f) * (phase * 2f) * 0.6f else a.coerceIn(0.2f, 1f)) * 255).toInt().coerceIn(40, 255) shl 24)
                c.drawText(if (showFirst) pr.first else "→ " + pr.second, cx, baseY - 6f * d - 4f * d * phase, text)
                text.color = oldC
            }
            4 -> { // mentés: dokumentum, amelyre sorok íródnak
                p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f * d
                c.drawRoundRect(cx - 7f * d, baseY - 16f * d, cx + 7f * d, baseY, 2f * d, 2f * d, p)
                p.style = Paint.Style.FILL
                val lines = 1 + (phase * 3.2f).toInt().coerceAtMost(3)
                for (k in 0 until lines) c.drawRect(cx - 4f * d, baseY - 13f * d + k * 4f * d, cx + 4f * d, baseY - 12f * d + k * 4f * d, p)
            }
        }
    }

    override fun onDraw(c: Canvas) {
        val n = states.size
        val w = width.toFloat()
        val side = 28f * d
        val step = (w - 2 * side) / (n - 1)
        val cy = 62f * d
        val r = 13f * d
        val done = Ui.color(context, R.color.ok)
        val primary = Ui.color(context, R.color.primary)
        val sec = Ui.color(context, R.color.textSecondary)
        val txt = Ui.color(context, R.color.textPrimary)
        val idle = 0x55888AA0

        // összekötők
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 4f * d
        for (i in 0 until n - 1) {
            val x0 = side + i * step + r
            val x1 = side + (i + 1) * step - r
            p.color = idle
            c.drawLine(x0, cy, x1, cy, p)
            if (states[i] == SrtJob.DONE) {
                p.color = done
                c.drawLine(x0, cy, x1, cy, p)
            } else if (states[i] == SrtJob.ACTIVE) {
                // az aktív lépés felé áramló pontok: haladás szerinti kitöltés + mozgó fények
                val fill = x0 + (x1 - x0) * (pct.coerceIn(0, 100) / 100f)
                p.color = primary
                c.drawLine(x0, cy, fill, cy, p)
                p.style = Paint.Style.FILL
                for (k in 0 until 3) {
                    val f = (phase + k / 3f) % 1f
                    val x = x0 + (x1 - x0) * f
                    p.color = (primary and 0x00FFFFFF) or (((1f - kotlin.math.abs(f - 0.5f) * 2f) * 200).toInt() shl 24)
                    c.drawCircle(x, cy, 3.2f * d, p)
                }
                p.style = Paint.Style.STROKE
            }
        }

        // csomópontok
        for (i in 0 until n) {
            val cx = side + i * step
            when (states[i]) {
                SrtJob.DONE -> {
                    val age = (System.currentTimeMillis() - doneAt[i]) / 520f
                    val pop = if (age in 0f..1f) 1f + 0.35f * sin(age * Math.PI.toFloat()) else 1f
                    if (age in 0f..1f) {
                        // kipattanó gyűrű + szikrák
                        p.style = Paint.Style.STROKE; p.strokeWidth = 2.4f * d
                        p.color = (done and 0x00FFFFFF) or (((1f - age) * 200).toInt() shl 24)
                        c.drawCircle(cx, cy, r + 20f * d * age, p)
                        p.style = Paint.Style.FILL
                        for (k in 0 until 8) {
                            val a = k * (Math.PI.toFloat() / 4f)
                            val rr = r + 4f * d + 22f * d * age
                            c.drawCircle(cx + rr * kotlin.math.cos(a), cy + rr * sin(a), (3f - 2f * age) * d, p)
                        }
                    }
                    p.style = Paint.Style.FILL; p.color = done
                    c.drawCircle(cx, cy, r * pop, p)
                    p.style = Paint.Style.STROKE; p.color = 0xFFFFFFFF.toInt(); p.strokeWidth = 2.4f * d
                    c.drawLine(cx - 5f * d, cy, cx - 1.5f * d, cy + 4f * d, p)
                    c.drawLine(cx - 1.5f * d, cy + 4f * d, cx + 6f * d, cy - 4.5f * d, p)
                }
                SrtJob.ACTIVE -> {
                    mini(c, i, cx, cy - r - 10f * d, primary)
                    // kifelé terjedő hullám
                    val a = ((1f - phase) * 120).toInt()
                    p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d
                    p.color = (primary and 0x00FFFFFF) or (a shl 24)
                    c.drawCircle(cx, cy, r + 12f * d * phase, p)
                    p.style = Paint.Style.FILL; p.color = primary
                    c.drawCircle(cx, cy, r, p)
                    // forgó pont a körben
                    val ang = phase * 2f * Math.PI.toFloat()
                    p.color = 0xFFFFFFFF.toInt()
                    c.drawCircle(cx + (r - 5f * d) * kotlin.math.cos(ang), cy + (r - 5f * d) * sin(ang), 2.4f * d, p)
                }
                SrtJob.ERROR, SrtJob.STOPPED -> {
                    p.style = Paint.Style.FILL; p.color = Ui.color(context, R.color.warn)
                    c.drawCircle(cx, cy, r, p)
                    text.color = 0xFFFFFFFF.toInt(); text.typeface = Typeface.DEFAULT_BOLD
                    c.drawText(if (states[i] == SrtJob.ERROR) "!" else "II", cx, cy + 4f * d, text)
                }
                else -> {
                    p.style = Paint.Style.STROKE; p.strokeWidth = 2f * d; p.color = idle
                    c.drawCircle(cx, cy, r, p)
                }
            }
            // felirat
            text.typeface = if (states[i] == SrtJob.ACTIVE) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            text.color = if (states[i] == SrtJob.PENDING) sec else txt
            val lab = labels[i]
            if (lab.length > 11) {
                val sp = lab.indexOf(' ')
                c.drawText(lab.substring(0, sp), cx, cy + r + 15f * d, text)
                c.drawText(lab.substring(sp + 1), cx, cy + r + 28f * d, text)
            } else c.drawText(lab, cx, cy + r + 15f * d, text)
        }
    }
}
