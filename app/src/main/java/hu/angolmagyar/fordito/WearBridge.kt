package hu.angolmagyar.fordito

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable

/**
 * Telefon ↔ Wear OS Data Layer híd.
 *
 * Útvonalak:
 *  - /am/cmd          üzenet: "start_mic" | "start_ptt" | "stop" | "toggle_pause" | "switch_dir"
 *  - /am/status       DataItem: running, paused, mode, lastHu, lastEn, warn
 *
 * Az óra modul (`:wear`) ugyanazt a protocolt használja.
 */
object WearBridge : MessageClient.OnMessageReceivedListener {

    private const val TAG = "WearBridge"
    const val PATH_CMD = "/am/cmd"
    const val PATH_STATUS = "/am/status"

    @Volatile private var bound = false
    private var appCtx: Context? = null

    fun start(ctx: Context) {
        if (bound) return
        appCtx = ctx.applicationContext
        try {
            Wearable.getMessageClient(ctx.applicationContext).addListener(this)
            bound = true
            pushStatus(ctx)
        } catch (e: Exception) {
            Log.w(TAG, "Wear nem elérhető: ${e.message}")
        }
    }

    fun stop(ctx: Context) {
        if (!bound) return
        try {
            Wearable.getMessageClient(ctx.applicationContext).removeListener(this)
        } catch (_: Exception) {
        }
        bound = false
    }

    /** Állapot küldése az órára. */
    fun pushStatus(ctx: Context, lastHu: String = "", lastEn: String = "") {
        try {
            val req = PutDataMapRequest.create(PATH_STATUS).apply {
                dataMap.putBoolean("running", AppState.running)
                dataMap.putBoolean("paused", AppState.paused)
                dataMap.putString("mode", AppState.mode ?: "")
                dataMap.putString("lastHu", lastHu.take(120))
                dataMap.putString("lastEn", lastEn.take(80))
                dataMap.putString("warn", (AppState.hint ?: "").take(120))
                dataMap.putLong("ts", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(ctx.applicationContext).putDataItem(req)
        } catch (e: Exception) {
            Log.d(TAG, "pushStatus: ${e.message}")
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != PATH_CMD) return
        val cmd = String(event.data, Charsets.UTF_8)
        val ctx = appCtx ?: return
        when (cmd) {
            "start_mic" -> {
                if (!AppState.running) {
                    val i = Intent(ctx, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(MainActivity.EXTRA_AUTO_START, TranslateService.MODE_MIC)
                    }
                    ctx.startActivity(i)
                }
            }
            "start_ptt" -> {
                if (!AppState.running) {
                    val i = Intent(ctx, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        putExtra(MainActivity.EXTRA_AUTO_START, TranslateService.MODE_PTT)
                    }
                    ctx.startActivity(i)
                }
            }
            "stop" -> {
                ctx.startService(
                    Intent(ctx, TranslateService::class.java).setAction(TranslateService.ACTION_STOP)
                )
            }
            "toggle_pause" -> {
                ctx.startService(
                    Intent(ctx, TranslateService::class.java)
                        .setAction(TranslateService.ACTION_TOGGLE_PAUSE)
                )
            }
            "switch_dir" -> {
                ctx.startService(
                    Intent(ctx, TranslateService::class.java)
                        .setAction(TranslateService.ACTION_SWITCH_DIR)
                )
            }
        }
        pushStatus(ctx)
    }
}
