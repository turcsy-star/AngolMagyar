package hu.angolmagyar.fordito

/** Output style for compatible online language-model translators. Offline engines remain unchanged. */
enum class TranslationStyle(val storageKey: String, val labelHu: String, val instruction: String) {
    NATURAL("natural", "Természetes", "Prefer natural, fluent target-language phrasing while preserving the source meaning and tone."),
    PRECISE("precise", "Pontos", "Prefer a close, faithful translation. Preserve details and do not add implied information."),
    FORMAL("formal", "Hivatalos", "Use a polite, professional and formal register unless the source clearly requires otherwise."),
    CASUAL("casual", "Közvetlen", "Use relaxed, conversational language while preserving the source meaning and avoiding forced slang.");

    companion object {
        fun fromStorage(value: String?): TranslationStyle = entries.firstOrNull { it.storageKey == value } ?: NATURAL
    }
}
