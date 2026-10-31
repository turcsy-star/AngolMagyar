package hu.angolmagyar.fordito

/** ZIP extraction guardrails shared by import and model-install flows. */
object ArchiveSafetyPolicy {
    const val DEFAULT_MAX_ENTRIES = 50_000
    const val DEFAULT_MAX_UNCOMPRESSED_BYTES = 2L * 1024 * 1024 * 1024
    const val DEFAULT_MAX_ENTRY_BYTES = 512L * 1024 * 1024
    const val DEFAULT_MAX_COMPRESSION_RATIO = 250L

    enum class Rejection { EMPTY, TOO_MANY_ENTRIES, INVALID_PATH, TOO_LARGE, ZIP_BOMB }

    data class EntryMetadata(
        val name: String,
        val uncompressedBytes: Long,
        val compressedBytes: Long
    )

    data class Limits(
        val maxEntries: Int = DEFAULT_MAX_ENTRIES,
        val maxTotalBytes: Long = DEFAULT_MAX_UNCOMPRESSED_BYTES,
        val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES,
        val maxCompressionRatio: Long = DEFAULT_MAX_COMPRESSION_RATIO
    )

    fun validate(entries: List<EntryMetadata>, limits: Limits = Limits()): Rejection? {
        if (entries.isEmpty()) return Rejection.EMPTY
        if (entries.size > limits.maxEntries) return Rejection.TOO_MANY_ENTRIES
        var total = 0L
        for (entry in entries) {
            if (!isSafeRelativePath(entry.name)) return Rejection.INVALID_PATH
            if (entry.uncompressedBytes < 0 || entry.compressedBytes < 0) return Rejection.INVALID_PATH
            if (entry.uncompressedBytes > limits.maxEntryBytes) return Rejection.TOO_LARGE
            total += entry.uncompressedBytes
            if (total < 0 || total > limits.maxTotalBytes) return Rejection.TOO_LARGE
            if (entry.uncompressedBytes > 0 && entry.compressedBytes == 0L) return Rejection.ZIP_BOMB
            if (entry.compressedBytes > 0 && entry.uncompressedBytes / entry.compressedBytes.coerceAtLeast(1) > limits.maxCompressionRatio) {
                return Rejection.ZIP_BOMB
            }
        }
        return null
    }

    fun isSafeRelativePath(raw: String): Boolean {
        if (raw.isBlank() || raw.startsWith('/') || raw.startsWith('\\') || raw.contains('\\')) return false
        if (raw.matches(Regex("^[A-Za-z]:.*"))) return false
        val normalized = raw.removeSuffix("/")
        if (normalized.isBlank()) return false
        val parts = normalized.split('/')
        return parts.none { it.isBlank() || it == "." || it == ".." }
    }
}
