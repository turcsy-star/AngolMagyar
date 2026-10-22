package hu.angolmagyar.fordito

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View

/**
 * Chunk-térkép: minden chunk egy négyzet. Szürke = vár, kék = dolgozik (hűtésnél borostyán), zöld = kész.
 */
class ChunkMapView(ctx: Context) : View(ctx) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var states = IntArray(0)
    private var cooling = false
    private val cell = Ui.dp(ctx, 14)
    private val gap = Ui.dp(ctx, 3)

    fun update() {
        states = SrtJob.chunkState.copyOf()
        cooling = SrtJob.cooling
        requestLayout()
        invalidate()
    }

    private fun perRow(w: Int) = ((w + gap) / (cell + gap)).coerceAtLeast(1)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val rows = if (states.isEmpty()) 0 else (states.size + perRow(w) - 1) / perRow(w)
        val h = if (rows == 0) 0 else rows * cell + (rows - 1) * gap
        setMeasuredDimension(w, h)
    }

    override fun onDraw(canvas: Canvas) {
        val per = perRow(width)
        val rad = Ui.dp(context, 3).toFloat()
        for (i in states.indices) {
            val x = (i % per) * (cell + gap).toFloat()
            val y = (i / per) * (cell + gap).toFloat()
            rect.set(x, y, x + cell, y + cell)
            paint.color = when (states[i]) {
                2 -> Ui.color(context, R.color.ok)
                1 -> if (cooling) Ui.color(context, R.color.warn) else SrtJob.COLORS[SrtJob.ST_RECOG]
                else -> 0x33808080
            }
            canvas.drawRoundRect(rect, rad, rad, paint)
        }
    }
}
