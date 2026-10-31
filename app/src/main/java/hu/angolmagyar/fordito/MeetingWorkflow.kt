package hu.angolmagyar.fordito

import hu.angolmagyar.fordito.core.review.ReviewIntelligence

/**
 * P5–P7 meeting workflow coordinator.
 *
 * Ties together:
 * - MeetingMode (prefs snapshot / restore)
 * - MeetingSummary (offline rule-based summary)
 * - ReviewIntelligence (quality findings, never auto-rewrites)
 * - Optional phrase candidates for local learning (Phrasebook / memory) – never auto-saved
 *
 * All offline. No network. No automatic overwrite of transcript text.
 */
object MeetingWorkflow {

    data class QualityOverview(
        val score0to100: Int,
        val totalFindings: Int,
        val criticalCount: Int,
        val highCount: Int,
        val topExplanations: List<String>
    )

    data class PhraseCandidate(
        val source: String,
        val target: String,
        val reason: String
    )

    data class Package(
        val summary: MeetingSummary.Result,
        val quality: QualityOverview,
        val phraseCandidates: List<PhraseCandidate>,
        val reportText: String
    )

    /**
     * Build a full offline meeting package from cues.
     * @param sourceLang / targetLang used only for phrase-candidate language tags in reasons.
     */
    fun buildPackage(
        cues: List<SrtExporter.Cue>,
        title: String = "Tárgyalás összefoglaló",
        speakerMap: Map<String, String> = emptyMap(),
        sourceLang: String = "en",
        targetLang: String = "hu"
    ): Package {
        val summary = MeetingSummary.generate(cues, title, speakerMap)
        val quality = analyzeQuality(cues)
        val phrases = phraseCandidates(cues, max = 12)
        val report = buildReport(summary, quality, phrases)
        return Package(summary, quality, phrases, report)
    }

    fun analyzeQuality(cues: List<SrtExporter.Cue>): QualityOverview {
        if (cues.isEmpty()) {
            return QualityOverview(100, 0, 0, 0, emptyList())
        }
        val lines = cues.map { c ->
            ReviewIntelligence.Line(
                text = c.target.ifBlank { c.source },
                source = c.source.takeIf { it.isNotBlank() },
                target = c.target.takeIf { it.isNotBlank() }
            )
        }
        val review = ReviewIntelligence.analyze(lines)
        val critical = review.findings.count { it.severity == ReviewIntelligence.Severity.CRITICAL }
        val high = review.findings.count { it.severity == ReviewIntelligence.Severity.HIGH }
        // Reuse ReviewSheet-style scoring: weighted cleanliness
        val score = if (review.totalLines == 0) 100 else {
            val perLine = review.findings.groupBy { it.lineIndex }.values.sumOf { items ->
                when (items.maxOf { it.severity }) {
                    ReviewIntelligence.Severity.CRITICAL -> 1.0
                    ReviewIntelligence.Severity.HIGH -> 0.6
                    ReviewIntelligence.Severity.MEDIUM -> 0.3
                    ReviewIntelligence.Severity.LOW -> 0.1
                    ReviewIntelligence.Severity.INFO -> 0.0
                }
            }
            (100.0 * (1.0 - perLine / review.totalLines)).toInt().coerceIn(0, 100)
        }
        val top = review.findings
            .sortedByDescending { it.severity }
            .take(8)
            .map { f ->
                val where = "sor ${f.lineIndex + 1}"
                val ev = f.evidence.takeIf { it.isNotBlank() }?.let { " ($it)" } ?: ""
                "$where · ${f.kind.name}: ${f.explanation}$ev"
            }
        return QualityOverview(score, review.findings.size, critical, high, top)
    }

    /**
     * Suggest phrase pairs for local learning. Never auto-saves.
     * Prefers action/decision-like lines and longer bilingual cues.
     */
    fun phraseCandidates(cues: List<SrtExporter.Cue>, max: Int = 12): List<PhraseCandidate> {
        val out = LinkedHashSet<PhraseCandidate>()
        for (c in cues) {
            val src = c.source.trim()
            val tgt = c.target.trim()
            if (src.length < 8 || tgt.length < 4) continue
            if (src.equals(tgt, ignoreCase = true)) continue
            val reason = when {
                c.speaker.isNotBlank() -> "beszélő: ${c.speaker}"
                else -> "tárgyalási sor"
            }
            out += PhraseCandidate(src, tgt, reason)
            if (out.size >= max) break
        }
        return out.toList()
    }

    private fun buildReport(
        summary: MeetingSummary.Result,
        quality: QualityOverview,
        phrases: List<PhraseCandidate>
    ): String {
        val sb = StringBuilder()
        sb.append(summary.fullText.trim()).append("\n\n")
        sb.append("Minőség (offline ellenőrzés)\n")
        sb.append("----------------------------\n")
        sb.append("Pontszám: ${quality.score0to100}/100\n")
        sb.append("Jelzések: ${quality.totalFindings} (kritikus: ${quality.criticalCount}, magas: ${quality.highCount})\n")
        if (quality.topExplanations.isNotEmpty()) {
            sb.append("\nFontos jelzések:\n")
            quality.topExplanations.forEach { sb.append("• ").append(it).append('\n') }
        }
        if (phrases.isNotEmpty()) {
            sb.append("\nTanulási jelöltek (nem mentve automatikusan):\n")
            phrases.take(6).forEach {
                sb.append("• ").append(it.source).append(" → ").append(it.target)
                    .append(" (").append(it.reason).append(")\n")
            }
        }
        sb.append("\nA minőségjelzések nem módosítják az átiratot. A tanulási jelöltek csak felhasználói mentés után kerülnek a szótárba.\n")
        return sb.toString()
    }

    /** Apply meeting prefs safely (delegates to MeetingMode). */
    fun enter(prefs: Prefs, temporary: Boolean = false) = MeetingMode.apply(prefs, temporary)

    /** Restore previous prefs (delegates to MeetingMode). */
    fun leave(prefs: Prefs) = MeetingMode.clear(prefs)
}
