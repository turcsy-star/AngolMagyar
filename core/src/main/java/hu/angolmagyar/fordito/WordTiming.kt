package hu.angolmagyar.fordito
import kotlin.math.abs
object WordTiming {
    data class Span(val startMs: Long, val endMs: Long)
    fun snapCues(
        cues: List<SrtExporter.Cue>,
        spans: List<Span>,
        padStartMs: Long = 80,
        padEndMs: Long = 200,
        maxShiftMs: Long = 1500,
        minDurMs: Long = 700,
        minGapMs: Long = 40
    ): List<SrtExporter.Cue> {
        if (cues.isEmpty() || spans.isEmpty()) return cues
        val order = cues.indices.sortedBy { cues[it].startMs }
        val sortedSpans = spans.filter { it.endMs > it.startMs }.sortedBy { it.startMs }
        if (sortedSpans.isEmpty()) return cues
        val first = LongArray(cues.size) { Long.MAX_VALUE }
        val last = LongArray(cues.size) { Long.MIN_VALUE }
        for (sp in sortedSpans) {
            val mid = (sp.startMs + sp.endMs) / 2
            var best = -1
            var bestDist = Long.MAX_VALUE
            for (i in order) {
                val c = cues[i]
                val d = when {
                    mid in c.startMs..c.endMs -> 0L
                    mid < c.startMs -> c.startMs - mid
                    else -> mid - c.endMs
                }
                if (d < bestDist) { bestDist = d; best = i }
            }
            if (best >= 0 && bestDist <= maxShiftMs) {
                if (sp.startMs < first[best]) first[best] = sp.startMs
                if (sp.endMs > last[best]) last[best] = sp.endMs
            }
        }
        val out = cues.toMutableList()
        for ((pos, i) in order.withIndex()) {
            val c = cues[i]
            if (first[i] == Long.MAX_VALUE) continue
            val prevEnd = if (pos > 0) out[order[pos - 1]].endMs else Long.MIN_VALUE / 2
            val nextStart = if (pos < order.lastIndex) cues[order[pos + 1]].startMs else Long.MAX_VALUE / 2
            var s = (first[i] - padStartMs)
            s = s.coerceIn(c.startMs - maxShiftMs, c.startMs + maxShiftMs)
            s = maxOf(s, prevEnd + minGapMs, 0L)
            var e = last[i] + padEndMs
            e = e.coerceIn(c.endMs - maxShiftMs, c.endMs + maxShiftMs)
            e = minOf(e, nextStart - minGapMs)
            if (e - s < minDurMs) e = minOf(s + minDurMs, nextStart - minGapMs)
            if (e <= s) continue
            out[i] = c.copy(startMs = s, endMs = e)
        }
        return out
    }
    fun estimateOffsetMs(cues: List<SrtExporter.Cue>, spans: List<Span>, minPairs: Int = 5, window: Long = 3000): Long? {
        if (spans.isEmpty()) return null
        val sorted = spans.sortedBy { it.startMs }
        val diffs = ArrayList<Long>()
        for (c in cues) {
            val sp = sorted.filter { abs(it.startMs - c.startMs) <= window }.minByOrNull { abs(it.startMs - c.startMs) } ?: continue
            diffs.add(sp.startMs - c.startMs)
        }
        if (diffs.size < minPairs) return null
        diffs.sort()
        return diffs[diffs.size / 2]
    }
    fun shift(cues: List<SrtExporter.Cue>, offsetMs: Long): List<SrtExporter.Cue> =
        if (offsetMs == 0L) cues else cues.map {
            val s = maxOf(0L, it.startMs + offsetMs)
            it.copy(startMs = s, endMs = maxOf(s + 1, it.endMs + offsetMs))
        }
    fun tighten(
        startMs: Long,
        endMs: Long,
        tokens: List<Span>,
        padStartMs: Long = 100,
        padEndMs: Long = 250,
        minDurMs: Long = 400,
        tolerance: Long = 300
    ): Pair<Long, Long> {
        val orig = startMs to endMs
        if (endMs <= startMs || tokens.size < 3) return orig
        if (tokens.any { it.endMs <= it.startMs || it.startMs < startMs - tolerance || it.endMs > endMs + tolerance }) return orig
        if (tokens.zipWithNext().any { (a, b) -> b.startMs < a.startMs }) return orig
        val s = (tokens.first().startMs - padStartMs).coerceIn(startMs, endMs)
        val e = (tokens.last().endMs + padEndMs).coerceIn(s, endMs)
        if (e - s < minDurMs) return orig
        return s to e
    }
}
