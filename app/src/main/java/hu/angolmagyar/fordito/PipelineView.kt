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

    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = 10.5f * d }

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1600
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { phase = it.animatedFraction; if (hasActive()) invalidate() }
    }

    private fun hasActive() = states.any { it == SrtJob.ACTIVE }

    fun set(newStates: IntArray, activePct: Int) {
        val n = minOf(newStates.size, states.size)
        var changed = pct != activePct
        for (i in 0 until n) if (states[i] != newStates[i]) { states[i] = newStates[i]; changed = true }
        pct = activePct
        if (changed) invalidate()
    }

    override fun onAttachedToWindow() { super.onAttachedToWindow(); anim.start() }
    override fun onDetachedFromWindow() { anim.cancel(); super.onDetachedFromWindow() }

    override fun onMeasure(w: Int, h: Int) {
        setMeasuredDimension(MeasureSpec.getSize(w), (86 * d).toInt())
    }

    override fun onDraw(c: Canvas) {
        val n = states.size
        val w = width.toFloat()
        val side = 28f * d
        val step = (w - 2 * side) / (n - 1)
        val cy = 30f * d
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
                    p.style = Paint.Style.FILL; p.color = done
                    c.drawCircle(cx, cy, r, p)
                    p.style = Paint.Style.STROKE; p.color = 0xFFFFFFFF.toInt(); p.strokeWidth = 2.4f * d
                    c.drawLine(cx - 5f * d, cy, cx - 1.5f * d, cy + 4f * d, p)
                    c.drawLine(cx - 1.5f * d, cy + 4f * d, cx + 6f * d, cy - 4.5f * d, p)
                }
                SrtJob.ACTIVE -> {
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
