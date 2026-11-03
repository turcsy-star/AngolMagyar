package hu.angolmagyar.fordito.ui.processing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import hu.angolmagyar.fordito.ui.design.PlatinumColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
data class FlowNode(
    val title: String, val status: StageStatus, val sub: String,
    val stageIndex: Int, val isTranslation: Boolean = false, val progress: Float? = null
)
fun flowNodes(state: ProcessingUiState): List<FlowNode> {
    val st = state.stages
    if (st.size < 5) return emptyList()
    val out = ArrayList<FlowNode>()
    for (i in 0..2) out.add(FlowNode(st[i].title, st[i].status, subLabel(st[i]), i, progress = st[i].progress))
    val r = st[3]
    out.add(FlowNode(if (state.withTranslation) "Felismerés" else r.title, r.status, subLabel(r), 3, progress = r.progress))
    if (state.withTranslation) {
        val ts = if (r.status == StageStatus.PENDING) StageStatus.PENDING else r.status
        out.add(FlowNode("Fordítás", ts, if (state.translatedLines > 0) "${state.translatedLines} sor" else "", 3, true, r.progress))
    }
    out.add(FlowNode(st[4].title, st[4].status, subLabel(st[4]), 4, progress = st[4].progress))
    return out
}
/**
 * Üstökös: fényes fej + exponenciálisan halványuló, zsugorodó csóva az útvonal mentén.
 * t: a fej helyzete (0..1) az útvonalon; a végeken kifut/becsúszik, hogy ne pattanjon.
 */
private fun DrawScope.drawComet(pm: PathMeasure, len: Float, t: Float, scale: Float = 1f) {
    if (len <= 0f) return
    val edge = minOf(1f, t / 0.10f, (1f - t) / 0.10f).coerceIn(0f, 1f)
    if (edge <= 0f) return
    val tail = 14
    for (k in tail downTo 1) {
        val tt = t - k * 0.020f
        if (tt < 0f) continue
        val frac = 1f - k / tail.toFloat()
        val p = pm.getPosition(len * tt)
        val rad = (1.0f + 3.6f * frac * frac) * scale
        drawCircle(PlatinumColors.DiamondWhite.copy(alpha = 0.55f * frac * frac * edge), rad.dp.toPx(), p)
    }
    val head = pm.getPosition(len * t)
    val gr = 11.dp.toPx() * scale
    drawCircle(Brush.radialGradient(listOf(PlatinumColors.DiamondWhite.copy(alpha = 0.85f * edge), Color.Transparent), head, gr), gr, head)
    drawCircle(PlatinumColors.PlatinumHigh.copy(alpha = edge), 2.2.dp.toPx() * scale, head)
}
private fun nodePos(i: Int, n: Int, w: Float, h: Float) =
    Offset(w * (i + 0.5f) / n, h * (if (i % 2 == 0) 0.40f else 0.60f))
@Composable
fun PlatinumFlowGraph(
    state: ProcessingUiState, selected: Int?, onSelect: (Int?) -> Unit,
    reduced: Boolean, modifier: Modifier = Modifier
) {
    val reducedMotion = rememberUpdatedState(reduced)
    val nodes = flowNodes(state)
    if (nodes.isEmpty()) return
    val n = nodes.size
    val haptic = LocalHapticFeedback.current
    val ms = if (reduced) 0 else 320
    val phase = if (reduced) 0f else {
        rememberInfiniteTransition(label = "flow").animateFloat(
            0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing)), label = "ph"
        ).value
    }
    val colors = nodes.map { animateColorAsState(jewelColor(it.status), tween(ms), label = "c").value }
    val checks = nodes.map { animateFloatAsState(if (it.status == StageStatus.COMPLETED) 1f else 0f, tween(if (reduced) 0 else 450), label = "k").value }
    val sels = nodes.indices.map { animateFloatAsState(if (selected == it) 1f else 0f, tween(ms), label = "s").value }
    val fills = nodes.map {
        animateFloatAsState(
            when (it.status) {
                StageStatus.COMPLETED, StageStatus.WARNING -> 1f
                StageStatus.RUNNING -> it.progress ?: 0f
                else -> 0f
            }, tween(if (reduced) 0 else 600), label = "f"
        ).value
    }
    // Fázisátadás: amikor egy csomópont KÉSZ lesz, egyszeri, fényesebb üstökös fut át a következőhöz.
    val scope = rememberCoroutineScope()
    val handoff = remember(n) { List(maxOf(n - 1, 0)) { Animatable(0f) } }
    val doneNow = nodes.map { it.status == StageStatus.COMPLETED }
    val prevDone = remember(n) { doneNow.toBooleanArray() }
    LaunchedEffect(doneNow) {
        for (i in 0 until n - 1) {
            if (doneNow[i] && !prevDone[i] && !reducedMotion.value) {
                val a = handoff[i]
                scope.launch {
                    a.snapTo(0f)
                    a.animateTo(1f, tween(780, easing = FastOutSlowInEasing))
                    a.snapTo(0f)
                }
            }
        }
        for (i in doneNow.indices) prevDone[i] = doneNow[i]
    }
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier.fillMaxWidth().height(100.dp)
                .semantics { contentDescription = "Folyamatábra, érintésre részletek" }
                .pointerInput(n, selected) {
                    detectTapGestures { off ->
                        var best = -1; var bd = Float.MAX_VALUE
                        for (i in 0 until n) {
                            val d = (nodePos(i, n, size.width.toFloat(), size.height.toFloat()) - off).getDistance()
                            if (d < bd) { bd = d; best = i }
                        }
                        if (best >= 0 && bd < 44.dp.toPx()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(if (selected == best) null else best)
                        }
                    }
                }
        ) {
            val r = 13.dp.toPx()
            val w = size.width; val h = size.height
            val pm = PathMeasure()
            for (i in 0 until n - 1) {
                val a = nodePos(i, n, w, h); val b = nodePos(i + 1, n, w, h)
                val mx = (a.x + b.x) / 2f
                val path = Path().apply { moveTo(a.x, a.y); cubicTo(mx, a.y, mx, b.y, b.x, b.y) }
                drawPath(path, PlatinumColors.Onyx, style = Stroke(5.dp.toPx(), cap = StrokeCap.Round))
                drawPath(path, PlatinumColors.SteelDark.copy(alpha = 0.45f), style = Stroke(1.dp.toPx()))
                pm.setPath(path, false)
                val len = pm.length
                val f = fills[i].coerceIn(0f, 1f)
                if (f > 0f) {
                    val seg = Path()
                    pm.getSegment(0f, len * f, seg, true)
                    drawPath(seg, Brush.horizontalGradient(listOf(PlatinumColors.Steel, PlatinumColors.PlatinumHigh, PlatinumColors.Platinum), a.x, b.x),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                if (nodes[i].status == StageStatus.RUNNING && !reducedMotion.value) {
                    drawComet(pm, len, phase)
                    drawComet(pm, len, (phase + 0.5f) % 1f, 0.6f)
                }
                val hv = handoff.getOrNull(i)?.value ?: 0f
                if (hv > 0f && hv < 1f && !reducedMotion.value) drawComet(pm, len, hv, 1.35f)
            }
            for (i in 0 until n) {
                val p = nodePos(i, n, w, h)
                if (sels[i] > 0f) drawCircle(PlatinumColors.DiamondWhite.copy(alpha = 0.7f * sels[i]), r + (7f + 2f * sels[i]).dp.toPx(), p, style = Stroke(1.6.dp.toPx()))
                jewel(nodes[i].status, colors[i], checks[i], p.x, p.y, r, phase, reducedMotion.value)
            }
        }
        Row(Modifier.fillMaxWidth()) {
            nodes.forEach { nd ->
                Column(
                    Modifier.weight(1f).semantics { contentDescription = "${nd.title}: ${nd.sub.ifBlank { "vár" }}" },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        nd.title, textAlign = TextAlign.Center, maxLines = 2, fontSize = 10.sp,
                        color = if (nd.status == StageStatus.PENDING) PlatinumColors.TextDim else PlatinumColors.Text,
                        fontWeight = if (nd.status == StageStatus.RUNNING) FontWeight.SemiBold else FontWeight.Normal
                    )
                    if (nd.sub.isNotEmpty()) Text(
                        nd.sub, fontSize = 10.sp, textAlign = TextAlign.Center, maxLines = 1,
                        color = when (nd.status) {
                            StageStatus.RUNNING -> PlatinumColors.DiamondWhite
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
