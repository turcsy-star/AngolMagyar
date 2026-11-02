package hu.angolmagyar.fordito.capture

/**
 * P8 – az OCR nyers kimenetének tisztítása (tiszta Kotlin, eszköz nélkül tesztelhető).
 * Összefűzi a sor végi kötőjellel tört szavakat, a sima sortöréseket szóközre cseréli,
 * a bekezdéshatárokat (üres sor) megtartja, és a felesleges szóközöket elhagyja.
 */
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
        // "inter-\nnational" -> "international" (csak kisbetűs folytatásnál)
        val joined = p.replace(Regex("([A-Za-zÀ-ž])-\\n([a-zà-ž])"), "$1$2")
        return joined.replace('\n', ' ').replace(Regex("[ \\t\\u00A0]+"), " ").trim()
    }

    /** Az OCR-blokkok szövege, bizonytalan (alacsony megbízhatóságú) blokkok jelölésével. */
    fun fromBlocks(blocks: List<OcrBlock>, minConfidence: Float = 0.5f): String =
        clean(blocks.joinToString("\n\n") { it.text })

    fun lowConfidenceCount(blocks: List<OcrBlock>, minConfidence: Float = 0.5f): Int =
        blocks.count { (it.confidence ?: 1f) < minConfidence }
}
