package hu.angolmagyar.fordito.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Compact faceted sapphire mark used by the Diamond identity. */
@Composable
fun DiamondBrandMark(modifier: Modifier = Modifier, animated: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "diamondBrand")
    val sweep = if (animated) {
        val animatedSweep by transition.animateFloat(
            initialValue = -0.35f,
            targetValue = 1.35f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
            label = "diamondSweep"
        )
        animatedSweep
    } else 0.5f
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val top = Offset(w * .5f, h * .06f)
        val right = Offset(w * .94f, h * .43f)
        val bottom = Offset(w * .5f, h * .96f)
        val left = Offset(w * .06f, h * .43f)
        val center = Offset(w * .5f, h * .48f)
        val silhouette = Path().apply {
            moveTo(top.x, top.y)
            lineTo(right.x, right.y)
            lineTo(bottom.x, bottom.y)
            lineTo(left.x, left.y)
            close()
        }
        drawPath(
            silhouette,
            Brush.linearGradient(listOf(Color(0xFFECFAFF), Color(0xFF65C9FF), Color(0xFF1555B8), Color(0xFF061A43)))
        )
        val upperFacet = Path().apply { moveTo(top.x, top.y); lineTo(center.x, center.y); lineTo(left.x, left.y); close() }
        val rightFacet = Path().apply { moveTo(top.x, top.y); lineTo(right.x, right.y); lineTo(center.x, center.y); close() }
        val lowerFacet = Path().apply { moveTo(left.x, left.y); lineTo(center.x, center.y); lineTo(bottom.x, bottom.y); close() }
        val farFacet = Path().apply { moveTo(center.x, center.y); lineTo(right.x, right.y); lineTo(bottom.x, bottom.y); close() }
        drawPath(upperFacet, Color(0xFFBDEEFF).copy(alpha = .72f))
        drawPath(rightFacet, Color(0xFF4BA8F7).copy(alpha = .72f))
        drawPath(lowerFacet, Color(0xFF174B9C).copy(alpha = .68f))
        drawPath(farFacet, Color(0xFF0A2860).copy(alpha = .72f))
        drawPath(silhouette, Color(0xFFCFF5FF), style = Stroke(width = (w * .035f).coerceAtLeast(1.dp.toPx())))
        drawLine(Color.White.copy(alpha = .72f), top, center, strokeWidth = w * .025f, cap = StrokeCap.Round)
        if (animated) {
            val x = w * sweep
            drawLine(
                Color.White.copy(alpha = .38f),
                Offset(x, h * .08f), Offset(x - h * .42f, h * .92f),
                strokeWidth = w * .07f,
                cap = StrokeCap.Round
            )
        }
    }
}
