package hu.angolmagyar.fordito

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

/**
 * Akkumulátor-optimalizálás kikapcsolása. Több készülékgyártó (Oppo/OnePlus/Xiaomi…) máshogy kezeli,
 * ezért egymás után több megoldást próbálunk, és soha nem dobunk kivételt.
 * Az első kéréshez kell a manifestben a REQUEST_IGNORE_BATTERY_OPTIMIZATIONS engedély.
 */
object BatterySettings {

    fun isIgnoring(ctx: Context): Boolean = try {
        (ctx.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager)
            .isIgnoringBatteryOptimizations(ctx.packageName)
    } catch (_: Exception) {
        false
    }

    fun open(ctx: Context) {
        val pkg = ctx.packageName
        val attempts: List<() -> Intent> = listOf(
            // 1) közvetlen „Engedélyezed, hogy mindig fusson a háttérben?” párbeszédablak
            { Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")) },
            // 2) az összes app listája az akkumulátor-optimalizálásnál
            { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
            // 3) az app adatlapja (ott: Akkumulátor → Korlátozás nélkül)
            { Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")) },
            // 4) a rendszerbeállítások főoldala
            { Intent(Settings.ACTION_SETTINGS) }
        )
        for (make in attempts) {
            try {
                ctx.startActivity(make().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                // próbáljuk a következőt
            }
        }
        Toast.makeText(
            ctx,
            "Nyisd meg kézzel: Beállítások → Akkumulátor → Alkalmazások → ${ctx.getString(R.string.app_name)} → Korlátozás nélkül",
            Toast.LENGTH_LONG
        ).show()
    }
}
