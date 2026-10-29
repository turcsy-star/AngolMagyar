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
 * Hang burkológörbe a feliratkészítés közben: kész = zöld, hátralévő = szürke; az a kis ablak (~30 mp),
 * amelyiken a felismerés éppen dolgozik, piros és fehér között pulzál.
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
    private var pulse = 0f
    private val pulseAnim = android.animation.ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1300
        repeatCount = android.animation.ValueAnimator.INFINITE
        interpolator = android.view.animation.LinearInterpolator()
        addUpdateListener {
            pulse = it.animatedFraction
            if (chunkState.any { s -> s == 1 }) invalidate()
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); pulseAnim.start() }
    override fun onDetachedFromWindow() { pulseAnim.cancel(); super.onDetachedFromWindow() }
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

    /** Az i. darab feldolgozási kurzora (ms): a darab kezdete + haladás × hossz. */
    private fun cursorMs(i: Int): Long {
        val a = ranges.getOrElse(2 * i) { 0L }
        val b = ranges.getOrElse(2 * i + 1) { a }
        return a + ((b - a) * chunkFrac.getOrElse(i) { 0.0 }.coerceIn(0.0, 1.0)).toLong()
    }

    /** Koppintásra (ms) – a képernyő megszólaltatja a hangot azon a ponton. */
    var onSeek: ((Long) -> Unit)? = null

    private var lastX = 0f

    init {
        // csak koppintás: a függőleges görgetés a képernyőé marad
        setOnTouchListener { _, e -> if (e.action == android.view.MotionEvent.ACTION_DOWN) lastX = e.x; false }
        setOnClickListener {
            if (width > 0 && totalMs > 1) onSeek?.invoke((lastX.coerceIn(0f, width.toFloat()) / width * totalMs).toLong())
        }
    }

    /** 0 = hátralévő (szürke), 1 = éppen dolgozik (piros–fehér pulzáló), 2 = kész (zöld) az idő (ms) alapján. */
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
            // az éppen dolgozott szakaszon belül: a kurzor előtt kész (zöld); a kurzor után egy ~30 mp-es ablak piros–fehér
            // (ezen dolgozik a Whisper), ami még távolabb van, az szürke
            if (chunkState[last] == 1) {
                val cur = cursorMs(last)
                if (tMs < cur) return 2
                return if (tMs < cur + 30_000L) 1 else 0
            }
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
            val doneC = Ui.color(context, R.color.ok)
            val activeRed = 0xFFFF4D4D.toInt()
            val activeWhite = 0xFFFFFFFF.toInt()
            val idleC = 0x66888AA0
            val progress = overallProgress()
            for (i in 0 until n) {
                val x = i * (w / n) + gap / 2f
                val bh = envelope[i].coerceIn(0.04f, 1f) * maxH
                rect.set(x, mid - bh, x + bw, mid + bh)
                val t = i.toFloat() / n
                val st = stateAt((t * totalMs).toLong(), progress, t)
                var barH = bh
                barPaint.color = when (st) {
                    2 -> doneC
                    1 -> {
                        // pulzálás: a szín piros és fehér között vált, a magasság is lüktet (a hullám végigfut az ablakon)
                        val k = 0.5f + 0.5f * kotlin.math.sin(pulse * 2f * Math.PI.toFloat() - i * 0.12f)
                        barH = bh * (0.85f + 0.3f * k)
                        androidx.core.graphics.ColorUtils.blendARGB(activeRed, activeWhite, k)
                    }
                    else -> idleC
                }
                rect.set(x, mid - barH, x + bw, mid + barH)
                val rad = min(bw / 2f, 4f * d)
                canvas.drawRoundRect(rect, rad, rad, barPaint)
            }
            // vékony kurzor az éppen dolgozott pontokon
            if (ranges.size >= 2 && totalMs > 0) {
                barPaint.color = Ui.color(context, R.color.textPrimary)
                for (i in chunkState.indices) if (chunkState[i] == 1 && 2 * i + 1 < ranges.size) {
                    val x = (cursorMs(i).toFloat() / totalMs) * w
                    rect.set(x - 1f * d, 2f * d, x + 1f * d, h - 2f * d)
                    canvas.drawRoundRect(rect, d, d, barPaint)
                }
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
