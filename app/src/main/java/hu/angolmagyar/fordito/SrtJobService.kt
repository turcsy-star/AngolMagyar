package hu.angolmagyar.fordito

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hang-/videófájlból SRT készítése előtérben futó szolgáltatásként: másik appra váltva,
 * zárolt képernyővel és az Activity bezárása után is tovább dolgozik (értesítés + wake lock).
 * Az állapotot a [SrtJob] tartja, a képernyő azt jeleníti meg.
 */
class SrtJobService : Service() {

    companion object {
        private const val CH_RUN = "srt_job_run"
        private const val CH_DONE = "srt_job_done"
        private const val NOTIF_RUN = 4402
        private const val NOTIF_DONE = 4403
        private const val ACTION_START = "hu.angolmagyar.fordito.SRT_START"
        private const val ACTION_CANCEL = "hu.angolmagyar.fordito.SRT_CANCEL"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_SRC = "src"
        private const val EXTRA_DIR = "dir"
        private const val EXTRA_TR = "tr"
        private const val EXTRA_DIARIZE = "diarize"

        /** Indítás: az állapot azonnal "fut"-ra vált, így a képernyő rögtön mutatja a lépéseket. */
        fun start(
            ctx: Context, uri: Uri, mode: SrtExporter.Mode, src: String, dir: String,
            needTranslate: Boolean, diarize: Boolean
        ) {
            if (SrtJob.running) return
            SrtJob.reset(needTranslate)
            val i = Intent(ctx, SrtJobService::class.java)
                .setAction(ACTION_START)
                .setData(uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_SRC, src)
                .putExtra(EXTRA_DIR, dir)
                .putExtra(EXTRA_TR, needTranslate)
                .putExtra(EXTRA_DIARIZE, diarize)
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (e: Exception) {
                SrtJob.begin(SrtJob.ST_PREP)
                SrtJob.fail("A háttérszolgáltatás nem indítható: ${e.message}")
            }
        }

        fun cancel(ctx: Context) {
            try {
                ctx.startService(Intent(ctx, SrtJobService::class.java).setAction(ACTION_CANCEL))
            } catch (_: Exception) {
            }
        }
    }

    private val cancelFlag = AtomicBoolean(false)
    private var chunker: ChunkedTranscriber? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var worker: Thread? = null
    private var lastNotifMs = 0L
    private var lastNotifStep = -2

    private val jobListener: () -> Unit = {
        if (SrtJob.running) {
            val now = System.currentTimeMillis()
            if (now - lastNotifMs >= 1000 || SrtJob.curStep != lastNotifStep) {
                lastNotifMs = now
                lastNotifStep = SrtJob.curStep
                try {
                    getSystemService(NotificationManager::class.java).notify(NOTIF_RUN, runNotif())
                } catch (_: Exception) {
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannels()
        SrtJob.addListener(jobListener)
    }

    override fun onDestroy() {
        SrtJob.removeListener(jobListener)
        releaseWake()
        super.onDestroy()
    }

    /** Android 15: az adatszinkron típusú előtér-szolgáltatásnak ~6 órás korlátja van. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelFlag.set(true)
        chunker?.cancel()
        if (SrtJob.running) SrtJob.fail("A rendszer időkorlátja lejárt (kb. 6 óra). Indítsd újra kisebb fájllal.")
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAsForeground()
                if (worker?.isAlive == true) return START_NOT_STICKY
                val uri = intent.data
                if (uri == null) {
                    SrtJob.begin(SrtJob.ST_PREP)
                    SrtJob.fail("Nincs kiválasztott fájl.")
                    stopFg()
                    return START_NOT_STICKY
                }
                val mode = try {
                    SrtExporter.Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "")
                } catch (_: Exception) {
                    SrtExporter.Mode.BOTH
                }
                launch(
                    uri, mode,
                    intent.getStringExtra(EXTRA_SRC) ?: "en",
                    intent.getStringExtra(EXTRA_DIR) ?: Prefs.DIR_NONE,
                    intent.getBooleanExtra(EXTRA_TR, false),
                    intent.getBooleanExtra(EXTRA_DIARIZE, false)
                )
            }
            ACTION_CANCEL -> {
                cancelFlag.set(true)
                chunker?.cancel()
                if (worker?.isAlive != true) {
                    // nincs mit leállítani (pl. a rendszer újraindította a szolgáltatást)
                    if (SrtJob.running) SrtJob.stopped()
                    stopFg()
                }
            }
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ értesítések

    private fun ensureChannels() {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_RUN, "SRT készítés (fut)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Folyamatjelző, amíg az átirat / felirat készül a háttérben"
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "SRT készítés (eredmény)", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Értesítés, ha elkészült vagy megszakadt a feliratkészítés"
            }
        )
    }

    private fun openPi(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, FileSubtitleActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun cancelPi(): PendingIntent = PendingIntent.getService(
        this, 1,
        Intent(this, SrtJobService::class.java).setAction(ACTION_CANCEL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun runNotif(): Notification {
        val pct = SrtJob.overall
        val step = SrtJob.label(SrtJob.curStep.coerceAtLeast(0))
        val now = System.currentTimeMillis()
        val eta = if (SrtJob.etaMs > 0)
            "Hátra kb. ${SrtJob.fmtDur(SrtJob.etaMs)} · kész kb. ${SrtJob.fmtClock(now + SrtJob.etaMs)}"
        else "Hátralévő idő: számítás…"
        val text = "$step · ${SrtJob.message}"
        return NotificationCompat.Builder(this, CH_RUN)
            .setSmallIcon(R.drawable.ic_subtitles)
            .setContentTitle("SRT készül… $pct%")
            .setContentText(text.take(100))
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\n$eta"))
            .setContentIntent(openPi())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(100, pct.coerceIn(0, 100), pct <= 0)
            .addAction(0, "Megszakítás", cancelPi())
            .build()
    }

    private fun startAsForeground() {
        val n = runNotif()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_RUN, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_RUN, n)
        }
    }

    private fun stopFg() {
        try {
            if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
        } catch (_: Exception) {
        }
        stopSelf()
    }

    private fun postResultNotif(title: String, text: String) {
        try {
            val n = NotificationCompat.Builder(this, CH_DONE)
                .setSmallIcon(R.drawable.ic_subtitles)
                .setContentTitle(title)
                .setContentText(text.take(100))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(openPi())
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(NOTIF_DONE, n)
        } catch (_: Exception) {
        }
    }

    private fun acquireWake() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AngolMagyar:SrtJob").also {
                it.setReferenceCounted(false)
                it.acquire(6 * 60 * 60 * 1000L) // max 6 óra
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWake() {
        try { wakeLock?.let { if (it.isHeld) it.release() } } catch (_: Exception) {}
        wakeLock = null
    }

    // ---------------------------------------------------------------- a feldolgozás

    private fun launch(
        uri: Uri, mode: SrtExporter.Mode, sourceLang: String, dir: String,
        needTranslate: Boolean, useDiarization: Boolean
    ) {
        cancelFlag.set(false)
        acquireWake()
        // Az értékeket most olvassuk ki: ha közben a képernyő bezárul, a beállítások változhatnak.
        val prefs = Prefs(this)
        val meeting = prefs.meetingMode
        val safeMode = prefs.meetingSafeMode
        val chunkMin = prefs.meetingChunkMinutes
        val preferOnnx = prefs.preferOnnx

        worker = Thread {
            var fr: FileRecognizer? = null
            var pcmFile: File? = null
            try {
                SrtJob.begin(SrtJob.ST_PREP)
                val modelSpec: ModelManager.Spec? = Engines.voskSpecFor(this, prefs, sourceLang)
                if (modelSpec != null && !ModelManager.isInstalled(this, modelSpec)) {
                    throw IllegalStateException(
                        "Nincs telepítve a ${modelSpec.label} modell. Töltsd le a Modellek képernyőn."
                    )
                }
                if (modelSpec == null) {
                    if (!SystemAsrSupport.audioSourceSupported()) {
                        throw IllegalStateException("A magyar felismeréshez Android 13 vagy újabb kell.")
                    }
                    if (!SystemAsrSupport.onDeviceAvailable(this) &&
                        !(prefs.allowOnlineAsr && SystemAsrSupport.anyAvailable(this))
                    ) {
                        throw IllegalStateException(
                            "Nincs magyar felismerő. Nyisd meg a Modellek képernyőt, és telepítsd a Google magyar offline csomagját."
                        )
                    }
                }
                SrtJob.finish()

                SrtJob.begin(SrtJob.ST_EXTRACT)
                val recognizer = FileRecognizer(this, prefs, cancelFlag)
                fr = recognizer
                val pcmTmp = extractPcm(uri)
                pcmFile = pcmTmp
                if (cancelFlag.get()) throw InterruptedException("Megszakítva")
                SrtJob.finish()

                SrtJob.begin(SrtJob.ST_PCM)
                val pcmBytes = pcmTmp.readBytes()
                val shorts = ShortArray(pcmBytes.size / 2)
                for (i in shorts.indices) {
                    val lo = pcmBytes[i * 2].toInt() and 0xff
                    val hi = pcmBytes[i * 2 + 1].toInt()
                    shorts[i] = ((hi shl 8) or lo).toShort()
                }
                val durationMin = shorts.size / 16000.0 / 60.0
                SrtJob.finish("%.1f perc hang".format(durationMin))
                val useChunked = meeting || safeMode || durationMin > 20

                val cues: List<SrtExporter.Cue>
                if (useChunked) {
                    SrtJob.begin(SrtJob.ST_RECOG, "%.0f perc hang, darabokban feldolgozva".format(durationMin))
                    val chunk = ChunkedTranscriber(this, prefs)
                    chunker = chunk
                    val chunkMs = chunkMin * 60 * 1000
                    var totalChunksEst = maxOf(1, ((shorts.size.toLong() * 1000 / 16000) / chunkMs).toInt() + 1)
                    var chunkStartMs = System.currentTimeMillis()
                    val result = chunk.run(
                        pcm16kMono = shorts,
                        chunkMs = chunkMs,
                        overlapMs = 8_000,
                        pauseBetweenMs = if (safeMode) 10_000L else 5_000L,
                        processChunk = { slice, offsetMs, idx, _, _ ->
                            chunkStartMs = System.currentTimeMillis()
                            val diarizer = DiarizerFactory.create(this, preferOnnx = preferOnnx)
                            val segs = if (useDiarization || meeting)
                                diarizer.diarize(slice, 16000) else emptyList()
                            val shifted = segs.map {
                                it.copy(startMs = it.startMs + offsetMs, endMs = it.endMs + offsetMs)
                            }
                            recognizer.recognize(
                                slice, sourceLang, modelSpec, needTranslate, dir, offsetMs,
                                { t -> speakerAt(prefs, shifted, t) }
                            ) { pct, msg ->
                                val tc = totalChunksEst.coerceAtLeast(1)
                                SrtJob.chunkCur = idx + 1
                                SrtJob.chunkTotal = tc
                                // a felismerés teljes haladása: kész chunkok + az aktuális %-a
                                val overall = ((idx * 100) + pct.coerceIn(0, 100)) / tc
                                val elapsedChunk = System.currentTimeMillis() - chunkStartMs
                                val etaChunk = if (pct in 1..99)
                                    ((elapsedChunk * 100.0 / pct) - elapsedChunk).toLong().coerceAtLeast(0)
                                else null
                                val etaTxt = if (etaChunk != null && etaChunk > 0)
                                    " · hátra e chunk: ${SrtJob.fmtDur(etaChunk)}" else ""
                                SrtJob.progress(overall.coerceIn(0, 99), "Chunk ${idx + 1}/$tc · $msg$etaTxt")
                            }
                        },
                        onProgress = { prog ->
                            totalChunksEst = prog.totalChunks.coerceAtLeast(1)
                            SrtJob.chunkTotal = totalChunksEst
                            SrtJob.chunkCur = prog.chunkIndex.coerceAtLeast(0)
                            SrtJob.cooling = prog.pausedForCooling
                            val msg = prog.message + if (prog.pausedForCooling) " ⏸ hűtés" else ""
                            SrtJob.progress(prog.pctOverall.coerceIn(0, 99), msg)
                        }
                    )
                    cues = result.cues
                    if (result.stoppedEarly) SrtJob.partial = true
                    SrtJob.finish("${cues.size} blokk")
                } else {
                    SrtJob.begin(SrtJob.ST_RECOG)
                    val diarizer = DiarizerFactory.create(this, preferOnnx = preferOnnx)
                    val segments = if (meeting || useDiarization) {
                        diarizer.diarize(shorts, 16000)
                    } else emptyList()
                    cues = recognizer.recognize(
                        shorts, sourceLang, modelSpec, needTranslate, dir, 0L,
                        { t -> speakerAt(prefs, segments, t) }
                    ) { pct, msg -> SrtJob.progress(pct.coerceIn(0, 99), msg) }
                    SrtJob.finish("${cues.size} blokk")
                }
                if (cancelFlag.get()) throw InterruptedException("Megszakítva")

                SrtJob.begin(SrtJob.ST_SAVE, "SRT összeállítása és mentése…")
                val srt = SrtExporter.build(cues, mode)

                // mentés a belső tárba is
                val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val name = "felirat_$stamp.srt"
                File(TranscriptStore.dir(this), name).writeText(srt, Charsets.UTF_8)
                val md = ExportHelper.toMarkdown(cues, "Tárgyalási átirat")
                ExportHelper.write(File(TranscriptStore.dir(this), "jegyzokonyv_$stamp.md"), md)
                val plain = ExportHelper.toPlainMinutes(cues)
                ExportHelper.write(File(TranscriptStore.dir(this), "jegyzokonyv_$stamp.txt"), plain)

                SrtJob.complete(cues.size, srt, name)
                postResultNotif(
                    "SRT kész – ${cues.size} feliratblokk",
                    "Koppints a megnyitáshoz, ott mentheted a telefonra (${name})."
                )
            } catch (e: InterruptedException) {
                SrtJob.stopped()
                postResultNotif("SRT készítés megszakítva", "A folyamat leállt.")
            } catch (e: Throwable) {
                SrtJob.fail(e.message ?: e.javaClass.simpleName)
                postResultNotif("SRT készítés hiba", e.message ?: e.javaClass.simpleName)
            } finally {
                try { fr?.close() } catch (_: Exception) {}
                try { pcmFile?.delete() } catch (_: Exception) {}
                chunker = null
                releaseWake()
                stopFg()
            }
        }.also { it.start() }
    }

    private fun speakerAt(prefs: Prefs, segments: List<SpeakerDiarizer.Segment>, timeMs: Long): String {
        if (segments.isEmpty()) return ""
        val seg = segments.firstOrNull { timeMs >= it.startMs && timeMs < it.endMs }
            ?: segments.minByOrNull { kotlin.math.abs(it.startMs - timeMs) }
        if (seg == null) return ""
        val map = SpeakerNames.fromPrefs(prefs)
        return SpeakerNames.label(seg.speakerId, map)
    }

    /** A fájlt a cache-be másolja, majd PCM 16 kHz mono-ra dekódolja. */
    private fun extractPcm(uri: Uri): File {
        SrtJob.progress(5, "Fájl másolása a gyorsítótárba…")
        val cache = File(cacheDir, "input_${System.currentTimeMillis()}")
        contentResolver.openInputStream(uri)?.use { input ->
            cache.outputStream().use { input.copyTo(it) }
        } ?: throw IllegalStateException("Nem olvasható a fájl.")
        if (cancelFlag.get()) throw InterruptedException("Megszakítva")

        SrtJob.progress(15, "Hang dekódolása (PCM 16 kHz)…")
        val pcm = File(cacheDir, "pcm_${System.currentTimeMillis()}.raw")
        try {
            // a dekódolás nem ad százalékot, ezért lassan növekvő becslést mutatunk az eltelt idővel
            val pulse = AtomicBoolean(true)
            val t0 = System.currentTimeMillis()
            Thread {
                var p = 15
                while (pulse.get() && !cancelFlag.get()) {
                    try { Thread.sleep(800) } catch (_: InterruptedException) { break }
                    if (!pulse.get()) break
                    p = (p + 1).coerceAtMost(90)
                    val sec = (System.currentTimeMillis() - t0) / 1000
                    SrtJob.progress(p, "Hang dekódolása… ${sec}s")
                }
            }.start()
            try {
                AudioHelper.decodeToPcm16kMono(cache, pcm) { cancelFlag.get() }
            } finally {
                pulse.set(false)
            }
        } finally {
            cache.delete()
        }
        if (!pcm.exists() || pcm.length() < 1000) {
            throw IllegalStateException("Nem sikerült dekódolni a hangot. Próbálj WAV/MP3 fájlt.")
        }
        SrtJob.progress(100, "Hang kinyerése kész")
        return pcm
    }
}
