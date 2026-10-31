package hu.angolmagyar.fordito.intelligence

/** Offline, curated English synonym hints. Suggestions are context-sensitive prompts, not replacements. */
object SynonymEngine {
    private val entries = mapOf(
        "good" to listOf("excellent" to "kiemelkedő", "useful" to "hasznos", "reliable" to "megbízható", "pleasant" to "kellemes"),
        "bad" to listOf("poor" to "gyenge", "harmful" to "káros", "unpleasant" to "kellemetlen", "inadequate" to "nem megfelelő"),
        "important" to listOf("essential" to "elengedhetetlen", "significant" to "jelentős", "crucial" to "döntő fontosságú"),
        "big" to listOf("large" to "nagy", "huge" to "hatalmas", "substantial" to "jelentős méretű"),
        "small" to listOf("tiny" to "apró", "minor" to "kisebb", "compact" to "kompakt"),
        "happy" to listOf("glad" to "örülő", "delighted" to "elragadtatott", "pleased" to "elégedett"),
        "sad" to listOf("unhappy" to "boldogtalan", "upset" to "zaklatott", "disappointed" to "csalódott"),
        "say" to listOf("state" to "kijelent", "mention" to "megemlít", "remark" to "megjegyez"),
        "make" to listOf("create" to "létrehoz", "produce" to "előállít", "develop" to "fejleszt"),
        "help" to listOf("assist" to "segít", "support" to "támogat", "guide" to "útmutatást ad"),
        "beautiful" to listOf("attractive" to "vonzó", "lovely" to "elbűvölő", "stunning" to "lenyűgöző"),
        "fast" to listOf("quick" to "gyors", "rapid" to "sebes", "swift" to "fürge"),
        "smart" to listOf("clever" to "okos", "intelligent" to "intelligens", "bright" to "eszű")
    )
    fun suggest(word: String): List<Pair<String, String>> =
        entries[word.trim().lowercase()].orEmpty()
    fun supportedWords(): Set<String> = entries.keys
}
