package hu.angolmagyar.fordito.ui.processing

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.angolmagyar.fordito.ui.design.PlatinumColors
import kotlin.math.PI
import kotlin.math.sin

internal fun jewelColor(s: StageStatus): Color = when (s) {
    StageStatus.PENDING -> PlatinumColors.GraphiteHigh
    StageStatus.RUNNING -> PlatinumColors.Champagne
    StageStatus.COMPLETED -> PlatinumColors.Emerald
    StageStatus.WARNING -> PlatinumColors.Amber
    StageStatus.FAILED -> PlatinumColors.Ruby
    StageStatus.CANCELLED -> PlatinumColors.Steel
}

internal fun fmtDurationShort(ms: Long): String {
    val s = ms / 1000
    return if (s < 60) "$s mp" else "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

internal fun subLabel(s: StageUi): String = when (s.status) {
    StageStatus.PENDING -> ""
    StageStatus.RUNNING -> s.progress?.let { "${(it * 100).toInt()}%" } ?: "…"
    StageStatus.COMPLETED, StageStatus.WARNING -> if (s.durationMs > 0) fmtDurationShort(s.durationMs) else "kész"
    StageStatus.FAILED -> "hiba"
    StageStatus.CANCELLED -> "megszakítva"
}

/** Platina folyamatvonal: ékköves csomópontok, bevésett (gravírozott) sín, fényes csillanás a kitöltésen. */
@Composable
fun PlatinumTimeline(stages: List<StageUi>, reduced: Boolean, modifier: Modifier = Modifier) {
    if (stages.isEmpty()) return
    val n = stages.size
    val ms = if (reduced) 0 else 320

    val phase = if (reduced) 0f else {
        rememberInfiniteTransition(label = "pt").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "ph"
        ).value
    }
    val colors = stages.map { animateColorAsState(jewelColor(it.status), tween(ms), label = "jc").value }
    val checks = stages.map {
        animateFloatAsState(
            if (it.status == StageStatus.COMPLETED) 1f else 0f, tween(if (reduced) 0 else 450), label = "ck"
        ).value
    }
    val fills = stages.map {
        animateFloatAsState(
            when (it.status) {
                StageStatus.COMPLETED, StageStatus.WARNING -> 1f
                StageStatus.RUNNING -> it.progress ?: 0f
                else -> 0f
            }, tween(if (reduced) 0 else 600), label = "pf"
        ).value
    }

    Column(modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(70.dp)) {
            val cy = size.height / 2f
            val r = 13.dp.toPx()
            fun cx(i: Int) = size.width * (i + 0.5f) / n

            for (i in 0 until n - 1) {
                val x0 = cx(i) + r + 5.dp.toPx()
                val x1 = cx(i + 1) - r - 5.dp.toPx()
                // bevésett sín: sötét horony + vékony fényes perem
                drawLine(PlatinumColors.Onyx, Offset(x0, cy), Offset(x1, cy), 5.dp.toPx(), StrokeCap.Round)
                drawLine(PlatinumColors.SteelDark.copy(alpha = 0.55f), Offset(x0, cy + 2.6.dp.toPx()), Offset(x1, cy + 2.6.dp.toPx()), 1.dp.toPx(), StrokeCap.Round)
                val f = fills[i].coerceIn(0f, 1f)
                if (f > 0f) {
                    val xe = x0 + (x1 - x0) * f
                    drawLine(
                        Brush.horizontalGradient(listOf(PlatinumColors.Steel, PlatinumColors.PlatinumHigh, PlatinumColors.Platinum), x0, xe.coerceAtLeast(x0 + 1f)),
                        Offset(x0, cy), Offset(xe, cy), 3.dp.toPx(), StrokeCap.Round
                    )
                    if (stages[i].status == StageStatus.RUNNING && !reduced) {
                        // csillanás: fényes sáv fut végig a kitöltött szakaszon
                        val sx = x0 + (xe - x0) * phase
                        val w = 18.dp.toPx()
                        drawLine(
                            Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.85f), Color.Transparent), sx - w, sx + w),
                            Offset((sx - w).coerceAtLeast(x0), cy), Offset((sx + w).coerceAtMost(xe), cy), 3.dp.toPx(), StrokeCap.Round
                        )
                    }
                }
                // osztásjelek a sínen (bevésett milestone-ok)
                for (k in 1..3) {
                    val tx = x0 + (x1 - x0) * k / 4f
                    drawLine(PlatinumColors.SteelDark.copy(alpha = 0.8f), Offset(tx, cy - 6.dp.toPx()), Offset(tx, cy - 3.dp.toPx()), 1.dp.toPx())
                }
            }
            for (i in 0 until n) jewel(stages[i].status, colors[i], checks[i], cx(i), cy, r, phase, reduced)
        }
        Row(Modifier.fillMaxWidth()) {
            stages.forEach { s ->
                Column(
                    Modifier.weight(1f).semantics { contentDescription = "${s.title}: ${subLabel(s).ifBlank { "vár" }}" },
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                ) {
                    Text(
                        s.title, textAlign = TextAlign.Center, maxLines = 2, fontSize = 10.sp,
                        color = if (s.status == StageStatus.PENDING) PlatinumColors.TextDim else PlatinumColors.Text,
                        fontWeight = if (s.status == StageStatus.RUNNING) FontWeight.SemiBold else FontWeight.Normal
                    )
                    val sub = subLabel(s)
                    if (sub.isNotEmpty()) Text(
                        sub, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1,
                        color = when (s.status) {
                            StageStatus.RUNNING -> PlatinumColors.Champagne
                            StageStatus.FAILED -> PlatinumColors.Ruby
                            StageStatus.WARNING -> PlatinumColors.Amber
                            else -> PlatinumColors.TextDim
                        }
                    )
                }
            }
        }
    }
}

internal fun DrawScope.jewel(status: StageStatus, color: Color, check: Float, x: Float, y: Float, r: Float, phase: Float, reduced: Boolean) {
    val c = Offset(x, y)
    val running = status == StageStatus.RUNNING

    if (running) {
        val br = if (reduced) 0.5f else 0.5f + 0.5f * sin(phase * 2f * PI.toFloat())
        val gr = r * (2.1f + 0.2f * br)
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.30f + 0.12f * br), Color.Transparent), c, gr), gr, c)
    }
    // fémes keret (foglalat)
    drawCircle(
        Brush.linearGradient(
            listOf(PlatinumColors.PlatinumHigh, PlatinumColors.Steel, PlatinumColors.SteelDark),
            Offset(x - r, y - r), Offset(x + r, y + r)
        ), r + 3.dp.toPx(), c
    )
    drawCircle(PlatinumColors.Onyx, r + 0.6.dp.toPx(), c)

    // ékkő
    if (status == StageStatus.PENDING) {
        drawCircle(PlatinumColors.Graphite, r - 1.dp.toPx(), c)
        drawCircle(PlatinumColors.SteelDark.copy(alpha = 0.6f), r - 1.dp.toPx(), c, style = Stroke(1.dp.toPx()))
        return
    }
    val hi = lerp(color, Color.White, 0.5f)
    val lo = lerp(color, Color.Black, 0.45f)
    drawCircle(
        Brush.radialGradient(listOf(hi, color, lo), Offset(x - r * 0.3f, y - r * 0.35f), r * 1.35f),
        r - 1.dp.toPx(), c
    )
    drawCircle(Color.White.copy(alpha = 0.55f), r * 0.17f, Offset(x - r * 0.38f, y - r * 0.42f)) // csillanás

    val sw = 2.2.dp.toPx()
    val ink = PlatinumColors.Onyx.copy(alpha = 0.85f)
    when (status) {
        StageStatus.RUNNING -> {
            if (!reduced) {
                val ar = r - 4.dp.toPx()
                for (s in 0 until 6) {
                    drawArc(
                        Color.White.copy(alpha = 0.9f * (1f - s / 6f)), phase * 360f - s * 14f, 14f, false,
                        Offset(x - ar, y - ar), Size(ar * 2, ar * 2), style = Stroke(sw, cap = StrokeCap.Round)
                    )
                }
                val t = phase
                val e = 1f - (1f - t) * (1f - t)
                drawCircle(color.copy(alpha = (1f - t) * (1f - t) * 0.5f), r + 14.dp.toPx() * e, c, style = Stroke(1.6.dp.toPx()))
            }
        }
        StageStatus.COMPLETED -> {
            val p1 = Offset(x - 5.dp.toPx(), y); val p2 = Offset(x - 1.5.dp.toPx(), y + 4.dp.toPx()); val p3 = Offset(x + 6.dp.toPx(), y - 4.5.dp.toPx())
            val a = (check * 2f).coerceIn(0f, 1f); val b = (check * 2f - 1f).coerceIn(0f, 1f)
            drawLine(ink, p1, Offset(p1.x + (p2.x - p1.x) * a, p1.y + (p2.y - p1.y) * a), sw, StrokeCap.Round)
            if (b > 0f) drawLine(ink, p2, Offset(p2.x + (p3.x - p2.x) * b, p2.y + (p3.y - p2.y) * b), sw, StrokeCap.Round)
        }
        StageStatus.WARNING -> {
            drawLine(ink, Offset(x, y - 5.dp.toPx()), Offset(x, y + 1.dp.toPx()), sw, StrokeCap.Round)
            drawCircle(ink, 1.3.dp.toPx(), Offset(x, y + 5.dp.toPx()))
        }
        StageStatus.FAILED -> {
            val d = 4.5.dp.toPx()
            drawLine(ink, Offset(x - d, y - d), Offset(x + d, y + d), sw, StrokeCap.Round)
            drawLine(ink, Offset(x + d, y - d), Offset(x - d, y + d), sw, StrokeCap.Round)
        }
        StageStatus.CANCELLED -> {
            drawLine(ink, Offset(x - 3.dp.toPx(), y - 4.dp.toPx()), Offset(x - 3.dp.toPx(), y + 4.dp.toPx()), sw, StrokeCap.Round)
            drawLine(ink, Offset(x + 3.dp.toPx(), y - 4.dp.toPx()), Offset(x + 3.dp.toPx(), y + 4.dp.toPx()), sw, StrokeCap.Round)
        }
        else -> {}
    }
}
