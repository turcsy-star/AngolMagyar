package hu.angolmagyar.fordito

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.min

/**
 * Látványos körgyűrű a teljes folyamat %-ához.
 * Animált gradiens ív, pulzáló glow futás közben, középen % + ETA.
 */
class RingView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    private val rect = RectF()
    private val matrix = Matrix()

    private var targetPct = 0
    private var displayPct = 0f
    private var pctLabel = "0%"
    private var etaLabel = ""
    private var running = false
    private var spin = 0f

    private val spinAnim = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 2800
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            spin = it.animatedValue as Float
            if (running) invalidate()
        }
    }
    private var pctAnim: ValueAnimator? = null

    init {
        val d = resources.displayMetrics.density
        bgPaint.strokeWidth = 14f * d
        arcPaint.strokeWidth = 14f * d
        glowPaint.strokeWidth = 22f * d
        pctPaint.textSize = 34f * d
        subPaint.textSize = 12f * d
        bgPaint.color = Ui.color(ctx, R.color.stroke)
        pctPaint.color = Ui.color(ctx, R.color.textPrimary)
        subPaint.color = Ui.color(ctx, R.color.textSecondary)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (running && !spinAnim.isStarted) spinAnim.start()
    }

    override fun onDetachedFromWindow() {
        spinAnim.cancel()
        pctAnim?.cancel()
        super.onDetachedFromWindow()
    }

    fun set(pct: Int, pctText: String, eta: String, running: Boolean) {
        targetPct = pct.coerceIn(0, 100)
        pctLabel = pctText
        etaLabel = eta
        val was = this.running
        this.running = running
        if (running && !was) {
            if (!spinAnim.isStarted) spinAnim.start()
        } else if (!running && was) {
            spinAnim.cancel()
        }
        pctAnim?.cancel()
        pctAnim = ValueAnimator.ofFloat(displayPct, targetPct.toFloat()).apply {
            duration = 420
            addUpdateListener {
                displayPct = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val size = (168 * d).toInt()
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val stroke = arcPaint.strokeWidth
        val pad = stroke * 1.2f
        rect.set(pad, pad, w - pad, h - pad)
        val cx = w / 2f
        val cy = h / 2f

        // háttér gyűrű
        canvas.drawArc(rect, 0f, 360f, false, bgPaint)

        val sweep = displayPct / 100f * 360f
        if (sweep > 0.5f) {
            val primary = Ui.color(context, R.color.primary)
            val tertiary = Ui.color(context, R.color.accentGlow)
            val ok = Ui.color(context, R.color.ok)
            val colors = intArrayOf(primary, tertiary, ok, primary)
            val shader = SweepGradient(cx, cy, colors, floatArrayOf(0f, 0.35f, 0.7f, 1f))
            matrix.setRotate(-90f + if (running) spin * 0.15f else 0f, cx, cy)
            shader.setLocalMatrix(matrix)
            arcPaint.shader = shader

            if (running) {
                glowPaint.color = primary
                glowPaint.alpha = 55
                canvas.drawArc(rect, -90f, sweep, false, glowPaint)
            }
            canvas.drawArc(rect, -90f, sweep, false, arcPaint)
            arcPaint.shader = null
        }

        // középső szöveg
        val fm = pctPaint.fontMetrics
        val textCy = cy - (fm.ascent + fm.descent) / 2f - if (etaLabel.isNotBlank()) 8f * resources.displayMetrics.density else 0f
        canvas.drawText(pctLabel, cx, textCy, pctPaint)
        if (etaLabel.isNotBlank()) {
            canvas.drawText(etaLabel, cx, cy + 22f * resources.displayMetrics.density, subPaint)
        }
    }
}
