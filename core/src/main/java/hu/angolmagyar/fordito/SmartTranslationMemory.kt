package hu.angolmagyar.fordito
import java.text.Normalizer
object SmartTranslationMemory {
    enum class Status { PENDING, APPROVED, REJECTED, RETIRED }
    enum class Scope { GLOBAL, PROJECT }
    data class Entry(
        val source: String,
        val target: String,
        val sourceLanguage: String,
        val targetLanguage: String,
        val scope: Scope = Scope.GLOBAL,
        val projectId: String? = null,
        val status: Status = Status.PENDING,
        val usageCount: Int = 0,
        val createdAtEpochMs: Long = 0L,
        val updatedAtEpochMs: Long = createdAtEpochMs,
        val context: String = ""
    )
    data class Match(
        val source: String,
        val candidates: List<Entry>,
        val ambiguous: Boolean,
        val requiresUserConfirmation: Boolean = true
    ) {
        val preferred: Entry? get() = candidates.firstOrNull()
    }
    data class Validation(val valid: Boolean, val errors: List<String>)
    fun normalizeText(value: String): String = Normalizer
        .normalize(value.trim(), Normalizer.Form.NFC)
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")
    fun normalizeLanguage(value: String): String = value.trim().lowercase().replace('_', '-')
    private val contextStopWords = setOf(
        "the", "and", "you", "that", "this", "with", "from", "for", "are", "was",
        "have", "has", "not", "but", "can", "will", "your", "all", "his", "her",
        "hogy", "mert", "vagy", "egy", "van", "volt", "lesz", "nem", "meg", "mar",
        "csak", "ezt", "azt", "aki", "ami", "mint", "utan", "elott", "minden"
    )
    private fun contextTokens(value: String): Set<String> = normalizeText(value)
        .split(' ')
        .filter { it.length >= 3 && it !in contextStopWords }
        .toSet()
    private fun contextSimilarity(left: Set<String>, right: Set<String>): Double {
        if (left.isEmpty() || right.isEmpty()) return 0.0
        return left.intersect(right).size.toDouble() / left.union(right).size.toDouble()
    }
    fun validate(entry: Entry): Validation {
        val errors = buildList {
            if (entry.source.isBlank()) add("A forrásszöveg nem lehet üres.")
            if (entry.target.isBlank()) add("A fordítás nem lehet üres.")
            if (entry.source.length > 2000) add("A forrásszöveg legfeljebb 2000 karakter lehet.")
            if (entry.target.length > 4000) add("A fordítás legfeljebb 4000 karakter lehet.")
            if (normalizeLanguage(entry.sourceLanguage).isBlank()) add("A forrásnyelv kötelező.")
            if (normalizeLanguage(entry.targetLanguage).isBlank()) add("A célnyelv kötelező.")
            if (normalizeLanguage(entry.sourceLanguage) == normalizeLanguage(entry.targetLanguage)) {
                add("A forrásnyelv és a célnyelv nem lehet azonos.")
            }
            if (entry.scope == Scope.PROJECT && entry.projectId.isNullOrBlank()) {
                add("Projektmemóriához projektazonosító szükséges.")
            }
            if (entry.usageCount < 0) add("A használatszám nem lehet negatív.")
        }
        return Validation(errors.isEmpty(), errors)
    }
    fun lookup(
        source: String,
        sourceLanguage: String,
        targetLanguage: String,
        projectId: String? = null,
        entries: List<Entry>,
        context: String = ""
    ): Match? {
        val key = normalizeText(source)
        if (key.isBlank()) return null
        val sourceLang = normalizeLanguage(sourceLanguage)
        val targetLang = normalizeLanguage(targetLanguage)
        val eligible = entries.filter { entry ->
            validate(entry).valid && entry.status == Status.APPROVED &&
                normalizeText(entry.source) == key &&
                normalizeLanguage(entry.sourceLanguage) == sourceLang &&
                normalizeLanguage(entry.targetLanguage) == targetLang &&
                (entry.scope == Scope.GLOBAL || entry.projectId == projectId)
        }
        if (eligible.isEmpty()) return null
        val projectMatches = eligible.filter { it.scope == Scope.PROJECT && it.projectId == projectId }
        val selectedScope = if (projectMatches.isNotEmpty()) Scope.PROJECT else Scope.GLOBAL
        val selected = eligible.filter { it.scope == selectedScope }
        // Ha van tárolt kontextus, azonos forrásmondat eltérő jelentéseit
        // a környező szöveg alapján rangsoroljuk. Kontextus nélkül a régi,
        // pontos egyezésű viselkedés marad érvényben.
        val queryTokens = contextTokens(context)
        val contextual = if (queryTokens.isNotEmpty()) {
            selected.map { entry -> entry to contextSimilarity(queryTokens, contextTokens(entry.context)) }
                .filter { it.second >= 0.15 }
        } else emptyList()
        val ranked = if (contextual.isNotEmpty()) {
            val best = contextual.maxOf { it.second }
            contextual.filter { it.second == best }.map { it.first }
        } else selected
        val distinctTargets = ranked.map { normalizeText(it.target) }.distinct()
        val ordered = ranked.sortedWith(
            compareByDescending<Entry> { it.usageCount }
                .thenByDescending { it.updatedAtEpochMs }
                .thenBy { normalizeText(it.target) }
        )
        return Match(source, ordered, ambiguous = distinctTargets.size > 1)
    }
    fun recordConfirmedUse(entry: Entry, nowEpochMs: Long): Entry = entry.copy(
        usageCount = (entry.usageCount + 1).coerceAtMost(Int.MAX_VALUE),
        updatedAtEpochMs = nowEpochMs
    )
    fun approve(entry: Entry, nowEpochMs: Long): Entry = entry.copy(
        status = Status.APPROVED,
        updatedAtEpochMs = nowEpochMs
    )
    fun retire(entry: Entry, nowEpochMs: Long): Entry = entry.copy(
        status = Status.RETIRED,
        updatedAtEpochMs = nowEpochMs
    )
}
