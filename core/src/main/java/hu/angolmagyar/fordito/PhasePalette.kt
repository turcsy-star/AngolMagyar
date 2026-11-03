package hu.angolmagyar.fordito

/**
 * A folyamat fázisaihoz tartozó hangulatszínek (ambient háttér) és a hozzájuk tartozó segédek.
 * A fázisváltás ellenőrzött (hiszterézis): a felismerés és a fordítás üzenete másodpercenként
 * váltogathat, de a szín csak akkor vált, ha az új fázis egy ideig stabil.
 */
object PhasePalette {
    enum class Phase { IDLE, PREPARE, RECOGNIZE, TRANSLATE, SAVE, DONE, ERROR, PAUSED }

    // SrtJob lépés-azonosítók (a core nem függ az app SrtJob objektumától)
    const val STEP_PREP = 0
    const val STEP_EXTRACT = 1
    const val STEP_PCM = 2
    const val STEP_RECOG = 3
    const val STEP_SAVE = 4

    fun color(p: Phase): Int = when (p) {
        Phase.IDLE -> 0xFF3B5B8C.toInt()
        Phase.PREPARE -> 0xFF7C8DB5.toInt()
        Phase.RECOGNIZE -> 0xFF2F9ADA.toInt()
        Phase.TRANSLATE -> 0xFFE0A42B.toInt()
        Phase.SAVE -> 0xFFAB47BC.toInt()
        Phase.DONE -> 0xFF2FBF8A.toInt()
        Phase.ERROR -> 0xFFE5534B.toInt()
        Phase.PAUSED -> 0xFFE28D5A.toInt()
    }

    fun phaseOf(
        step: Int, running: Boolean, finished: Boolean, failed: Boolean,
        withTranslation: Boolean, message: String, cooling: Boolean
    ): Phase = when {
        failed -> Phase.ERROR
        finished && !running -> Phase.DONE
        !running -> Phase.IDLE
        cooling -> Phase.PAUSED
        step == STEP_SAVE -> Phase.SAVE
        step == STEP_RECOG ->
            if (withTranslation && message.trimStart().startsWith("Fordítás")) Phase.TRANSLATE else Phase.RECOGNIZE
        step in STEP_PREP..STEP_PCM -> Phase.PREPARE
        else -> Phase.IDLE
    }

    /** Lineáris ARGB-keverés csatornánként; t = 0 → a, t = 1 → b. */
    fun blend(a: Int, b: Int, t: Float): Int {
        val k = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * k + 0.5f).toInt().coerceIn(0, 255)
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    fun withAlpha(color: Int, alpha: Float): Int =
        ((alpha.coerceIn(0f, 1f) * 255f + 0.5f).toInt() shl 24) or (color and 0x00FFFFFF)

    /** A fázisváltás hiszterézise: új fázis csak akkor lép életbe, ha [holdMs]-ig folyamatosan ez a jelölt. */
    class Tracker(private val holdMs: Long = 1500L, start: Phase = Phase.IDLE) {
        var current: Phase = start
            private set
        private var candidate: Phase = start
        private var since = 0L

        fun update(nowMs: Long, observed: Phase): Phase {
            // Befejezés és hiba azonnal látszik, a többi fázis csak stabilan vált.
            if (observed == Phase.DONE || observed == Phase.ERROR || observed == Phase.IDLE || current == Phase.IDLE) {
                current = observed; candidate = observed; since = nowMs
                return current
            }
            if (observed != candidate) { candidate = observed; since = nowMs }
            if (candidate != current && nowMs - since >= holdMs) current = candidate
            return current
        }
    }
}
