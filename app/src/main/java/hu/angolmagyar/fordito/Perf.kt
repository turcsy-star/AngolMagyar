package hu.angolmagyar.fordito

import android.content.Context
import android.os.Build

/**
 * Feldolgozási teljesítmény (1–5), futás közben is állítható csúszkával.
 * A szintből adódik: hány chunk fusson egyszerre (Vosk), hány szálon számoljon a Whisper,
 * mennyit pihenjen két chunk közt, és mennyire legyen óvatos melegedésnél.
 */
object Perf {
    const val MIN = 1
    const val MAX = 5
    private val NAMES = arrayOf("Takarékos", "Mérsékelt", "Kiegyensúlyozott", "Gyors", "Maximum")

    @Volatile var level = 3
        private set

    fun load(ctx: Context): Int {
        level = Prefs(ctx).raw().getInt("perfLevel", 4).coerceIn(MIN, MAX)
        return level
    }

    fun set(ctx: Context, v: Int) {
        level = v.coerceIn(MIN, MAX)
        Prefs(ctx).raw().edit().putInt("perfLevel", level).apply()
        SrtJob.changed()
    }

    fun name(l: Int = level) = NAMES[(l - 1).coerceIn(0, 4)]

    private fun cores() = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

    /** Whisper (ggml) szálszám. */
    fun whisperThreads(l: Int = level): Int = when (l) {
        1 -> 2
        2 -> (cores() * 3 / 8).coerceAtLeast(2)
        3 -> (cores() / 2).coerceAtLeast(2)
        4 -> (cores() - 2).coerceIn(3, 6)
        else -> cores().coerceAtMost(8)
    }

    /** Egyszerre feldolgozott chunkok száma Voskhoz (a modell közös, a felismerők külön szálon futnak). */
    fun voskWorkers(l: Int = level): Int = when (l) {
        1, 2, 3 -> 1
        4 -> 2
        else -> 3
    }.coerceAtMost(cores())

    /** Szálprioritás (Android nice-érték): minél magasabb a szint, annál előbb kap CPU-időt. */
    fun threadPriority(l: Int = level): Int = when (l) {
        1 -> android.os.Process.THREAD_PRIORITY_BACKGROUND
        2 -> android.os.Process.THREAD_PRIORITY_DEFAULT
        3 -> android.os.Process.THREAD_PRIORITY_FOREGROUND
        4 -> android.os.Process.THREAD_PRIORITY_DISPLAY
        else -> android.os.Process.THREAD_PRIORITY_URGENT_DISPLAY
    }

    /** A hívó szál prioritásának beállítása az aktuális szint szerint (élő csúszkához újra hívjuk). */
    fun applyPriority() {
        try { android.os.Process.setThreadPriority(threadPriority()) } catch (_: Throwable) {}
    }

    /** A legtöbb egyszerre futó munkaszál (a szálkészlet mérete). */
    fun maxWorkers(): Int = voskWorkers(MAX)

    /** Pihenő két chunk között (ms). */
    fun pauseMs(l: Int = level): Long = when (l) {
        1 -> 8_000L
        2 -> 4_000L
        3 -> 2_000L
        4 -> 500L
        else -> 0L
    }

    /** Melegedésnél mikor álljon meg: a PowerManager.THERMAL_STATUS_* ennél nagyobb-egyenlő értékénél. */
    fun thermalBlockAt(l: Int = level, strict: Boolean = false): Int = when {
        strict || l <= 2 -> 2          // MODERATE
        l <= 4 -> 3                    // SEVERE
        else -> 4                      // CRITICAL
    }

    fun describe(l: Int = level): String {
        val w = if (voskWorkers(l) > 1) "Vosk ${voskWorkers(l)} chunk párhuzamosan" else "Vosk 1 szálon"
        val p = if (pauseMs(l) > 0) "${pauseMs(l) / 1000} mp szünet chunkok közt" else "szünet nélkül"
        return "${name(l)} – Whisper ${whisperThreads(l)} CPU-szál, prioritás ${threadPriority(l)}, $w, $p"
    }

    fun isArm64(): Boolean = Build.SUPPORTED_64_BIT_ABIS.any { it == "arm64-v8a" }
}
