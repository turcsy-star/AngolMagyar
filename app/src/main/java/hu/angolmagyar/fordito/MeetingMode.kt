package hu.angolmagyar.fordito

import android.content.Context
import org.json.JSONObject

/**
 * Tárgyalás mód: magyar átirat, lazább confidence, hosszabb csend = új blokk,
 * takarékos ki, preferált nagy/egyedi magyar modell.
 *
 * A bekapcsolás előtti beállításokat elmentjük, és a [clear] visszaállítja őket,
 * így a fájl-átirat képernyő nem írja át tartósan az élő fordítás beállításait.
 */
object MeetingMode {

    /** @param temporary true, ha csak az adott képernyő idejére kell (visszaállítjuk kilépéskor). */
    fun apply(prefs: Prefs, temporary: Boolean = false) {
        if (!prefs.meetingMode) {
            val b = JSONObject()
            b.put("translateDir", prefs.translateDir)
            b.put("eco", prefs.eco)
            b.put("forcedEco", prefs.forcedEco)
            b.put("confidenceMin", prefs.confidenceMin)
            b.put("delayMs", prefs.delayMs)
            b.put("pushToTalk", prefs.pushToTalk)
            b.put("saveTranscript", prefs.saveTranscript)
            b.put("showEnglish", prefs.showEnglish)
            b.put("modelId", prefs.modelId)
            b.put("meetingSafeMode", prefs.meetingSafeMode)
            b.put("meetingChunkMinutes", prefs.meetingChunkMinutes)
            prefs.meetingBackup = b.toString()
            prefs.meetingTemporary = temporary
        } else if (!temporary) {
            prefs.meetingTemporary = false
        }
        prefs.translateDir = Prefs.DIR_NONE
        prefs.eco = false
        prefs.forcedEco = false
        prefs.confidenceMin = 12
        prefs.delayMs = 0
        prefs.pushToTalk = false
        prefs.saveTranscript = true
        prefs.showEnglish = false
        // magyar modell preferencia
        prefs.modelId = "hu_large" // ha nincs telepítve, a runtime HU_SMALL-ra esik
        prefs.meetingSafeMode = true
        prefs.meetingChunkMinutes = 12
        prefs.meetingMode = true
    }

    /** Kikapcsolás + a korábbi beállítások visszaállítása. */
    fun clear(prefs: Prefs) {
        val raw = prefs.meetingBackup
        if (raw.isNotBlank()) {
            try {
                val b = JSONObject(raw)
                prefs.translateDir = b.optString("translateDir", Prefs.DIR_EN_HU)
                prefs.eco = b.optBoolean("eco", false)
                prefs.forcedEco = b.optBoolean("forcedEco", false)
                prefs.confidenceMin = b.optInt("confidenceMin", 0)
                prefs.delayMs = b.optInt("delayMs", 0)
                prefs.pushToTalk = b.optBoolean("pushToTalk", false)
                prefs.saveTranscript = b.optBoolean("saveTranscript", true)
                prefs.showEnglish = b.optBoolean("showEnglish", true)
                prefs.modelId = b.optString("modelId", "small")
                prefs.meetingSafeMode = b.optBoolean("meetingSafeMode", true)
                prefs.meetingChunkMinutes = b.optInt("meetingChunkMinutes", 12)
            } catch (_: Exception) {
            }
        }
        prefs.meetingBackup = ""
        prefs.meetingTemporary = false
        prefs.meetingMode = false
    }

    fun isReady(ctx: Context): Boolean {
        return ModelManager.isInstalled(ctx, ModelManager.HU_LARGE) ||
            ModelManager.isInstalled(ctx, ModelManager.HU_SMALL)
    }

    fun preferredSpec(ctx: Context): ModelManager.Spec =
        ModelManager.forLang("hu", preferLarge = true, ctx)

    fun statusText(ctx: Context): String {
        val large = ModelManager.isInstalled(ctx, ModelManager.HU_LARGE)
        val small = ModelManager.isInstalled(ctx, ModelManager.HU_SMALL)
        return when {
            large -> ctx.getString(R.string.meeting_status_large)
            small -> ctx.getString(R.string.meeting_status_small)
            else -> ctx.getString(R.string.meeting_status_none)
        }
    }
}
