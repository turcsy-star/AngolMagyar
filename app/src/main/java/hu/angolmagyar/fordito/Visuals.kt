package hu.angolmagyar.fordito

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Dinamikus valós idejű hanghullám vizualizáció beszédfelismeréshez és felvételhez.
 */
@Composable
fun RealtimeWaveform(
    amplitude: Float,
    isRecording: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wavePhase"
    )

    val animatedAmplitude by animateFloatAsState(
        targetValue = if (isRecording) amplitude.coerceIn(0.1f, 1.0f) else 0.05f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "amplitudeAnim"
    )

    Canvas(modifier = modifier.fillMaxWidth().height(100.dp)) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f

        if (width <= 0 || height <= 0) return@Canvas

        val path = Path()
        val numPoints = 100
        val step = width / numPoints

        path.moveTo(0f, centerY)
        for (i in 0..numPoints) {
            val x = i * step
            val normalizedX = (i.toFloat() / numPoints) * 2f * Math.PI.toFloat()
            val sineVal = sin(normalizedX * 2.5f + phase)
            val envelope = sin(i.toFloat() / numPoints * Math.PI.toFloat()) // Smooth fade at edges
            val y = centerY + (sineVal * animatedAmplitude * (height * 0.4f) * envelope)

            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        drawPath(
            path = path,
            color = waveColor,
            style = Stroke(width = 4.dp.toPx())
        )
    }
}
