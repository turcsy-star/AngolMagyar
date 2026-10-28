package hu.angolmagyar.fordito

/**
 * Konzervatív, determinisztikus utófeldolgozó az átiratok végső ellenőrzéséhez.
 *
 * Nem talál ki hiányzó tartalmat: csak explicit felhasználói javítólistákat,
 * szótári alakokat és biztonságos formázási szabályokat alkalmaz.
 * A "reviewRecommended" jelzés ellenőrzési javaslat, nem nyelvi modellből származó pontossági mérés.
 */
object SubtitlePostProcessor {

    data class Options(
        val applyEnglishRules: Boolean = true,
        val applyHungarianRules: Boolean = true,
        val applyGlossary: Boolean = true,
        val removeFillers: Boolean = false,
        val normalizePunctuation: Boolean = true,
        val addTerminalPunctuation: Boolean = false
    )

    data class Result(
        val text: String,
        val changed: Boolean,
        val reviewRecommended: Boolean,
        val appliedRuleCount: Int,
        val notes: List<String>
    )

    fun process(
        text: String,
        language: Language,
        englishRules: List<Pair<Regex, String>> = emptyList(),
        hungarianRules: List<Pair<Regex, String>> = emptyList(),
        glossary: List<Pair<String, String>> = emptyList(),
        options: Options = Options()
    ): Result {
        if (text.isBlank()) {
            return Result(text, changed = false, reviewRecommended = false, appliedRuleCount = 0, notes = emptyList())
        }

        var result = text
        var rulesApplied = 0
        val notes = mutableListOf<String>()

        val rules = when (language) {
            Language.ENGLISH -> if (options.applyEnglishRules) englishRules else emptyList()
            Language.HUNGARIAN -> if (options.applyHungarianRules) hungarianRules else emptyList()
        }
        for ((regex, replacement) in rules) {
            val updated = regex.replace(result, Regex.escapeReplacement(replacement))
            if (updated != result) rulesApplied++
            result = updated
        }

        if (options.applyGlossary && glossary.isNotEmpty()) {
            val updated = when (language) {
                Language.ENGLISH -> TextPolish.polishEnglish(result, glossary, options.removeFillers)
                Language.HUNGARIAN -> TextPolish.polishHungarian(result, glossary, options.removeFillers)
            }
            if (updated != result) rulesApplied++
            result = updated
        } else {
            result = when (language) {
                Language.ENGLISH -> TextPolish.polishEnglish(result, removeFillers = options.removeFillers)
                Language.HUNGARIAN -> TextPolish.polishHungarian(result, removeFillers = options.removeFillers)
            }
        }

        if (options.normalizePunctuation) {
            val normalized = result
                .replace(Regex("[ \\t]+([,.;:!?…])"), "$1")
                .replace(Regex("([!?]){3,}"), "$1")
                .replace(Regex("([,;:])\\1+"), "$1")
                .replace(Regex("[ \\t]{2,}"), " ")
                .trim()
            if (normalized != result) {
                rulesApplied++
                result = normalized
            }
        }

        if (!options.addTerminalPunctuation) {
            // A felirat lehet szándékos töredék: az automatikus pontozást visszavonjuk,
            // ha a bemenetnek nem volt lezáró írásjele.
            val originalTrimmed = text.trimEnd()
            val originalHadTerminal = originalTrimmed.lastOrNull() in listOf('.', '!', '?', '…', '”', '"')
            if (!originalHadTerminal && result.endsWith(".") && result.split(Regex("\\s+")).size >= 4) {
                result = result.dropLast(1)
            }
        }

        if (Regex("\\b(?:TODO|FIXME|\\[unclear\\]|\\[inaudible\\])\\b", RegexOption.IGNORE_CASE).containsMatchIn(result)) {
            notes += "Ellenőrizd a bizonytalan vagy szerkesztői jelöléseket."
        }
        if (Regex("\\b\\w{25,}\\b").containsMatchIn(result)) {
            notes += "Szokatlanul hosszú szó található; ellenőrizd a beszédfelismerést."
        }
        if (Regex("[!?]{2,}").containsMatchIn(text)) {
            notes += "A forrásban ismétlődő írásjelek szerepeltek."
        }

        return Result(
            text = result,
            changed = result != text,
            reviewRecommended = notes.isNotEmpty(),
            appliedRuleCount = rulesApplied,
            notes = notes.distinct()
        )
    }

    enum class Language { ENGLISH, HUNGARIAN }
}
