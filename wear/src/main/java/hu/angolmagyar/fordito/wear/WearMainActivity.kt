package hu.angolmagyar.fordito.wear

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable

/**
 * Wear OS companion: a telefon állapotától függő gombok (indítás / szünet / irány / leállítás),
 * színes állapotsor, az utolsó felirat nagyban, rezgés új figyelmeztetésnél.
 * Protokoll: /am/cmd, /am/status (lásd telefon WearBridge).
 */
class WearMainActivity : AppCompatActivity(), DataClient.OnDataChangedListener {

    companion object {
        const val PATH_CMD = "/am/cmd"
        const val PATH_STATUS = "/am/status"
    }

    private lateinit var statusView: TextView
    private lateinit var warnView: TextView
    private lateinit var captionView: TextView
    private lateinit var idleButtons: LinearLayout
    private lateinit var runButtons: LinearLayout
    private lateinit var pauseBtn: Button

    private var phoneNodeId: String? = null
    private var lastWarn = ""

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun btn(label: String, cmd: String) = Button(this).apply {
        text = label
        isAllCaps = false
        minHeight = dp(44)
        setOnClickListener { sendCmd(cmd) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(14), dp(18), dp(14), dp(24))
        }

        statusView = TextView(this).apply {
            text = getString(R.string.w_connecting)
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.LTGRAY)
        }
        warnView = TextView(this).apply {
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFB454.toInt())
            visibility = View.GONE
        }
        captionView = TextView(this).apply {
            text = "—"
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            setPadding(0, dp(10), 0, dp(10))
        }

        idleButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(btn(getString(R.string.w_start_mic), "start_mic"))
            addView(btn(getString(R.string.w_start_ptt), "start_ptt"))
        }
        pauseBtn = btn(getString(R.string.w_pause), "toggle_pause")
        runButtons = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(pauseBtn)
            addView(btn(getString(R.string.w_switch), "switch_dir"))
            addView(btn(getString(R.string.w_stop), "stop"))
        }

        col.addView(statusView)
        col.addView(warnView)
        col.addView(captionView)
        col.addView(idleButtons)
        col.addView(runButtons)
        setContentView(ScrollView(this).apply { addView(col) })

        resolvePhoneNode()
    }

    override fun onResume() {
        super.onResume()
        try {
            Wearable.getDataClient(this).addListener(this)
        } catch (_: Exception) {
        }
        resolvePhoneNode()
    }

    override fun onPause() {
        super.onPause()
        try {
            Wearable.getDataClient(this).removeListener(this)
        } catch (_: Exception) {
        }
    }

    private fun resolvePhoneNode() {
        try {
            Wearable.getNodeClient(this).connectedNodes
                .addOnSuccessListener { nodes: List<Node> ->
                    phoneNodeId = nodes.firstOrNull { it.isNearby }?.id ?: nodes.firstOrNull()?.id
                    statusView.text = getString(if (phoneNodeId != null) R.string.w_phone_ok else R.string.w_no_phone)
                }
                .addOnFailureListener {
                    statusView.text = getString(R.string.w_api_error)
                }
        } catch (_: Exception) {
            statusView.text = getString(R.string.w_unavailable)
        }
    }

    private fun buzz(ms: Long = 40) {
        try {
            val v: Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as? Vibrator
            }
            v?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } catch (_: Exception) {
        }
    }

    private fun sendCmd(cmd: String) {
        val node = phoneNodeId
        if (node == null) {
            Toast.makeText(this, R.string.w_no_phone, Toast.LENGTH_SHORT).show()
            resolvePhoneNode()
            return
        }
        try {
            Wearable.getMessageClient(this)
                .sendMessage(node, PATH_CMD, cmd.toByteArray(Charsets.UTF_8))
                .addOnSuccessListener { buzz(30) }
                .addOnFailureListener {
                    Toast.makeText(this, R.string.w_send_failed, Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, e.message ?: "", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDataChanged(buffer: DataEventBuffer) {
        for (event in buffer) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            val path = event.dataItem.uri.path ?: continue
            if (path != PATH_STATUS) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val running = map.getBoolean("running", false)
            val paused = map.getBoolean("paused", false)
            val mode = map.getString("mode") ?: ""
            val hu = map.getString("lastHu") ?: ""
            val en = map.getString("lastEn") ?: ""
            val warn = map.getString("warn") ?: ""

            runOnUiThread {
                statusView.text = when {
                    !running -> getString(R.string.w_idle)
                    paused -> getString(R.string.w_paused, mode)
                    else -> getString(R.string.w_running, mode)
                }
                statusView.setTextColor(
                    when {
                        !running -> Color.LTGRAY
                        paused -> 0xFFFBBF24.toInt()
                        else -> 0xFF34D399.toInt()
                    }
                )
                idleButtons.visibility = if (running) View.GONE else View.VISIBLE
                runButtons.visibility = if (running) View.VISIBLE else View.GONE
                pauseBtn.text = getString(if (paused) R.string.w_resume else R.string.w_pause)
                captionView.text = when {
                    hu.isNotBlank() -> hu
                    en.isNotBlank() -> en
                    else -> "—"
                }
                if (warn.isBlank()) {
                    warnView.visibility = View.GONE
                } else {
                    warnView.text = warn
                    warnView.visibility = View.VISIBLE
                    if (warn != lastWarn) buzz(120) // új figyelmeztetés: hosszabb rezgés
                }
                lastWarn = warn
            }
        }
    }
}
