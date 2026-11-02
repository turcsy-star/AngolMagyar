package hu.angolmagyar.fordito.capture
object OcrTextCleaner {
    fun clean(raw: String): String {
        if (raw.isBlank()) return ""
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val paragraphs = normalized.split(Regex("\n\\s*\n"))
        return paragraphs
            .map { cleanParagraph(it) }
            .filter { it.isNotEmpty() }
            .joinToString("\n\n")
    }
    private fun cleanParagraph(p: String): String {
        val joined = p.replace(Regex("([A-Za-zÀ-ž])-\\n([a-zà-ž])"), "$1$2")
        return joined.replace('\n', ' ').replace(Regex("[ \\t\\u00A0]+"), " ").trim()
    }
    fun fromBlocks(blocks: List<OcrBlock>, minConfidence: Float = 0.5f): String =
        clean(blocks.joinToString("\n\n") { it.text })
    fun lowConfidenceCount(blocks: List<OcrBlock>, minConfidence: Float = 0.5f): Int =
        blocks.count { (it.confidence ?: 1f) < minConfidence }
}
