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
 * Titkok (API-kulcsok) tárolása Android Keystore AES-GCM kulccsal titkosítva. A kulcs nem hagyja el a
 * Keystore-t. Ha a titkosítás valamiért nem érhető el, a tárolás a régi (egyszerű) módra esik vissza,
 * hogy az app ne veszítse el a beállítást; a régi egyszerű érték első olvasáskor áttelepül.
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
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(c.iv + ct, Base64.NO_WRAP)
    }

    internal fun decrypt(blob: String): String {
        val raw = Base64.decode(blob, Base64.NO_WRAP)
        require(raw.size > 13) { "csonka titkosított érték" }
        val iv = raw.copyOfRange(0, 12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        return String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    }

    /** Olvasás: előbb a titkosított érték; ha csak egyszerű van, áttelepíti. */
    fun get(p: SharedPreferences, name: String): String {
        val enc = p.getString(name + SUFFIX, null)
        if (!enc.isNullOrEmpty()) {
            try { return decrypt(enc) } catch (e: Exception) { DiagLog.log("SecretStore: visszafejtés sikertelen (${e.javaClass.simpleName})") }
        }
        val legacy = p.getString(name, "") ?: ""
        if (legacy.isNotEmpty()) set(p, name, legacy) // áttelepítés
        return legacy
    }

    fun set(p: SharedPreferences, name: String, value: String) {
        val v = value.trim()
        if (v.isEmpty()) { p.edit().remove(name).remove(name + SUFFIX).apply(); return }
        try {
            p.edit().putString(name + SUFFIX, encrypt(v)).remove(name).apply()
        } catch (e: Exception) {
            DiagLog.log("SecretStore: titkosítás nem érhető el, egyszerű tárolás (${e.javaClass.simpleName})")
            p.edit().putString(name, v).remove(name + SUFFIX).apply()
        }
    }
}
