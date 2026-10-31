package hu.angolmagyar.fordito

/**
 * Lightweight helper for live / panel UIs (P5).
 * Runs TranslationIntelligence on a finalized source→target pair and returns
 * short, display-ready strings. Never mutates the translation.
 */
object LiveTranslationHints {

    data class Hints(
        val memorySuggestion: String?,
        val qualityLabels: List<String>,
        val contextNote: String?,
        val hasCriticalQuality: Boolean
    ) {
        val isEmpty: Boolean
            get() = memorySuggestion.isNullOrBlank() && qualityLabels.isEmpty() && contextNote.isNullOrBlank()
    }

    /**
     * @param memoryStore optional; Keystore failure → no memory suggestion
     * @param includeContext set false for high-frequency partial lines
     */
    fun forPair(
        source: String,
        target: String,
        sourceLanguage: String = "en",
        targetLanguage: String = "hu",
        projectId: String? = null,
        memoryStore: SmartTranslationMemoryStore? = null,
        includeContext: Boolean = true
    ): Hints {
        if (source.isBlank() || target.isBlank()) {
            return Hints(null, emptyList(), null, false)
        }
        val result = TranslationIntelligence.analyze(
            source = source,
            currentTarget = target,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            projectId = projectId,
            memoryStore = memoryStore,
            includeContext = includeContext
        )
        val memory = result.preferredMemoryTarget?.takeIf {
            !it.equals(target.trim(), ignoreCase = true)
        }
        val labels = result.qualityFlags.map { it.labelHu }
        val note = when {
            result.context == null -> null
            result.context.ambiguity == SmartContextTranslation.AmbiguityLevel.HIGH ->
                result.context.notes.firstOrNull() ?: "Magas kétértelműség – ellenőrizd a fordítást."
            result.context.ambiguity == SmartContextTranslation.AmbiguityLevel.MEDIUM ->
                result.context.notes.firstOrNull()
            result.context.alternatives.isNotEmpty() ->
                result.context.alternatives.firstOrNull()?.let { "Alternatíva: ${it.text} (${it.reason})" }
            else -> result.context.notes.firstOrNull()
        }
        val critical = result.qualityFlags.any { it.severity == "critical" }
        return Hints(memory, labels, note, critical)
    }
}
