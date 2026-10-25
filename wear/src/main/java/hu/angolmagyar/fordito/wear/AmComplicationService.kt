package hu.angolmagyar.fordito.wear

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService

/**
 * Wear complication: mutatja, fut-e az élő fordítás, és indítja az órás appot.
 * A telefon WearBridge /am/status DataItem-jéből a legutóbbi állapotot a
 * shared prefs-ben tároljuk (a WearMainActivity frissíti).
 */
class AmComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        if (type != ComplicationType.SHORT_TEXT) return null
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("AM").build(),
            contentDescription = PlainComplicationText.Builder("AngolMagyar").build()
        ).setTitle(PlainComplicationText.Builder("Készenlét").build()).build()
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null
        val prefs = applicationContext.getSharedPreferences("wear_status", MODE_PRIVATE)
        val running = prefs.getBoolean("running", false)
        val paused = prefs.getBoolean("paused", false)
        val label = when {
            running && paused -> "Szünet"
            running -> "Élő"
            else -> "Készenlét"
        }
        val tap = PendingIntent.getActivity(
            this, 0,
            Intent(this, WearMainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder("AM").build(),
            contentDescription = PlainComplicationText.Builder("AngolMagyar: $label").build()
        )
            .setTitle(PlainComplicationText.Builder(label).build())
            .setTapAction(tap)
            .build()
    }
}
