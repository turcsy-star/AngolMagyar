package hu.angolmagyar.fordito
object DialogueDash {
    fun apply(
        cues: List<SrtExporter.Cue>,
        maxGapMs: Long = 400,
        maxBlockMs: Long = 6000,
        maxChars: Int = 84
    ): List<SrtExporter.Cue> {
        val out = ArrayList<SrtExporter.Cue>()
        var i = 0
        while (i < cues.size) {
            val a = cues[i]
            val b = cues.getOrNull(i + 1)
            if (b != null && canMerge(a, b, maxGapMs, maxBlockMs, maxChars)) {
                out.add(
                    a.copy(
                        endMs = b.endMs,
                        source = join(a.source, b.source),
                        target = join(a.target, b.target),
                        speaker = "",
                        lp = minOf(a.lp, b.lp),
                        wordConfidence = emptyList(),
                        noSpeechProb = maxOf(a.noSpeechProb, b.noSpeechProb)
                    )
                )
                i += 2
            } else {
                out.add(a.copy(speaker = a.speaker))
                i++
            }
        }
        return out
    }
    private fun dash(t: String) = if (t.isBlank()) t else "- " + t.trim().removePrefix("-").trim()
    private fun join(a: String, b: String): String = when {
        a.isBlank() && b.isBlank() -> ""
        a.isBlank() -> b
        b.isBlank() -> a
        else -> dash(a) + "\n" + dash(b)
    }
    private fun canMerge(a: SrtExporter.Cue, b: SrtExporter.Cue, gap: Long, maxMs: Long, maxChars: Int): Boolean {
        if (a.speaker.isBlank() || b.speaker.isBlank() || a.speaker == b.speaker) return false
        if (b.startMs - a.endMs !in 0..gap && b.startMs < a.endMs - gap) return false
        if (b.startMs - a.endMs > gap) return false
        if (b.endMs - a.startMs > maxMs) return false
        val srcOk = a.source.isNotBlank() == b.source.isNotBlank()
        val tgtOk = a.target.isNotBlank() == b.target.isNotBlank()
        if (!srcOk || !tgtOk) return false
        if (a.source.length + b.source.length > maxChars) return false
        if (a.target.length + b.target.length > maxChars) return false
        return a.source.isNotBlank() || a.target.isNotBlank()
    }
}
