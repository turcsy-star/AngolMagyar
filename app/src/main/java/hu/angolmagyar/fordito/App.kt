package hu.angolmagyar.fordito

import android.app.Application
import android.graphics.Color
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import java.io.File

/** Alkalmazás-szintű inicializálás: téma, OLED, hibanapló, megszakadt átiratok helyreállítása. */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        AppState.logFile = File(filesDir, "errors.log")
        val prefs = Prefs(this)
        applyTheme(prefs.themeMode)
        oledBlack = prefs.oledBlack
        // a :core nem ismeri a natív könyvtárat: a neurális VAD-ot innen kapja
        Vad.neuralProvider = { model, audio, threads -> WhisperNative.vadSegments(model, audio, threads) }
        // hőállapot-figyelés: melegedéskor a Whisper kevesebb szálon fut (Perf.thermalScaled)
        try {
            val pm = getSystemService(POWER_SERVICE) as android.os.PowerManager
            Perf.thermalStatus = pm.currentThermalStatus
            pm.addThermalStatusListener { st ->
                Perf.thermalStatus = st
                DiagLog.log("HŐÁLLAPOT: $st")
            }
        } catch (e: Exception) { DiagLog.log("Hőállapot-figyelés nem indítható: ${e.message}") }
        // Ha a rendszer kilőtte a szolgáltatást, a félbehagyott mondatot pótoljuk.
        Thread { TranscriptStore.recoverPartials(this) }.start()
    }

    companion object {
        @Volatile var oledBlack: Boolean = false

        fun applyTheme(mode: Int) {
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }

        fun applyOledToWindow(activity: android.app.Activity) {
            if (!oledBlack) return
            activity.window.statusBarColor = Color.BLACK
            activity.window.navigationBarColor = Color.BLACK
            activity.window.decorView.setBackgroundColor(Color.BLACK)
            if (Build.VERSION.SDK_INT >= 29) {
                activity.window.isNavigationBarContrastEnforced = false
            }
        }
    }
}
