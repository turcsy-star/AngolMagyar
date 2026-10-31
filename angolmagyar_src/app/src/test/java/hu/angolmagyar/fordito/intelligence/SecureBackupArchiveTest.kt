package hu.angolmagyar.fordito.intelligence

import org.junit.Assert.*
import org.junit.Test

class SecureBackupArchiveTest {
    @Test fun encryptedPayloadRoundTrips() {
        val original = "angolmagyar-infinity-backup|árvíztűrő tükörfúrógép"
        val encrypted = SecureBackupArchive.encrypt(original, "Diamond#2026".toCharArray())
        assertNotEquals(original, encrypted.toString(Charsets.UTF_8))
        assertEquals(original, SecureBackupArchive.decrypt(encrypted, "Diamond#2026".toCharArray()))
    }

    @Test fun rejectsWrongPassword() {
        val encrypted = SecureBackupArchive.encrypt("sensitive", "Correct#123".toCharArray())
        try {
            SecureBackupArchive.decrypt(encrypted, "Wrong#123".toCharArray())
            fail("Wrong password must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Hibás jelszó"))
        }
    }

    @Test fun rejectsTamperedPayload() {
        val encrypted = SecureBackupArchive.encrypt("payload", "Correct#123".toCharArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()
        try {
            SecureBackupArchive.decrypt(encrypted, "Correct#123".toCharArray())
            fail("Tampered payload must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun enforcesMinimumPasswordLength() {
        try {
            SecureBackupArchive.encrypt("payload", "short".toCharArray())
            fail("Short passwords must be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("8 karakter"))
        }
    }
}
