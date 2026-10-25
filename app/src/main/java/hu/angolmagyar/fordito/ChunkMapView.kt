package hu.angolmagyar.fordito

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator

/**
 * Chunk-térkép: minden chunk egy lekerekített cella.
 * Szürke = vár, animált kék/lila = dolgozik, zöld = kész, borostyán = hűtés.
 */
class ChunkMapView(ctx: Context) : View(ctx) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val rect = RectF()
    private var states = IntArray(0)
    private var fracs = DoubleArray(0)
    private var cooling = false
    private val cell = Ui.dp(ctx, 22)
    private val gap = Ui.dp(ctx, 4)
    private var pulse = 0f

    private val anim = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulse = it.animatedFraction
            if (states.any { s -> s == 1 }) invalidate()
        }
    }

    init {
        textPaint.textSize = 9f * resources.displayMetrics.density
        textPaint.color = 0xCCFFFFFF.toInt()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!anim.isStarted) anim.start()
    }

    override fun onDetachedFromWindow() {
        anim.cancel()
        super.onDetachedFromWindow()
    }

    fun update() {
        states = SrtJob.chunkState.copyOf()
        fracs = try { SrtJob.chunkFracCopy() } catch (_: Exception) { DoubleArray(0) }
        cooling = SrtJob.cooling
        requestLayout()
        invalidate()
    }

    private fun perRow(w: Int) = ((w + gap) / (cell + gap)).coerceAtLeast(1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = if (states.isEmpty()) 0 else (states.size + perRow(w) - 1) / perRow(w)
        val h = if (rows == 0) 0 else rows * cell + (rows - 1) * gap + Ui.dp(context, 4)
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val per = perRow(width)
        val rad = Ui.dp(context, 5).toFloat()
        val ok = Ui.color(context, R.color.ok)
        val active = Ui.color(context, R.color.primary)
        val warn = Ui.color(context, R.color.warn)
        val idle = 0x44808080

        for (i in states.indices) {
            val x = (i % per) * (cell + gap).toFloat()
            val y = (i / per) * (cell + gap).toFloat()
            rect.set(x, y, x + cell, y + cell)

            when (states[i]) {
                2 -> {
                    paint.color = ok
                    canvas.drawRoundRect(rect, rad, rad, paint)
                    // checkmark-like dot
                    paint.color = 0xEEFFFFFF.toInt()
                    canvas.drawCircle(x + cell / 2f, y + cell / 2f, cell * 0.12f, paint)
                }
                1 -> {
                    val frac = fracs.getOrElse(i) { 0.0 }.toFloat().coerceIn(0f, 1f)
                    // background
                    paint.color = idle
                    canvas.drawRoundRect(rect, rad, rad, paint)
                    // filled portion
                    val fill = RectF(rect.left, rect.top, rect.left + rect.width() * frac.coerceAtLeast(0.08f), rect.bottom)
                    paint.color = if (cooling) warn else active
                    canvas.drawRoundRect(fill, rad, rad, paint)
                    // pulse glow
                    val a = (80 + 60 * kotlin.math.sin(pulse * Math.PI * 2).toFloat()).toInt().coerceIn(40, 140)
                    glowPaint.color = (if (cooling) warn else active) and 0x00FFFFFF or (a shl 24)
                    canvas.drawRoundRect(rect, rad, rad, glowPaint)
                    // percent text if cell large enough
                    if (cell >= Ui.dp(context, 18)) {
                        textPaint.color = 0xEEFFFFFF.toInt()
                        canvas.drawText(
                            "${(frac * 100).toInt()}",
                            x + cell / 2f,
                            y + cell / 2f - (textPaint.ascent() + textPaint.descent()) / 2f,
                            textPaint
                        )
                    }
                }
                else -> {
                    paint.color = idle
                    canvas.drawRoundRect(rect, rad, rad, paint)
                }
            }
        }
    }
}
