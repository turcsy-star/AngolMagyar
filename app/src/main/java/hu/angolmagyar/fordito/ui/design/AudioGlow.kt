package hu.angolmagyar.fordito.ui.design
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import hu.angolmagyar.fordito.AppState
import kotlinx.coroutines.delay
import kotlin.math.abs
/**
 * A valós mikrofon/belső hang szintjéből (AppState.level, ugyanaz, mint a waveformé) számolt, simított glow-érték.
 * Gyors felfutás, lassú lecsengés. Reduced motion alatt mindig 0, tehát nincs glow és nincs ciklus.
 * Csak akkor ír állapotot, ha az érték érdemben változott, így nem invalidál feleslegesen.
 */
@Composable
fun rememberAudioGlow(reduced: Boolean): State<Float> {
    val glow = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(reduced) {
        if (reduced) {
            glow.floatValue = 0f
            return@LaunchedEffect
        }
        while (true) {
            val target = AppState.level.coerceIn(0f, 1f)
            val cur = glow.floatValue
            val next = if (target > cur) cur + (target - cur) * 0.55f else cur + (target - cur) * 0.12f
            if (abs(next - cur) > 0.003f) glow.floatValue = next
            else if (target == 0f && cur != 0f && cur < 0.02f) glow.floatValue = 0f
            delay(33L)
        }
    }
    return glow
}
/**
 * Hangszint-vezérelt ambient fény: alulról felfelé táguló sugaras bloom + világosodó, vastagodó keret.
 * A State-et csak a rajzolási fázisban olvassa, így a szintváltozás nem okoz recompositiont.
 * A láncban a .clip(shape).background(...) UTÁN kell állnia.
 */
fun Modifier.audioGlow(
    level: State<Float>,
    color: Color = PlatinumColors.DiamondWhite,
    corner: Dp = 18.dp
): Modifier = drawBehind {
    val l = level.value
    if (l < 0.02f) return@drawBehind
    val e = l * l * (3f - 2f * l)
    val tint = lerp(color, Color.White, 0.35f * e)
    val w = size.width
    val h = size.height
    val reach = maxOf(w, h) * (0.45f + 0.5f * e)
    drawRect(
        Brush.radialGradient(
            listOf(tint.copy(alpha = 0.10f + 0.34f * e), Color.Transparent), Offset(w / 2f, h), reach
        )
    )
    drawRoundRect(
        tint.copy(alpha = 0.10f + 0.55f * e),
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(1.dp.toPx() + 1.2.dp.toPx() * e)
    )
}
