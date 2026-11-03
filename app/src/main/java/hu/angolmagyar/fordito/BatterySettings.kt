package hu.angolmagyar.fordito
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
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
            { Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")) },
            { Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS) },
            { Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")) },
            { Intent(Settings.ACTION_SETTINGS) }
        )
        for (make in attempts) {
            try {
                ctx.startActivity(make().addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
            }
        }
        Toast.makeText(
            ctx,
            "Nyisd meg kézzel: Beállítások → Akkumulátor → Alkalmazások → ${ctx.getString(R.string.app_name)} → Korlátozás nélkül",
            Toast.LENGTH_LONG
        ).show()
    }
}
