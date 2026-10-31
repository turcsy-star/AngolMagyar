package hu.angolmagyar.fordito

/**
 * P2/P3 pipeline coordinator: Smart Translation Memory + Smart Context + quality flags.
 *
 * Rules enforced here (aligned with Platinum Intelligence 6.0 principles):
 * - Memory and context results are **suggestions only**; never auto-overwrite the current translation.
 * - Quality flags come from deterministic offline checks (TranslationCheck); they are not "AI certainty".
 * - If the memory store cannot be read (Keystore error), no memory match is offered.
 * - Project-scoped approved entries outrank global ones (handled inside SmartTranslationMemory).
 */
object TranslationIntelligence {

    data class QualityFlag(
        val code: String,
        val labelHu: String,
        val severity: String // "critical" | "high" | "medium" | "low" | "info"
    )

    data class Result(
        val source: String,
        val currentTarget: String,
        val memoryMatch: SmartTranslationMemory.Match?,
        val context: SmartContextTranslation.ContextSuggestion?,
        val qualityFlags: List<QualityFlag>,
        /** True when memory has a single non-ambiguous approved candidate the user may accept. */
        val hasClearMemorySuggestion: Boolean,
        /** True when context reports medium/high ambiguity or multiple alternatives. */
        val hasContextAlternatives: Boolean
    ) {
        val preferredMemoryTarget: String?
            get() = memoryMatch?.preferred?.target?.takeIf { hasClearMemorySuggestion }

        val summaryNotes: List<String>
            get() = buildList {
                memoryMatch?.let { m ->
                    if (m.ambiguous) add("A fordítási memória több eltérő jóváhagyott változatot talált – válassz manuálisan.")
                    else m.preferred?.let { add("Memóriajavaslat (jóváhagyott, ${it.scope.name.lowercase()}): „${it.target}”") }
                }
                context?.notes?.forEach { add(it) }
                qualityFlags.forEach { add("${it.labelHu} (${it.severity})") }
            }
    }

    /**
     * Analyze a source/target pair offline.
     * @param memoryStore optional; when null, memory lookup is skipped.
     * @param projectId optional project scope for memory.
     * @param includeContext when false, skips SmartContextTranslation (cheaper for bulk).
     */
    fun analyze(
        source: String,
        currentTarget: String,
        sourceLanguage: String = "en",
        targetLanguage: String = "hu",
        projectId: String? = null,
        memoryStore: SmartTranslationMemoryStore? = null,
        includeContext: Boolean = true
    ): Result {
        val src = source.trim()
        val tgt = currentTarget.trim()

        val memoryMatch: SmartTranslationMemory.Match? = memoryStore?.lookup(
            source = src,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            projectId = projectId
        )

        val context: SmartContextTranslation.ContextSuggestion? =
            if (includeContext && src.isNotEmpty() &&
                (sourceLanguage.startsWith("en", ignoreCase = true) || sourceLanguage.equals("en-US", true) || sourceLanguage.equals("en-GB", true))
            ) {
                SmartContextTranslation.suggest(src, tgt.ifBlank { null })
            } else null

        val qualityFlags = buildQualityFlags(src, tgt)

        val clearMemory = memoryMatch != null && !memoryMatch.ambiguous && memoryMatch.preferred != null
        val hasAlts = context != null && (
            context.alternatives.isNotEmpty() ||
                context.ambiguity == SmartContextTranslation.AmbiguityLevel.MEDIUM ||
                context.ambiguity == SmartContextTranslation.AmbiguityLevel.HIGH
            )

        return Result(
            source = src,
            currentTarget = tgt,
            memoryMatch = memoryMatch,
            context = context,
            qualityFlags = qualityFlags,
            hasClearMemorySuggestion = clearMemory,
            hasContextAlternatives = hasAlts
        )
    }

    /** Deterministic offline quality flags for a single pair (EN→HU oriented). */
    fun buildQualityFlags(sourceEn: String, targetHu: String): List<QualityFlag> {
        if (sourceEn.isBlank() || targetHu.isBlank()) return emptyList()
        return TranslationCheck.check(sourceEn, targetHu).map { code ->
            when (code) {
                "szám eltér" -> QualityFlag(code, "Szám eltérés a forrás és a fordítás között", "high")
                "tagadás eltér" -> QualityFlag(code, "Tagadás eltérés – a jelentés megfordulhat", "critical")
                "név hiányzik" -> QualityFlag(code, "Tulajdonnév / betűszó hiányzik a fordításból", "high")
                else -> QualityFlag(code, code, "medium")
            }
        }
    }

    /**
     * Build a PENDING memory entry from a user-approved translation.
     * Caller must persist via SmartTranslationMemoryStore.save / approve.
     */
    fun pendingEntry(
        source: String,
        target: String,
        sourceLanguage: String,
        targetLanguage: String,
        projectId: String? = null,
        nowEpochMs: Long = System.currentTimeMillis()
    ): SmartTranslationMemory.Entry = SmartTranslationMemory.Entry(
        source = source.trim(),
        target = target.trim(),
        sourceLanguage = sourceLanguage,
        targetLanguage = targetLanguage,
        scope = if (projectId.isNullOrBlank()) SmartTranslationMemory.Scope.GLOBAL else SmartTranslationMemory.Scope.PROJECT,
        projectId = projectId?.takeIf { it.isNotBlank() },
        status = SmartTranslationMemory.Status.PENDING,
        usageCount = 0,
        createdAtEpochMs = nowEpochMs,
        updatedAtEpochMs = nowEpochMs
    )
}
