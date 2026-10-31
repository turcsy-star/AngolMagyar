package hu.angolmagyar.fordito.ui.processing

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.angolmagyar.fordito.ui.design.PlatinumColors
import hu.angolmagyar.fordito.ui.design.PremiumMotion
import androidx.compose.ui.platform.LocalContext
import kotlin.math.cos
import kotlin.math.sin

/**
 * Diamond Process Theater – v3.01
 *
 * Részletgazdag, prémium folyamatvizualizáció:
 * - aktuális művelet „hős” panele élő pulzussal
 * - lépésenkénti sín animált energiával
 * - aktív lépés részletei (progress, komment, élő szöveg)
 * - csökkentett mozgás támogatása
 */
@Composable
fun DiamondProcessTheater(
    state: ProcessingUiState,
    reduced: Boolean,
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val cinema = remember { PremiumMotion.cinemaEnabled(ctx) }
    val stages = state.stages
    if (stages.isEmpty() && !state.running && !state.finished) return

    val active = stages.firstOrNull {
        it.status == StageStatus.RUNNING || it.status == StageStatus.WARNING
    } ?: stages.lastOrNull { it.status == StageStatus.COMPLETED || it.status == StageStatus.FAILED }

    val pulse = if (reduced || !state.running) 0f else {
        val inf = rememberInfiniteTransition(label = "dpt_pulse")
        inf.animateFloat(
            0.35f, 1f,
            infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
            label = "p"
        ).value
    }
    val sweep = if (reduced || !state.running) 0f else {
        val inf = rememberInfiniteTransition(label = "dpt_sweep")
        inf.animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(2800, easing = LinearEasing)),
            label = "s"
        ).value
    }
    val flow = if (reduced || !state.running) 0f else {
        val inf = rememberInfiniteTransition(label = "dpt_flow")
        inf.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween(1600, easing = LinearEasing)),
            label = "f"
        ).value
    }

    val shape = RoundedCornerShape(20.dp)
    DiamondDepthStack(
        active = state.running,
        reduced = reduced,
        cinema = cinema,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        PlatinumColors.Champagne.copy(alpha = 0.35f),
                        PlatinumColors.IceBlue.copy(alpha = 0.15f),
                        PlatinumColors.SteelDark.copy(alpha = 0.4f)
                    )
                ),
                shape
            )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DIAMOND · FOLYAMAT",
                color = PlatinumColors.Champagne,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )
            val statusLabel = when {
                state.errorMessage != null -> "HIBA"
                state.cancelled -> "MEGSZAKÍTVA"
                state.finished -> "KÉSZ"
                state.running -> "FUT"
                else -> "VÁRAKOZÁS"
            }
            Text(
                statusLabel,
                color = when {
                    state.errorMessage != null -> PlatinumColors.Ruby
                    state.finished -> PlatinumColors.Emerald
                    state.running -> PlatinumColors.IceBlue
                    else -> PlatinumColors.Steel
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp
            )
        }

        // —— Aktuális művelet hős panel ——
        AnimatedContent(
            targetState = active?.title to active?.detail,
            transitionSpec = {
                (fadeIn(tween(if (reduced) 0 else 280)) + slideInVertically(tween(if (reduced) 0 else 320)) { it / 6 })
                    .togetherWith(fadeOut(tween(if (reduced) 0 else 180)))
            },
            label = "hero"
        ) { (title, detail) ->
            HeroOperationCard(
                title = title ?: if (state.finished) "Feldolgozás kész" else "Előkészítés…",
                detail = detail?.takeIf { it.isNotBlank() }
                    ?: state.engineLabel.takeIf { it.isNotBlank() }
                    ?: "A rendszer a következő lépésre vár",
                progress = active?.progress,
                running = state.running && active?.status == StageStatus.RUNNING,
                pulse = pulse,
                sweep = sweep,
                reduced = reduced,
                overallPercent = state.overallPercent,
                elapsedMs = state.elapsedMs,
                remainingMs = state.remainingMs
            )
        }

        // —— Lépés sín ——
        if (stages.isNotEmpty()) {
            StepEnergyRail(stages = stages, flow = flow, reduced = reduced)
        }

        // —— Lépéslista vizualizáció ——
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            stages.forEach { stage ->
                StageRowRich(stage = stage, isActive = stage.index == active?.index, reduced = reduced)
            }
        }

        // —— Élő tartalom ——
        AnimatedVisibility(
            visible = state.running && (state.heard.isNotBlank() || state.translated.isNotBlank()),
            enter = fadeIn(tween(if (reduced) 0 else 250)),
            exit = fadeOut(tween(if (reduced) 0 else 200))
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(PlatinumColors.Graphite.copy(alpha = 0.85f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("ÉLŐ JEL", color = PlatinumColors.Steel, fontSize = 9.sp, letterSpacing = 1.5.sp)
                if (state.heard.isNotBlank()) {
                    Text(
                        "„${state.heard}”",
                        color = PlatinumColors.Text,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (state.translated.isNotBlank()) {
                    Text(
                        "→ ${state.translated}",
                        color = PlatinumColors.IceBlue,
                        fontSize = 13.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // —— Meta sor ——
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MetaChip("SZEGMENS", metaSegments(state))
            if (state.fileTotal > 1) MetaChip("FÁJL", "${state.fileNo}/${state.fileTotal}")
            state.overallPercent?.let { MetaChip("ÖSSZES", "$it%") }
            if (state.translatedLines > 0) MetaChip("FORDÍTVA", "${state.translatedLines}")
            if (state.engineLabel.isNotBlank()) MetaChip("MOTOR", state.engineLabel.take(12))
        }
        }
    }
}

@Composable
private fun HeroOperationCard(
    title: String,
    detail: String,
    progress: Float?,
    running: Boolean,
    pulse: Float,
    sweep: Float,
    reduced: Boolean,
    overallPercent: Int?,
    elapsedMs: Long = 0L,
    remainingMs: Long? = null
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        PlatinumColors.GraphiteHigh,
                        PlatinumColors.Graphite.copy(alpha = 0.9f)
                    )
                )
            )
            .border(1.dp, PlatinumColors.SteelDark.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Chronograph: külső = összes %, belső = lépés progress
        Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(72.dp)) {
                val strokeOuter = 3.5.dp.toPx()
                val strokeInner = 3.dp.toPx()
                val rOuter = size.minDimension / 2f - strokeOuter
                val rInner = rOuter - 7.dp.toPx()
                drawCircle(PlatinumColors.SteelDark.copy(alpha = 0.45f), rOuter, style = Stroke(strokeOuter))
                drawCircle(PlatinumColors.SteelDark.copy(alpha = 0.35f), rInner, style = Stroke(strokeInner))
                val overall = (overallPercent?.div(100f) ?: if (running) 0.08f else 0f).coerceIn(0f, 1f)
                val stage = (progress ?: overall).coerceIn(0f, 1f)
                if (overall > 0f) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(PlatinumColors.Champagne, PlatinumColors.IceBlue, PlatinumColors.Champagne)
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * overall,
                        useCenter = false,
                        style = Stroke(strokeOuter, cap = StrokeCap.Round),
                        size = Size(rOuter * 2, rOuter * 2),
                        topLeft = Offset(center.x - rOuter, center.y - rOuter)
                    )
                }
                if (stage > 0f) {
                    drawArc(
                        color = PlatinumColors.Emerald.copy(alpha = 0.9f),
                        startAngle = -90f,
                        sweepAngle = 360f * stage,
                        useCenter = false,
                        style = Stroke(strokeInner, cap = StrokeCap.Round),
                        size = Size(rInner * 2, rInner * 2),
                        topLeft = Offset(center.x - rInner, center.y - rInner)
                    )
                }
                if (running && !reduced) {
                    rotate(sweep) {
                        val glowR = rOuter + 3.dp.toPx()
                        drawCircle(PlatinumColors.IceBlue.copy(alpha = 0.22f * pulse), glowR, style = Stroke(2.dp.toPx()))
                        val ang = Math.toRadians(0.0)
                        drawCircle(
                            PlatinumColors.Champagne.copy(alpha = 0.9f * pulse),
                            3.dp.toPx(),
                            Offset(center.x + glowR * cos(ang).toFloat(), center.y + glowR * sin(ang).toFloat())
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    overallPercent?.let { "$it%" } ?: if (running) "…" else "—",
                    color = PlatinumColors.Text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "AKTUÁLIS MŰVELET",
                color = PlatinumColors.Steel,
                fontSize = 9.sp,
                letterSpacing = 1.2.sp
            )
            Text(
                title,
                color = PlatinumColors.PlatinumHigh,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                color = PlatinumColors.TextDim,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (elapsedMs > 0L || remainingMs != null) {
                Text(
                    buildString {
                        if (elapsedMs > 0L) append("eltelt ${fmtMs(elapsedMs)}")
                        if (remainingMs != null) {
                            if (isNotEmpty()) append(" · ")
                            append("hátra ~${fmtMs(remainingMs)}")
                        }
                    },
                    color = PlatinumColors.Steel,
                    fontSize = 10.sp
                )
            }
            if (running) {
                // mikro progress sáv
                val animP by animateFloatAsState(
                    progress ?: 0.08f,
                    tween(if (reduced) 0 else PremiumMotion.STATE_CHANGE),
                    label = "hp"
                )
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .padding(top = 2.dp)
                ) {
                    drawRoundRect(
                        PlatinumColors.SteelDark.copy(alpha = 0.6f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                    val w = size.width * animP.coerceIn(0f, 1f)
                    if (w > 0) {
                        drawRoundRect(
                            Brush.horizontalGradient(
                                listOf(PlatinumColors.Champagne, PlatinumColors.IceBlue)
                            ),
                            size = Size(w, size.height),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepEnergyRail(
    stages: List<StageUi>,
    flow: Float,
    reduced: Boolean
) {
    val n = stages.size
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        if (n == 0) return@Canvas
        val cy = size.height / 2f
        val r = 7.dp.toPx()
        fun cx(i: Int) = size.width * (i + 0.5f) / n

        for (i in 0 until n - 1) {
            val x0 = cx(i) + r + 3.dp.toPx()
            val x1 = cx(i + 1) - r - 3.dp.toPx()
            drawLine(
                PlatinumColors.SteelDark.copy(alpha = 0.7f),
                Offset(x0, cy), Offset(x1, cy),
                3.dp.toPx(), StrokeCap.Round
            )
            val leftDone = stages[i].status == StageStatus.COMPLETED ||
                stages[i].status == StageStatus.WARNING ||
                stages[i].status == StageStatus.RUNNING
            if (leftDone) {
                val fill = when (stages[i].status) {
                    StageStatus.COMPLETED, StageStatus.WARNING -> 1f
                    StageStatus.RUNNING -> (stages[i].progress ?: 0.4f).coerceIn(0f, 1f)
                    else -> 0f
                }
                val xe = x0 + (x1 - x0) * fill
                drawLine(
                    Brush.horizontalGradient(
                        listOf(PlatinumColors.Champagne.copy(alpha = 0.85f), PlatinumColors.IceBlue.copy(alpha = 0.75f))
                    ),
                    Offset(x0, cy), Offset(xe, cy),
                    3.dp.toPx(), StrokeCap.Round
                )
                if (!reduced && stages[i].status == StageStatus.RUNNING && fill > 0.05f) {
                    val px = x0 + (xe - x0) * flow
                    drawCircle(PlatinumColors.PlatinumHigh.copy(alpha = 0.9f), 3.5.dp.toPx(), Offset(px, cy))
                    drawCircle(PlatinumColors.IceBlue.copy(alpha = 0.35f), 7.dp.toPx(), Offset(px, cy))
                }
            }
        }
        for (i in 0 until n) {
            val c = when (stages[i].status) {
                StageStatus.COMPLETED -> PlatinumColors.Emerald
                StageStatus.RUNNING -> PlatinumColors.IceBlue
                StageStatus.WARNING -> PlatinumColors.Amber
                StageStatus.FAILED -> PlatinumColors.Ruby
                StageStatus.CANCELLED -> PlatinumColors.Steel
                StageStatus.PENDING -> PlatinumColors.SteelDark
            }
            val center = Offset(cx(i), cy)
            if (stages[i].status == StageStatus.RUNNING && !reduced) {
                drawCircle(c.copy(alpha = 0.25f), r + 5.dp.toPx(), center)
            }
            drawCircle(PlatinumColors.Onyx, r + 1.dp.toPx(), center)
            drawCircle(c, r, center)
            if (stages[i].status == StageStatus.COMPLETED) {
                // mini check hint
                drawCircle(PlatinumColors.Onyx.copy(alpha = 0.35f), r * 0.35f, center)
            }
        }
    }
}

@Composable
private fun StageRowRich(stage: StageUi, isActive: Boolean, reduced: Boolean) {
    val bg = if (isActive) PlatinumColors.GraphiteHigh.copy(alpha = 0.9f) else Color.Transparent
    val borderAlpha = if (isActive) 0.45f else 0.12f
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(1.dp, PlatinumColors.SteelDark.copy(alpha = borderAlpha), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val dot = when (stage.status) {
            StageStatus.COMPLETED -> PlatinumColors.Emerald
            StageStatus.RUNNING -> PlatinumColors.IceBlue
            StageStatus.WARNING -> PlatinumColors.Amber
            StageStatus.FAILED -> PlatinumColors.Ruby
            StageStatus.CANCELLED -> PlatinumColors.Steel
            StageStatus.PENDING -> PlatinumColors.SteelDark
        }
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dot)
                .then(
                    if (isActive && stage.status == StageStatus.RUNNING && !reduced) {
                        Modifier.drawBehind {
                            drawCircle(dot.copy(alpha = 0.35f), radius = size.minDimension)
                        }
                    } else Modifier
                )
        )
        Column(Modifier.weight(1f)) {
            Text(
                stage.title.ifBlank { "Lépés ${stage.index + 1}" },
                color = if (isActive) PlatinumColors.PlatinumHigh else PlatinumColors.TextDim,
                fontSize = 13.sp,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
            )
            if (stage.detail.isNotBlank()) {
                Text(
                    stage.detail,
                    color = PlatinumColors.Steel,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        val pct = stage.progress
        Text(
            when (stage.status) {
                StageStatus.COMPLETED -> if (stage.durationMs > 0) fmtMs(stage.durationMs) else "kész"
                StageStatus.RUNNING -> pct?.let { "${(it * 100).toInt()}%" } ?: "…"
                StageStatus.WARNING -> "figyelmeztetés"
                StageStatus.FAILED -> "hiba"
                StageStatus.CANCELLED -> "megszakítva"
                StageStatus.PENDING -> "vár"
            },
            color = dot,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MetaChip(label: String, value: String) {
    Column(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PlatinumColors.Graphite.copy(alpha = 0.8f))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = PlatinumColors.Steel, fontSize = 8.sp, letterSpacing = 0.8.sp)
        Text(value, color = PlatinumColors.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun metaSegments(state: ProcessingUiState): String {
    val total = state.chunksTotal
    return if (total != null) "${state.chunksDone}/$total" else "${state.chunksDone}"
}

private fun fmtMs(ms: Long): String {
    val s = ms / 1000
    return if (s < 60) "${s}s" else "${s / 60}m ${s % 60}s"
}
