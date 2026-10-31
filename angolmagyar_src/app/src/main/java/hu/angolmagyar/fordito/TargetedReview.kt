package hu.angolmagyar.fordito

import android.content.Context
import android.net.Uri
import hu.angolmagyar.fordito.core.review.ReviewIntelligence
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Célzott újrafelismerés: csak a minőségellenőrző által kritikusnak/magasnak jelölt sorok hangszakaszát
 * futtatja újra nagyobb nyalábbal (és ha van, a large-v3 modellel). Nem ír át semmit magától:
 * javaslatokat ad, amelyeket a felhasználó fogad el vagy vet el.
 */
object TargetedReview {

    /** Egy javaslat: a [first]..[last] sorok helyére [fresh] kerülne. */
    class Proposal(
        val first: Int, val last: Int,
        val old: List<SrtExporter.Cue>, val fresh: List<SrtExporter.Cue>,
        /** Az új eredmény átlagos megbízhatósága jobb-e a réginél. */
        val better: Boolean
    ) {
        fun oldText() = old.joinToString(" ") { it.source }.trim()
        fun newText() = fresh.joinToString(" ") { it.source }.trim()
        val changed: Boolean get() = oldText() != newText()
    }

    /** A kijelölt sorindexeket összefüggő szakaszokba fogja (egy szakasz legfeljebb 25 mp). */
    fun groups(indices: List<Int>, cues: List<SrtExporter.Cue>): List<IntRange> {
        val sorted = indices.filter { it in cues.indices }.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val out = ArrayList<IntRange>()
        var gs = sorted[0]; var ge = sorted[0]
        for (i in sorted.drop(1)) {
            if (i == ge + 1 && cues[i].endMs - cues[gs].startMs <= 25_000) ge = i
            else { out.add(gs..ge); gs = i; ge = i }
        }
        out.add(gs..ge)
        return out
    }

    /** A kiválasztandó sorok: a kritikus/magas jelzésű sorok, a keretek (sor, hang-másodperc) szerint. */
    fun pickLines(
        summary: ReviewIntelligence.ReviewSummary,
        maxLines: Int = 16, maxAudioSeconds: Int = 240
    ): List<Int> {
        val strong = ReviewIntelligence.ReviewSummary(
            summary.totalLines, summary.flaggedLines, summary.criticalFindings, summary.highFindings,
            summary.findings.filter { it.severity.ordinal >= ReviewIntelligence.Severity.HIGH.ordinal }
        )
        return ReviewIntelligence.planTargetedReview(strong, maxLines, maxAudioSeconds)
    }

    /**
     * Hosszú művelet: háttérszálról hívandó. A teljes hangot ideiglenes PCM-fájlba dekódolja, majd a szakaszokat
     * újrafelismeri. Hibánál kivételt dob, megszakításnál üres listát ad.
     */
    fun run(
        ctx: Context, uri: Uri, cues: List<SrtExporter.Cue>, lines: List<Int>, srcLang: String,
        cancel: AtomicBoolean, status: (String) -> Unit
    ): List<Proposal> {
        val groups = groups(lines, cues)
        if (groups.isEmpty()) return emptyList()
        val spec = ModelManager.installedSpecs(ctx).firstOrNull { it.id == ModelManager.WH_LARGE.id }
            ?: Engines.whisperSpec(ctx)
            ?: throw IllegalStateException("Nincs telepített Whisper modell (Modellek képernyő).")
        val pcm = File(ctx.cacheDir, "targeted_${System.currentTimeMillis()}.pcm")
        val rec = FileRecognizer(ctx, Prefs(ctx), cancel)
        try {
            status("Hang előkészítése…")
            AudioHelper.decodeUriToPcm16kMono(ctx, uri, pcm, { cancel.get() }) { p -> status("Hang előkészítése: $p%") }
            if (cancel.get()) return emptyList()
            val spans = groups.map { g -> maxOf(0L, cues[g.first].startMs - 500) to (cues[g.last].endMs + 500) }
            val beam = (AsrPrefs.beam(ctx) + 4).coerceAtMost(10)
            val results = rec.refine(pcm, spans, spec, srcLang, beam) { d, t -> status("Pontosítás (${spec.label}): $d/$t") }
            val out = ArrayList<Proposal>()
            for ((k, g) in groups.withIndex()) {
                val res = results.getOrNull(k)?.takeIf { it.isNotEmpty() } ?: continue
                val old = cues.subList(g.first, g.last + 1).toList()
                val spk = old.first().speaker
                val oldLp = old.map { it.lp.toDouble() }.average()
                val newLp = res.map { it.lp.toDouble() }.average()
                out.add(Proposal(g.first, g.last, old, res.map { it.copy(speaker = spk) }, newLp > oldLp + 0.05))
            }
            return out
        } finally {
            try { rec.close() } catch (e: Exception) { DiagLog.swallow("TargetedReview", e) }
            try { pcm.delete() } catch (e: Exception) { DiagLog.swallow("TargetedReview", e) }
        }
    }

    /** A kiválasztott javaslatok alkalmazása (hátulról előre, hogy az indexek ne csússzanak el). */
    fun apply(cues: List<SrtExporter.Cue>, chosen: List<Proposal>): List<SrtExporter.Cue> {
        val out = ArrayList(cues)
        for (p in chosen.sortedByDescending { it.first }) {
            for (i in p.last downTo p.first) if (i in out.indices) out.removeAt(i)
            out.addAll(p.first.coerceAtMost(out.size), p.fresh)
        }
        return out
    }
}
