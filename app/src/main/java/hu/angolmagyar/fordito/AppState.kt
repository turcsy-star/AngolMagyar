package hu.angolmagyar.fordito

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.service.quicksettings.TileService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppError(val code: String, val message: String)

/** Egy pillanatnyi állapot a UI számára (v2.21+ reaktív). */
data class AppSnapshot(
    val running: Boolean = false,
    val paused: Boolean = false,
    val mode: String? = null,
    val level: Float = 0f,
    val hint: String? = null,
    val lastError: AppError? = null,
    val sessionStartedAt: Long = 0L,
    val lastCaptionHu: String = "",
    val lastCaptionEn: String = ""
)

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
        set(v) {
            if (field == v) return
            field = v
            publish()
        }
    @Volatile var paused = false
        set(v) {
            if (field == v) return
            field = v
            publish()
        }
    @Volatile var mode: String? = null
        set(v) {
            if (field == v) return
            field = v
            publish()
        }
    @Volatile var level = 0f
    @Volatile var hint: String? = null
        set(v) {
            if (field == v) return
            field = v
            publish()
        }
    @Volatile var lastError: AppError? = null
        set(v) {
            field = v
            if (v != null) addLog("ERR/${v.code}: ${v.message}")
            publish()
        }
    @Volatile var testResult: String? = null
    /** Munkamenet kezdete (elapsedRealtime ms), 0 ha nem fut. */
    @Volatile var sessionStartedAt = 0L
    @Volatile var lastCaptionHu: String = ""
    @Volatile var lastCaptionEn: String = ""

    // v2.22.4 – többnyelvű kétpaneles élő napló (eredeti | fordítás, beszélők, nyelvek)
    private val _liveLog = MutableStateFlow<List<LiveLine>>(emptyList())
    val liveLog: StateFlow<List<LiveLine>> = _liveLog.asStateFlow()
    private val liveIds = java.util.concurrent.atomic.AtomicLong(0)
    fun nextLiveId(): Long = liveIds.incrementAndGet()
    @Synchronized fun liveAppend(line: LiveLine) { _liveLog.value = LiveLog.append(_liveLog.value, line) }
    @Synchronized fun liveSetTranslation(id: Long, t: String) { _liveLog.value = LiveLog.setTranslation(_liveLog.value, id, t) }
    @Synchronized fun liveClear() { _liveLog.value = emptyList() }

    // v2.21.1 – level throttling (max ~20 fps) a felesleges recomposition elkerülésére
    private var lastLevelPublishMs = 0L
    private const val LEVEL_THROTTLE_MS = 50L

    /** Hangszint frissítése throttlinggel. A nyers érték mindig tárolódik. */
    fun updateLevel(newLevel: Float) {
        level = newLevel
        val now = SystemClock.uptimeMillis()
        if (now - lastLevelPublishMs >= LEVEL_THROTTLE_MS) {
            lastLevelPublishMs = now
            publish()
        }
    }

    // v2.21.0 – reaktív StateFlow + legacy listener
    private val _snapshot = MutableStateFlow(AppSnapshot())
    val snapshot: StateFlow<AppSnapshot> = _snapshot.asStateFlow()

    private val listeners = java.util.concurrent.CopyOnWriteArrayList<() -> Unit>()
    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }

    /** Frissíti a StateFlow-t és értesíti a legacy listenereket. */
    fun notifyListeners() {
        publish()
        for (l in listeners) try { l() } catch (_: Exception) {}
    }

    private fun publish() {
        _snapshot.value = AppSnapshot(
            running = running,
            paused = paused,
            mode = mode,
            level = level,
            hint = hint,
            lastError = lastError,
            sessionStartedAt = sessionStartedAt,
            lastCaptionHu = lastCaptionHu,
            lastCaptionEn = lastCaptionEn
        )
    }

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
