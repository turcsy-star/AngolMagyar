package hu.angolmagyar.fordito.ui.processing

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.angolmagyar.fordito.ui.design.PlatinumColors
import hu.angolmagyar.fordito.ui.design.PremiumMotion

/**
 * Platina haladássáv letöltésekhez: kefélt fém kitöltés, 20 gravírozott osztás, csillanás.
 * pct < 0 = ismeretlen haladás: csak egy futó fénycsík jelzi, hogy dolgozik (százalék nélkül).
 */
internal fun safeProgressPercent(pct: Int): Int = pct.coerceIn(0, 100)

fun PlatinumProgressBar(pct: Int, message: String, reduced: Boolean, modifier: Modifier = Modifier) {
    val known = pct >= 0
    val safePct = safeProgressPercent(pct)
    val frac by animateFloatAsState(safePct / 100f, tween(if (reduced) 0 else 500), label = "pf")
    val sweep = if (reduced) 0.5f else rememberInfiniteTransition(label = "pb").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "sw"
    ).value

    Column(
        modifier.fillMaxWidth().semantics {
            role = Role.ProgressBar
            if (known) {
                progressBarRangeInfo = ProgressBarRangeInfo(safePct.toFloat(), 0f..100f)
                contentDescription = "Letöltés: $safePct százalék"
            } else {
                progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                contentDescription = "Letöltés folyamatban"
            }
        },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth()) {
            Text("LETÖLTÉS", color = PlatinumColors.Champagne, fontSize = 10.sp, letterSpacing = 1.8.sp, modifier = Modifier.weight(1f))
            Text(if (known) "$safePct%" else "…", color = PlatinumColors.Text, fontSize = 15.sp, fontFamily = FontFamily.Monospace)
        }
        Canvas(Modifier.fillMaxWidth().height(16.dp)) {
            val r = CornerRadius(size.height / 2f)
            drawRoundRect(PlatinumColors.Onyx, Offset.Zero, size, r)
            drawRoundRect(PlatinumColors.SteelDark.copy(alpha = 0.6f), Offset(0.5.dp.toPx(), 0.5.dp.toPx()), Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()), r, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()))
            val pad = 2.dp.toPx()
            val inner = size.width - pad * 2
            val h = size.height - pad * 2
            if (known && frac > 0f) {
                val w = (inner * frac).coerceAtLeast(h)
                drawRoundRect(
                    Brush.verticalGradient(listOf(PlatinumColors.PlatinumHigh, PlatinumColors.Platinum, PlatinumColors.Steel), pad, pad + h),
                    Offset(pad, pad), Size(w, h), CornerRadius(h / 2f)
                )
                if (!reduced && frac < 1f) { // csillanás a kitöltésen
                    val cx = pad + w * sweep; val bw = 26.dp.toPx()
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.8f), Color.Transparent), cx - bw, cx + bw),
                        Offset((cx - bw).coerceAtLeast(pad), pad), Size((cx + bw).coerceAtMost(pad + w) - (cx - bw).coerceAtLeast(pad), h), CornerRadius(h / 2f)
                    )
                }
            } else if (!known) {
                val bw = inner * 0.22f
                val x = pad + (inner - bw) * sweep
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color.Transparent, PlatinumColors.Champagne.copy(alpha = 0.85f), Color.Transparent), x, x + bw),
                    Offset(x, pad), Size(bw, h), CornerRadius(h / 2f)
                )
            }
            // 20 gravírozott osztás
            for (i in 1 until 20) {
                val x = pad + inner * i / 20f
                val lit = known && x <= pad + inner * frac
                drawLine(
                    (if (lit) PlatinumColors.Onyx else PlatinumColors.SteelDark).copy(alpha = if (i % 5 == 0) 0.8f else 0.4f),
                    Offset(x, pad + (if (i % 5 == 0) 0f else h * 0.3f)), Offset(x, pad + h - (if (i % 5 == 0) 0f else h * 0.3f)), 1.dp.toPx()
                )
            }
        }
        if (message.isNotBlank()) Text(message, color = PlatinumColors.TextDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** View-burkoló a View-alapú Modellkezelőhöz. */
class PlatinumProgressView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : FrameLayout(ctx, attrs) {
    private var pct by mutableIntStateOf(-1)
    private var msg by mutableStateOf("")
    private val reduced = PremiumMotion.reduced(ctx)

    init {
        addView(ComposeView(ctx).apply { setContent { PlatinumProgressBar(pct, msg, reduced) } },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    fun set(percent: Int, message: String) { if (pct != percent) pct = percent; if (msg != message) msg = message }
}
