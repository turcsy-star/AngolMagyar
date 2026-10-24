package hu.angolmagyar.fordito

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Egyszerű offline TextToSpeech a lefordított sorok felolvasásához.
 * A nyelv a célnyelv alapján kerül kiválasztásra.
 */
class TtsHelper(ctx: Context) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = TextToSpeech(ctx.applicationContext, this)
    private var ready = false
    private var pending: String? = null
    private var targetLang = "hu"

    fun setLanguage(langCode: String) {
        targetLang = langCode
        if (ready) applyLanguage()
    }

    private fun applyLanguage() {
        val locale = when (targetLang) {
            "hu" -> Locale("hu", "HU")
            "en" -> Locale.US
            "de" -> Locale.GERMAN
            "fr" -> Locale.FRENCH
            "es" -> Locale("es", "ES")
            "it" -> Locale.ITALIAN
            "uk" -> Locale("uk", "UA")
            else -> Locale("hu", "HU")
        }
        tts?.language = locale
    }

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            applyLanguage()
            pending?.let { speak(it) }
            pending = null
        }
    }

    fun speak(text: String) {
        if (text.isBlank()) return
        if (!ready) {
            pending = text
            return
        }
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "am_final")
        } catch (_: Exception) {
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (_: Exception) {
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (_: Exception) {
        }
        tts = null
        ready = false
    }
}
