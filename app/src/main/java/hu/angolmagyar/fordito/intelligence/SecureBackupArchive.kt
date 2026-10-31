package hu.angolmagyar.fordito.intelligence

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Authenticated, password-protected envelope for the portable learning backup. */
object SecureBackupArchive {
    private const val MAGIC = "AMPIBKP1"
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val KEY_BITS = 256
    private const val ITERATIONS = 210_000
    private const val MAX_CIPHER_BYTES = 24 * 1024 * 1024

    fun encrypt(plainText: String, password: CharArray): ByteArray {
        require(password.size >= 8) { "A jelszónak legalább 8 karakteresnek kell lennie." }
        val random = SecureRandom()
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val key = deriveKey(password, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { out ->
                    out.write(MAGIC.toByteArray(Charsets.US_ASCII))
                    out.writeByte(1)
                    out.writeByte(ITERATIONS / 1000)
                    out.writeByte(salt.size)
                    out.write(salt)
                    out.writeByte(iv.size)
                    out.write(iv)
                    out.writeInt(encrypted.size)
                    out.write(encrypted)
                }
                bytes.toByteArray()
            }
        } finally { key.encoded?.fill(0) }
    }

    fun decrypt(payload: ByteArray, password: CharArray): String {
        require(payload.size in 40..MAX_CIPHER_BYTES) { "Érvénytelen vagy túl nagy titkosított mentés." }
        require(password.isNotEmpty()) { "Add meg a mentés jelszavát." }
        val input = DataInputStream(ByteArrayInputStream(payload))
        val magic = ByteArray(MAGIC.length).also(input::readFully).toString(Charsets.US_ASCII)
        require(magic == MAGIC) { "Ismeretlen vagy nem titkosított mentésformátum." }
        require(input.readUnsignedByte() == 1) { "Nem támogatott titkosítási verzió." }
        val iterations = input.readUnsignedByte() * 1000
        require(iterations in 100_000..500_000) { "Érvénytelen kulcsszármaztatási paraméter." }
        val saltSize = input.readUnsignedByte()
        require(saltSize == SALT_SIZE) { "Sérült mentés (salt)." }
        val salt = ByteArray(saltSize).also(input::readFully)
        val ivSize = input.readUnsignedByte()
        require(ivSize == IV_SIZE) { "Sérült mentés (IV)." }
        val iv = ByteArray(ivSize).also(input::readFully)
        val encryptedSize = input.readInt()
        require(encryptedSize in 16..MAX_CIPHER_BYTES && encryptedSize == input.available()) { "Sérült mentésméret." }
        val encrypted = ByteArray(encryptedSize).also(input::readFully)
        val key = deriveKey(password, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (badTag: AEADBadTagException) {
            throw IllegalArgumentException("Hibás jelszó vagy sérült mentés.", badTag)
        } finally { key.encoded?.fill(0) }
    }

    private fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try { SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES") }
        finally { spec.clearPassword() }
    }
}

data class BackupPreview(
    val createdAt: Long,
    val dictionaryCount: Int,
    val translationCount: Int,
    val reviewCount: Int
) {
    companion object {
        fun from(backup: LearningBackup) = BackupPreview(
            backup.createdAt, backup.dictionary.size, backup.translations.size, backup.reviews.size
        )
    }
}
