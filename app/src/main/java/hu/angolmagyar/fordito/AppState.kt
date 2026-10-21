package hu.angolmagyar.fordito

import android.content.ComponentName
import android.content.Context
import android.service.quicksettings.TileService

data class AppError(val code: String, val message: String)

/** Folyamaton belüli közös állapot a szolgáltatás és a képernyők között. */
object AppState {
    const val ERR_NO_MODEL = "no_model"
    const val ERR_NO_TRANSLATOR = "no_translator"
    const val ERR_NO_OVERLAY = "no_overlay"
    const val ERR_NO_MIC = "no_mic"
    const val ERR_PROJECTION = "projection"
    const val ERR_AUDIO = "audio"
    const val ERR_OTHER = "other"

    @Volatile var running = false
    @Volatile var paused = false
    @Volatile var mode: String? = null
    @Volatile var level = 0f
    @Volatile var hint: String? = null
    @Volatile var lastError: AppError? = null
        set(v) {
            field = v
            if (v != null) addLog("ERR/${v.code}: ${v.message}")
        }
    @Volatile var testResult: String? = null
    /** Munkamenet kezdete (elapsedRealtime ms), 0 ha nem fut. */
    @Volatile var sessionStartedAt = 0L
    @Volatile var lastCaptionHu: String = ""
    @Volatile var lastCaptionEn: String = ""

    /** Hibanapló fájl (az App állítja be). */
    @Volatile var logFile: java.io.File? = null
    private val recent = ArrayDeque<String>()

    /** Egy sor a hibanaplóba (memória + fájl, legfeljebb ~200 KB). */
    @Synchronized
    fun addLog(line: String) {
        val stamp = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val entry = "[$stamp] $line"
        recent.addLast(entry)
        while (recent.size > 100) recent.removeFirst()
        try {
            val f = logFile ?: return
            if (f.exists() && f.length() > 200_000) {
                val keep = f.readLines().takeLast(300)
                f.writeText(keep.joinToString("\n") + "\n")
            }
            f.appendText(entry + "\n")
        } catch (_: Exception) {
        }
    }

    /** A legutóbbi naplósorok (újabb elöl). */
    @Synchronized
    fun readLog(max: Int = 60): List<String> {
        val f = logFile
        val fromFile = try {
            if (f != null && f.exists()) f.readLines().takeLast(max) else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
        val lines = if (fromFile.isNotEmpty()) fromFile else recent.toList().takeLast(max)
        return lines.reversed()
    }

    @Synchronized
    fun clearLog() {
        recent.clear()
        try {
            logFile?.delete()
        } catch (_: Exception) {
        }
    }

    fun notifyTile(ctx: Context) {
        try {
            TileService.requestListeningState(ctx, ComponentName(ctx, QuickTile::class.java))
        } catch (_: Exception) {
        }
    }
}
