package hu.angolmagyar.fordito.ui.processing

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.angolmagyar.fordito.AppState
import hu.angolmagyar.fordito.TranslateService
import hu.angolmagyar.fordito.ui.design.PlatinumColors
import hu.angolmagyar.fordito.ui.design.PremiumMotion
import kotlinx.coroutines.delay

private const val BARS = 48

/**
 * Élő hangszintmérő: 48 oszlop, balra gördülő előzmény. Minden oszlop a [AppState.level] egy korábbi
 * valós mintája (nincs kitalált hullámzás); csendben csak egy vékony pont látszik.
 */
@Composable
fun PlatinumLevelMeter(reduced: Boolean, modifier: Modifier = Modifier) {
    val bars = remember { FloatArray(BARS) }
    var frame by remember { mutableIntStateOf(0) }
    var peak by remember { mutableIntStateOf(0) } // 0..100, lassan lecsengő csúcsjelző

    LaunchedEffect(Unit) {
        while (true) {
            val lvl = AppState.level.coerceIn(0f, 1f)
            val last = bars[BARS - 1]
            // gyors felfutás, lassú lecsengés
            val v = if (lvl >= last) lvl else last * 0.8f + lvl * 0.2f
            System.arraycopy(bars, 1, bars, 0, BARS - 1)
            bars[BARS - 1] = v
            val p = (v * 100).toInt()
            peak = if (p >= peak) p else (peak - 1).coerceAtLeast(0)
            frame++
            delay(if (reduced) 90L else 40L)
        }
    }

    val running = AppState.running
    val paused = AppState.paused
    val source = when (AppState.mode) {
        TranslateService.MODE_INTERNAL -> "BELSŐ HANG"
        TranslateService.MODE_PTT -> "MIKROFON · PUSH-TO-TALK"
        TranslateService.MODE_MIC -> "MIKROFON"
        else -> "–"
    }
    val now = (bars[BARS - 1] * 100).toInt()
    val state = when { !running -> "KÉSZENLÉT"; paused -> "SZÜNET"; now < 2 -> "CSEND"; else -> "FIGYEL" }

    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.verticalGradient(listOf(PlatinumColors.GraphiteHigh, PlatinumColors.Onyx)))
            .border(1.dp, Brush.linearGradient(listOf(PlatinumColors.PlatinumHigh.copy(alpha = 0.45f), PlatinumColors.SteelDark.copy(alpha = 0.2f), PlatinumColors.Champagne.copy(alpha = 0.35f))), shape)
            .padding(12.dp)
            .semantics { contentDescription = "Hangszint: $now százalék, $source, $state" },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(source, color = PlatinumColors.Champagne, fontSize = 10.sp, letterSpacing = 1.8.sp, modifier = Modifier.weight(1f))
            Text(state, color = if (state == "FIGYEL") PlatinumColors.Emerald else PlatinumColors.TextDim, fontSize = 10.sp, letterSpacing = 1.4.sp)
        }
        Canvas(Modifier.fillMaxWidth().height(56.dp)) {
            frame.let { } // az újrarajzolás kulcsa
            val slot = size.width / BARS
            val bw = slot * 0.56f
            val mid = size.height / 2f
            // középvonal
            drawLine(PlatinumColors.SteelDark.copy(alpha = 0.5f), Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx())
            for (i in 0 until BARS) {
                val v = bars[i]
                val half = (v * (size.height / 2f - 2.dp.toPx())).coerceAtLeast(1.2.dp.toPx())
                val x = i * slot + (slot - bw) / 2f
                val age = i / (BARS - 1f) // az újabb minták fényesebbek
                val top = if (v > 0.66f) PlatinumColors.Champagne else PlatinumColors.PlatinumHigh
                drawRoundRect(
                    Brush.verticalGradient(listOf(top.copy(alpha = 0.35f + 0.65f * age), PlatinumColors.Steel.copy(alpha = 0.25f + 0.5f * age)), mid - half, mid + half),
                    Offset(x, mid - half), Size(bw, half * 2f), CornerRadius(bw / 2f)
                )
            }
        }
        Row(Modifier.fillMaxWidth()) {
            Text("SZINT", color = PlatinumColors.Steel, fontSize = 9.sp, letterSpacing = 1.6.sp, modifier = Modifier.weight(1f))
            Text("$now%  ·  csúcs $peak%", color = PlatinumColors.Text, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

/** View-burkoló a View-alapú Élő fordítás képernyőhöz. */
class PlatinumLevelView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : FrameLayout(ctx, attrs) {
    init {
        val reduced = PremiumMotion.reduced(ctx)
        addView(ComposeView(ctx).apply { setContent { PlatinumLevelMeter(reduced) } },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }
}
