package hu.angolmagyar.fordito

import android.animation.AnimatorSet
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

/** Közös, kézzel épített UI-elemek (téma-érzékeny színekkel). */
object Ui {

    /** Tartalom maximális szélessége (tablet / fekvő mód). */
    private const val MAX_CONTENT_DP = 640

    fun dp(ctx: Context, v: Int) = (v * ctx.resources.displayMetrics.density).toInt()

    /** Android 12+: a háttérképből képzett rendszer-kiemelőszín (Beállítások → Haladó → Dinamikus színek). */
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
        return ContextCompat.getColor(ctx, res)
    }

    /** Függőleges oszlop, amely széles képernyőn nem nyúlik szét. */
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

    /** ScrollView, amelyben az oszlop vízszintesen középre kerül. */
    fun scrollOf(ctx: Context, column: View): ScrollView =
        ScrollView(ctx).apply {
            addView(
                column,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_HORIZONTAL
                )
            )
        }

    fun card(ctx: Context): LinearLayout =
        LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(ctx, 18)
            setPadding(pad, pad, pad, pad)
            // finom, üveges hatás: a kártya színe enyhén a kiemelőszín felé árnyalódik
            val base = color(ctx, R.color.card)
            val tint = androidx.core.graphics.ColorUtils.blendARGB(base, color(ctx, R.color.primary), 0.06f)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(base, tint)).apply {
                cornerRadius = dp(ctx, 20).toFloat()
                setStroke(dp(ctx, 1), androidx.core.graphics.ColorUtils.setAlphaComponent(color(ctx, R.color.stroke), 200))
            }
            elevation = dp(ctx, 2).toFloat()
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
                animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    onClick()
                }.start()
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(ctx, 10) }
        }

    /**
     * Kompakt indítókártya: ikon + cím + alcím. A kiemelt (pl. utoljára használt) kártya
     * színes, a többi csendes keretes.
     */
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
                animate().scaleX(0.98f).scaleY(0.98f).setDuration(80).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(140).start()
                    onClick()
                }.start()
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


    /** Négyzetes indító csempe: ikon + cím + rövid leírás. Két csempe fér egymás mellé. */
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
                animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).withEndAction {
                    animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    onClick()
                }.start()
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

    /** Két csempe egymás mellett. */
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

    /** Négyzetes ikongomb leírással (TalkBack + hosszú nyomásra tooltip). */
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

    /** Staggered fade+slide belépés a gyerek view-kra. */
    fun animateIn(parent: ViewGroup, delayStepMs: Long = 60) {
        var d = 0L
        for (i in 0 until parent.childCount) {
            val v = parent.getChildAt(i)
            // kezdőállapot: csak itt, az animáció indításakor lesz átlátszó
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

    /** Animáció nélkül láthatóvá teszi a gyerekeket (frissítéskor nincs villogás). */
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
