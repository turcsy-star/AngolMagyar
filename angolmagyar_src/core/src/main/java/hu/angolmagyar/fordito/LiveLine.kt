package hu.angolmagyar.fordito

/** Az élő kétpaneles nézet egy sora: ki, milyen nyelven mondta, eredeti és fordított szöveg. */
data class LiveLine(
    val id: Long,
    /** 1-től induló beszélősorszám, 0 = ismeretlen */
    val speaker: Int,
    /** felismert nyelv BCP-47 kódja (hu, en, de…), üres = ismeretlen */
    val lang: String,
    val original: String,
    val translated: String,
    /** false = még csak részleges (élő) szöveg */
    val final: Boolean = true
)

/** Az élő napló tiszta logikája (UI és szolgáltatás közös, tesztelhető része). */
object LiveLog {
    const val MAX_LINES = 60

    /** Hozzáfűz egy sort; a régi sorokat levágja. */
    fun append(lines: List<LiveLine>, line: LiveLine): List<LiveLine> {
        val l = lines.filter { it.final } + line
        return if (l.size > MAX_LINES) l.takeLast(MAX_LINES) else l
    }

    /** A részleges sor cseréje/beszúrása (a lista végén legfeljebb egy részleges sor lehet). */
    fun withPartial(lines: List<LiveLine>, partial: LiveLine?): List<LiveLine> {
        val base = lines.filter { it.final }
        return if (partial == null) base else base + partial.copy(final = false)
    }

    /** Egy már meglévő sor fordításának utólagos beírása (a fordítás aszinkron érkezik). */
    fun setTranslation(lines: List<LiveLine>, id: Long, translated: String): List<LiveLine> =
        lines.map { if (it.id == id) it.copy(translated = translated) else it }

    /** Igaz, ha az i. sor nyelve eltér az előző (nem üres nyelvű) soréhoz képest: "másik nyelven szól". */
    fun langSwitched(lines: List<LiveLine>, i: Int): Boolean {
        if (i <= 0 || i >= lines.size) return false
        val cur = lines[i].lang
        if (cur.isBlank()) return false
        val prev = lines.subList(0, i).lastOrNull { it.lang.isNotBlank() } ?: return false
        return prev.lang != cur
    }

    /** Igaz, ha az i. sor beszélője eltér az előzőétől (új "beszélő-buborék" kezdődik). */
    fun speakerChanged(lines: List<LiveLine>, i: Int): Boolean {
        if (i < 0 || i >= lines.size) return false
        if (i == 0) return true
        return lines[i - 1].speaker != lines[i].speaker
    }

    /** Egyszerű szöveges mentés a naplóról. */
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
