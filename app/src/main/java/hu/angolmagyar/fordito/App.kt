package hu.angolmagyar.fordito

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import java.io.File

/** Alkalmazás-szintű inicializálás: téma, hibanapló, megszakadt átiratok helyreállítása. */
class App : Application() {

    override fun onCreate() {
        super.onCreate()
        AppState.logFile = File(filesDir, "errors.log")
        applyTheme(Prefs(this).themeMode)
        // Ha a rendszer kilőtte a szolgáltatást, a félbehagyott mondatot pótoljuk.
        Thread { TranscriptStore.recoverPartials(this) }.start()
    }

    companion object {
        fun applyTheme(mode: Int) {
            AppCompatDelegate.setDefaultNightMode(
                when (mode) {
                    1 -> AppCompatDelegate.MODE_NIGHT_NO
                    2 -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
