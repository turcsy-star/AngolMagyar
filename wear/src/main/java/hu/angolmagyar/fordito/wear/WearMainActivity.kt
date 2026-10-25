package hu.angolmagyar.fordito.wear

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * v2.21.1 – Wear OS felület: valódi Data Layer szinkron a telefonnal.
 *
 * Protocol (megegyezik a telefon WearBridge-ével):
 *  - /am/cmd     üzenet: "start_mic" | "stop" | "toggle_pause"
 *  - /am/status  DataItem: running, paused, mode, lastHu, lastEn, warn
 */
class WearMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                WearApp()
            }
        }
    }
}

private const val TAG = "WearAM"
private const val PATH_CMD = "/am/cmd"
private const val PATH_STATUS = "/am/status"

data class PhoneStatus(
    val running: Boolean = false,
    val paused: Boolean = false,
    val mode: String = "",
    val lastHu: String = "",
    val lastEn: String = "",
    val warn: String = "",
    val connected: Boolean = false
)

private fun wearHaptic(ctx: Context) {
    try {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        if (Build.VERSION.SDK_INT >= 29) {
            v.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
        } else if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createOneShot(30, 80))
        }
    } catch (_: Exception) {}
}

@Composable
fun WearApp() {
    val context = LocalContext.current
    var status by remember { mutableStateOf(PhoneStatus()) }
    var busy by remember { mutableStateOf(false) }

    // Data Layer figyelés
    DisposableEffect(Unit) {
        val client = Wearable.getDataClient(context)
        val listener = DataClient.OnDataChangedListener { buffer: DataEventBuffer ->
            for (event in buffer) {
                if (event.type != DataEvent.TYPE_CHANGED) continue
                val path = event.dataItem.uri.path ?: continue
                if (path != PATH_STATUS) continue
                try {
                    val map = DataMapItem.fromDataItem(event.dataItem).dataMap
                    status = PhoneStatus(
                        running = map.getBoolean("running", false),
                        paused = map.getBoolean("paused", false),
                        mode = map.getString("mode", "") ?: "",
                        lastHu = map.getString("lastHu", "") ?: "",
                        lastEn = map.getString("lastEn", "") ?: "",
                        warn = map.getString("warn", "") ?: "",
                        connected = true
                    )
                    // Complication frissítéshez
                    context.getSharedPreferences("wear_status", Context.MODE_PRIVATE).edit()
                        .putBoolean("running", status.running)
                        .putBoolean("paused", status.paused)
                        .apply()
                } catch (e: Exception) {
                    Log.w(TAG, "status parse: ${e.message}")
                }
            }
        }
        client.addListener(listener)
        onDispose { client.removeListener(listener) }
    }

    // Első csatlakozás ellenőrzés
    LaunchedEffect(Unit) {
        try {
            val nodes = withContext(Dispatchers.IO) {
                Wearable.getNodeClient(context).connectedNodes.await()
            }
            if (nodes.isNotEmpty()) {
                status = status.copy(connected = true)
            }
        } catch (e: Exception) {
            Log.d(TAG, "nodes: ${e.message}")
        }
    }

    fun sendCmd(cmd: String) {
        if (busy) return
        wearHaptic(context)
        busy = true
        // fire-and-forget a UI szálon (Wearable API main-safe)
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { nodes: List<Node> ->
                if (nodes.isEmpty()) {
                    status = status.copy(connected = false)
                    busy = false
                    return@addOnSuccessListener
                }
                val msgClient: MessageClient = Wearable.getMessageClient(context)
                var remaining = nodes.size
                for (node in nodes) {
                    msgClient.sendMessage(node.id, PATH_CMD, cmd.toByteArray(Charsets.UTF_8))
                        .addOnCompleteListener {
                            remaining--
                            if (remaining <= 0) busy = false
                        }
                }
            }
            .addOnFailureListener {
                status = status.copy(connected = false)
                busy = false
            }
    }

    Scaffold(timeText = { TimeText() }) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Állapot
            Text(
                text = when {
                    !status.connected -> "Telefon?"
                    status.running && status.paused -> "Szünet"
                    status.running -> "Fut"
                    else -> "Angol-Magyar"
                },
                style = MaterialTheme.typography.title3,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Felirat előnézet
            val caption = status.lastHu.ifBlank { status.lastEn }
            if (caption.isNotBlank()) {
                Text(
                    text = caption,
                    style = MaterialTheme.typography.body2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
            } else if (status.warn.isNotBlank()) {
                Text(
                    text = status.warn,
                    style = MaterialTheme.typography.caption2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Gombok
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        if (status.running) sendCmd("stop") else sendCmd("start_mic")
                    },
                    modifier = Modifier.size(56.dp),
                    colors = if (status.running) {
                        ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.error
                        )
                    } else {
                        ButtonDefaults.primaryButtonColors()
                    }
                ) {
                    Text(if (status.running) "Stop" else "Rec")
                }

                if (status.running) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = { sendCmd("toggle_pause") },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Text(if (status.paused) "▶" else "❚❚")
                    }
                }
            }

            if (!status.connected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Nincs kapcsolat a telefonnal",
                    style = MaterialTheme.typography.caption3,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
