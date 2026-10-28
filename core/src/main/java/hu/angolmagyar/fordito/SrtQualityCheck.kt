package hu.angolmagyar.fordito

/** Export előtti, determinisztikus SRT-ellenőrzés. Nem módosítja a feliratokat. */
object SrtQualityCheck {
    enum class Severity { ERROR, WARNING, INFO }

    data class Issue(
        val cueIndex: Int,
        val code: String,
        val severity: Severity,
        val message: String
    )

    data class Report(val issues: List<Issue>) {
        val errorCount: Int get() = issues.count { it.severity == Severity.ERROR }
        val warningCount: Int get() = issues.count { it.severity == Severity.WARNING }
        val canExport: Boolean get() = errorCount == 0
    }

    /**
     * Ellenőrzi az időbélyegeket, az üres tartalmakat és az időbeli átfedéseket.
     * BOTH módban legalább az egyik nyelvi mezőnek tartalmaznia kell szöveget;
     * TARGET_ONLY módban az üres fordítás figyelmeztetés.
     */
    fun inspect(cues: List<SrtExporter.Cue>, mode: SrtExporter.Mode): Report {
        val issues = mutableListOf<Issue>()
        var previous: SrtExporter.Cue? = null

        cues.forEachIndexed { index, cue ->
            val number = index + 1
            if (cue.startMs < 0) {
                issues += Issue(number, "NEGATIVE_START", Severity.ERROR, "A kezdési idő nem lehet negatív.")
            }
            if (cue.endMs <= cue.startMs) {
                issues += Issue(number, "INVALID_DURATION", Severity.ERROR, "A záróidőnek a kezdés után kell lennie.")
            }

            val sourceBlank = cue.source.isBlank()
            val targetBlank = cue.target.isBlank()
            when (mode) {
                SrtExporter.Mode.SOURCE_ONLY -> if (sourceBlank) {
                    issues += Issue(number, "EMPTY_SOURCE", Severity.WARNING, "A forrásszöveg üres.")
                }
                SrtExporter.Mode.TARGET_ONLY -> if (targetBlank) {
                    issues += Issue(number, "EMPTY_TRANSLATION", Severity.WARNING, "A magyar fordítás üres.")
                }
                SrtExporter.Mode.BOTH -> {
                    if (sourceBlank && targetBlank) {
                        issues += Issue(number, "EMPTY_CUE", Severity.WARNING, "Mindkét szövegmező üres.")
                    } else if (sourceBlank) {
                        issues += Issue(number, "EMPTY_SOURCE", Severity.INFO, "A forrásszöveg hiányzik.")
                    } else if (targetBlank) {
                        issues += Issue(number, "EMPTY_TRANSLATION", Severity.WARNING, "A magyar fordítás üres.")
                    }
                }
            }

            val prior = previous
            if (prior != null && cue.startMs < prior.startMs) {
                issues += Issue(number, "OUT_OF_ORDER", Severity.ERROR, "A feliratszakaszok nincsenek időrendben.")
            } else if (prior != null && cue.startMs < prior.endMs && cue.endMs > cue.startMs && prior.endMs > prior.startMs) {
                issues += Issue(number, "OVERLAP", Severity.WARNING, "Ez a szakasz időben átfed az előzővel.")
            }
            previous = cue
        }
        return Report(issues)
    }
}
