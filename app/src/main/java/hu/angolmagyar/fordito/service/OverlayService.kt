package hu.angolmagyar.fordito.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import hu.angolmagyar.fordito.AppState
import hu.angolmagyar.fordito.R
import hu.angolmagyar.fordito.ui.MainActivity

/**
 * v2.21.0 – Lebegő ablak szolgáltatás.
 * Megjelenít egy kis, mindig látható feliratot a rendszer tetején.
 * A meglévő Overlay.kt logikájával párhuzamosan használható / később összevonható.
 */
class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: TextView? = null
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> hideOverlay()
            else -> showOverlay()
        }
        return START_STICKY
    }

    private fun showOverlay() {
        if (running) return
        running = true

        startForeground(NOTIFICATION_ID, buildNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val tv = TextView(this).apply {
            text = AppState.lastCaptionHu.ifBlank { "Angol-Magyar – várakozás…" }
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xCC0F172A.toInt())
            setPadding(24, 16, 24, 16)
            textSize = 14f
            maxLines = 3
        }
        overlayView = tv

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 80
        }

        try {
            windowManager?.addView(tv, params)
        } catch (e: Exception) {
            AppState.addLog("OverlayService addView failed: ${e.message}")
            stopSelf()
        }
    }

    private fun hideOverlay() {
        running = false
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {
        }
        overlayView = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val channelId = "overlay_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(channelId, "Lebegő felirat", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Angol-Magyar lebegő ablak")
            .setContentText("Érintsd meg a visszatéréshez")
            .setSmallIcon(R.drawable.ic_stat_translate)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        hideOverlay()
        super.onDestroy()
    }

    companion object {
        const val ACTION_SHOW = "hu.angolmagyar.fordito.OVERLAY_SHOW"
        const val ACTION_HIDE = "hu.angolmagyar.fordito.OVERLAY_HIDE"
        private const val NOTIFICATION_ID = 42
    }
}
