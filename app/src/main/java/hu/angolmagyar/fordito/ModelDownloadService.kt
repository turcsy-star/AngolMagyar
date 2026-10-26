package hu.angolmagyar.fordito

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Modell-letöltés előtérben (foreground service), hogy képernyőzár / Activity kilépés
 * ne szakítsa meg a kapcsolatot. Folytatható (HTTP Range), szüneteltethető.
 */
class ModelDownloadService : Service() {

    companion object {
        const val CHANNEL = "model_download"
        const val NOTIF_ID = 42
        const val ACTION_START = "hu.angolmagyar.fordito.DL_START"
        const val ACTION_PAUSE = "hu.angolmagyar.fordito.DL_PAUSE"
        const val ACTION_RESUME = "hu.angolmagyar.fordito.DL_RESUME"
        const val ACTION_CANCEL = "hu.angolmagyar.fordito.DL_CANCEL"
        const val EXTRA_IDS = "ids"

        @Volatile var running = false
            private set
        @Volatile var paused = false
            private set
        @Volatile var pct = 0
            private set
        @Volatile var message = ""
            private set
        @Volatile var done = false
            private set
        @Volatile var failedMsg: String? = null
            private set

        private val listeners = CopyOnWriteArrayList<(Int, String, Boolean, Boolean) -> Unit>()

        fun addListener(l: (pct: Int, msg: String, paused: Boolean, finished: Boolean) -> Unit) {
            listeners.add(l)
            if (running || done) l(pct, message, paused, done)
        }

        fun removeListener(l: (Int, String, Boolean, Boolean) -> Unit) {
            listeners.remove(l)
        }

        fun start(ctx: Context, ids: List<String>) {
            val i = Intent(ctx, ModelDownloadService::class.java)
                .setAction(ACTION_START)
                .putStringArrayListExtra(EXTRA_IDS, ArrayList(ids))
            if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i)
            else ctx.startService(i)
        }

        fun pause(ctx: Context) {
            ctx.startService(Intent(ctx, ModelDownloadService::class.java).setAction(ACTION_PAUSE))
        }

        fun resume(ctx: Context) {
            ctx.startService(Intent(ctx, ModelDownloadService::class.java).setAction(ACTION_RESUME))
        }

        fun cancel(ctx: Context) {
            ctx.startService(Intent(ctx, ModelDownloadService::class.java).setAction(ACTION_CANCEL))
        }

        private fun notifyListeners() {
            val p = pct
            val m = message
            val pa = paused
            val f = done
            for (l in listeners) {
                try { l(p, m, pa, f) } catch (_: Exception) {}
            }
        }
    }

    private val cancelFlag = AtomicBoolean(false)
    private var ctl = ModelManager.DlControl()
    private var wakeLock: PowerManager.WakeLock? = null
    private var worker: Thread? = null
    private var queue: List<String> = emptyList()
    private var queueTotal = 0
    private var completedBytes = 0L
    private var totalEstBytes = 0L
    private val MB = 1024L * 1024L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AngolMagyar:ModelDl").apply {
            setReferenceCounted(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val ids = intent.getStringArrayListExtra(EXTRA_IDS)?.toList().orEmpty()
                if (ids.isEmpty()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (running && worker?.isAlive == true) return START_STICKY
                startAsForeground("Letöltés indul…")
                beginQueue(ids)
            }
            ACTION_PAUSE -> {
                if (running && !paused) {
                    paused = true
                    ctl.pause()
                    post(-1, "$message · szüneteltetve", false)
                    updateNotif("Szüneteltetve", true)
                }
            }
            ACTION_RESUME -> {
                if (running && paused && queue.isNotEmpty()) {
                    paused = false
                    beginQueue(queue) // folytatja a fennmaradó sort
                }
            }
            ACTION_CANCEL -> {
                cancelFlag.set(true)
                ctl.pause()
                paused = false
                running = false
                done = true
                message = "Megszakítva"
                Companion.notifyListeners()
                stopFg()
                Thread { finalNotif("Letöltés megszakítva", "A félbehagyott rész megmarad, folytatható.") }.start()
            }
        }
        return START_STICKY
    }

    private fun beginQueue(ids: List<String>) {
        queue = ids
        if (queueTotal == 0) queueTotal = ids.size
        if (totalEstBytes == 0L) {
            totalEstBytes = ids.sumOf { estBytes(it) }
        }
        cancelFlag.set(false)
        ctl = ModelManager.DlControl()
        running = true
        paused = false
        done = false
        failedMsg = null
        worker = Thread {
            try {
                wakeLock?.acquire(6 * 60 * 60 * 1000L) // max 6 óra
            } catch (_: Exception) {}
            val failed = ArrayList<String>()
            val prefs = Prefs(this)
            while (queue.isNotEmpty() && !cancelFlag.get()) {
                val id = queue.first()
                val est = estBytes(id)
                val idx = queueTotal - queue.size + 1
                var currentIsMl = id == "mlkit"
                try {
                    val t0 = SystemClock.elapsedRealtime()
                    var lastBytes = 0L
                    if (id == "mlkit") {
                        post(-1, "($idx/$queueTotal) Fordítómodell…", false)
                        downloadMlKit("($idx/$queueTotal)")
                    } else {
                        val spec = ModelManager.byId(id)
                        ModelManager.download(
                            this, spec, ctl,
                            { doneB, total ->
                                val now = SystemClock.elapsedRealtime()
                                val dt = ((now - t0) / 1000.0).coerceAtLeast(0.5)
                                val speed = ((doneB - lastBytes).coerceAtLeast(0) / dt / 1024).toInt()
                                lastBytes = doneB
                                val overallDone = completedBytes + doneB
                                val pctV = if (totalEstBytes > 0)
                                    (overallDone * 100 / totalEstBytes).toInt().coerceIn(0, 99)
                                else if (total > 0) (doneB * 100 / total).toInt().coerceIn(0, 99)
                                else -1
                                val etaSec = if (speed > 0 && total > doneB)
                                    ((total - doneB) / 1024 / speed.coerceAtLeast(1)).toLong()
                                else -1L
                                val eta = if (etaSec > 0) " · ${fmtEta(etaSec)}" else ""
                                post(
                                    pctV,
                                    "($idx/$queueTotal) ${shortName(id)} · ${doneB / MB}/${if (total > 0) total / MB else "?"} MB · ${speed} KB/s$eta",
                                    false
                                )
                            },
                            { stage -> post(-1, "($idx/$queueTotal) ${shortName(id)}: $stage", false) }
                        )
                        if (spec.isWhisper) AsrPrefs.setWhisperId(this, spec.id)
                        if (id == ModelManager.LARGE.id) prefs.modelId = "large"
                        if (id == ModelManager.SMALL.id) prefs.modelId = "small"
                    }
                    completedBytes += est
                    queue = queue.drop(1)
                } catch (e: ModelManager.PausedException) {
                    paused = true
                    post(-1, "${shortName(id)} · szüneteltetve", true)
                    updateNotif("Szüneteltetve – koppints a folytatáshoz", true)
                    try { wakeLock?.release() } catch (_: Exception) {}
                    return@Thread
                } catch (e: Exception) {
                    if (cancelFlag.get()) break
                    failed.add("${shortName(id)}: ${friendly(e)}")
                    completedBytes += est
                    queue = queue.drop(1)
                }
            }
            try { wakeLock?.release() } catch (_: Exception) {}
            running = false
            done = true
            if (cancelFlag.get()) {
                message = "Megszakítva. A félbehagyott rész megmarad."
                failedMsg = null
            } else if (failed.isEmpty()) {
                pct = 100
                message = "Kész, minden letöltődött."
                failedMsg = null
            } else {
                message = "Letöltési hiba"
                failedMsg = failed.joinToString("\n")
            }
            Companion.notifyListeners()
            stopFg()
            // A "ragadt" folyamatjelző helyett végleges, elhúzható értesítés (különben örökre
            // "Modellek letöltése (2/3) 547/547 MB" maradna a sávban, mintha nem készült volna el).
            val fm = failedMsg
            when {
                cancelFlag.get() -> finalNotif("Letöltés megszakítva", "A félbehagyott rész megmarad, folytatható.")
                fm != null -> finalNotif("Letöltési hiba", fm)
                else -> finalNotif("Modellek letöltve ✓", "Minden kért modell telepítve van.")
            }
        }.also { it.start() }
    }

    private fun finalNotif(title: String, text: String) {
        try {
            // várunk, hogy az Android ne dobja el a végső értesítést a gyors frissítések miatt
            try { Thread.sleep(1500) } catch (_: InterruptedException) {}
            getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
            try { Thread.sleep(300) } catch (_: InterruptedException) {}
            val open = PendingIntent.getActivity(
                this, 0,
                Intent(this, ModelsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val n = NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_translate)
                .setContentTitle(title)
                .setContentText(text.take(80))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setOngoing(false)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
        } catch (_: Exception) {
        }
    }

    private fun post(p: Int, msg: String, isPaused: Boolean) {
        if (p >= 0) pct = p
        message = msg
        paused = isPaused
        Companion.notifyListeners()
        updateNotif(msg)
    }

    private fun estBytes(id: String): Long = when (id) {
        "mlkit" -> 60 * MB
        ModelManager.LARGE.id -> 130 * MB
        ModelManager.SMALL.id -> 40 * MB
        else -> ModelManager.byId(id).let { if (it.approxMb > 0) it.approxMb * MB else 50 * MB }
    }

    private fun shortName(id: String): String =
        if (id == "mlkit") "Fordítómodell"
        else try { ModelManager.byId(id).label.substringBefore("(").trim() } catch (_: Exception) { id }

    private fun fmtEta(sec: Long): String = when {
        sec < 60 -> "kb. $sec mp"
        sec < 3600 -> "kb. ${(sec + 59) / 60} perc"
        else -> "kb. ${sec / 3600} ó ${(sec % 3600 + 59) / 60} p"
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: return "ismeretlen hiba"
        return when {
            m.contains("abort", true) || m.contains("reset", true) || m.contains("timed out", true) ||
                m.contains("hiányos", true) || m.contains("Unable to resolve", true) ||
                m.contains("Connection", true) ->
                "megszakadt a kapcsolat – folytatható az OK-val"
            else -> m
        }
    }

    /** Igaz, ha a magyar fordítómodell ténylegesen letöltött állapotban van (az angol beépített). */
    private fun huModelPresent(): Boolean {
        return try {
            val mgr = com.google.mlkit.common.model.RemoteModelManager.getInstance()
            val m = com.google.mlkit.nl.translate.TranslateRemoteModel.Builder(
                com.google.mlkit.nl.translate.TranslateLanguage.HUNGARIAN).build()
            val l = java.util.concurrent.CountDownLatch(1)
            var r = false
            val direct = java.util.concurrent.Executor { it.run() }
            mgr.isModelDownloaded(m)
                .addOnSuccessListener(direct) { r = it == true; l.countDown() }
                .addOnFailureListener(direct) { l.countDown() }
            l.await(10, java.util.concurrent.TimeUnit.SECONDS)
            r
        } catch (_: Exception) {
            false
        }
    }

    /**
     * A magyar fordítómodell letöltése. Korábban az időtúllépés is "sikernek" számított, ezért a rendszer
     * késznek jelezte a letöltést, miközben a modell nem volt meg. Most a végén ellenőrizzük, hogy tényleg letöltődött-e.
     */
    private fun downloadMlKit(label: String) {
        if (huModelPresent()) return
        MlKitModels.ensure(
            listOf(com.google.mlkit.nl.translate.TranslateLanguage.ENGLISH, com.google.mlkit.nl.translate.TranslateLanguage.HUNGARIAN),
            { cancelFlag.get() },
            { el ->
                if (ctl.paused) throw ModelManager.PausedException()
                post(-1, "$label Fordítómodell letöltése… ${el / 60}:${"%02d".format(el % 60)}", false)
            },
            20 * 60L
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Modell letöltés", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Vosk / fordító modellek letöltése a háttérben"
                setShowBadge(false)
            }
        )
    }

    private fun startAsForeground(text: String) {
        val notif = buildNotif(text)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notif)
        }
    }

    @Volatile private var lastNotifMs = 0L

    /** Az Android eldobja a túl gyakori értesítésfrissítéseket (akár a végsőt is), ezért legfeljebb ~1/mp. */
    private fun updateNotif(text: String, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastNotifMs < 1000) return
        lastNotifMs = now
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIF_ID, buildNotif(text))
    }

    private fun buildNotif(text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, ModelsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pausePi = PendingIntent.getService(
            this, 1,
            Intent(this, ModelDownloadService::class.java).setAction(
                if (paused) ACTION_RESUME else ACTION_PAUSE
            ),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelPi = PendingIntent.getService(
            this, 2,
            Intent(this, ModelDownloadService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_translate)
            .setContentTitle(if (paused) "Letöltés szünetel" else "Modellek letöltése")
            .setContentText(text.take(80))
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setOngoing(!done)
            .setOnlyAlertOnce(true)
            .setProgress(100, pct.coerceIn(0, 100), pct < 0)
            .addAction(0, if (paused) "Folytatás" else "Szünet", pausePi)
            .addAction(0, "Megszakítás", cancelPi)
            .build()
    }

    private fun stopFg() {
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH)
            else @Suppress("DEPRECATION") stopForeground(false)
        } catch (_: Exception) {}
        stopSelf()
    }

    override fun onDestroy() {
        // Ne állítsuk le a letöltést Activity onDestroy miatt – a service önálló.
        // Csak ha a rendszer öli meg a service-t, a Range folytatás a partial fájlból megy.
        try { wakeLock?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
