package hu.angolmagyar.fordito

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import androidx.core.graphics.ColorUtils

/**
 * Színkódolt teljes folyamat-sáv: minden lépés külön szegmens a saját színében, a szélessége a lépés
 * súlya. Kész = teli, aktív = részben kitöltve, várakozó = halvány (mint a Whisper-dashboard fenti sávja).
 */
class SegmentedBar(ctx: Context) : View(ctx) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val states = IntArray(SrtJob.WEIGHTS.size)
    private val pcts = IntArray(SrtJob.WEIGHTS.size)

    init {
        minimumHeight = Ui.dp(ctx, 14)
    }

    fun update() {
        for (i in states.indices) {
            states[i] = SrtJob.steps[i].state
            pcts[i] = SrtJob.steps[i].pct
        }
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(Ui.dp(context, 14), heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        val total = SrtJob.WEIGHTS.sum().toFloat()
        val gap = Ui.dp(context, 2).toFloat()
        val h = height.toFloat()
        val r = h / 2f
        var x = 0f
        for (i in SrtJob.WEIGHTS.indices) {
            val w = width * SrtJob.WEIGHTS[i] / total
            val left = x
            val right = x + w - (if (i < SrtJob.WEIGHTS.size - 1) gap else 0f)
            x += w
            val color = when (states[i]) {
                SrtJob.ERROR -> Ui.color(context, R.color.err)
                SrtJob.STOPPED -> Ui.color(context, R.color.warn)
                else -> if (i == SrtJob.ST_RECOG && states[i] == SrtJob.ACTIVE && SrtJob.cooling)
                    Ui.color(context, R.color.warn) else SrtJob.COLORS[i]
            }
            // sáv-alap (halvány)
            paint.color = ColorUtils.setAlphaComponent(color, 60)
            rect.set(left, 0f, right, h)
            canvas.drawRoundRect(rect, r, r, paint)
            // kitöltés
            val frac = when (states[i]) {
                SrtJob.DONE, SrtJob.ERROR -> 1f
                SrtJob.ACTIVE, SrtJob.STOPPED -> if (pcts[i] < 0) 0.25f else (pcts[i] / 100f).coerceIn(0.04f, 1f)
                else -> 0f
            }
            if (frac > 0f) {
                paint.color = color
                rect.set(left, 0f, left + (right - left) * frac, h)
                canvas.drawRoundRect(rect, r, r, paint)
            }
        }
    }
}
