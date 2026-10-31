package hu.angolmagyar.fordito

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.MessageDigest

class ModelIntegrityVerifierTest {
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun verifiesExactSizeAndDigest() {
        val bytes = "offline-model".toByteArray()
        val entry = ModelIntegrityVerifier.ManifestEntry("asr-hu", "1.0", bytes.size.toLong(), hash(bytes))
        val result = ModelIntegrityVerifier.verify(entry, ByteArrayInputStream(bytes))
        assertEquals(ModelIntegrityVerifier.Status.VERIFIED, result.status)
        assertTrue(result.usable)
    }

    @Test fun rejectsDigestMismatch() {
        val bytes = "model".toByteArray()
        val entry = ModelIntegrityVerifier.ManifestEntry("asr", "1", bytes.size.toLong(), "0".repeat(64))
        assertEquals(ModelIntegrityVerifier.Status.HASH_MISMATCH,
            ModelIntegrityVerifier.verify(entry, ByteArrayInputStream(bytes)).status)
    }

    @Test fun rejectsOversizedStreamBeforeUnboundedRead() {
        val bytes = ByteArray(128) { 1 }
        val entry = ModelIntegrityVerifier.ManifestEntry("asr", "1", 10, "a".repeat(64), maxBytes = 12)
        assertEquals(ModelIntegrityVerifier.Status.TOO_LARGE,
            ModelIntegrityVerifier.verify(entry, ByteArrayInputStream(bytes)).status)
    }

    @Test fun rejectsMalformedManifest() {
        val entry = ModelIntegrityVerifier.ManifestEntry("", "1", 1, "bad")
        assertEquals(ModelIntegrityVerifier.Status.INVALID_MANIFEST,
            ModelIntegrityVerifier.verify(entry, ByteArrayInputStream(byteArrayOf(1))).status)
    }

    @Test fun reportsIoFailure() {
        val entry = ModelIntegrityVerifier.ManifestEntry("asr", "1", 1, "a".repeat(64))
        val broken = object : InputStream() { override fun read(): Int = error("disk failure") }
        assertEquals(ModelIntegrityVerifier.Status.IO_ERROR, ModelIntegrityVerifier.verify(entry, broken).status)
    }
}
