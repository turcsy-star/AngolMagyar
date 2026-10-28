package hu.angolmagyar.fordito

import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentificationOptions
import com.google.mlkit.nl.languageid.LanguageIdentifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Valódi on-device nyelvfelismerés (ML Kit Language ID).
 * Támogatott a legtöbb európai nyelv; offline, kis modell.
 *
 * A [LanguageHint] heurisztika tartalék, ha az ML Kit nem elérhető / bizonytalan.
 */
object LanguageIdHelper {

    @Volatile private var client: LanguageIdentifier? = null

    private fun client(): LanguageIdentifier {
        client?.let { return it }
        synchronized(this) {
            client?.let { return it }
            val c = LanguageIdentification.getClient(
                LanguageIdentificationOptions.Builder()
                    .setConfidenceThreshold(0.5f)
                    .build()
            )
            client = c
            return c
        }
    }

    /**
     * Szinkron azonosítás (háttérszálon hívd).
     * @return BCP-47 kód (en, hu, de…) vagy null
     */
    fun identifyBlocking(text: String, timeoutMs: Long = 800): String? {
        val t = text.trim()
        if (t.length < 8) return null
        val ref = AtomicReference<String?>(null)
        val latch = CountDownLatch(1)
        try {
            client().identifyLanguage(t)
                .addOnSuccessListener { code ->
                    if (code != null && code != "und") ref.set(code)
                    latch.countDown()
                }
                .addOnFailureListener {
                    latch.countDown()
                }
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            return null
        }
        return ref.get()
    }

    /** Beszélgetés mód: true = HU→EN forrás, false = EN→HU. null = ne válts. */
    fun suggestConversationHuToEn(text: String, currentHuToEn: Boolean): Boolean? {
        val code = identifyBlocking(text)
        if (code != null) {
            return when {
                code.startsWith("hu") -> if (!currentHuToEn) true else null
                code.startsWith("en") -> if (currentHuToEn) false else null
                else -> null
            }
        }
        return LanguageHint.suggestConversationHuToEn(text, currentHuToEn)
    }

    /**
     * Aszinkron javaslat – nem blokkolja a főszálat.
     * Először heurisztika, majd ML Kit megerősítés.
     */
    fun suggestConversationHuToEnAsync(
        text: String,
        currentHuToEn: Boolean,
        onResult: (Boolean?) -> Unit
    ) {
        val heuristic = LanguageHint.suggestConversationHuToEn(text, currentHuToEn)
        if (heuristic != null) {
            onResult(heuristic)
            return
        }
        val t = text.trim()
        if (t.length < 12) {
            onResult(null)
            return
        }
        try {
            client().identifyLanguage(t)
                .addOnSuccessListener { code ->
                    val want = when {
                        code == null || code == "und" -> null
                        code.startsWith("hu") -> if (!currentHuToEn) true else null
                        code.startsWith("en") -> if (currentHuToEn) false else null
                        else -> null
                    }
                    onResult(want)
                }
                .addOnFailureListener { onResult(null) }
        } catch (_: Exception) {
            onResult(null)
        }
    }

    /** Forrásnyelv javaslat a Prefs LANG_* kódokra. */
    fun suggestSourceLang(text: String): String? {
        val code = identifyBlocking(text) ?: return when (LanguageHint.detect(text)) {
            LanguageHint.Lang.HU -> Prefs.LANG_HU
            LanguageHint.Lang.EN -> Prefs.LANG_EN
            else -> null
        }
        return when {
            code.startsWith("hu") -> Prefs.LANG_HU
            code.startsWith("en") -> Prefs.LANG_EN
            code.startsWith("de") -> Prefs.LANG_DE
            code.startsWith("fr") -> Prefs.LANG_FR
            code.startsWith("es") -> Prefs.LANG_ES
            code.startsWith("it") -> Prefs.LANG_IT
            code.startsWith("uk") -> Prefs.LANG_UK
            else -> null
        }
    }

    fun close() {
        try { client?.close() } catch (e: Exception) { DiagLog.swallow("LanguageIdHelper", e) }
        client = null
    }
}
