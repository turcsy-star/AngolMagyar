package hu.angolmagyar.fordito.ui.processing

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.angolmagyar.fordito.ui.design.PlatinumColors

/** A teljes haladás időbeli menete – a mintákat a panel a valós %-értékekből gyűjti (nincs szimuláció). */
@Composable
fun PlatinumSparkline(samples: List<Pair<Long, Int>>, modifier: Modifier = Modifier) {
    if (samples.size < 2) return
    Column(modifier.fillMaxWidth()) {
        Text("HALADÁS IDŐBEN", color = PlatinumColors.Steel, fontSize = 9.sp, letterSpacing = 1.6.sp)
        Canvas(Modifier.fillMaxWidth().height(38.dp)) {
            val t0 = samples.first().first
            val span = (samples.last().first - t0).coerceAtLeast(1L).toFloat()
            fun pt(s: Pair<Long, Int>) = Offset(size.width * (s.first - t0) / span, size.height * (1f - s.second.coerceIn(0, 100) / 100f))
            // vezetősínek 25/50/75%
            for (q in 1..3) drawLine(PlatinumColors.SteelDark.copy(alpha = 0.3f), Offset(0f, size.height * q / 4f), Offset(size.width, size.height * q / 4f), 1.dp.toPx())
            val line = Path()
            samples.forEachIndexed { i, s -> val p = pt(s); if (i == 0) line.moveTo(p.x, p.y) else line.lineTo(p.x, p.y) }
            val fill = Path().apply {
                addPath(line); lineTo(size.width, size.height); lineTo(0f, size.height); close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(PlatinumColors.Champagne.copy(alpha = 0.30f), Color.Transparent)))
            drawPath(line, Brush.horizontalGradient(listOf(PlatinumColors.Steel, PlatinumColors.PlatinumHigh, PlatinumColors.Champagne)),
                style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            val e = pt(samples.last())
            drawCircle(PlatinumColors.Champagne.copy(alpha = 0.35f), 6.dp.toPx(), e)
            drawCircle(PlatinumColors.PlatinumHigh, 2.5.dp.toPx(), e)
        }
    }
}
