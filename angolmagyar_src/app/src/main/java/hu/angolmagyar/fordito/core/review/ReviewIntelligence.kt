package hu.angolmagyar.fordito.core.review

import java.security.MessageDigest
import java.text.Normalizer
import hu.angolmagyar.fordito.TranslationCheck

/** Deterministic, offline review helpers. These flag possible problems; they never rewrite transcript text. */
object ReviewIntelligence {
    enum class Severity { INFO, LOW, MEDIUM, HIGH, CRITICAL }
    enum class Kind { LOW_CONFIDENCE, NUMBER, NEGATION, DATE_OR_TIME, NAME_CANDIDATE, SHORT_FRAGMENT, REPEATED_TEXT, EMPTY_TEXT, BAD_TIMING }

    data class Finding(
        val lineIndex: Int,
        val kind: Kind,
        val severity: Severity,
        val explanation: String,
        val evidence: String = ""
    )

    data class Line(
        val text: String,
        val confidencePercent: Int? = null,
        val startMs: Long? = null,
        val endMs: Long? = null,
        /** Ha forrás és fordítás is megvan, a szám/tagadás/név jelzés csak eltérés esetén keletkezik. */
        val source: String? = null,
        val target: String? = null,
        /** Szó-szintű Whisper-bizonytalanság jelzése (a szerkesztőből). */
        val weakWords: Boolean = false
    )

    data class ReviewSummary(
        val totalLines: Int,
        val flaggedLines: Int,
        val criticalFindings: Int,
        val highFindings: Int,
        val findings: List<Finding>
    )

    private val numberPattern = Regex("(?<!\\w)(?:\\d{1,3}(?:[ .]\\d{3})+|\\d+)(?:[.,]\\d+)?%?(?!\\w)")
    private val dateTimePattern = Regex("(?i)(?<!\\w)(?:\\d{1,2}[./-]\\d{1,2}(?:[./-]\\d{2,4})?|\\d{1,2}:\\d{2}|\\d{4}[./-]\\d{1,2}[./-]\\d{1,2})(?!\\w)")
    private val negationPattern = Regex("(?i)(?<!\\p{L})(?:nem|se|sem|soha|nincs|nincsen|ne|semmi|not|never|no|none|without)(?!\\p{L})")
    private val shortWords = setOf(
        "nem", "ne", "se", "sem", "nincs", "igen", "nem.", "de", "és", "vagy",
        "no", "not", "yes", "yeah", "yep", "nope", "ok", "okay", "sure", "thanks", "right"
    )

    /** Analyze only. Nothing in this API mutates or autocorrects the supplied lines. */
    fun analyze(lines: List<Line>, lowConfidencePercent: Int = 55): ReviewSummary {
        val findings = mutableListOf<Finding>()
        val seen = HashMap<String, Int>()
        lines.forEachIndexed { index, line ->
            val text = line.text.trim()
            fun add(kind: Kind, severity: Severity, explanation: String, evidence: String = "") {
                findings += Finding(index, kind, severity, explanation, evidence)
            }
            if (text.isEmpty()) add(Kind.EMPTY_TEXT, Severity.MEDIUM, "Üres feliratsor; ellenőrizd, nem maradt-e ki beszéd.")
            val conf = line.confidencePercent
            if (conf != null && conf < lowConfidencePercent) {
                add(Kind.LOW_CONFIDENCE, if (conf < 30) Severity.HIGH else Severity.MEDIUM,
                    "Alacsony szó-/sorconfidence; hallgasd vissza.", "$conf%")
            }
            val paired = !line.source.isNullOrBlank() && !line.target.isNullOrBlank()
            if (line.weakWords) add(Kind.LOW_CONFIDENCE, Severity.MEDIUM, "Gyenge szókonfidencia; hallgasd vissza a jelölt szavakat.")
            if (paired) {
                // Csak tényleges eltérésre szólunk: a forrásban meglévő szám/tagadás/név hiányzik vagy más a fordításban.
                for (flag in TranslationCheck.check(line.source!!, line.target!!)) when (flag) {
                    "szám eltér" -> add(Kind.NUMBER, Severity.HIGH, "A forrás és a fordítás számai eltérnek.",
                        numberPattern.findAll(line.source!!).joinToString(", ") { it.value })
                    "tagadás eltér" -> add(Kind.NEGATION, Severity.CRITICAL, "A forrásban tagadás van, a fordításban nincs; a jelentés megfordulhat.")
                    "név hiányzik" -> add(Kind.NAME_CANDIDATE, Severity.HIGH, "A forrás egyik neve hiányzik a fordításból.")
                }
            } else {
                // Egynyelvű sor: ha ismert és elég magas a bizonyosság, nem jelzünk; ha nem ismert, csak alacsony prioritással.
                val confident = conf != null && conf >= lowConfidencePercent
                val unknown = conf == null
                fun sev(full: Severity) = if (unknown) Severity.values()[maxOf(0, full.ordinal - 2)] else full
                if (!confident) {
                    val numbers = numberPattern.findAll(text).map { it.value }.toList()
                    if (numbers.isNotEmpty()) add(Kind.NUMBER, sev(Severity.HIGH), "Számot tartalmaz: érdemes a hanggal összevetni.", numbers.joinToString(", "))
                    val negations = negationPattern.findAll(text).map { it.value }.toList()
                    if (negations.isNotEmpty()) add(Kind.NEGATION, sev(Severity.CRITICAL), "Tagadást vagy kizáró szót tartalmaz; a jelentés megfordulhat.", negations.joinToString(", "))
                    val dates = dateTimePattern.findAll(text).map { it.value }.toList()
                    if (dates.isNotEmpty()) add(Kind.DATE_OR_TIME, sev(Severity.HIGH), "Dátumot vagy időpontot tartalmaz.", dates.joinToString(", "))
                }
            }
            val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
            if (text.isNotEmpty() && words.size <= 2 && text.length < 18 && words.none { it.lowercase().trimEnd('.', ',', '!', '?') in shortWords }) {
                add(Kind.SHORT_FRAGMENT, Severity.LOW, "Rövid töredék; lehet természetes válasz is, csak ellenőrzési jelzés.")
            }
            val normalized = normalize(text)
            if (normalized.length >= 12) {
                val previous = seen.putIfAbsent(normalized, index)
                if (previous != null && index - previous <= 30) {
                    add(Kind.REPEATED_TEXT, Severity.MEDIUM, "Hasonló szöveg ismétlődik 30 soron belül.", "${previous + 1}. sor")
                }
            }
            if (line.startMs != null && line.endMs != null && line.endMs <= line.startMs) {
                add(Kind.BAD_TIMING, Severity.HIGH, "A felirat időbélyegeinek sorrendje hibás.")
            }
        }
        val unique = findings.distinctBy { Triple(it.lineIndex, it.kind, it.evidence) }
        return ReviewSummary(lines.size, unique.map { it.lineIndex }.distinct().size,
            unique.count { it.severity == Severity.CRITICAL }, unique.count { it.severity == Severity.HIGH },
            unique.sortedWith(compareByDescending<Finding> { it.severity.ordinal }.thenBy { it.lineIndex })
        )
    }

    /** Prioritize only the lines selected for review; a zero budget means no work is scheduled. */
    fun planTargetedReview(summary: ReviewSummary, maxLines: Int, maxAudioSeconds: Int,
                           estimatedSecondsPerLine: Int = 8): List<Int> {
        if (maxLines <= 0 || maxAudioSeconds <= 0 || estimatedSecondsPerLine <= 0) return emptyList()
        val capacity = minOf(maxLines, maxAudioSeconds / estimatedSecondsPerLine)
        if (capacity <= 0) return emptyList()
        return summary.findings.asSequence()
            .sortedWith(compareByDescending<Finding> { it.severity.ordinal }.thenBy { it.lineIndex })
            .map { it.lineIndex }.distinct().take(capacity).toList()
    }

    /** Stable fingerprint can be used to avoid processing an unchanged audio/text pair twice. */
    fun fingerprint(vararg parts: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        parts.forEach { part ->
            val bytes = part.toByteArray(Charsets.UTF_8)
            digest.update((bytes.size ushr 24).toByte())
            digest.update((bytes.size ushr 16).toByte())
            digest.update((bytes.size ushr 8).toByte())
            digest.update(bytes.size.toByte())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun normalize(value: String): String = Normalizer.normalize(value.lowercase(), Normalizer.Form.NFC)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
}

/** Audit entry model for UI/storage integration. Keep before/after values for reversible edits. */
data class ReviewAuditEntry(
    val timestampEpochMs: Long,
    val lineIndex: Int,
    val beforeText: String,
    val afterText: String,
    val reason: String,
    val acceptedByUser: Boolean,
    val source: String = "manual"
)

/** A measured benchmark result; WER is intentionally kept separate from semantic critical-error counts. */
data class RecognitionBenchmark(
    val version: String,
    val referenceId: String,
    val wordErrorRate: Double,
    val criticalErrors: Int,
    val elapsedMs: Long,
    val audioDurationMs: Long
) {
    val realtimeFactor: Double
        get() = if (audioDurationMs <= 0) 0.0 else elapsedMs.toDouble() / audioDurationMs
}
