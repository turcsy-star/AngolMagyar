package hu.angolmagyar.fordito

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max
import kotlin.math.min

/**
 * Hang burkológörbe a feliratkészítés közben, lüktetés nélkül:
 * kész szakasz = fehér (olvasható szín), éppen dolgozott szakasz = zöld, hátralévő = szürke.
 */
class WaveView @JvmOverloads constructor(
    ctx: Context, attrs: AttributeSet? = null
) : View(ctx, attrs) {

    private var envelope = FloatArray(0)
    private var totalMs = 1L
    private var ranges = LongArray(0) // párok: startMs, endMs (darabonként)
    private var chunkState = IntArray(0)
    private var chunkFrac = DoubleArray(0)

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()

    init {
        val d = resources.displayMetrics.density
        setPadding(0, (8 * d).toInt(), 0, (8 * d).toInt())
    }

    fun setEnvelope(env: FloatArray, totalMs: Long) {
        envelope = env
        this.totalMs = max(1L, totalMs)
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
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (96 * d).toInt())
    }

    /** 0 = hátralévő (szürke), 1 = éppen dolgozik (zöld), 2 = kész (fehér) az idő (ms) alapján. */
    private fun stateAt(tMs: Long, progress: Float, t: Float): Int {
        if (ranges.size >= 2 && chunkState.size * 2 <= ranges.size) {
            var i = 0
            var last = -1
            while (i + 1 < ranges.size && i / 2 < chunkState.size) {
                if (tMs >= ranges[i]) last = i / 2
                if (tMs < ranges[i + 1]) break
                i += 2
            }
            if (last < 0) return chunkState.getOrElse(0) { 0 }
            return chunkState[last]
        }
        return when {
            progress < 0f -> 0
            t < progress - 0.001f -> 2
            t < progress + 0.02f -> 1
            else -> 0
        }
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return
        val d = resources.displayMetrics.density
        val mid = h / 2f
        val maxH = h * 0.42f
        val n = envelope.size
        if (n > 0) {
            val gap = max(1f, w / n * 0.18f)
            val bw = max(1.5f, w / n - gap)
            val doneC = Ui.color(context, R.color.textPrimary)
            val activeC = Ui.color(context, R.color.ok)
            val idleC = 0x66888AA0
            val progress = overallProgress()
            for (i in 0 until n) {
                val x = i * (w / n) + gap / 2f
                val bh = envelope[i].coerceIn(0.04f, 1f) * maxH
                rect.set(x, mid - bh, x + bw, mid + bh)
                val t = i.toFloat() / n
                barPaint.color = when (stateAt((t * totalMs).toLong(), progress, t)) {
                    2 -> doneC
                    1 -> activeC
                    else -> idleC
                }
                val rad = min(bw / 2f, 4f * d)
                canvas.drawRoundRect(rect, rad, rad, barPaint)
            }
        } else {
            barPaint.color = 0x44808080
            val bars = 32
            val bw = w / bars * 0.55f
            for (i in 0 until bars) {
                val bh = 0.25f * maxH
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
