package hu.angolmagyar.fordito

/**
 * Mondat szintű puffer: partialokat gyűjt, finalnál / írásjel / időzítő után fordít.
 * Stabilabb, természetesebb fordítás, kevesebb "pisláló" szöveg.
 */
class SentenceBuffer(
    private val flushMs: Long = 2200L,
    private val onFlush: (text: String, isFinal: Boolean) -> Unit
) {
    private var acc = StringBuilder()
    private var lastChangeAt = 0L
    private var lastFlushed = ""

    fun onPartial(text: String, now: Long) {
        val t = text.trim()
        if (t.isEmpty() || t == acc.toString()) return
        acc = StringBuilder(t)
        lastChangeAt = now
    }

    fun onFinal(text: String, now: Long) {
        val t = text.trim()
        if (t.isEmpty()) return
        acc = StringBuilder(t)
        lastChangeAt = now
        flush(now, final = true)
    }

    /** Hívd periodikusan (pl. 300 ms). */
    fun tick(now: Long) {
        if (acc.isEmpty()) return
        if (now - lastChangeAt >= flushMs) {
            flush(now, final = false)
        }
    }

    private fun flush(now: Long, final: Boolean) {
        val t = acc.toString().trim()
        if (t.isEmpty() || t == lastFlushed) {
            if (final) {
                acc.clear()
            }
            return
        }
        // írásjel = inkább final
        val hard = final || t.last() in charArrayOf('.', '!', '?')
        lastFlushed = t
        onFlush(t, hard)
        if (hard) {
            acc.clear()
            lastFlushed = ""
        }
    }

    fun reset() {
        acc.clear()
        lastFlushed = ""
        lastChangeAt = 0L
    }
}
