package hu.angolmagyar.fordito.ui.processing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import hu.angolmagyar.fordito.ui.design.PlatinumColors

/**
 * Háromrétegű mélység-háttér (v3.13): lassú aurora parallax.
 * reduceMotion / nem-cinema: statikus gradiens.
 */
@Composable
fun DiamondDepthStack(
    active: Boolean,
    reduced: Boolean,
    cinema: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val drift = if (reduced || !cinema) 0f else {
        val inf = rememberInfiniteTransition(label = "depth")
        inf.animateFloat(
            0f, 1f,
            infiniteRepeatable(
                tween(if (active) 9000 else 16000, easing = LinearEasing),
                RepeatMode.Reverse
            ),
            label = "drift"
        ).value
    }

    Box(modifier.fillMaxWidth()) {
        Canvas(Modifier.matchParentSize()) {
            // Réteg 1 – mély ónix
            drawRect(
                Brush.verticalGradient(
                    listOf(PlatinumColors.Onyx, PlatinumColors.Graphite.copy(alpha = 0.95f))
                )
            )
            // Réteg 2 – lassú aurora foltok (parallax eltolás)
            val dx = size.width * 0.08f * drift
            val dy = size.height * 0.05f * (1f - drift)
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFF1A3A5C).copy(alpha = if (active) 0.35f else 0.18f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.2f + dx, size.height * 0.3f + dy),
                    radius = size.minDimension * 0.55f
                ),
                radius = size.minDimension * 0.55f,
                center = Offset(size.width * 0.2f + dx, size.height * 0.3f + dy)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFF3A2A18).copy(alpha = if (active) 0.28f else 0.14f),
                        Color.Transparent
                    ),
                    center = Offset(size.width * 0.85f - dx, size.height * 0.7f - dy),
                    radius = size.minDimension * 0.5f
                ),
                radius = size.minDimension * 0.5f,
                center = Offset(size.width * 0.85f - dx, size.height * 0.7f - dy)
            )
            // Réteg 3 – finom élfény felül
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.06f), Color.Transparent)
                ),
                size = size.copy(height = size.height * 0.25f)
            )
        }
        content()
    }
}
