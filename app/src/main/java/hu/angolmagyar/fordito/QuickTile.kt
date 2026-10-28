package hu.angolmagyar.fordito

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Gyorsbeállítás-csempe: egy koppintás az indításhoz/leállításhoz.
 * v2.21.1 – AppState listener alapú azonnali állapotfrissítés.
 */
class QuickTile : TileService() {

    private val stateListener: () -> Unit = { refresh() }

    override fun onStartListening() {
        AppState.addListener(stateListener)
        refresh()
    }

    override fun onStopListening() {
        AppState.removeListener(stateListener)
        super.onStopListening()
    }

    override fun onClick() {
        if (AppState.running) {
            startService(
                Intent(this, TranslateService::class.java)
                    .setAction(TranslateService.ACTION_STOP)
            )
            // A listener automatikusan frissít, de azonnali visszajelzéshez:
            refresh()
            return
        }
        // Az indításhoz előtérben lévő képernyő kell (mikrofon / képernyő-hangrögzítés engedély).
        val lastMode = Prefs(this).lastMode
        val intent = Intent(this, LiveTranslateActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(LiveTranslateActivity.EXTRA_AUTO_START, lastMode)
        }
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(
                this, 1, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = if (AppState.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = if (AppState.running) {
            "Fut – koppints a leállításhoz"
        } else {
            "Koppints az indításhoz"
        }
        tile.updateTile()
    }
}
