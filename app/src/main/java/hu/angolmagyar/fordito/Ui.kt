package hu.angolmagyar.fordito
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.content.ContextCompat
import androidx.compose.ui.graphics.toArgb
import hu.angolmagyar.fordito.ui.design.LuxuryPalettes
object Ui {
    private const val MAX_CONTENT_DP = 640
    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()
    fun dynamicColors(ctx: Context): Boolean = try {
        android.os.Build.VERSION.SDK_INT >= 31 && Prefs(ctx).raw().getBoolean("dynColors", false)
    } catch (_: Exception) { false }
    fun setDynamicColors(ctx: Context, on: Boolean) { Prefs(ctx).raw().edit().putBoolean("dynColors", on).apply() }
    fun color(ctx: Context, res: Int): Int {
        if (android.os.Build.VERSION.SDK_INT >= 31 && (res == R.color.primary || res == R.color.accentGlow || res == R.color.primaryDim) && dynamicColors(ctx)) {
            val night = (ctx.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES
            return try {
                ContextCompat.getColor(ctx, if (night) android.R.color.system_accent1_200 else android.R.color.system_accent1_600)
            } catch (_: Exception) { ContextCompat.getColor(ctx, res) }
        }
        val prefs = Prefs(ctx)
        if (prefs.platinumTheme) {
            val palette = LuxuryPalettes.byId(prefs.luxuryThemeId)
            when (res) {
                R.color.bg -> return palette.background.toArgb()
                R.color.card -> return palette.surface.toArgb()
                R.color.textPrimary -> return palette.text.toArgb()
                R.color.textSecondary -> return palette.text.copy(alpha = 0.72f).toArgb()
                R.color.stroke -> return palette.edge.copy(alpha = 0.58f).toArgb()
                R.color.primary -> return palette.accent.toArgb()
                R.color.primaryDim -> return palette.accent.copy(alpha = 0.88f).toArgb()
                R.color.accentGlow -> return palette.glow.toArgb()
            }
        }
        if (res == R.color.primary || res == R.color.primaryDim || res == R.color.accentGlow) {
            if (Accent.id(ctx) != "indigo") {
                return if (res == R.color.accentGlow) Accent.glow(ctx) else Accent.primary(ctx)
            }
        }
        return ContextCompat.getColor(ctx, res)
    }
    private class MaxWidthColumn(ctx: Context, private val maxPx: Int) : LinearLayout(ctx) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val size = MeasureSpec.getSize(widthMeasureSpec)
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            val w = if (mode != MeasureSpec.UNSPECIFIED && size > maxPx) {
                MeasureSpec.makeMeasureSpec(maxPx, MeasureSpec.EXACTLY)
            } else {
                widthMeasureSpec
            }
            super.onMeasure(w, heightMeasureSpec)
        }
    }
    fun column(ctx: Context): LinearLayout =
        MaxWidthColumn(ctx, dp(ctx, MAX_CONTENT_DP)).apply { orientation = LinearLayout.VERTICAL }
    fun scrollOf(ctx: Context, column: View): ScrollView =
        ScrollView(ctx).apply {
            isFillViewport = true
            clipToPadding = false
            setBackgroundColor(color(ctx, R.color.bg))
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                column,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL
                )
            )
        }
    fun heroHeader(ctx: Context, eyebrow: String, heading: String, detail: String): LinearLayout {
        val themePrefs = Prefs(ctx)
        val palette = LuxuryPalettes.byId(if (themePrefs.platinumTheme) themePrefs.luxuryThemeId else "premium")
        val motionMs = hu.angolmagyar.fordito.ui.design.PremiumMotion.cardEnter(ctx)
        val wrap = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(ctx, 20), dp(ctx, 20), dp(ctx, 20), dp(ctx, 20))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(palette.surface.toArgb(), palette.background.toArgb(), palette.accent.copy(alpha = 0.12f).toArgb())
            ).apply {
                cornerRadius = dp(ctx, 24).toFloat()
                setStroke(dp(ctx, 1), palette.edge.copy(alpha = 0.62f).toArgb())
            }
            elevation = dp(ctx, 5).toFloat()
            alpha = if (motionMs == 0) 1f else 0f
            translationY = if (motionMs == 0) 0f else dp(ctx, 12).toFloat()
        }
        wrap.addView(View(ctx).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(palette.accent.toArgb(), palette.glow.toArgb(), palette.glow.copy(alpha = 0f).toArgb()))
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 76), dp(ctx, 3)).apply {
                bottomMargin = dp(ctx, 13)
            }
        })
        wrap.addView(TextView(ctx).apply {
            text = eyebrow.uppercase()
            textSize = 10f
            letterSpacing = 0.18f
            setTextColor(palette.accent.toArgb())
            setTypeface(typeface, Typeface.BOLD)
        })
        wrap.addView(TextView(ctx).apply {
            text = heading
            textSize = 25f
            letterSpacing = -0.025f
            setTextColor(palette.text.toArgb())
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(ctx, 7), 0, dp(ctx, 4))
        })
        wrap.addView(TextView(ctx).apply {
            text = detail
            textSize = 13f
            setTextColor(palette.text.copy(alpha = 0.76f).toArgb())
            setLineSpacing(dp(ctx, 2).toFloat(), 1f)
        })
        wrap.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(ctx, 8) }
        if (motionMs > 0) {
            wrap.animate().alpha(1f).translationY(0f).setStartDelay(45)
                .setDuration(motionMs.toLong()).setInterpolator(DecelerateInterpolator(1.7f)).start()
        }
        return wrap
    }
    fun sectionLabel(ctx: Context, label: String): TextView =
        TextView(ctx).apply {
            text = label.uppercase()
            textSize = 11f
            letterSpacing = 0.14f
            setTextColor(LuxuryPalettes.byId(if (Prefs(ctx).platinumTheme) Prefs(ctx).luxuryThemeId else "premium").accent.toArgb())
            setTypeface(typeface, Typeface.BOLD)
            setPadding(dp(ctx, 2), dp(ctx, 18), 0, dp(ctx, 5))
        }
    fun card(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(ctx, 18)
            setPadding(pad, pad, pad, pad)
            val base = color(ctx, R.color.card)
            val tint = androidx.core.graphics.ColorUtils.blendARGB(base, color(ctx, R.color.accentGlow), 0.085f)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(
                androidx.core.graphics.ColorUtils.blendARGB(base, color(ctx, R.color.primaryDim), 0.055f),
                tint,
                base
            )).apply {
                cornerRadius = dp(ctx, 20).toFloat()
                setStroke(dp(ctx, 1), androidx.core.graphics.ColorUtils.setAlphaComponent(color(ctx, R.color.accentGlow), 76))
            }
            elevation = dp(ctx, 3).toFloat()
            val motionMs = hu.angolmagyar.fordito.ui.design.PremiumMotion.cardEnter(ctx)
            alpha = if (motionMs == 0) 1f else 0f
            translationY = if (motionMs == 0) 0f else dp(ctx, 9).toFloat()
            if (motionMs > 0) post {
                if (isAttachedToWindow) animate().alpha(1f).translationY(0f)
                    .setDuration(motionMs.toLong()).setInterpolator(DecelerateInterpolator(1.5f)).start()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, 12) }
        }
    fun text(
        ctx: Context, t: String, size: Float, colorRes: Int, bold: Boolean
    ): TextView = TextView(ctx).apply {
        text = t
        textSize = size
        setTextColor(color(ctx, colorRes))
        if (bold) setTypeface(typeface, Typeface.BOLD)
        letterSpacing = 0.01f
    }
    fun title(ctx: Context, t: String, size: Float = 22f): TextView =
        text(ctx, t, size, R.color.textPrimary, true).apply {
            setPadding(0, dp(ctx, 2), 0, dp(ctx, 4))
            letterSpacing = -0.01f
        }
    fun body(ctx: Context, t: String): TextView = text(ctx, t, 15f, R.color.textPrimary, false)
    fun small(ctx: Context, t: String): TextView =
        text(ctx, t, 13f, R.color.textSecondary, false).apply {
            setPadding(0, dp(ctx, 2), 0, dp(ctx, 2))
        }
    fun button(ctx: Context, label: String, filled: Boolean, onClick: () -> Unit): AppCompatButton =
        AppCompatButton(ctx).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            minHeight = dp(ctx, 52)
            minimumHeight = dp(ctx, 52)
            letterSpacing = 0.02f
            val shape = GradientDrawable().apply {
                cornerRadius = dp(ctx, 14).toFloat()
                if (filled) setColor(color(ctx, R.color.primary))
                else {
                    setColor(Color.TRANSPARENT)
                    setStroke(dp(ctx, 1), color(ctx, R.color.primary))
                }
            }
            background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), shape, null)
            setTextColor(color(ctx, if (filled) R.color.onPrimary else R.color.primary))
            setOnClickListener {
                Haptics.click(ctx)
                onClick()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, 10) }
        }
    fun modeCard(
        ctx: Context, iconRes: Int, title: String, subtitle: String,
        highlighted: Boolean, onClick: () -> Unit
    ): LinearLayout {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(ctx, 18).toFloat()
            if (highlighted) {
                colors = intArrayOf(color(ctx, R.color.primary), color(ctx, R.color.primaryDim))
                orientation = GradientDrawable.Orientation.TL_BR
            } else {
                setColor(color(ctx, R.color.card))
                setStroke(dp(ctx, 1), color(ctx, R.color.stroke))
            }
        }
        val fg = if (highlighted) Color.WHITE else color(ctx, R.color.textPrimary)
        val fgSub = if (highlighted) 0xCCFFFFFF.toInt() else color(ctx, R.color.textSecondary)
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(ctx, 18), dp(ctx, 16), dp(ctx, 18), dp(ctx, 16))
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null)
            isClickable = true
            isFocusable = true
            contentDescription = "$title. $subtitle"
            elevation = if (highlighted) dp(ctx, 4).toFloat() else 0f
            setOnClickListener {
                Haptics.click(ctx)
                onClick()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, 10) }
            addView(ImageView(ctx).apply {
                setImageResource(iconRes)
                setColorFilter(if (highlighted) Color.WHITE else color(ctx, R.color.primary))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 30), dp(ctx, 30)).apply {
                    marginEnd = dp(ctx, 16)
                }
            })
            val texts = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
            texts.addView(TextView(ctx).apply {
                text = title
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(fg)
            })
            texts.addView(TextView(ctx).apply {
                text = subtitle
                textSize = 13f
                setTextColor(fgSub)
            })
            addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
    }
    fun tile(
        ctx: Context, iconRes: Int, title: String, subtitle: String,
        enabled: Boolean = true, onClick: () -> Unit
    ): LinearLayout {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(ctx, 18).toFloat()
            setColor(color(ctx, R.color.card))
            setStroke(dp(ctx, 1), color(ctx, R.color.stroke))
        }
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(dp(ctx, 16), dp(ctx, 16), dp(ctx, 16), dp(ctx, 16))
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null)
            isClickable = true
            isFocusable = true
            minimumHeight = dp(ctx, 132)
            contentDescription = if (enabled) "$title. $subtitle" else "$title. $subtitle. Előbb töltsd le a modelleket."
            if (!enabled) alpha = 0.45f
            setOnClickListener {
                Haptics.click(ctx)
                onClick()
            }
            addView(ImageView(ctx).apply {
                setImageResource(iconRes)
                setColorFilter(color(ctx, if (enabled) R.color.primary else R.color.textSecondary))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(dp(ctx, 34), dp(ctx, 34)).apply {
                    bottomMargin = dp(ctx, 10)
                }
            })
            addView(TextView(ctx).apply {
                text = title
                textSize = 17f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(ctx, R.color.textPrimary))
            })
            addView(TextView(ctx).apply {
                text = subtitle
                textSize = 13f
                setTextColor(color(ctx, R.color.textSecondary))
            })
        }
    }
    fun tileRow(ctx: Context, left: View, right: View): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, 10) }
            addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginEnd = dp(ctx, 5)
            })
            addView(right, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = dp(ctx, 5)
            })
        }
    fun smallButton(ctx: Context, label: String, onClick: () -> Unit): AppCompatButton =
        AppCompatButton(ctx).apply {
            text = label
            isAllCaps = false
            textSize = 13f
            minHeight = dp(ctx, 42)
            minimumHeight = dp(ctx, 42)
            val shape = GradientDrawable().apply {
                cornerRadius = dp(ctx, 12).toFloat()
                setColor(color(ctx, R.color.bgElevated))
                setStroke(dp(ctx, 1), color(ctx, R.color.stroke))
            }
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null)
            setTextColor(color(ctx, R.color.textPrimary))
            setOnClickListener {
                Haptics.tick(ctx)
                onClick()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    fun iconButton(ctx: Context, iconRes: Int, description: String, onClick: () -> Unit): AppCompatImageButton =
        AppCompatImageButton(ctx).apply {
            setImageResource(iconRes)
            setColorFilter(color(ctx, R.color.textPrimary))
            contentDescription = description
            androidx.appcompat.widget.TooltipCompat.setTooltipText(this, description)
            val shape = GradientDrawable().apply {
                cornerRadius = dp(ctx, 12).toFloat()
                setColor(color(ctx, R.color.bgElevated))
                setStroke(dp(ctx, 1), color(ctx, R.color.stroke))
            }
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, null)
            setOnClickListener {
                Haptics.tick(ctx)
                onClick()
            }
            layoutParams = LinearLayout.LayoutParams(dp(ctx, 46), dp(ctx, 46))
        }
    fun progressRow(ctx: Context): Pair<ProgressBar, TextView> {
        val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            isIndeterminate = false
            progress = 0
            progressTintList = ColorStateList.valueOf(color(ctx, R.color.primary))
            progressBackgroundTintList = ColorStateList.valueOf(color(ctx, R.color.stroke))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(ctx, 10)
            ).apply { topMargin = dp(ctx, 10) }
        }
        val label = small(ctx, "")
        return bar to label
    }
    fun animateIn(parent: ViewGroup, delayStepMs: Long = 60) {
        var d = 0L
        for (i in 0 until parent.childCount) {
            val v = parent.getChildAt(i)
            v.alpha = 0f
            v.translationY = dp(parent.context, 16).toFloat()
            v.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(d)
                .setDuration(280)
                .setInterpolator(DecelerateInterpolator(1.4f))
                .start()
            d += delayStepMs
        }
    }
    fun settle(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val v = parent.getChildAt(i)
            v.alpha = 1f
            v.translationY = 0f
        }
    }
    fun pulse(view: View) {
        val a1 = ObjectAnimator.ofFloat(view, View.SCALE_X, 1f, 1.06f, 1f)
        val a2 = ObjectAnimator.ofFloat(view, View.SCALE_Y, 1f, 1.06f, 1f)
        AnimatorSet().apply {
            playTogether(a1, a2)
            duration = 900
            interpolator = OvershootInterpolator(0.6f)
            start()
        }
    }
}
