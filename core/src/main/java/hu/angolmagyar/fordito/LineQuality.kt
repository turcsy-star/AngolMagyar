package hu.angolmagyar.fordito

/** Feliratblokkok gyanús jeleinek összegyűjtése az ellenőrző nézethez (felismerés + fordítás szempontjából). */
object LineQuality {
    private val SENT_END = ".?!…\"”)"
    private val LETTER = Regex("\\p{L}")

    private fun words(t: String) = t.trim().split(Regex("\\s+")).count { it.isNotEmpty() }

    fun flags(prev: SrtExporter.Cue?, c: SrtExporter.Cue, next: SrtExporter.Cue?, hasTarget: Boolean): List<String> {
        val out = ArrayList<String>()
        val en = c.source.trim()
        val hu = c.target.trim()
        val shown = if (hasTarget) hu else en
        if (shown.isEmpty()) { out.add("üres"); return out }
        val dur = c.endMs - c.startMs
        if (dur <= 0) out.add("hibás idő")
        else if (shown.replace("\n", " ").length * 1000.0 / dur > 28) out.add("nagyon gyors")
        if (prev != null) {
            val p = (if (hasTarget) prev.target else prev.source).trim()
            if (p.equals(shown, ignoreCase = true) && words(shown) >= 2) out.add("ismétlődés")
        }
        if (c.lp < -1.0f) out.add("bizonytalan felismerés")
        if (words(en) <= 2 && en.isNotEmpty() && en.last() !in SENT_END && next != null && next.startMs - c.endMs < 1500)
            out.add("töredék")
        if (hasTarget) {
            if (TextPolish.looksFormal(hu)) out.add("magázó alak")
            if (hu.equals(en, ignoreCase = true) && en.length > 3 && LETTER.containsMatchIn(en)) out.add("fordítatlan")
            if (en.length >= 15 && (hu.length > en.length * 2.6 || hu.length < en.length * 0.35)) out.add("hossz-arány")
            if (!out.contains("fordítatlan")) out.addAll(TranslationCheck.check(en, hu))
        }
        val prevGap = if (prev == null) Long.MAX_VALUE else c.startMs - prev.endMs
        val nextGap = if (next == null) Long.MAX_VALUE else next.startMs - c.endMs
        if (prevGap >= 90_000 && nextGap >= 90_000 && !(prev == null && next == null) && words(shown) <= 6) out.add("magányos")
        return out
    }

    fun summarize(cues: List<SrtExporter.Cue>): List<List<String>> {
        val hasTarget = cues.any { it.target.isNotBlank() }
        return cues.mapIndexed { i, c -> flags(cues.getOrNull(i - 1), c, cues.getOrNull(i + 1), hasTarget) }
    }
}
