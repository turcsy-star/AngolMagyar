package hu.angolmagyar.fordito

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Helyi összeomlás-napló. A kezeletlen kivételt a filesDir/crash.log fájlba írja (szinkron, mert a
 * folyamat mindjárt megszűnik), majd továbbadja az eredeti kezelőnek, így a rendszer viselkedése nem változik.
 * A következő indításkor a főképernyő felajánlja a (anonimizált) jelentés elküldését.
 */
object CrashLog {
    private const val FILE = "crash.log"
    private const val PREF_SEEN = "crashLogSeenLen"

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun install(app: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try { record(app, thread, error) } catch (_: Throwable) { }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun record(ctx: Context, thread: Thread, error: Throwable) {
        val version = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?" } catch (_: Throwable) { "?" }
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val meta = linkedMapOf(
            "Verzió" to version,
            "Telefon" to "${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            "Memória" to "max ${Runtime.getRuntime().maxMemory() / (1024 * 1024)} MB, szabad ${Runtime.getRuntime().freeMemory() / (1024 * 1024)} MB",
            "Hőállapot" to Perf.thermalStatus.toString(),
            "Feladat" to "fut=${SrtJob.running} lépés=${SrtJob.curStep} folyamat=${SrtJob.overall}%"
        )
        val entry = CrashReport.format(time, thread.name, error, meta)
        val f = file(ctx)
        val old = try { if (f.exists()) f.readText(Charsets.UTF_8) else "" } catch (_: Throwable) { "" }
        f.writeText(CrashReport.append(old, entry), Charsets.UTF_8)
        try { DiagLog.log("ÖSSZEOMLÁS: ${error.javaClass.simpleName}: ${error.message}") } catch (_: Throwable) { }
    }

    /** A napló teljes szövege (üres, ha nincs). */
    fun read(ctx: Context): String = try {
        val f = file(ctx)
        if (f.exists()) f.readText(Charsets.UTF_8) else ""
    } catch (_: Throwable) { "" }

    /** Új (még nem mutatott) összeomlás összefoglalója, vagy null. */
    fun unseenSummary(ctx: Context): String? {
        val text = read(ctx)
        if (text.isBlank()) return null
        val seen = Prefs(ctx).raw().getInt(PREF_SEEN, 0)
        if (text.length == seen) return null
        return CrashReport.lastSummary(text).ifBlank { "ismeretlen hiba" }
    }

    fun markSeen(ctx: Context) {
        Prefs(ctx).raw().edit().putInt(PREF_SEEN, read(ctx).length).apply()
    }

    fun clear(ctx: Context) {
        try { file(ctx).delete() } catch (_: Throwable) { }
        Prefs(ctx).raw().edit().remove(PREF_SEEN).apply()
    }
}
