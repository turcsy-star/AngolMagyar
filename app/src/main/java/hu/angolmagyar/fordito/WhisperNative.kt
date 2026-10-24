package hu.angolmagyar.fordito

/** JNI híd a whisper.cpp-hez. Egy példány = egy betöltött modell; egyszerre egy szál használja. */
class WhisperNative private constructor(private var handle: Long) {

    interface Listener {
        fun onProgress(percent: Int)
        /** t0/t1: 10 ms-os egységekben a chunk elejéhez képest. */
        fun onSegment(t0: Long, t1: Long, text: ByteArray)
    }

    class Seg(val startMs: Long, val endMs: Long, val text: String, val noSpeech: Float)

    /** 0 = rendben. */
    fun transcribe(pcm: FloatArray, lang: String, threads: Int, beam: Int,
                   prompt: String?, listener: Listener?): Int {
        val h = handle
        if (h == 0L) return -101
        return nTranscribe(h, threads, pcm, lang, prompt, beam, listener)
    }

    fun segments(): List<Seg> {
        val h = handle
        if (h == 0L) return emptyList()
        val n = nSegCount(h)
        val out = ArrayList<Seg>(n)
        for (i in 0 until n) {
            val t = String(nSegText(h, i), Charsets.UTF_8).trim()
            if (t.isEmpty()) continue
            out.add(Seg(nSegT0(h, i) * 10, nSegT1(h, i) * 10, t, nSegNoSpeech(h, i)))
        }
        return out
    }

    fun abort(on: Boolean) { val h = handle; if (h != 0L) nAbort(h, on) }

    @Synchronized fun close() {
        val h = handle
        handle = 0L
        if (h != 0L) nFree(h)
    }

    companion object {
        private var loaded: Boolean? = null
        private var loadError: String? = null

        /** A natív kód ARMv8.2 + dotprod + fp16 utasításokra épül; régebbi CPU-n nem szabad betölteni (SIGILL). */
        private fun cpuOk(): Boolean {
            if (!Perf.isArm64()) return false
            return try {
                val txt = java.io.File("/proc/cpuinfo").readText()
                val feat = txt.lineSequence().firstOrNull { it.startsWith("Features") } ?: return true
                feat.contains("asimddp") && (feat.contains("fphp") || feat.contains("asimdhp"))
            } catch (_: Exception) { true }
        }

        @Synchronized fun available(): Boolean {
            if (loaded == null) {
                loaded = if (!cpuOk()) {
                    loadError = "Ez a processzor nem támogatja a Whisper gyorsított utasításait (ARMv8.2 dotprod/fp16)."
                    false
                } else try { System.loadLibrary("angolwhisper"); true }
                catch (t: Throwable) { loadError = t.message ?: t.javaClass.simpleName; false }
            }
            return loaded == true
        }

        fun error(): String = loadError ?: ""

        /** null, ha a modell nem tölthető be. */
        fun open(path: String): WhisperNative? {
            if (!available()) return null
            val h = nInit(path)
            return if (h == 0L) null else WhisperNative(h)
        }

        fun systemInfo(): String = if (available()) nSystemInfo() else ""

        @JvmStatic private external fun nInit(path: String): Long
        @JvmStatic private external fun nFree(h: Long)
        @JvmStatic private external fun nAbort(h: Long, on: Boolean)
        @JvmStatic private external fun nTranscribe(h: Long, threads: Int, audio: FloatArray, lang: String,
                                                    prompt: String?, beam: Int, listener: Listener?): Int
        @JvmStatic private external fun nSegCount(h: Long): Int
        @JvmStatic private external fun nSegT0(h: Long, i: Int): Long
        @JvmStatic private external fun nSegT1(h: Long, i: Int): Long
        @JvmStatic private external fun nSegText(h: Long, i: Int): ByteArray
        @JvmStatic private external fun nSegNoSpeech(h: Long, i: Int): Float
        @JvmStatic private external fun nSystemInfo(): String
    }
}
