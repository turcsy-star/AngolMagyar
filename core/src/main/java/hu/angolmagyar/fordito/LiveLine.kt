package hu.angolmagyar.fordito
data class LiveLine(
    val id: Long,
    val speaker: Int,
    val lang: String,
    val original: String,
    val translated: String,
    val final: Boolean = true
)
object LiveLog {
    const val MAX_LINES = 60
    fun append(lines: List<LiveLine>, line: LiveLine): List<LiveLine> {
        val l = lines.filter { it.final } + line
        return if (l.size > MAX_LINES) l.takeLast(MAX_LINES) else l
    }
    fun withPartial(lines: List<LiveLine>, partial: LiveLine?): List<LiveLine> {
        val base = lines.filter { it.final }
        return if (partial == null) base else base + partial.copy(final = false)
    }
    fun setTranslation(lines: List<LiveLine>, id: Long, translated: String): List<LiveLine> =
        lines.map { if (it.id == id) it.copy(translated = translated) else it }
    fun langSwitched(lines: List<LiveLine>, i: Int): Boolean {
        if (i <= 0 || i >= lines.size) return false
        val cur = lines[i].lang
        if (cur.isBlank()) return false
        val prev = lines.subList(0, i).lastOrNull { it.lang.isNotBlank() } ?: return false
        return prev.lang != cur
    }
    fun speakerChanged(lines: List<LiveLine>, i: Int): Boolean {
        if (i < 0 || i >= lines.size) return false
        if (i == 0) return true
        return lines[i - 1].speaker != lines[i].speaker
    }
    fun toText(lines: List<LiveLine>, targetName: String): String = buildString {
        for (l in lines) {
            if (!l.final) continue
            append(if (l.speaker > 0) "[${l.speaker}. beszélő" else "[?")
            if (l.lang.isNotBlank()) append(", ").append(LangCatalog.nameHu(l.lang))
            append("] ").append(l.original).append('\n')
            if (l.translated.isNotBlank()) append("   → ").append(targetName).append(": ").append(l.translated).append('\n')
        }
    }
}
