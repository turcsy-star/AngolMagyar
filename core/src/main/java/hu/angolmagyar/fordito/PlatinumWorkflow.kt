package hu.angolmagyar.fordito
object PlatinumWorkflow {
    enum class Kind { TEXT_TRANSLATION, AUDIO_TO_SUBTITLE }
    enum class RunStatus { READY, RUNNING, FAILED, COMPLETED, CANCELLED }
    enum class StepStatus { PENDING, RUNNING, SUCCEEDED, FAILED, CANCELLED }
    data class StepDefinition(
        val id: String,
        val title: String,
        val dependsOn: Set<String> = emptySet(),
        val requiredCapabilities: Set<String> = emptySet(),
        val requiresNetwork: Boolean = false,
        val maxAttempts: Int = 2
    )
    data class Definition(val id: String, val kind: Kind, val steps: List<StepDefinition>)
    data class StepState(
        val definition: StepDefinition,
        val status: StepStatus = StepStatus.PENDING,
        val attempts: Int = 0,
        val lastError: String? = null
    )
    data class Run(
        val runId: String,
        val definition: Definition,
        val offlineMode: Boolean,
        val steps: List<StepState>,
        val status: RunStatus = RunStatus.READY,
        val revision: Long = 1
    )
    sealed class Outcome {
        data class Accepted(val run: Run) : Outcome()
        data class Rejected(val reason: String) : Outcome()
    }
    fun validate(definition: Definition): List<String> = buildList {
        if (definition.id.isBlank()) add("A munkafolyamat azonosítója nem lehet üres.")
        if (definition.steps.isEmpty()) add("A munkafolyamatnak legalább egy lépést kell tartalmaznia.")
        val ids = definition.steps.map { it.id }
        if (ids.any { it.isBlank() }) add("A lépésazonosító nem lehet üres.")
        if (ids.size != ids.toSet().size) add("A lépésazonosítóknak egyedinek kell lenniük.")
        val known = ids.toSet()
        definition.steps.forEach { step ->
            if (step.dependsOn.any { it !in known }) add("Ismeretlen függőség: ${step.id}.")
            if (step.id in step.dependsOn) add("Egy lépés nem függhet saját magától: ${step.id}.")
            if (step.maxAttempts < 1) add("A próbálkozások száma legalább 1 legyen: ${step.id}.")
        }
        if (hasCycle(definition.steps)) add("A munkafolyamat függőségi gráfja körkörös.")
    }
    fun create(runId: String, definition: Definition, offlineMode: Boolean): Outcome {
        if (runId.isBlank()) return Outcome.Rejected("A futásazonosító nem lehet üres.")
        val errors = validate(definition)
        if (errors.isNotEmpty()) return Outcome.Rejected(errors.distinct().joinToString(" "))
        return Outcome.Accepted(Run(runId, definition, offlineMode, definition.steps.map(::StepState)))
    }
    fun nextRunnable(run: Run, capabilities: Set<String>, networkAvailable: Boolean): String? {
        if (run.status in setOf(RunStatus.COMPLETED, RunStatus.CANCELLED, RunStatus.FAILED)) return null
        val states = run.steps.associateBy { it.definition.id }
        return run.steps.firstOrNull { state ->
            state.status == StepStatus.PENDING &&
                state.definition.dependsOn.all { states[it]?.status == StepStatus.SUCCEEDED } &&
                state.definition.requiredCapabilities.all { it in capabilities } &&
                !state.definition.requiresNetwork.let { needsNetwork -> needsNetwork && (run.offlineMode || !networkAvailable) }
        }?.definition?.id
    }
    fun start(run: Run, stepId: String, capabilities: Set<String>, networkAvailable: Boolean): Outcome {
        if (run.status in setOf(RunStatus.COMPLETED, RunStatus.CANCELLED, RunStatus.FAILED)) {
            return Outcome.Rejected("A lezárt munkafolyamat nem indítható.")
        }
        if (run.steps.any { it.status == StepStatus.RUNNING }) return Outcome.Rejected("Már fut egy másik lépés.")
        val index = run.steps.indexOfFirst { it.definition.id == stepId }
        if (index < 0) return Outcome.Rejected("Ismeretlen munkafolyamat-lépés.")
        val state = run.steps[index]
        if (state.status != StepStatus.PENDING) return Outcome.Rejected("A lépés nem várakozó állapotú.")
        val states = run.steps.associateBy { it.definition.id }
        if (!state.definition.dependsOn.all { states[it]?.status == StepStatus.SUCCEEDED }) {
            return Outcome.Rejected("A lépés előfeltételei még nem teljesültek.")
        }
        val missing = state.definition.requiredCapabilities - capabilities
        if (missing.isNotEmpty()) return Outcome.Rejected("Hiányzó helyi képesség: ${missing.sorted().joinToString(", ")}.")
        if (state.definition.requiresNetwork && (run.offlineMode || !networkAvailable)) {
            return Outcome.Rejected("Ez a lépés hálózatot igényel, offline módban nem indítható.")
        }
        if (state.attempts >= state.definition.maxAttempts) return Outcome.Rejected("A lépés elérte a maximális próbálkozásszámot.")
        val updated = run.steps.toMutableList()
        updated[index] = state.copy(status = StepStatus.RUNNING, attempts = state.attempts + 1, lastError = null)
        return Outcome.Accepted(run.copy(steps = updated, status = RunStatus.RUNNING, revision = run.revision + 1))
    }
    fun complete(run: Run, stepId: String): Outcome = updateRunning(run, stepId) { it.copy(status = StepStatus.SUCCEEDED, lastError = null) }
    fun fail(run: Run, stepId: String, safeError: String): Outcome = updateRunning(run, stepId) {
        it.copy(status = StepStatus.FAILED, lastError = safeError.trim().take(500).ifBlank { "Ismeretlen feldolgozási hiba." })
    }
    fun retry(run: Run, stepId: String): Outcome {
        if (run.status in setOf(RunStatus.COMPLETED, RunStatus.CANCELLED)) return Outcome.Rejected("A lezárt munkafolyamat nem módosítható.")
        val index = run.steps.indexOfFirst { it.definition.id == stepId }
        if (index < 0) return Outcome.Rejected("Ismeretlen munkafolyamat-lépés.")
        val state = run.steps[index]
        if (state.status != StepStatus.FAILED) return Outcome.Rejected("Csak sikertelen lépés indítható újra.")
        if (state.attempts >= state.definition.maxAttempts) return Outcome.Rejected("A lépés elérte a maximális próbálkozásszámot.")
        val updated = run.steps.toMutableList()
        updated[index] = state.copy(status = StepStatus.PENDING, lastError = null)
        return Outcome.Accepted(restate(run.copy(steps = updated, revision = run.revision + 1)))
    }
    fun cancel(run: Run): Outcome {
        if (run.status == RunStatus.COMPLETED) return Outcome.Rejected("A befejezett munkafolyamat nem szakítható meg.")
        if (run.status == RunStatus.CANCELLED) return Outcome.Accepted(run)
        val updated = run.steps.map { state ->
            if (state.status == StepStatus.PENDING || state.status == StepStatus.RUNNING) state.copy(status = StepStatus.CANCELLED) else state
        }
        return Outcome.Accepted(run.copy(steps = updated, status = RunStatus.CANCELLED, revision = run.revision + 1))
    }
    private fun updateRunning(run: Run, stepId: String, transform: (StepState) -> StepState): Outcome {
        val index = run.steps.indexOfFirst { it.definition.id == stepId }
        if (index < 0) return Outcome.Rejected("Ismeretlen munkafolyamat-lépés.")
        if (run.steps[index].status != StepStatus.RUNNING) return Outcome.Rejected("Csak futó lépés zárható le.")
        val updated = run.steps.toMutableList()
        updated[index] = transform(updated[index])
        return Outcome.Accepted(restate(run.copy(steps = updated, revision = run.revision + 1)))
    }
    private fun restate(run: Run): Run {
        val status = when {
            run.steps.all { it.status == StepStatus.SUCCEEDED } -> RunStatus.COMPLETED
            run.steps.any { it.status == StepStatus.RUNNING } -> RunStatus.RUNNING
            run.steps.any { it.status == StepStatus.FAILED } -> RunStatus.FAILED
            else -> RunStatus.READY
        }
        return run.copy(status = status)
    }
    private fun hasCycle(steps: List<StepDefinition>): Boolean {
        val byId = steps.associateBy { it.id }
        val visiting = mutableSetOf<String>()
        val visited = mutableSetOf<String>()
        fun visit(id: String): Boolean {
            if (id in visiting) return true
            if (id in visited) return false
            visiting += id
            val cyclic = byId[id]?.dependsOn.orEmpty().any(::visit)
            visiting -= id
            visited += id
            return cyclic
        }
        return steps.any { visit(it.id) }
    }
    val textTranslationTemplate = Definition(
        id = "platinum_text_translation",
        kind = Kind.TEXT_TRANSLATION,
        steps = listOf(
            StepDefinition("prepare", "Szöveg előkészítése"),
            StepDefinition("translate_local", "Helyi fordítás", setOf("prepare"), setOf("offline_translation")),
            StepDefinition("quality_audit", "Fordításminőség ellenőrzése", setOf("translate_local"), setOf("quality_audit")),
            StepDefinition("save_local", "Eredmény mentése", setOf("quality_audit"), setOf("local_storage"))
        )
    )
    val audioToSubtitleTemplate = Definition(
        id = "platinum_audio_to_subtitle",
        kind = Kind.AUDIO_TO_SUBTITLE,
        steps = listOf(
            StepDefinition("import_audio", "Hangfájl előkészítése"),
            StepDefinition("transcribe_local", "Offline beszédfelismerés", setOf("import_audio"), setOf("offline_asr")),
            StepDefinition("review_transcript", "Átirat ellenőrzése", setOf("transcribe_local")),
            StepDefinition("translate_local", "Offline fordítás", setOf("review_transcript"), setOf("offline_translation")),
            StepDefinition("quality_audit", "Minőségellenőrzés", setOf("translate_local"), setOf("quality_audit")),
            StepDefinition("export_subtitle", "Felirat exportálása", setOf("quality_audit"), setOf("subtitle_export"))
        )
    )
}
