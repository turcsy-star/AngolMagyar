package hu.angolmagyar.fordito

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * Spektrogram-csík a hullámforma alatt: a már feldolgozott rész teljes fényű, a hátralévő halvány.
 * Az adat a valós PCM-ből számolt [Spectrogram.Grid]; a nézetre koppintva a lejátszás odaugrik.
 */
class SpectrogramView @JvmOverloads constructor(ctx: Context) : View(ctx) {
    private var bitmap: Bitmap? = null
    private var totalMs = 1L
    private var fraction = 0f
    private val dst = RectF()
    private val bright = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dim = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 90 }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xCCFFFFFF.toInt() }
    private var lastX = 0f
    var onSeek: ((Long) -> Unit)? = null

    init {
        contentDescription = "Spektrogram: a felvétel hangszíne az idő függvényében"
        setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_DOWN) lastX = e.x; false }
        setOnClickListener {
            if (width > 0 && totalMs > 1) onSeek?.invoke((lastX.coerceIn(0f, width.toFloat()) / width * totalMs).toLong())
        }
    }

    fun setGrid(grid: Spectrogram.Grid, totalMs: Long) {
        this.totalMs = maxOf(1L, totalMs)
        if (grid.columns == 0 || grid.bands == 0) { bitmap = null; invalidate(); return }
        val base = 0xFF0A1020.toInt()
        val accent = PhasePalette.color(PhasePalette.Phase.RECOGNIZE)
        val px = IntArray(grid.columns * grid.bands)
        for (c in 0 until grid.columns) {
            for (b in 0 until grid.bands) {
                // fent a magas hangok, lent a mélyek
                px[(grid.bands - 1 - b) * grid.columns + c] = Spectrogram.heatColor(grid.value(c, b), base, accent)
            }
        }
        bitmap = Bitmap.createBitmap(px, grid.columns, grid.bands, Bitmap.Config.ARGB_8888)
        invalidate()
    }

    fun setProgress(f: Float) {
        val v = f.coerceIn(0f, 1f)
        if (v != fraction) { fraction = v; invalidate() }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val d = resources.displayMetrics.density
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), (52 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        val w = width.toFloat()
        val h = height.toFloat()
        val pad = 4 * resources.displayMetrics.density
        dst.set(0f, pad, w, h - pad)
        canvas.drawBitmap(bmp, null, dst, dim)
        if (fraction > 0f) {
            canvas.save()
            canvas.clipRect(0f, 0f, w * fraction, h)
            canvas.drawBitmap(bmp, null, dst, bright)
            canvas.restore()
            if (fraction < 1f) canvas.drawRect(w * fraction - 1f, pad, w * fraction + 1f, h - pad, line)
        }
    }
}
