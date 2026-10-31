package hu.angolmagyar.fordito.ui.processing

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.runtime.mutableStateListOf
import hu.angolmagyar.fordito.ui.design.PlatinumGlassCard
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.angolmagyar.fordito.ui.design.PlatinumColors
import hu.angolmagyar.fordito.ui.design.PremiumMotion
import kotlinx.coroutines.delay

/**
 * Platinum Luxury feldolgozó kártya: műszer + gravírozott statisztikák + ékköves folyamatvonal.
 * Minden érték a [ProcessingUiState]-ből jön (valós adat); ami ismeretlen, az üresen vagy „számítás…” felirattal látszik.
 */
@Composable
fun PlatinumPanel(state: ProcessingUiState, reduced: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(24.dp)
    var entered by remember { mutableStateOf(reduced) }
    LaunchedEffect(Unit) { entered = true }

    // másodpercenként ketyegő óra a futó időhöz (az adat ~1 mp-enként frissül, közte az óra számol)
    val base = remember(state) { System.currentTimeMillis() }
    var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.running) {
        while (state.running) { tick = System.currentTimeMillis(); delay(1000) }
    }
    val sinceBase = if (state.running) (tick - base).coerceAtLeast(0L) else 0L
    val elapsed = state.elapsedMs + sinceBase
    val remaining = state.remainingMs?.let { (it - sinceBase).coerceAtLeast(0L) }

    var selected by remember { mutableStateOf<Int?>(null) }
    val history = remember { mutableStateListOf<Pair<Long, Int>>() }
    LaunchedEffect(state.overallPercent, state.running) {
        val p = state.overallPercent
        if (state.running && p != null) {
            if (history.isNotEmpty() && p < history.last().second) history.clear() // új feldolgozás
            if (history.isEmpty() || history.last().second != p) {
                history.add(System.currentTimeMillis() to p)
                if (history.size > 150) history.removeAt(0)
            }
        }
    }

    var prevFinished by remember { mutableStateOf(state.finished) }
    var seal by remember { mutableStateOf(false) }
    LaunchedEffect(state.finished, state.errorMessage, state.cancelled) {
        if (state.finished && !prevFinished && state.errorMessage == null && !state.cancelled && !reduced) seal = true
        prevFinished = state.finished
    }

    val rim = Brush.linearGradient(
        listOf(PlatinumColors.PlatinumHigh.copy(alpha = 0.55f), PlatinumColors.SteelDark.copy(alpha = 0.25f), PlatinumColors.Champagne.copy(alpha = 0.40f))
    )

    AnimatedVisibility(
        visible = entered,
        enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(PremiumMotion.CARD_ENTER)) + slideInVertically(tween(PremiumMotion.CARD_ENTER)) { it / 10 }
    ) {
        Box(
            modifier.fillMaxWidth().clip(shape)
                .background(Brush.verticalGradient(listOf(PlatinumColors.GraphiteHigh, PlatinumColors.Graphite, PlatinumColors.Onyx)))
                .border(1.dp, rim, shape)
                .drawBehind { // felső fényes hajszálvonal
                    drawLine(
                        Brush.horizontalGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent)),
                        Offset(size.width * 0.08f, 1f), Offset(size.width * 0.92f, 1f), 1.5.dp.toPx()
                    )
                }
                .padding(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("PLATINUM · FELDOLGOZÁS", color = PlatinumColors.Champagne, fontSize = 10.5.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Medium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    PlatinumDial(
                        percent = state.overallPercent, chunksDone = state.chunksDone, chunksTotal = state.chunksTotal,
                        running = state.running, finished = state.finished && state.errorMessage == null && !state.cancelled,
                        failed = state.errorMessage != null, cancelled = state.cancelled, reduced = reduced
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Stat("ELTELT", if (state.startedOrDone()) fmtClock(elapsed) else "–")
                        Stat(
                            "HÁTRA",
                            when {
                                state.finished -> "kész"
                                remaining != null -> "kb. " + fmtClock(remaining)
                                state.running -> "számítás…"
                                else -> "–"
                            },
                            sub = if (state.running && remaining != null) state.etaBasis else ""
                        )
                        state.chunksTotal?.let { Stat("SZEGMENS", "${state.chunksDone} / $it") }
                        if (state.fileTotal > 1 && state.fileNo > 0) Stat("FÁJL", "${state.fileNo} / ${state.fileTotal}")
                        if (state.engineLabel.isNotBlank()) Stat("MOTOR", state.engineLabel)
                    }
                }
                Hairline()
                PlatinumFlowGraph(state, selected, { selected = it }, reduced)
                AnimatedVisibility(
                    visible = selected != null,
                    enter = if (reduced) fadeIn(tween(0)) else fadeIn(tween(250)) + expandVertically(tween(300)),
                    exit = if (reduced) fadeOut(tween(0)) else fadeOut(tween(200)) + shrinkVertically(tween(250))
                ) {
                    val nd = selected?.let { flowNodes(state).getOrNull(it) }
                    if (nd != null) NodeDetail(nd, state, reduced)
                }
                if (history.size >= 2) PlatinumGlassCard(Modifier.fillMaxWidth(), reduced) { PlatinumSparkline(history.toList()) }
                state.stages.firstOrNull { it.status == StageStatus.RUNNING || it.status == StageStatus.WARNING }?.let { a ->
                    val line = listOf(a.title, a.detail).filter { it.isNotBlank() }.joinToString(" – ")
                    Text(line, color = PlatinumColors.TextDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (state.running && state.heard.isNotBlank())
                    Text("„${state.heard}”", color = PlatinumColors.Text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (state.running && state.translated.isNotBlank())
                    Text("→ ${state.translated}", color = PlatinumColors.IceBlue, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                val note = state.errorMessage?.let { it to PlatinumColors.Ruby } ?: state.warningMessage?.let { it to PlatinumColors.Amber }
                if (note != null) Text(
                    (if (note.second == PlatinumColors.Ruby) "✕ " else "⚠ ") + note.first, color = note.second, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(note.second.copy(alpha = 0.10f))
                        .border(1.dp, note.second.copy(alpha = 0.35f), RoundedCornerShape(12.dp)).padding(10.dp)
                )
            }
            if (seal) CompletionBurst(
                Modifier.size(170.dp).align(Alignment.TopStart).padding(top = 0.dp),
                colors = listOf(PlatinumColors.Champagne, PlatinumColors.PlatinumHigh, PlatinumColors.Emerald)
            ) { seal = false }
        }
    }
}

private fun ProcessingUiState.startedOrDone() = running || finished || elapsedMs > 0

private fun fmtClock(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

@Composable
private fun Stat(label: String, value: String, sub: String = "") {
    Column {
        Text(label, color = PlatinumColors.Steel, fontSize = 9.sp, letterSpacing = 1.6.sp)
        Text(value, color = PlatinumColors.Text, fontSize = 15.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (sub.isNotBlank()) Text(sub, color = PlatinumColors.TextDim, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Hairline() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 2.dp).background(
            Brush.horizontalGradient(listOf(Color.Transparent, PlatinumColors.Steel.copy(alpha = 0.5f), Color.Transparent))
        ).padding(top = 1.dp)
    )
}

@Composable
private fun NodeDetail(nd: FlowNode, state: ProcessingUiState, reduced: Boolean) {
    val st = state.stages.getOrNull(nd.stageIndex)
    PlatinumGlassCard(Modifier.fillMaxWidth(), reduced) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(nd.title, color = PlatinumColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            val facts = ArrayList<String>()
            facts.add(when (nd.status) {
                StageStatus.PENDING -> "Állapot: vár"; StageStatus.RUNNING -> "Állapot: fut"
                StageStatus.COMPLETED -> "Állapot: kész"; StageStatus.WARNING -> "Állapot: figyelmeztetéssel"
                StageStatus.FAILED -> "Állapot: hiba"; StageStatus.CANCELLED -> "Állapot: megszakítva"
            })
            if (st != null && st.durationMs > 0) facts.add("Idő: " + fmtDurationShort(st.durationMs))
            nd.progress?.let { facts.add("Haladás: ${(it * 100).toInt()}%") }
            if (nd.isTranslation) facts.add("Lefordított sorok: ${state.translatedLines}")
            if (nd.stageIndex == 3 && !nd.isTranslation) state.chunksTotal?.let { facts.add("Szegmens: ${state.chunksDone}/$it") }
            Text(facts.joinToString("  ·  "), color = PlatinumColors.TextDim, fontSize = 12.sp)
            val detail = if (nd.isTranslation) state.engineLabel else st?.detail.orEmpty()
            if (detail.isNotBlank()) Text(detail, color = PlatinumColors.IceBlue, fontSize = 12.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
    }
}
