package hu.angolmagyar.fordito.ui.design

import android.content.Context
import android.provider.Settings
import hu.angolmagyar.fordito.Prefs

/** Animációs időtartamok (ms) és a csökkentett animációs mód. */
object PremiumMotion {
    const val CARD_ENTER = 420
    const val NODE_ACTIVATE = 450
    const val FLOW_LOOP = 1500
    const val STATE_CHANGE = 300
    const val SUCCESS = 850

    /** Igaz, ha a felhasználó kikapcsolta az animációkat az appban vagy a rendszerben. */
    fun reduced(ctx: Context): Boolean {
        if (Prefs(ctx).reduceMotion) return true
        return try {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) { false }
    }
}
