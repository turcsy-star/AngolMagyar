package hu.angolmagyar.fordito

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.view.View
import android.view.animation.LinearInterpolator
import hu.angolmagyar.fordito.PhasePalette.Phase
import hu.angolmagyar.fordito.ui.design.PremiumMotion

/**
 * Fázisalapú ambient háttér: a képernyő tetején és alján lágy fénygömb, amelynek színe a folyamat
 * fázisát követi (felismerés hűvös kék, fordítás meleg arany, kész zöld, hiba piros).
 * A színváltás keresztfelvezetés, futás közben a fény lassan "lélegzik". Mozgáscsökkentésnél
 * (PremiumMotion) nincs animáció: a szín azonnal vált, és nincs lélegzés.
 */
class AmbientBackdropView(ctx: Context) : View(ctx) {
    private val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    private val animateColors = PremiumMotion.profile(ctx) != PremiumMotion.Profile.NONE
    private val allowBreath = PremiumMotion.allowLoops(ctx)
    private val topPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var color = PhasePalette.color(Phase.IDLE)
    private var target = color
    private var phase = Phase.IDLE
    private var colorAnim: ValueAnimator? = null
    private var breathAnim: ValueAnimator? = null
    private var breathing = false

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setPhase(p: Phase, running: Boolean) {
        if (p != phase) {
            phase = p
            val to = PhasePalette.color(p)
            if (to != target) {
                target = to
                colorAnim?.cancel()
                if (animateColors && isAttachedToWindow) {
                    val from = color
                    colorAnim = ValueAnimator.ofObject(ArgbEvaluator(), from, to).apply {
                        duration = 900
                        addUpdateListener { color = it.animatedValue as Int; rebuildShaders(); invalidate() }
                        start()
                    }
                } else {
                    color = to; rebuildShaders(); invalidate()
                }
            }
        }
        setBreathing(running && allowBreath)
    }

    private fun setBreathing(on: Boolean) {
        if (on == breathing) return
        breathing = on
        breathAnim?.cancel()
        if (on && isAttachedToWindow) {
            breathAnim = ValueAnimator.ofFloat(0.82f, 1f).apply {
                duration = 3600
                repeatMode = ValueAnimator.REVERSE
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    val a = (255f * (it.animatedValue as Float)).toInt()
                    topPaint.alpha = a
                    bottomPaint.alpha = a
                    invalidate()
                }
                start()
            }
        } else {
            topPaint.alpha = 255
            bottomPaint.alpha = 255
            invalidate()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (breathing) { breathing = false; setBreathing(true) }
    }

    override fun onDetachedFromWindow() {
        colorAnim?.cancel()
        breathAnim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        rebuildShaders()
    }

    private fun rebuildShaders() {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        val topA = if (night) 0.34f else 0.18f
        val botA = if (night) 0.16f else 0.09f
        topPaint.shader = RadialGradient(
            w * 0.5f, -h * 0.05f, h * 0.9f,
            PhasePalette.withAlpha(color, topA), PhasePalette.withAlpha(color, 0f), Shader.TileMode.CLAMP
        )
        bottomPaint.shader = RadialGradient(
            w * 0.85f, h * 1.05f, h * 0.6f,
            PhasePalette.withAlpha(color, botA), PhasePalette.withAlpha(color, 0f), Shader.TileMode.CLAMP
        )
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, topPaint)
        canvas.drawRect(0f, 0f, w, h, bottomPaint)
    }
}
