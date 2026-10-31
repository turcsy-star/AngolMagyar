package hu.angolmagyar.fordito

/**
 * Offline tárgyalási összefoglaló: statisztika, beszélők, kulcsmondatok.
 * Nem LLM – szabályalapú, gyors, adatvédelembarát.
 */
object MeetingSummary {

    data class Result(
        val title: String,
        val durationLabel: String,
        val cueCount: Int,
        val speakers: List<Pair<String, Int>>, // label → sorok száma
        val keyLines: List<String>,
        val fullText: String,
        val actions: List<String> = emptyList(),
        val decisions: List<String> = emptyList()
    )

    /** Ékezet-független kulcsszavak (a [TranscriptRepo.fold] formájára). */
    private val ACTION_WORDS = listOf(
        "meg kell", "kell hogy", "feladat", "hatarido", "legyszi", "kerlek", "kerem",
        "elintez", "felelos", "teendo", "kuldd", "kuldj", "kovetkezo lepes", "utanajar",
        "action item", "deadline", "need to", "has to", "have to", "follow up", "to do",
        "i will send", "we will", "please send", "by monday", "by friday", "next step"
    )
    private val DECISION_WORDS = listOf(
        "megegyezt", "elfogadtuk", "dontottunk", "dontes", "jovahagy", "megszavaz",
        "rendben, akkor", "maradunk", "we agreed", "we decided", "decision", "approved",
        "agreed", "let's go with"
    )

    fun generate(
        cues: List<SrtExporter.Cue>,
        title: String = "Tárgyalás összefoglaló",
        speakerMap: Map<String, String> = emptyMap()
    ): Result {
        if (cues.isEmpty()) {
            return Result(title, "0:00", 0, emptyList(), emptyList(), "$title\n\n(Üres átirat)\n")
        }

        val named = if (speakerMap.isEmpty()) cues else cues.map { c ->
            if (c.speaker.isBlank()) c
            else c.copy(speaker = SpeakerNames.label(c.speaker, speakerMap))
        }

        val start = named.minOf { it.startMs }
        val end = named.maxOf { it.endMs.coerceAtLeast(it.startMs + 1000) }
        val durMs = (end - start).coerceAtLeast(0)
        val durationLabel = formatDuration(durMs)

        val bySpeaker = linkedMapOf<String, Int>()
        for (c in named) {
            val sp = c.speaker.ifBlank { "—" }
            bySpeaker[sp] = (bySpeaker[sp] ?: 0) + 1
        }
        val speakers = bySpeaker.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        // Kulcsmondatok: hosszabb cél (vagy forrás) sorok, max 12
        val scored = named.mapNotNull { c ->
            val line = c.target.ifBlank { c.source }.trim()
            if (line.length < 25) return@mapNotNull null
            val words = line.split(Regex("\\s+")).size
            if (words < 5) return@mapNotNull null
            val score = words + if (c.speaker.isNotBlank()) 2 else 0
            Triple(score, c.startMs, line)
        }.sortedByDescending { it.first }.take(12)
            .sortedBy { it.second }
            .map { it.third }

        val actions = pick(named, ACTION_WORDS)
        val decisions = pick(named, DECISION_WORDS)

        val sb = StringBuilder()
        sb.append(title).append("\n")
        sb.append("=".repeat(title.length.coerceAtMost(40))).append("\n\n")
        sb.append("Időtartam: ").append(durationLabel).append("\n")
        sb.append("Bejegyzések: ").append(named.size).append("\n\n")

        if (speakers.isNotEmpty() && speakers.any { it.first != "—" }) {
            sb.append("Beszélők:\n")
            for ((name, n) in speakers) {
                sb.append("  • ").append(name).append(": ").append(n).append(" sor\n")
            }
            sb.append('\n')
        }

        if (scored.isNotEmpty()) {
            sb.append("Kiemelt mondatok:\n")
            for (line in scored) {
                sb.append("  – ").append(line).append('\n')
            }
            sb.append('\n')
        }

        if (decisions.isNotEmpty()) {
            sb.append("Döntések:\n")
            for (line in decisions) sb.append("  ✔ ").append(line).append('\n')
            sb.append('\n')
        }
        if (actions.isNotEmpty()) {
            sb.append("Teendők / feladatok:\n")
            for (line in actions) sb.append("  ☐ ").append(line).append('\n')
            sb.append('\n')
        }

        sb.append("Teljes átirat (rövidítve):\n")
        var lastSp = ""
        for (c in named.take(80)) {
            val line = c.target.ifBlank { c.source }
            if (line.isBlank()) continue
            val t = SrtExporter.formatTime(c.startMs).substring(0, 8)
            val sp = c.speaker.ifBlank { "" }
            if (sp.isNotBlank() && sp != lastSp) {
                sb.append("\n[").append(sp).append("]\n")
                lastSp = sp
            }
            sb.append(t).append("  ").append(line).append('\n')
        }
        if (named.size > 80) sb.append("\n… (+").append(named.size - 80).append(" további sor)\n")

        return Result(
            title = title,
            durationLabel = durationLabel,
            cueCount = named.size,
            speakers = speakers,
            keyLines = scored,
            fullText = sb.toString(),
            actions = actions,
            decisions = decisions
        )
    }

    /** Kulcsszavakra illeszkedő sorok időrendben (max. 10), időbélyeggel. */
    private fun pick(cues: List<SrtExporter.Cue>, words: List<String>): List<String> {
        val out = mutableListOf<String>()
        for (c in cues) {
            val line = c.target.ifBlank { c.source }.trim()
            if (line.length < 8) continue
            val folded = TranscriptRepo.fold(c.source + " " + c.target)
            if (words.any { folded.contains(it) }) {
                val t = SrtExporter.formatTime(c.startMs).substring(0, 8)
                val sp = if (c.speaker.isBlank()) "" else c.speaker + ": "
                out.add("[$t] $sp$line")
                if (out.size >= 10) break
            }
        }
        return out
    }

    private fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%d:%02d", m, s)
    }
}
