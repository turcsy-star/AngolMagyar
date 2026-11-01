package hu.angolmagyar.fordito.ui.processing

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.angolmagyar.fordito.AppState
import hu.angolmagyar.fordito.Haptics
import hu.angolmagyar.fordito.Prefs
import hu.angolmagyar.fordito.ui.design.PlatinumColors
import hu.angolmagyar.fordito.ui.design.PremiumMotion
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

private const val BARS = 56

/**
 * Élő fordítás – Diamond színház (v3.02)
 *
 * Valós hangszint + fázis (Figyel / Felismer / Fordít / Szünet) + spektrum + élő szövegcsík.
 * Nincs kitalált hang; az oszlopok az [AppState.level] történetéből épülnek.
 */
@Composable
fun DiamondLiveTheater(reduced: Boolean, modifier: Modifier = Modifier) {
    // Kotlin 2.0.21 Compose IR may lose the parameter mapping when this value is
    // captured directly by generated animation/effect lambdas. Keep a stable State.
    val reducedMotion = rememberUpdatedState(reduced)
    val ctx = LocalContext.current
    val cinema = remember(ctx) { PremiumMotion.cinemaEnabled(ctx) }
    val bars = remember { FloatArray(BARS) }
    var frame by remember { mutableIntStateOf(0) }
    var smoothLevel by remember { mutableFloatStateOf(0f) }
    var peak by remember { mutableIntStateOf(0) }
    var prevPhase by remember { mutableStateOf<LivePhase?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            val lvl = AppState.level.coerceIn(0f, 1f)
            val last = bars[BARS - 1]
            val v = if (lvl >= last) lvl else last * 0.82f + lvl * 0.18f
            System.arraycopy(bars, 1, bars, 0, BARS - 1)
            bars[BARS - 1] = v
            smoothLevel = smoothLevel * 0.7f + lvl * 0.3f
            val pct = (lvl * 100).toInt()
            if (pct > peak) peak = pct else peak = (peak * 0.92f).toInt()
            frame++
            delay(if (reducedMotion.value) 80L else 42L)
        }
    }

    val phase = rememberLivePhase(smoothLevel)
    LaunchedEffect(phase) {
        if (prevPhase != null && prevPhase != phase) {
            Haptics.tick(ctx)
        }
        prevPhase = phase
    }
    val silence = phase == LivePhase.LISTENING && smoothLevel < 0.06f
    val spectrumScale by animateFloatAsState(
        targetValue = if (silence && cinema) 0.55f else 1f,
        animationSpec = tween(if (reduced) 0 else 500),
        label = "silenceScale"
    )
    val pulse = if (reduced) 0.6f else {
        val inf = rememberInfiniteTransition(label = "dl_pulse")
        inf.animateFloat(
            0.4f, 1f,
            infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Reverse),
            label = "p"
        ).value
    }
    val ringSpin = if (reduced) 0f else {
        val inf = rememberInfiniteTransition(label = "dl_ring")
        inf.animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(3200, easing = LinearEasing)),
            label = "r"
        ).value
    }

    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(PlatinumColors.GraphiteHigh, PlatinumColors.Onyx.copy(alpha = 0.96f))
                )
            )
            .border(
                1.dp,
                Brush.linearGradient(
                    listOf(
                        PlatinumColors.Champagne.copy(alpha = 0.4f),
                        PlatinumColors.IceBlue.copy(alpha = 0.2f),
                        PlatinumColors.SteelDark.copy(alpha = 0.45f)
                    )
                ),
                shape
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "DIAMOND · ÉLŐ JEL",
                color = PlatinumColors.Champagne,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Medium
            )
            PhaseBadge(phase, pulse)
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PhaseOrb(phase = phase, level = smoothLevel, spin = ringSpin, pulse = pulse, reduced = reduced)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimatedContent(
                    targetState = phase,
                    transitionSpec = {
                        (fadeIn(tween(if (reducedMotion.value) 0 else 220)) + slideInVertically { it / 5 })
                            .togetherWith(fadeOut(tween(if (reducedMotion.value) 0 else 160)))
                    },
                    label = "phaseTitle"
                ) { p ->
                    Text(
                        phaseTitle(p),
                        color = PlatinumColors.PlatinumHigh,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    phaseDetail(phase),
                    color = PlatinumColors.TextDim,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "szint ${(smoothLevel * 100).toInt()}%  ·  csúcs $peak%",
                    color = PlatinumColors.Steel,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Spektrum
        if (silence) {
            Text(
                "Várakozás a beszédre…",
                color = PlatinumColors.Steel,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height((72 * spectrumScale).dp)
                .clip(RoundedCornerShape(12.dp))
                .background(PlatinumColors.Graphite.copy(alpha = 0.65f))
                .padding(horizontal = 4.dp)
        ) {
            frame.let { }
            val slot = size.width / BARS
            val bw = slot * 0.55f
            val mid = size.height / 2f
            drawLine(
                PlatinumColors.SteelDark.copy(alpha = 0.45f),
                Offset(0f, mid), Offset(size.width, mid), 1.dp.toPx()
            )
            for (i in 0 until BARS) {
                val v = bars[i]
                val half = (v * (size.height / 2f - 3.dp.toPx())).coerceAtLeast(1.1.dp.toPx())
                val x = i * slot + (slot - bw) / 2f
                val age = i / (BARS - 1f)
                val active = phase == LivePhase.LISTENING || phase == LivePhase.RECOGNIZING
                val top = when {
                    v > 0.7f -> PlatinumColors.Champagne
                    active -> PlatinumColors.IceBlue
                    else -> PlatinumColors.PlatinumHigh
                }
                drawRoundRect(
                    Brush.verticalGradient(
                        listOf(
                            top.copy(alpha = 0.3f + 0.7f * age),
                            PlatinumColors.Steel.copy(alpha = 0.2f + 0.45f * age)
                        ),
                        mid - half,
                        mid + half
                    ),
                    Offset(x, mid - half),
                    Size(bw, half * 2f),
                    CornerRadius(bw / 2f)
                )
            }
        }

        // Fázis lépések
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LivePhase.entries.forEach { p ->
                val on = p == phase
                val done = phaseOrder(p) < phaseOrder(phase)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            when {
                                on -> PlatinumColors.GraphiteHigh
                                done -> PlatinumColors.Graphite.copy(alpha = 0.5f)
                                else -> PlatinumColors.Graphite.copy(alpha = 0.25f)
                            }
                        )
                        .border(
                            1.dp,
                            if (on) phaseColor(p).copy(alpha = 0.55f) else PlatinumColors.SteelDark.copy(alpha = 0.25f),
                            RoundedCornerShape(10.dp)
                        )
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (on || done) phaseColor(p) else PlatinumColors.SteelDark)
                    )
                    Text(
                        phaseShort(p),
                        color = if (on) PlatinumColors.Text else PlatinumColors.Steel,
                        fontSize = 9.sp,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        // Broadcast lower-third / élő szöveg
        DiamondLowerThird(
            visible = AppState.lastCaptionEn.isNotBlank() || AppState.lastCaptionHu.isNotBlank(),
            phaseLabel = phaseTitle(phase),
            primary = AppState.lastCaptionEn,
            secondary = AppState.lastCaptionHu.let { if (it.isNotBlank()) "→ $it" else "" },
            reduced = reduced,
            cinema = cinema
        )
    }
}

private enum class LivePhase { IDLE, LISTENING, RECOGNIZING, TRANSLATING, PAUSED }

@Composable
private fun rememberLivePhase(level: Float): LivePhase {
    var phase by remember { mutableStateOf(LivePhase.IDLE) }
    LaunchedEffect(Unit) {
        while (true) {
            phase = when {
                AppState.paused -> LivePhase.PAUSED
                !AppState.running -> LivePhase.IDLE
                level > 0.08f -> LivePhase.LISTENING
                AppState.lastCaptionEn.isNotBlank() && AppState.lastCaptionHu.isBlank() -> LivePhase.RECOGNIZING
                AppState.lastCaptionHu.isNotBlank() -> LivePhase.TRANSLATING
                else -> LivePhase.LISTENING
            }
            // refine: if service running and quiet, still listening
            if (AppState.running && !AppState.paused && level <= 0.08f &&
                phase != LivePhase.TRANSLATING && phase != LivePhase.RECOGNIZING
            ) {
                phase = LivePhase.LISTENING
            }
            delay(120)
        }
    }
    return phase
}

private fun phaseOrder(p: LivePhase) = when (p) {
    LivePhase.IDLE -> 0
    LivePhase.LISTENING -> 1
    LivePhase.RECOGNIZING -> 2
    LivePhase.TRANSLATING -> 3
    LivePhase.PAUSED -> -1
}

private fun phaseColor(p: LivePhase) = when (p) {
    LivePhase.IDLE -> PlatinumColors.Steel
    LivePhase.LISTENING -> PlatinumColors.Emerald
    LivePhase.RECOGNIZING -> PlatinumColors.IceBlue
    LivePhase.TRANSLATING -> PlatinumColors.Champagne
    LivePhase.PAUSED -> PlatinumColors.Amber
}

private fun phaseTitle(p: LivePhase) = when (p) {
    LivePhase.IDLE -> "Készenlét"
    LivePhase.LISTENING -> "Figyelés"
    LivePhase.RECOGNIZING -> "Felismerés"
    LivePhase.TRANSLATING -> "Fordítás"
    LivePhase.PAUSED -> "Szüneteltetve"
}

private fun phaseDetail(p: LivePhase) = when (p) {
    LivePhase.IDLE -> "Indítsd az élő fordítást a mikrofon engedélyével."
    LivePhase.LISTENING -> "A mikrofon aktív – beszélj természetesen."
    LivePhase.RECOGNIZING -> "A beszéd szöveggé alakul."
    LivePhase.TRANSLATING -> "A felismert szöveg fordítása folyamatban."
    LivePhase.PAUSED -> "A felvétel szünetel – folytathatod bármikor."
}

private fun phaseShort(p: LivePhase) = when (p) {
    LivePhase.IDLE -> "Kész"
    LivePhase.LISTENING -> "Figyel"
    LivePhase.RECOGNIZING -> "ASR"
    LivePhase.TRANSLATING -> "Fordít"
    LivePhase.PAUSED -> "Szünet"
}

@Composable
private fun PhaseBadge(phase: LivePhase, pulse: Float) {
    Text(
        phaseShort(phase).uppercase(),
        color = phaseColor(phase).copy(alpha = 0.65f + 0.35f * pulse),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun PhaseOrb(
    phase: LivePhase,
    level: Float,
    spin: Float,
    pulse: Float,
    reduced: Boolean
) {
    val reducedMotion = rememberUpdatedState(reduced)
    val animLevel by animateFloatAsState(level, tween(if (reduced) 0 else 120), label = "lv")
    Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(68.dp)) {
            val stroke = 3.5.dp.toPx()
            val r = size.minDimension / 2f - stroke
            drawCircle(PlatinumColors.SteelDark.copy(alpha = 0.55f), r, style = Stroke(stroke))
            val sweep = (0.12f + animLevel * 0.88f) * 360f
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(phaseColor(phase), PlatinumColors.Champagne, phaseColor(phase))
                ),
                startAngle = -90f + if (reducedMotion.value) 0f else spin * 0.15f,
                sweepAngle = sweep,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
                size = Size(r * 2, r * 2),
                topLeft = Offset(center.x - r, center.y - r)
            )
            if (!reducedMotion.value && (phase == LivePhase.LISTENING || phase == LivePhase.RECOGNIZING)) {
                val gr = r + 4.dp.toPx() * pulse
                drawCircle(phaseColor(phase).copy(alpha = 0.2f * pulse), gr)
                val ang = Math.toRadians(spin.toDouble())
                drawCircle(
                    PlatinumColors.PlatinumHigh.copy(alpha = 0.85f),
                    3.dp.toPx(),
                    Offset(center.x + gr * cos(ang).toFloat(), center.y + gr * sin(ang).toFloat())
                )
            }
        }
        Text(
            "${(animLevel * 100).toInt()}",
            color = PlatinumColors.Text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/** View-burkoló az Élő fordítás képernyőhöz. */
class DiamondLiveView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : FrameLayout(ctx, attrs) {
    init {
        val reduced = PremiumMotion.reduced(ctx)
        addView(
            ComposeView(ctx).apply { setContent { DiamondLiveTheater(reduced) } },
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        )
    }
}
