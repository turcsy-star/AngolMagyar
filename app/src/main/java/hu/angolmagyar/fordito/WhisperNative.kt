package hu.angolmagyar.fordito

/** JNI híd a whisper.cpp-hez. Egy példány = egy betöltött modell; egyszerre egy szál használja. */
class WhisperNative private constructor(private var handle: Long) {

    interface Listener {
        fun onProgress(percent: Int)
        /** t0/t1: 10 ms-os egységekben a chunk elejéhez képest. */
        fun onSegment(t0: Long, t1: Long, text: ByteArray)
    }

    data class TokenScore(
        val text: String,
        val probability: Float,
        val startMs: Long,
        val endMs: Long
    ) {
        /** Jelzésre szolgáló érték, nem a szó helyességének bizonyítéka. */
        val needsReview: Boolean get() = !probability.isFinite() || probability < 0.35f
    }

    class Seg(val startMs: Long, val endMs: Long, val text: String, val noSpeech: Float,
              val avgLogProb: Float = 1f, val tokens: List<TokenScore> = emptyList()) {
        /** Szavankénti, óvatos minimum-token pontszám; az SRT-sorok kiemeléséhez használjuk. */
        val wordConfidence: List<Float> by lazy {
            val out = ArrayList<Float>()
            var current: Float? = null
            for (token in tokens) {
                if (token.text.firstOrNull()?.isWhitespace() == true && current != null) {
                    out.add(current!!); current = null
                }
                if (token.text.isNotEmpty() && !token.text.trim().startsWith("<|")) {
                    val p = token.probability.coerceIn(0f, 1f)
                    current = if (current == null) p else minOf(current!!, p)
                }
            }
            if (current != null) out.add(current!!)
            if (out.size == text.trim().split(Regex("\\s+")).size) out else emptyList()
        }
    }

    /** 0 = rendben. */
    fun transcribe(pcm: FloatArray, lang: String, threads: Int, beam: Int,
                   prompt: String?, listener: Listener?, vadModel: String? = null): Int {
        val h = handle
        if (h == 0L) return -101
        return nTranscribe(h, threads, pcm, lang, prompt, beam, vadModel, listener)
    }

    fun segments(): List<Seg> {
        val h = handle
        if (h == 0L) return emptyList()
        val n = nSegCount(h)
        val out = ArrayList<Seg>(n)
        for (i in 0 until n) {
            val t = String(nSegText(h, i), Charsets.UTF_8).trim()
            if (t.isEmpty()) continue
            val lp = try { nSegAvgLogProb(h, i) } catch (_: Throwable) { 1f }
            val tokens = try {
                val count = nSegTokenCount(h, i).coerceAtMost(4096)
                (0 until count).mapNotNull { ti ->
                    val tokenText = String(nSegTokenText(h, i, ti), Charsets.UTF_8)
                    val probability = nSegTokenProbability(h, i, ti)
                    val t0 = nSegTokenT0(h, i, ti).coerceAtLeast(0) * 10
                    val t1 = nSegTokenT1(h, i, ti).coerceAtLeast(nSegTokenT0(h, i, ti)) * 10
                    if (tokenText.isEmpty()) null else TokenScore(tokenText, probability, t0, t1)
                }
            } catch (_: Throwable) { emptyList() }
            out.add(Seg(nSegT0(h, i) * 10, nSegT1(h, i) * 10, t, nSegNoSpeech(h, i), lp, tokens))
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

        /** Silero VAD: beszédszakaszok (t0,t1 párok, centiszekundum); null = hiba / nincs natív könyvtár. */
        fun vadSegments(modelPath: String, audio: FloatArray, threads: Int): FloatArray? {
            if (!available()) return null
            return try { nVad(modelPath, audio, threads, 0.5f, 100) } catch (_: Throwable) { null }
        }

        @JvmStatic private external fun nVad(path: String, audio: FloatArray, threads: Int,
                                             threshold: Float, minSilenceMs: Int): FloatArray?

        @JvmStatic private external fun nInit(path: String): Long
        @JvmStatic private external fun nFree(h: Long)
        @JvmStatic private external fun nAbort(h: Long, on: Boolean)
        @JvmStatic private external fun nTranscribe(h: Long, threads: Int, audio: FloatArray, lang: String,
                                                    prompt: String?, beam: Int, vadPath: String?, listener: Listener?): Int
        @JvmStatic private external fun nSegCount(h: Long): Int
        @JvmStatic private external fun nSegT0(h: Long, i: Int): Long
        @JvmStatic private external fun nSegT1(h: Long, i: Int): Long
        @JvmStatic private external fun nSegText(h: Long, i: Int): ByteArray
        @JvmStatic private external fun nSegNoSpeech(h: Long, i: Int): Float
        @JvmStatic private external fun nSegAvgLogProb(h: Long, i: Int): Float
        @JvmStatic private external fun nSegTokenCount(h: Long, i: Int): Int
        @JvmStatic private external fun nSegTokenText(h: Long, i: Int, token: Int): ByteArray
        @JvmStatic private external fun nSegTokenProbability(h: Long, i: Int, token: Int): Float
        @JvmStatic private external fun nSegTokenT0(h: Long, i: Int, token: Int): Long
        @JvmStatic private external fun nSegTokenT1(h: Long, i: Int, token: Int): Long
        @JvmStatic private external fun nSystemInfo(): String
    }
}
