package hu.angolmagyar.fordito

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Éjszakai üzemmód: a várakozó fájlokat eltesszük, és amint a telefon töltőn van (és Wi-Fi-n, ha online
 * fordítás is kell), értesítést küldünk. A feldolgozást az értesítésre koppintás után, a Fájl képernyő
 * indítja: az Android a háttérből nem engedi szabadon indítani az adatszinkron előtér-szolgáltatást,
 * ezért a felhasználó egy koppintással jóváhagy – reggelre így is kész lehet.
 */
object NightMode {
    private const val JOB_ID = 4410
    private const val KEY = "nightQueue"
    private const val CH = "night_mode"
    const val EXTRA_NIGHT = "night_start"

    private fun sp(ctx: Context) = Prefs(ctx).raw()

    fun pending(ctx: Context): List<Pair<Uri, String>> {
        val raw = sp(ctx).getString(KEY, null) ?: return emptyList()
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map { i -> a.getJSONObject(i).let { Uri.parse(it.getString("u")) to it.optString("n") } }
        } catch (e: Exception) {
            DiagLog.log("NightMode: sérült sor (${e.message})")
            emptyList()
        }
    }

    fun clear(ctx: Context) {
        sp(ctx).edit().remove(KEY).apply()
        try { (ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).cancel(JOB_ID) } catch (_: Exception) {}
    }

    /** Elteszi a várakozó fájlokat és ütemez; ad: ütemezett fájlok száma. */
    fun schedule(ctx: Context, items: List<SrtJob.QItem>): Int {
        if (items.isEmpty()) return 0
        val arr = JSONArray()
        for (q in items) {
            try {
                ctx.contentResolver.takePersistableUriPermission(q.uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) { /* nem minden URI engedi: az app élő jogosultsága marad */ }
            arr.put(JSONObject().put("u", q.uri.toString()).put("n", q.name))
        }
        sp(ctx).edit().putString(KEY, arr.toString()).apply()
        val needNet = OnlinePrefs.usable(ctx) && OnlinePrefs.mode(ctx) != OnlinePrefs.OFF
        val info = JobInfo.Builder(JOB_ID, ComponentName(ctx, NightJobService::class.java))
            .setRequiresCharging(true)
            .setRequiresBatteryNotLow(true)
            .setRequiredNetworkType(if (needNet) JobInfo.NETWORK_TYPE_UNMETERED else JobInfo.NETWORK_TYPE_NONE)
            .build()
        try {
            (ctx.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler).schedule(info)
        } catch (e: Exception) {
            DiagLog.log("NightMode: ütemezés sikertelen: ${e.message}")
            return 0
        }
        return arr.length()
    }

    internal fun notifyReady(ctx: Context) {
        val n = pending(ctx).size
        if (n == 0) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CH, "Éjszakai feldolgozás", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val pi = PendingIntent.getActivity(
            ctx, 7,
            Intent(ctx, FileSubtitleActivity::class.java).putExtra(EXTRA_NIGHT, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val note = NotificationCompat.Builder(ctx, CH)
            .setSmallIcon(R.drawable.ic_subtitles)
            .setContentTitle("Töltőn vagy – indulhat az éjszakai feldolgozás")
            .setContentText("$n fájl vár. Koppints az indításhoz.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try { nm.notify(4411, note) } catch (e: SecurityException) { DiagLog.log("NightMode: nincs értesítési jog") }
    }
}

class NightJobService : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        try { NightMode.notifyReady(this) } catch (e: Exception) { DiagLog.log("NightJob hiba: ${e.message}") }
        return false
    }
    override fun onStopJob(params: JobParameters?): Boolean = false
}
