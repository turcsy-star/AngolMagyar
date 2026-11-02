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
@Suppress("UNUSED_PARAMETER")
fun Modifier.pressScale(reduced: Boolean = false): Modifier = this
@Composable
fun PlatinumGlassCard(modifier: Modifier = Modifier, reduced: Boolean = false, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier.pressScale(reduced)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.045f))
            .background(Brush.linearGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent, PlatinumColors.DiamondWhite.copy(alpha = 0.05f))))
            .border(1.dp, Brush.linearGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.05f), PlatinumColors.DiamondWhite.copy(alpha = 0.25f))), shape)
            .padding(12.dp)
    ) { content() }
}
