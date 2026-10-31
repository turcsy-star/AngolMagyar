package hu.angolmagyar.fordito.ui.processing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.angolmagyar.fordito.ui.design.PremiumColors
import hu.angolmagyar.fordito.ui.design.PremiumMotion

/**
 * A cinematic feldolgozó kártya: fejléc (összesített %), 5 lépéses folyamatvonal, az aktív lépés
 * leírása, az éppen hallott / lefordított mondat, figyelmeztetés vagy hiba, befejezéskor animáció.
 * Minden érték a [ProcessingUiState]-ből jön; ami nem ismert, az nem jelenik meg.
 */
@Composable
fun ProcessingPanel(state: ProcessingUiState, reduced: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    var entered by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { entered = true }

    // a befejezési animáció csak akkor fut, ha a "kész" átmenetet élőben látjuk (nem újranyitáskor)
    var prevFinished by remember { mutableStateOf(state.finished) }
    var burst by remember { mutableStateOf(false) }
    LaunchedEffect(state.finished, state.errorMessage, state.cancelled) {
        if (state.finished && !prevFinished && state.errorMessage == null && !state.cancelled && !reduced) burst = true
        prevFinished = state.finished
    }

    AnimatedVisibility(
        visible = entered,
        enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(PremiumMotion.CARD_ENTER)) +
            slideInVertically(tween(PremiumMotion.CARD_ENTER)) { it / 8 }
    ) {
        Box(
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(
                    Brush.linearGradient(
                        listOf(PremiumColors.NavySurface, PremiumColors.DeepNavy, PremiumColors.Sapphire.copy(alpha = 0.42f))
                    )
                )
                .border(
                    BorderStroke(
                        1.dp,
                        Brush.linearGradient(
                            listOf(
                                PremiumColors.DiamondEdge.copy(alpha = 0.72f),
                                PremiumColors.CyanGlow.copy(alpha = 0.22f),
                                PremiumColors.BrightBlue.copy(alpha = 0.52f),
                                PremiumColors.DiamondEdge.copy(alpha = 0.18f)
                            )
                        )
                    ), shape
                )
                .padding(14.dp)
        ) {
            Box(
                Modifier
                    .size(176.dp)
                    .align(Alignment.TopEnd)
                    .background(
                        Brush.radialGradient(
                            listOf(PremiumColors.CyanGlow.copy(alpha = if (reduced) 0.055f else 0.095f), androidx.compose.ui.graphics.Color.Transparent)
                        )
                    )
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Header(state)
                ProcessingTimeline(state.stages, reduced)
                ActiveDetail(state)
                LiveText(state)
                Notice(state)
                Chips(state)
            }
            if (burst) CompletionBurst(Modifier.size(140.dp).align(Alignment.TopCenter)) { burst = false }
        }
    }
}

@Composable
private fun Header(s: ProcessingUiState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("FELDOLGOZÁS", color = PremiumColors.CyanGlow, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
            val status = when {
                s.errorMessage != null -> "Hiba történt"
                s.cancelled -> "Megszakítva"
                s.finished -> "Kész"
                s.running -> "Folyamatban"
                else -> "Készenlét"
            }
            Text(status, color = PremiumColors.IceWhite, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        if (s.fileTotal > 1 && s.fileNo > 0)
            Text("${s.fileNo}/${s.fileTotal} fájl", color = PremiumColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.padding(end = 10.dp))
        s.overallPercent?.let {
            Text("$it%", color = PremiumColors.CyanGlow, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActiveDetail(s: ProcessingUiState) {
    val active = s.stages.firstOrNull { it.status == StageStatus.RUNNING || it.status == StageStatus.WARNING }
        ?: return
    val pct = active.progress?.let { " · ${(it * 100).toInt()}%" } ?: ""
    val line = listOf(active.title + pct, active.detail).filter { it.isNotBlank() }.joinToString(" – ")
    Text(line, color = PremiumColors.TextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun LiveText(s: ProcessingUiState) {
    if (!s.running) return
    if (s.heard.isNotBlank())
        Text("„${s.heard}”", color = PremiumColors.IceWhite, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (s.translated.isNotBlank())
        Text("→ ${s.translated}", color = PremiumColors.BrightBlue, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun Notice(s: ProcessingUiState) {
    val (text, color) = when {
        s.errorMessage != null -> s.errorMessage to PremiumColors.Error
        s.warningMessage != null -> s.warningMessage to PremiumColors.Warning
        else -> return
    }
    Text(
        (if (color == PremiumColors.Error) "✕ " else "⚠ ") + text, color = color, fontSize = 12.sp,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)).padding(10.dp)
    )
}

@Composable
private fun Chips(s: ProcessingUiState) {
    val items = ArrayList<String>()
    s.chunksTotal?.let { items.add("Szegmens ${s.chunksDone}/$it") }
    if (s.engineLabel.isNotBlank()) items.add(s.engineLabel)
    if (items.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach {
            Text(
                it, color = PremiumColors.IceWhite, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(PremiumColors.ElectricBlue.copy(alpha = 0.22f))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            )
        }
    }
}
