package hu.angolmagyar.fordito.ui.processing

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.angolmagyar.fordito.ui.design.PlatinumColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Platina műszer: fémes kávagyűrű forgó fényponttal, 60 gravírozott osztás, haladásív pezsgőarany véggel
 * és szegmensgyűrű (egy szegmens = egy feldolgozott darab). Csak valós adatot mutat:
 * ha a % vagy a szegmensszám nem ismert, a megfelelő rész üresen marad.
 */
@Composable
fun PlatinumDial(
    percent: Int?, chunksDone: Int, chunksTotal: Int?,
    running: Boolean, finished: Boolean, failed: Boolean, cancelled: Boolean,
    reduced: Boolean, size: Dp = 128.dp, modifier: Modifier = Modifier
) {
    val frac by animateFloatAsState(
        (percent ?: 0).coerceIn(0, 100) / 100f, tween(if (reduced) 0 else 700), label = "dialFrac"
    )
    val spin = if (reduced || !running) 0f else {
        rememberInfiniteTransition(label = "dial").animateFloat(
            0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing)), label = "spin"
        ).value
    }
    val pulse = if (reduced || !running) 0.6f else {
        rememberInfiniteTransition(label = "pulse").animateFloat(
            0.3f, 1f, infiniteRepeatable(tween(1400), androidx.compose.animation.core.RepeatMode.Reverse), label = "p"
        ).value
    }
    val accent = when {
        failed -> PlatinumColors.Ruby
        cancelled -> PlatinumColors.Steel
        finished -> PlatinumColors.Emerald
        else -> PlatinumColors.Champagne
    }
    val desc = "Összesített haladás: " + (percent?.let { "$it százalék" } ?: "ismeretlen")

    Box(modifier.size(size).semantics { contentDescription = desc }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val c = Offset(this.size.width / 2f, this.size.height / 2f)
            val rad = this.size.minDimension / 2f

            // kávagyűrű: fémes kúpos átmenet, a fénypont lassan körbejár
            val bezelW = rad * 0.085f
            rotate(spin * 360f, c) {
                drawCircle(
                    Brush.sweepGradient(
                        listOf(
                            PlatinumColors.SteelDark, PlatinumColors.Platinum, PlatinumColors.PlatinumHigh,
                            PlatinumColors.Steel, PlatinumColors.SteelDark, PlatinumColors.Steel,
                            PlatinumColors.Platinum, PlatinumColors.SteelDark
                        ), c
                    ),
                    rad - bezelW / 2f, c, style = Stroke(bezelW)
                )
            }
            // lap
            drawCircle(
                Brush.radialGradient(
                    listOf(PlatinumColors.GraphiteHigh, PlatinumColors.Graphite, PlatinumColors.Onyx),
                    c, rad * 0.9f
                ), rad - bezelW, c
            )

            // 60 gravírozott osztás
            val tickOut = rad * 0.86f
            for (i in 0 until 60) {
                val a = (i / 60f) * 2f * PI.toFloat() - PI.toFloat() / 2f
                val major = i % 5 == 0
                val tickIn = tickOut - rad * (if (major) 0.085f else 0.045f)
                val lit = percent != null && (i + 0.5f) / 60f <= frac
                val col = when {
                    lit && major -> PlatinumColors.PlatinumHigh
                    lit -> PlatinumColors.Platinum.copy(alpha = 0.8f)
                    major -> PlatinumColors.Steel.copy(alpha = 0.55f)
                    else -> PlatinumColors.SteelDark.copy(alpha = 0.7f)
                }
                drawLine(
                    col, Offset(c.x + tickIn * cos(a), c.y + tickIn * sin(a)),
                    Offset(c.x + tickOut * cos(a), c.y + tickOut * sin(a)),
                    strokeWidth = (if (major) 1.8f else 1f) * density, cap = StrokeCap.Round
                )
            }

            // haladásív
            val arcR = rad * 0.68f
            val arcW = rad * 0.075f
            val tl = Offset(c.x - arcR, c.y - arcR)
            val sz = Size(arcR * 2, arcR * 2)
            drawArc(PlatinumColors.SteelDark.copy(alpha = 0.35f), 0f, 360f, false, tl, sz, style = Stroke(arcW))
            if (frac > 0f) {
                rotate(-90f, c) {
                    drawArc(
                        Brush.sweepGradient(
                            0f to PlatinumColors.Steel, 0.5f * frac.coerceAtLeast(0.01f) to PlatinumColors.Platinum,
                            frac.coerceAtLeast(0.02f) to accent, 1f to PlatinumColors.Steel, center = c
                        ),
                        0f, 360f * frac, false, tl, sz, style = Stroke(arcW, cap = StrokeCap.Round)
                    )
                }
                // fényes ívvég
                val ea = frac * 2f * PI.toFloat() - PI.toFloat() / 2f
                val tip = Offset(c.x + arcR * cos(ea), c.y + arcR * sin(ea))
                drawCircle(
                    Brush.radialGradient(listOf(accent.copy(alpha = 0.55f * pulse + 0.15f), Color.Transparent), tip, arcW * 2.4f),
                    arcW * 2.4f, tip
                )
                drawCircle(PlatinumColors.PlatinumHigh, arcW * 0.32f, tip)
            }

            // szegmensgyűrű: egy szegmens = egy darab (csak ismert összegnél)
            val n = chunksTotal ?: 0
            if (n in 1..72) {
                val segR = rad * 0.52f
                val segW = rad * 0.045f
                val stl = Offset(c.x - segR, c.y - segR)
                val ssz = Size(segR * 2, segR * 2)
                val gap = if (n > 1) 3.2f else 0f
                val sweep = 360f / n - gap
                for (i in 0 until n) {
                    val col = when {
                        i < chunksDone -> PlatinumColors.IceBlue.copy(alpha = 0.9f)
                        i == chunksDone && running -> accent.copy(alpha = pulse)
                        else -> PlatinumColors.SteelDark.copy(alpha = 0.45f)
                    }
                    drawArc(col, -90f + i * (360f / n) + gap / 2f, sweep, false, stl, ssz, style = Stroke(segW, cap = StrokeCap.Butt))
                }
            }

            // üveg-visszfény a lap felső részén
            drawArc(
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.10f), Color.Transparent), c.y - rad, c.y),
                200f, 140f, false, Offset(c.x - rad * 0.8f, c.y - rad * 0.8f), Size(rad * 1.6f, rad * 1.6f),
                style = Stroke(rad * 0.05f, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                percent?.let { "$it" } ?: "—", color = PlatinumColors.Text,
                fontSize = (size.value * 0.23f).sp, fontWeight = FontWeight.Light
            )
            Text(
                when { failed -> "HIBA"; cancelled -> "MEGSZAKÍTVA"; finished -> "KÉSZ"; running -> "FOLYAMATBAN"; else -> "KÉSZENLÉT" },
                color = accent, fontSize = (size.value * 0.065f).sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Medium
            )
        }
    }
}
