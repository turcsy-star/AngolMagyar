package hu.angolmagyar.fordito.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/** Mikrointerakció: érintésre finoman összenyomódik (nem fogyasztja el az eseményt, a görgetés működik). */
fun Modifier.pressScale(reduced: Boolean = false): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed && !reduced) 0.97f else 1f, tween(if (reduced) 0 else 140), label = "press")
    this.graphicsLayer { scaleX = scale; scaleY = scale }.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            pressed = true
            waitForUpOrCancellation()
            pressed = false
        }
    }
}

/** Többrétegű „üveg” felület: áttetsző fehér réteg, ferde fénykiemelés és átmenetes perem. */
@Composable
fun PlatinumGlassCard(modifier: Modifier = Modifier, reduced: Boolean = false, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.pressScale(reduced)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.045f))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, PlatinumColors.Champagne.copy(alpha = 0.05f))))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f), PlatinumColors.Champagne.copy(alpha = 0.25f))), shape)
            .padding(12.dp)
    ) { content() }
}
