package hu.angolmagyar.fordito

import java.io.InputStream
import java.security.MessageDigest

/**
 * Platformfüggetlen modell-integritás kapu. A fájl letöltését/telepítését nem végzi:
 * a host által átadott streamet korlátozottan olvassa, és csak hiteles digest esetén
 * ad VERIFIED eredményt. A caller felelőssége a stream lezárása.
 */
object ModelIntegrityVerifier {
    data class ManifestEntry(
        val modelId: String,
        val version: String,
        val expectedBytes: Long,
        val sha256: String,
        val maxBytes: Long = expectedBytes
    )

    enum class Status { VERIFIED, INVALID_MANIFEST, SIZE_MISMATCH, TOO_LARGE, HASH_MISMATCH, IO_ERROR }
    data class Result(val status: Status, val bytesRead: Long = 0, val actualSha256: String? = null) {
        val usable: Boolean get() = status == Status.VERIFIED
    }

    fun validateManifest(entry: ManifestEntry): Boolean =
        entry.modelId.isNotBlank() &&
            entry.version.isNotBlank() &&
            entry.expectedBytes > 0 &&
            entry.maxBytes >= entry.expectedBytes &&
            entry.sha256.matches(Regex("^[a-fA-F0-9]{64}$"))

    fun verify(entry: ManifestEntry, input: InputStream): Result {
        if (!validateManifest(entry)) return Result(Status.INVALID_MANIFEST)
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var count = 0L
        return try {
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read == 0) {
                    val single = input.read()
                    if (single < 0) break
                    count += 1
                    if (count > entry.maxBytes) return Result(Status.TOO_LARGE, count)
                    digest.update(single.toByte())
                    continue
                }
                count += read
                if (count > entry.maxBytes) return Result(Status.TOO_LARGE, count)
                digest.update(buffer, 0, read)
            }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            when {
                count != entry.expectedBytes -> Result(Status.SIZE_MISMATCH, count, actual)
                !actual.equals(entry.sha256, ignoreCase = true) -> Result(Status.HASH_MISMATCH, count, actual)
                else -> Result(Status.VERIFIED, count, actual)
            }
        } catch (_: Exception) {
            Result(Status.IO_ERROR, count)
        }
    }
}
