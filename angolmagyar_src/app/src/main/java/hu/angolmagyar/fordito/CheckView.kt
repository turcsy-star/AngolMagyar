package hu.angolmagyar.fordito

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * "Kész" jelzés: a kör kipattan (overshoot), a pipa belerajzolódik, apró pöttyök szállnak szét.
 * A [play] indítja az animációt; előtte a nézet üres.
 */
class CheckView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val sizePx = Ui.dp(ctx, 76)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFFFFFFFF.toInt()
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val seg = Path()
    private val measure = PathMeasure()
    private val over = OvershootInterpolator(2.2f)
    private var t = 0f
    private var played = false
    private var anim: ValueAnimator? = null

    init {
        fill.color = Ui.color(ctx, R.color.ok)
        dot.color = Ui.color(ctx, R.color.accentGlow)
    }

    /** Elindítja az animációt (többszöri hívás újraindítja). */
    fun play() {
        anim?.cancel()
        played = true
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900
            addUpdateListener { t = it.animatedValue as Float; invalidate() }
            start()
        }
        performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(sizePx, sizePx)
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        if (!played) return
        val w = width.toFloat()
        val cx = w / 2f
        val cy = height / 2f
        val r = w * 0.36f

        // 1) kör: 0..0.45 között kipattan
        val circleT = (t / 0.45f).coerceIn(0f, 1f)
        val scale = over.getInterpolation(circleT)
        canvas.drawCircle(cx, cy, r * scale, fill)

        // 2) pipa: 0.35..0.85 között rajzolódik
        val u = ((t - 0.35f) / 0.5f).coerceIn(0f, 1f)
        if (u > 0f) {
            path.reset()
            path.moveTo(w * 0.30f, w * 0.52f)
            path.lineTo(w * 0.45f, w * 0.66f)
            path.lineTo(w * 0.71f, w * 0.37f)
            measure.setPath(path, false)
            seg.reset()
            measure.getSegment(0f, measure.length * u, seg, true)
            tick.strokeWidth = w * 0.075f
            canvas.drawPath(seg, tick)
        }

        // 3) szétszálló pöttyök: 0.30..1.0
        val b = ((t - 0.30f) / 0.70f).coerceIn(0f, 1f)
        if (b > 0f && b < 1f) {
            val n = 10
            val dr = w * 0.025f * (1f - b * 0.5f)
            dot.alpha = ((1f - b) * 255).toInt().coerceIn(0, 255)
            for (i in 0 until n) {
                val ang = i * (2.0 * Math.PI / n) + 0.3
                val dist = r * (1.15f + 0.45f * b)
                canvas.drawCircle(
                    cx + (cos(ang) * dist).toFloat(), cy + (sin(ang) * dist).toFloat(), min(dr, w * 0.04f), dot
                )
            }
        }
    }
}
