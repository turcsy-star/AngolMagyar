package hu.angolmagyar.fordito.ui.design

import android.content.Context
import android.provider.Settings
import hu.angolmagyar.fordito.Prefs

/**
 * Platinum Motion Engine – v2.65.0
 *
 * Egységes mozgásrendszer és automatikus animációcsökkentés.
 * A konstansok ms-ban vannak; a [scale] figyelembe veszi a csökkentett módot.
 */
object PremiumMotion {
    const val CARD_ENTER = 420
    const val NODE_ACTIVATE = 450
    const val FLOW_LOOP = 1500
    const val STATE_CHANGE = 300
    const val SUCCESS = 850
    const val FADE = 220
    const val SLIDE = 320
    const val SPRING_STIFFNESS = 380f
    const val SPRING_DAMPING = 0.82f

    enum class Profile {
        /** Teljes animációk. */
        FULL,
        /** Rövidebb, diszkrétebb mozgás. */
        SUBTLE,
        /** Csak elengedhetetlen állapotváltások, hurok nélkül. */
        REDUCED,
        /** Semmi animáció. */
        NONE
    }

    /** Igaz, ha a felhasználó kikapcsolta az animációkat az appban vagy a rendszerben. */
    fun reduced(ctx: Context): Boolean {
        if (Prefs(ctx).reduceMotion) return true
        return try {
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        } catch (e: Exception) {
            false
        }
    }

    /** Aktuális profil a preferenciák és a rendszerbeállítás alapján. */
    fun profile(ctx: Context): Profile {
        if (reduced(ctx)) return Profile.NONE
        return when (Prefs(ctx).motionProfile.lowercase()) {
            "subtle" -> Profile.SUBTLE
            "reduced" -> Profile.REDUCED
            "none" -> Profile.NONE
            else -> Profile.FULL
        }
    }

    /**
     * Időtartam skálázása a profil szerint.
     * @return 0 ha NONE, egyébként a skálázott ms.
     */
    fun scale(ctx: Context, baseMs: Int): Int {
        return when (profile(ctx)) {
            Profile.FULL -> baseMs
            Profile.SUBTLE -> (baseMs * 0.65f).toInt().coerceAtLeast(80)
            Profile.REDUCED -> (baseMs * 0.35f).toInt().coerceAtLeast(40)
            Profile.NONE -> 0
        }
    }

    fun cardEnter(ctx: Context): Int = scale(ctx, CARD_ENTER)
    fun stateChange(ctx: Context): Int = scale(ctx, STATE_CHANGE)
    fun success(ctx: Context): Int = scale(ctx, SUCCESS)
    fun fade(ctx: Context): Int = scale(ctx, FADE)

    /** Hurok-animációk (pl. folyamatjelző) futhatnak-e. */
    fun allowLoops(ctx: Context): Boolean = profile(ctx) == Profile.FULL

    /** Cinema mód: FULL profil + felhasználói kapcsoló. */
    fun cinemaEnabled(ctx: Context): Boolean =
        profile(ctx) == Profile.FULL && Prefs(ctx).diamondCinemaMode

    /** Státusz szöveg a Next Wave / diagnosztika képernyőhöz. */
    fun statusLabel(ctx: Context): String {
        val p = profile(ctx)
        return when (p) {
            Profile.FULL -> "Teljes mozgásprofil"
            Profile.SUBTLE -> "Diszkrét mozgás"
            Profile.REDUCED -> "Csökkentett animáció"
            Profile.NONE -> "Animációk kikapcsolva"
        }
    }
}
