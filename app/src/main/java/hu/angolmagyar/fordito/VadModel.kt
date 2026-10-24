package hu.angolmagyar.fordito

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A Silero VAD modell (whisper.cpp ggml formátum, ~1 MB): egyszeri letöltés, utána offline. */
object VadModel {
    private const val NAME = "ggml-silero-v5.1.2.bin"
    private const val URL_STR = "https://huggingface.co/ggml-org/whisper-vad/resolve/main/ggml-silero-v5.1.2.bin"
    private const val MIN_BYTES = 300_000L

    fun file(ctx: Context): File = File(File(ctx.filesDir, "vad").also { it.mkdirs() }, NAME)

    /** A modellfájl, vagy null, ha nincs meg és nem tölthető le (ekkor marad a hangerő-alapú szűrés). */
    fun ensure(ctx: Context, say: (String) -> Unit): File? {
        val f = file(ctx)
        if (f.exists() && f.length() > MIN_BYTES) return f
        val tmp = File(f.path + ".part")
        try {
            say("Csendfelismerő modell letöltése (kb. 1 MB, egyszeri)…")
            val c = URL(URL_STR).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 25_000
            c.instanceFollowRedirects = true
            if (c.responseCode != 200) {
                DiagLog.log("VAD modell letöltés: HTTP ${c.responseCode}")
                c.disconnect()
                return null
            }
            c.inputStream.use { inp -> tmp.outputStream().use { out -> inp.copyTo(out) } }
            c.disconnect()
            if (tmp.length() < MIN_BYTES) { tmp.delete(); return null }
            if (f.exists()) f.delete()
            if (!tmp.renameTo(f)) return null
            DiagLog.log("VAD modell letöltve: ${f.length()} bájt")
            return f
        } catch (e: Throwable) {
            DiagLog.log("VAD modell letöltés hiba: ${e.javaClass.simpleName}: ${e.message}")
            try { tmp.delete() } catch (_: Throwable) {}
            return null
        }
    }
}
