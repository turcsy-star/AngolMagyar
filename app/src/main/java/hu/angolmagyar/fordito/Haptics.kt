package hu.angolmagyar.fordito

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object Haptics {
    fun tick(ctx: Context) {
        if (!Prefs(ctx).haptics) return
        try {
            val v = vibrator(ctx) ?: return
            if (Build.VERSION.SDK_INT >= 29) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
            } else if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(12, 40))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(12)
            }
        } catch (_: Exception) {
        }
    }

    fun click(ctx: Context) {
        if (!Prefs(ctx).haptics) return
        try {
            val v = vibrator(ctx) ?: return
            if (Build.VERSION.SDK_INT >= 29) {
                v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
            } else if (Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createOneShot(25, 60))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(25)
            }
        } catch (_: Exception) {
        }
    }

    private fun vibrator(ctx: Context): Vibrator? {
        return if (Build.VERSION.SDK_INT >= 31) {
            val vm = ctx.getSystemService(VibratorManager::class.java)
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }
}
