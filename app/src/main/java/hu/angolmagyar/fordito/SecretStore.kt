package hu.angolmagyar.fordito

import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * API-kulcsok fail-closed tárolása Android Keystore AES-GCM titkosítással.
 * Ha a Keystore nem érhető el, a titok nem kerül egyszerű szövegként a SharedPreferences-be.
 * A korábbi plaintext értékeket első sikeres olvasáskor titkosítva migrálja.
 */
object SecretStore {
    private const val ALIAS = "angolmagyar_secret_v1"
    private const val PROVIDER = "AndroidKeyStore"
    private const val SUFFIX = "_enc"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    internal fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + encrypted, Base64.NO_WRAP)
    }

    internal fun decrypt(blob: String): String {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        require(raw.size > 12 + 16) { "csonka titkosított érték" }
        val iv = raw.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    }

    /** Titkosított érték olvasása; régi plaintext csak sikeres titkosított migráció után adható vissza. */
    fun get(p: SharedPreferences, name: String): String {
        val encrypted = p.getString(name + SUFFIX, null)
        if (!encrypted.isNullOrEmpty()) {
            return try {
                decrypt(encrypted)
            } catch (e: Exception) {
                p.edit().remove(name).remove(name + SUFFIX).apply()
                DiagLog.log("SecretStore: visszafejtés sikertelen; sérült titok eltávolítva (${e.javaClass.simpleName})")
                ""
            }
        }

        val legacy = p.getString(name, "") ?: ""
        if (legacy.isBlank()) return ""
        return try {
            val migrated = encrypt(legacy)
            p.edit().putString(name + SUFFIX, migrated).remove(name).apply()
            legacy
        } catch (e: Exception) {
            // Biztonságosabb a régi, titkosítatlan kulcs eltávolítása, mint plaintextként tovább tárolni.
            p.edit().remove(name).apply()
            DiagLog.log("SecretStore: régi titok migrációja sikertelen; plaintext eltávolítva (${e.javaClass.simpleName})")
            ""
        }
    }

    /**
     * @return true, ha a törlés vagy a titkosított mentés sikerült; false, ha a Keystore nem érhető el.
     * Titkosítási hiba esetén nem ír vissza plaintext értéket.
     */
    fun set(p: SharedPreferences, name: String, value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) {
            p.edit().remove(name).remove(name + SUFFIX).apply()
            return true
        }
        return try {
            val encrypted = encrypt(trimmed)
            p.edit().putString(name + SUFFIX, encrypted).remove(name).apply()
            true
        } catch (e: Exception) {
            p.edit().remove(name).apply()
            DiagLog.log("SecretStore: titkosított mentés sikertelen; plaintext tárolás tiltva (${e.javaClass.simpleName})")
            false
        }
    }
}
