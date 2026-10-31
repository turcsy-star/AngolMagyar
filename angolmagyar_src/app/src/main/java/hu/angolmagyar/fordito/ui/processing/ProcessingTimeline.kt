package hu.angolmagyar.fordito.ui.processing

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.angolmagyar.fordito.ui.design.PremiumColors
import hu.angolmagyar.fordito.ui.design.PremiumMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private fun statusColor(s: StageStatus): Color = when (s) {
    StageStatus.PENDING -> PremiumColors.BrightBlue.copy(alpha = 0.35f)
    StageStatus.RUNNING -> PremiumColors.CyanGlow
    StageStatus.COMPLETED -> PremiumColors.Success
    StageStatus.WARNING -> PremiumColors.Warning
    StageStatus.FAILED -> PremiumColors.Error
    StageStatus.CANCELLED -> PremiumColors.Cancelled
}

private fun statusWord(s: StageStatus) = when (s) {
    StageStatus.PENDING -> "vár"
    StageStatus.RUNNING -> "fut"
    StageStatus.COMPLETED -> "kész"
    StageStatus.WARNING -> "figyelmeztetés"
    StageStatus.FAILED -> "hiba"
    StageStatus.CANCELLED -> "megszakítva"
}

/**
 * Öt csomópontos folyamatvonal egyetlen Canvas-on. A végtelen animáció csak az aktív szakaszra fut,
 * és nem indul újra a haladás- vagy szövegfrissítésre (a kulcsa nem az adat).
 */
@Composable
fun ProcessingTimeline(stages: List<StageUi>, reduced: Boolean, modifier: Modifier = Modifier) {
    if (stages.isEmpty()) return
    val n = stages.size
    val ctx = LocalContext.current
    val stateMs = if (reduced) 0 else PremiumMotion.STATE_CHANGE

    val phase = if (reduced) 0f else {
        val t = rememberInfiniteTransition(label = "flow")
        t.animateFloat(
            initialValue = 0f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(PremiumMotion.FLOW_LOOP, easing = LinearEasing)),
            label = "phase"
        ).value
    }

    val colors = stages.map { animateColorAsState(statusColor(it.status), tween(stateMs), label = "c").value }
    val checks = stages.map {
        animateFloatAsState(
            if (it.status == StageStatus.COMPLETED || it.status == StageStatus.WARNING) 1f else 0f,
            tween(if (reduced) 0 else PremiumMotion.NODE_ACTIVATE), label = "chk"
        ).value
    }
    val fills = stages.map {
        animateFloatAsState(
            when (it.status) {
                StageStatus.COMPLETED, StageStatus.WARNING -> 1f
                StageStatus.RUNNING -> it.progress ?: 0f
                else -> 0f
            },
            tween(if (reduced) 0 else 600), label = "fill"
        ).value
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(72.dp)
        ) {
            val cy = size.height / 2f
            val r = 13.dp.toPx()
            fun cx(i: Int) = size.width * (i + 0.5f) / n

            // összekötők
            for (i in 0 until n - 1) {
                val x0 = cx(i) + r + 3.dp.toPx()
                val x1 = cx(i + 1) - r - 3.dp.toPx()
                drawLine(PremiumColors.BrightBlue.copy(alpha = 0.18f), Offset(x0, cy), Offset(x1, cy),
                    strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                val st = stages[i].status
                val f = fills[i].coerceIn(0f, 1f)
                if (f > 0f) {
                    val col = if (st == StageStatus.RUNNING) PremiumColors.CyanGlow.copy(alpha = 0.6f) else colors[i]
                    drawLine(col, Offset(x0, cy), Offset(x0 + (x1 - x0) * f, cy),
                        strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
                }
                if (st == StageStatus.RUNNING && !reduced) comet(x0, x1, cy, phase)
            }

            // csomópontok
            for (i in 0 until n) drawNode(stages[i].status, colors[i], checks[i], cx(i), cy, r, phase, reduced)
        }

        Row(Modifier.fillMaxWidth()) {
            stages.forEach { s ->
                Text(
                    s.title,
                    modifier = Modifier.weight(1f).semantics { contentDescription = "${s.title}: ${statusWord(s.status)}" },
                    color = if (s.status == StageStatus.PENDING) PremiumColors.TextSecondary else PremiumColors.IceWhite,
                    fontSize = 10.5.sp,
                    fontWeight = if (s.status == StageStatus.RUNNING) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
    }
}

/** Mozgó fénypontok az aktív szakaszon (elhalványuló farokkal). */
private fun DrawScope.comet(x0: Float, x1: Float, cy: Float, phase: Float) {
    for (k in 0 until 2) {
        val f0 = (phase + k * 0.5f) % 1f
        val edge = sin(PI.toFloat() * f0)
        for (tl in 0 until 7) {
            val f = (f0 - tl * 0.022f).coerceAtLeast(0f)
            val e = 0.5f - 0.5f * cos(PI.toFloat() * f)
            val a = (edge * (1f - tl / 7f)).coerceIn(0f, 1f)
            drawCircle(PremiumColors.CyanGlow.copy(alpha = a), (3.4f - tl * 0.28f).dp.toPx(), Offset(x0 + (x1 - x0) * e, cy))
        }
    }
}

private fun DrawScope.drawNode(
    status: StageStatus, color: Color, check: Float,
    x: Float, y: Float, r: Float, phase: Float, reduced: Boolean
) {
    val c = Offset(x, y)
    val stroke = 2.4.dp.toPx()
    when (status) {
        StageStatus.PENDING -> drawCircle(color, r, c, style = Stroke(2.dp.toPx()))
        StageStatus.RUNNING -> {
            val br = if (reduced) 0.5f else 0.5f + 0.5f * sin(phase * 2f * PI.toFloat())
            val gr = r * (2f + 0.25f * br)
            drawCircle(Brush.radialGradient(
                0.35f to color.copy(alpha = 0.28f + 0.12f * br), 1f to color.copy(alpha = 0f), center = c, radius = gr
            ), gr, c)
            if (!reduced) for (k in 0 until 2) {
                val t = (phase + k * 0.5f) % 1f
                val e = 1f - (1f - t) * (1f - t)
                drawCircle(color.copy(alpha = ((1f - t) * (1f - t) * 0.5f)), r + 14.dp.toPx() * e, c, style = Stroke(1.8.dp.toPx()))
            }
            drawCircle(color, r, c)
            val ar = r - 4.5.dp.toPx()
            val deg = if (reduced) 0f else phase * 360f
            for (s in 0 until 6) {
                drawArc(Color.White.copy(alpha = 1f - s / 6f), deg - s * 14f, 14f, false,
                    Offset(x - ar, y - ar), androidx.compose.ui.geometry.Size(ar * 2, ar * 2),
                    style = Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        StageStatus.COMPLETED, StageStatus.WARNING -> {
            drawCircle(color, r, c)
            if (status == StageStatus.COMPLETED) {
                val p1 = Offset(x - 5.dp.toPx(), y); val p2 = Offset(x - 1.5.dp.toPx(), y + 4.dp.toPx()); val p3 = Offset(x + 6.dp.toPx(), y - 4.5.dp.toPx())
                val a = (check * 2f).coerceIn(0f, 1f); val b = (check * 2f - 1f).coerceIn(0f, 1f)
                drawLine(Color.White, p1, Offset(p1.x + (p2.x - p1.x) * a, p1.y + (p2.y - p1.y) * a), stroke, StrokeCap.Round)
                if (b > 0f) drawLine(Color.White, p2, Offset(p2.x + (p3.x - p2.x) * b, p2.y + (p3.y - p2.y) * b), stroke, StrokeCap.Round)
            } else bang(x, y, stroke)
        }
        StageStatus.FAILED -> {
            drawCircle(color, r, c)
            val d = 4.5.dp.toPx()
            drawLine(Color.White, Offset(x - d, y - d), Offset(x + d, y + d), stroke, StrokeCap.Round)
            drawLine(Color.White, Offset(x + d, y - d), Offset(x - d, y + d), stroke, StrokeCap.Round)
        }
        StageStatus.CANCELLED -> {
            drawCircle(color, r, c)
            drawLine(Color.White, Offset(x - 3.dp.toPx(), y - 4.dp.toPx()), Offset(x - 3.dp.toPx(), y + 4.dp.toPx()), stroke, StrokeCap.Round)
            drawLine(Color.White, Offset(x + 3.dp.toPx(), y - 4.dp.toPx()), Offset(x + 3.dp.toPx(), y + 4.dp.toPx()), stroke, StrokeCap.Round)
        }
    }
}

private fun DrawScope.bang(x: Float, y: Float, stroke: Float) {
    val dark = PremiumColors.DeepNavy
    drawLine(dark, Offset(x, y - 5.dp.toPx()), Offset(x, y + 1.dp.toPx()), stroke, StrokeCap.Round)
    drawCircle(dark, 1.4.dp.toPx(), Offset(x, y + 5.dp.toPx()))
}
