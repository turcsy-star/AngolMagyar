package hu.angolmagyar.fordito

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.max
import kotlin.math.min

/**
 * Hang burkológörbe + beszédszakaszok + chunk-haladás.
 * Látványos, animált oszlopdiagram a feliratkészítés közben.
 */
class WaveView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var envelope = FloatArray(0)
    private var totalMs = 1L
    private var ranges = LongArray(0) // párok: startMs, endMs
    private var chunkState = IntArray(0)
    private var chunkFrac = DoubleArray(0)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rangePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    private var phase = 0f
    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 900
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedFraction
            invalidate()
        }
    }

    init {
        val d = resources.displayMetrics.density
        rangePaint.color = 0x224F46E5
        cursorPaint.color = Ui.color(ctx, R.color.primary)
        cursorPaint.strokeWidth = 2f * d
        setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (envelope.isNotEmpty() && !anim.isStarted) anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    fun setEnvelope(env: FloatArray, totalMs: Long) {
        envelope = env
        this.totalMs = max(1L, totalMs)
        if (env.isNotEmpty() && !anim.isStarted) anim.start()
        requestLayout()
        invalidate()
    }

    fun setRanges(wr: LongArray) {
        ranges = wr
        invalidate()
    }

    fun setProgress(states: IntArray, fracs: DoubleArray) {
        chunkState = states
        chunkFrac = fracs
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        val h = (96 * d).toInt()
        val w = MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val d = resources.displayMetrics.density
        val mid = h / 2f
        val maxH = h * 0.42f

        // beszédszakasz sávok (háttér)
        if (ranges.size >= 2 && totalMs > 0) {
            var i = 0
            while (i + 1 < ranges.size) {
                val x0 = (ranges[i].toFloat() / totalMs) * w
                val x1 = (ranges[i + 1].toFloat() / totalMs) * w
                rect.set(x0, 0f, x1, h)
                canvas.drawRect(rect, rangePaint)
                i += 2
            }
        }

        // burkológörbe oszlopok
        val n = envelope.size
        if (n > 0) {
            val gap = max(1f, w / n * 0.18f)
            val bw = max(1.5f, w / n - gap)
            val primary = Ui.color(context, R.color.primary)
            val glow = Ui.color(context, R.color.accentGlow)
            val done = Ui.color(context, R.color.ok)
            val idle = 0x66888AA0

            // hol tart a feldolgozás (0..1)
            val progress = overallProgress()

            for (i in 0 until n) {
                val x = i * (w / n) + gap / 2f
                val base = envelope[i].coerceIn(0.04f, 1f)
                val pulse = if (progress in 0f..1f && i.toFloat() / n in (progress - 0.04f)..(progress + 0.02f)) {
                    0.15f * kotlin.math.sin(phase * Math.PI * 2).toFloat().let { if (it < 0) -it else it }
                } else 0f
                val amp = (base + pulse).coerceIn(0.04f, 1f)
                val bh = amp * maxH
                rect.set(x, mid - bh, x + bw, mid + bh)

                val t = i.toFloat() / n
                barPaint.color = when {
                    t < progress - 0.001f -> done
                    t < progress + 0.02f -> glow
                    else -> idle
                }
                val rad = min(bw / 2f, 4f * d)
                canvas.drawRoundRect(rect, rad, rad, barPaint)
            }

            // kurzor
            if (progress in 0f..1f) {
                val cx = progress * w
                canvas.drawLine(cx, 4f * d, cx, h - 4f * d, cursorPaint)
                // soft glow at cursor
                progressPaint.shader = LinearGradient(
                    cx - 18f * d, 0f, cx + 18f * d, 0f,
                    intArrayOf(0x006366F1, 0x556366F1, 0x006366F1),
                    null, Shader.TileMode.CLAMP
                )
                canvas.drawRect(cx - 18f * d, 0f, cx + 18f * d, h, progressPaint)
                progressPaint.shader = null
            }
        } else {
            // placeholder hullám
            barPaint.color = 0x44808080
            val bars = 32
            val bw = w / bars * 0.55f
            for (i in 0 until bars) {
                val amp = 0.2f + 0.15f * kotlin.math.sin(i * 0.7f + phase * 6f)
                val bh = amp * maxH
                val x = i * (w / bars) + (w / bars - bw) / 2f
                rect.set(x, mid - bh, x + bw, mid + bh)
                canvas.drawRoundRect(rect, 3f * d, 3f * d, barPaint)
            }
        }
    }

    private fun overallProgress(): Float {
        if (chunkState.isEmpty()) return -1f
        var sum = 0.0
        for (i in chunkState.indices) {
            sum += when (chunkState[i]) {
                2 -> 1.0
                1 -> chunkFrac.getOrElse(i) { 0.0 }.coerceIn(0.0, 1.0)
                else -> 0.0
            }
        }
        return (sum / chunkState.size).toFloat()
    }
}
