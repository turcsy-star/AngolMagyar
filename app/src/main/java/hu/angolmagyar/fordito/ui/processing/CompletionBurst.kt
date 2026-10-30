package hu.angolmagyar.fordito.ui.processing

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.ui.design.PremiumColors
import hu.angolmagyar.fordito.ui.design.PremiumMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Egyszeri, rövid sikeres befejezési animáció (gyűrű + szikrák). Csökkentett módban nem rajzol. */
@Composable
fun CompletionBurst(
    modifier: Modifier = Modifier,
    colors: List<Color> = listOf(PremiumColors.Success, PremiumColors.CyanGlow),
    onDone: () -> Unit = {}
) {
    val t = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        t.animateTo(1f, tween(PremiumMotion.SUCCESS, easing = LinearEasing))
        onDone()
    }
    Canvas(modifier) {
        val p = t.value
        if (p >= 1f) return@Canvas
        val c = Offset(size.width / 2f, size.height / 2f)
        val ease = 1f - (1f - p) * (1f - p)
        val maxR = size.minDimension * 0.5f
        drawCircle(colors.first().copy(alpha = (1f - p) * 0.8f), maxR * 0.3f + maxR * 0.7f * ease, c, style = Stroke(3.dp.toPx()))
        for (k in 0 until 12) {
            val a = k * (2f * PI.toFloat() / 12f)
            val rr = maxR * 0.25f + maxR * 0.75f * ease
            drawCircle(
                colors[k % colors.size].copy(alpha = 1f - p),
                (3.5f * (1f - p) + 0.5f).dp.toPx(),
                Offset(c.x + rr * cos(a), c.y + rr * sin(a))
            )
        }
    }
}
