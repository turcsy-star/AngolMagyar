package hu.angolmagyar.fordito

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Hang-/videófájlok sorozatából SRT készítése előtérben futó szolgáltatásként: másik appra váltva,
 * zárolt képernyővel és az Activity bezárása után is tovább dolgozik (értesítés + wake lock).
 * A fájlok listáját és az állapotot a [SrtJob] tartja, a képernyő azt jeleníti meg.
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
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_LANG = "lang"
        const val KIND_SUBTITLE = "subtitle"
        const val KIND_TRANSCRIPT = "transcript"

        /** Ennyi ideje nincs haladás → figyelmeztetés (valószínűleg a rendszer lelassította a háttérfutást). */
        const val STALL_WARN_MS = 150_000L

        /**
         * Indítás: a [SrtJob.queue] várakozó fájljait dolgozza fel sorban.
         * Az állapot azonnal "fut"-ra vált, így a képernyő rögtön mutatja a lépéseket.
         */
        fun start(
            ctx: Context, mode: SrtExporter.Mode, src: String, dir: String,
            needTranslate: Boolean, diarize: Boolean, kind: String, langTag: String
        ) {
            if (SrtJob.batchRunning) return
            val waiting = SrtJob.queue.filter { it.state == SrtJob.Q_WAIT }
            if (waiting.isEmpty()) return
            SrtJob.startBatch()
            SrtJob.reset(needTranslate)
            val i = Intent(ctx, SrtJobService::class.java)
                .setAction(ACTION_START)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .putExtra(EXTRA_MODE, mode.name)
                .putExtra(EXTRA_SRC, src)
                .putExtra(EXTRA_DIR, dir)
                .putExtra(EXTRA_TR, needTranslate)
                .putExtra(EXTRA_DIARIZE, diarize)
                .putExtra(EXTRA_KIND, kind)
                .putExtra(EXTRA_LANG, langTag)
            // a fájlokhoz tartozó olvasási jogot is átadjuk a szolgáltatásnak
            val clip = ClipData.newRawUri("files", waiting[0].uri)
            for (k in 1 until waiting.size) clip.addItem(ClipData.Item(waiting[k].uri))
            i.clipData = clip
            try {
                if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
            } catch (e: Exception) {
                SrtJob.begin(SrtJob.ST_PREP)
                SrtJob.fail("A háttérszolgáltatás nem indítható: ${e.message}")
                SrtJob.endBatch()
            }
        }

        fun cancel(ctx: Context) {
            try {
                ctx.startService(Intent(ctx, SrtJobService::class.java).setAction(ACTION_CANCEL))
            } catch (_: Exception) {
            }
        }

        fun langName(code: String): String = when (code) {
            "hu" -> "magyar"; "de" -> "német"; "fr" -> "francia"
            "es" -> "spanyol"; "it" -> "olasz"; "uk" -> "ukrán"
            else -> "angol"
        }
    }

    /** Egy kötegre (az összes fájlra) érvényes beállítások. */
    private class Opts(
        val mode: SrtExporter.Mode, val src: String, val dir: String, val needTranslate: Boolean,
        val diarize: Boolean, val kind: String, val langTag: String
    )

    private val cancelFlag = AtomicBoolean(false)
    private var chunker: ParallelChunker? = null
    private var recRef: FileRecognizer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var worker: Thread? = null
    private var lastNotifMs = 0L
    private var lastNotifStep = -2
    private val tickHandler = Handler(Looper.getMainLooper())
    private val notifTick = object : Runnable {
        override fun run() {
            if (!SrtJob.batchRunning) return
            pushRunNotif()
            tickHandler.postDelayed(this, 5_000)
        }
    }

    private val jobListener: () -> Unit = {
        if (SrtJob.batchRunning) {
            val now = System.currentTimeMillis()
            if (now - lastNotifMs >= 1000 || SrtJob.curStep != lastNotifStep) {
                lastNotifMs = now
                lastNotifStep = SrtJob.curStep
                pushRunNotif()
            }
        }
    }

    private fun pushRunNotif() {
        try {
            getSystemService(NotificationManager::class.java).notify(NOTIF_RUN, runNotif())
        } catch (_: Exception) {
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
        tickHandler.removeCallbacks(notifTick)
        releaseWake()
        super.onDestroy()
    }

    /** Android 15: az adatszinkron típusú előtér-szolgáltatásnak ~6 órás korlátja van. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        cancelFlag.set(true)
        chunker?.cancel()
        recRef?.abort()
        if (SrtJob.running) SrtJob.fail("A rendszer időkorlátja lejárt (kb. 6 óra). Indítsd újra kisebb fájlokkal.")
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startAsForeground()
                if (worker?.isAlive == true) return START_NOT_STICKY
                val mode = try {
                    SrtExporter.Mode.valueOf(intent.getStringExtra(EXTRA_MODE) ?: "")
                } catch (_: Exception) {
                    SrtExporter.Mode.BOTH
                }
                val opts = Opts(
                    mode,
                    intent.getStringExtra(EXTRA_SRC) ?: "en",
                    intent.getStringExtra(EXTRA_DIR) ?: Prefs.DIR_NONE,
                    intent.getBooleanExtra(EXTRA_TR, false),
                    intent.getBooleanExtra(EXTRA_DIARIZE, false),
                    intent.getStringExtra(EXTRA_KIND) ?: KIND_SUBTITLE,
                    intent.getStringExtra(EXTRA_LANG) ?: (intent.getStringExtra(EXTRA_SRC) ?: "en")
                )
                runBatch(opts)
            }
            ACTION_CANCEL -> {
                cancelFlag.set(true)
                chunker?.cancel()
                recRef?.abort()
                if (worker?.isAlive != true) {
                    // nincs mit leállítani (pl. a rendszer újraindította a szolgáltatást)
                    if (SrtJob.running) SrtJob.stopped()
                    SrtJob.stopWaiting()
                    SrtJob.endBatch()
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
                description = "Hang és rezgés, ha elkészült vagy megszakadt a feliratkészítés"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250, 150, 400)
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
        val now = System.currentTimeMillis()
        val pct = SrtJob.overall
        val step = SrtJob.label(SrtJob.curStep.coerceAtLeast(0))
        val files = if (SrtJob.fileTotal > 1) " · ${SrtJob.fileNo}/${SrtJob.fileTotal}. fájl" else ""
        val eta = if (SrtJob.etaAtMs > 0)
            "Várhatóan kész: ${SrtJob.fmtClock(SrtJob.etaAtMs)} körül (hátra kb. ${SrtJob.fmtDur(SrtJob.etaMs)})"
        else "Várható befejezés: az első darab után"
        val stalled = SrtJob.running && !SrtJob.cooling && now - SrtJob.lastProgressMs > STALL_WARN_MS
        val warn = if (stalled) "⚠ Nincs új haladás ${SrtJob.fmtDur(now - SrtJob.lastProgressMs)} ideje – ellenőrizd az akkumulátor-beállítást.\n" else ""
        val text = "$step · ${SrtJob.message}"
        return NotificationCompat.Builder(this, CH_RUN)
            .setSmallIcon(R.drawable.ic_subtitles)
            .setContentTitle("SRT készül… $pct%$files")
            .setContentText((if (stalled) "⚠ nincs haladás · " else "") + text.take(90))
            .setStyle(NotificationCompat.BigTextStyle().bigText("$warn$text\n$eta"))
            .setContentIntent(openPi())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(true)
            .setWhen(if (SrtJob.batchStartMs > 0) SrtJob.batchStartMs else now)
            .setUsesChronometer(true)
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
        tickHandler.removeCallbacks(notifTick)
        tickHandler.postDelayed(notifTick, 5_000)
    }

    private fun stopFg() {
        tickHandler.removeCallbacks(notifTick)
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

    private fun errText(e: Throwable): String {
        val parts = ArrayList<String>()
        var t: Throwable? = e
        var n = 0
        while (t != null && n < 4) {
            val m = t.message
            parts.add(if (m.isNullOrBlank()) t.javaClass.simpleName else "${t.javaClass.simpleName}: $m")
            t = t.cause
            n++
        }
        return parts.joinToString(" ← ")
    }

    /** A teljes sor feldolgozása egymás után; egy fájl hibája nem állítja meg a többit. */
    private fun runBatch(o: Opts) {
        cancelFlag.set(false)
        acquireWake()
        Perf.load(this)
        worker = Thread {
            try {
                while (!cancelFlag.get()) {
                    val item = SrtJob.takeNext() ?: break
                    processOne(item, o)
                }
            } catch (_: Throwable) {
            } finally {
                if (cancelFlag.get()) SrtJob.stopWaiting()
                SrtJob.endBatch()
                chunker = null
                recRef = null
                batchNotif()
                releaseWake()
                stopFg()
            }
        }.also { it.start() }
    }

    private fun batchNotif() {
        val items = SrtJob.queue.filter { it.state != SrtJob.Q_WAIT }
        if (items.isEmpty()) return
        val ok = items.count { it.state == SrtJob.Q_DONE }
        val err = items.count { it.state == SrtJob.Q_ERR }
        val stop = items.count { it.state == SrtJob.Q_STOP }
        if (items.size == 1) {
            val it = items[0]
            when (it.state) {
                SrtJob.Q_DONE -> postResultNotif(
                    "SRT kész – ${it.cueCount} feliratblokk",
                    if (it.saved) it.detail else "Koppints a megnyitáshoz, ott mentheted a telefonra."
                )
                SrtJob.Q_ERR -> postResultNotif("SRT készítés hiba", it.detail)
                else -> postResultNotif("SRT készítés megszakítva", it.detail.ifBlank { "A folyamat leállt." })
            }
        } else {
            val parts = ArrayList<String>()
            parts.add("$ok kész")
            if (err > 0) parts.add("$err hibás")
            if (stop > 0) parts.add("$stop megszakítva")
            postResultNotif("SRT készítés vége – ${items.size} fájl", parts.joinToString(", "))
        }
    }

    private fun processOne(item: SrtJob.QItem, o: Opts) {
        val uri = item.uri
        val prefs = Prefs(this)
        val sourceLang = o.src
        val needTranslate = o.needTranslate
        val dir = o.dir
        val mode = o.mode
        val useDiarization = o.diarize
        val kind = o.kind
        val langTag = o.langTag
        // Az értékeket most olvassuk ki: ha közben a képernyő bezárul, a beállítások változhatnak.
        val meeting = prefs.meetingMode
        val safeMode = prefs.meetingSafeMode
        val chunkMin = prefs.meetingChunkMinutes
        val preferOnnx = prefs.preferOnnx

        SrtJob.reset(needTranslate)

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val base = item.name.takeIf { it.isNotBlank() }?.let { OutputWriter.baseName(it) } ?: "felirat_$stamp"
        val srtFileName = srtNameFor(kind, base, langTag, false)
        val hist = getSharedPreferences("srt_hist", MODE_PRIVATE)
        var partialSupplier: (() -> List<SrtExporter.Cue>)? = null
        var histKey = ""
        var audioMs = 1L

        /** Elmenti az eddigi (részleges) eredményt, ha van; a mentés helyét adja vissza. */
        fun salvage(): String? {
            val cues = try { partialSupplier?.invoke().orEmpty() } catch (_: Exception) { emptyList() }
            if (cues.isEmpty()) return null
            return try {
                saveOutputs(kind, uri, base, langTag, SrtExporter.build(cues, mode), cues, true)
            } catch (_: Exception) {
                null
            }
        }

        var fr: FileRecognizer? = null
        var pcmFile: File? = null
        try {
            // ------------------------------------------------------------ előkészítés
            SrtJob.begin(SrtJob.ST_PREP, "Felismerő kiválasztása…")
            val asr = Engines.asrFor(this, prefs, sourceLang)
            SrtJob.engineLabel = asr.label
            val tgtCode = if (needTranslate) dir.substringAfter("_", "") else ""
            SrtJob.langLabel = langName(sourceLang) +
                (if (tgtCode.isNotBlank()) " → ${langName(tgtCode)} fordítás" else "")
            android.util.Log.i("SrtJob", "Felismerő: ${asr.label} · nyelv: $sourceLang · fájl: ${item.name}")
            when (asr.kind) {
                Engines.K_WHISPER -> if (asr.whisper == null || !ModelManager.isInstalled(this, asr.whisper)) {
                    throw IllegalStateException(
                        if (!WhisperNative.available())
                            "A Whisper nem használható ezen a telefonon: ${WhisperNative.error()}"
                        else "Nincs letöltött Whisper modell. Nyisd meg a Modellek képernyőt, és töltsd le (magyarhoz ajánlott)."
                    )
                }
                Engines.K_VOSK -> if (asr.vosk == null || !ModelManager.isInstalled(this, asr.vosk)) {
                    throw IllegalStateException(
                        "Nincs telepítve a ${asr.vosk?.label ?: "Vosk"} modell. Töltsd le a Modellek képernyőn."
                    )
                }
                else -> {
                    // A rendszer-felismerő csak magyarhoz való; más nyelvnél nem váltunk rá csendben.
                    if (sourceLang != "hu") {
                        throw IllegalStateException(
                            "Nincs ${langName(sourceLang)} nyelvű felismerő modell (Whisper vagy Vosk). " +
                                "Töltsd le a Modellek képernyőn – a rendszer-felismerő csak magyar beszédhez használható."
                        )
                    }
                    if (!SystemAsrSupport.audioSourceSupported()) {
                        throw IllegalStateException(
                            "A magyar felismeréshez Android 13 vagy újabb kell – vagy töltsd le a Whisper modellt a Modellek képernyőn."
                        )
                    }
                    if (!SystemAsrSupport.onDeviceAvailable(this) &&
                        !(prefs.allowOnlineAsr && SystemAsrSupport.anyAvailable(this))
                    ) {
                        throw IllegalStateException(
                            "Nincs magyar felismerő. Töltsd le a Whisper modellt a Modellek képernyőn (offline, pontos)."
                        )
                    }
                }
            }
            val recognizer = FileRecognizer(this, prefs, cancelFlag)
            fr = recognizer
            recRef = recognizer
            SrtJob.progress(40, "Felismerő modell betöltése: ${asr.label}…")
            recognizer.prepare(asr, needTranslate, dir)
            if (cancelFlag.get()) throw InterruptedException("Megszakítva")
            SrtJob.finish(asr.label)

            // -------------------------------------------------------- hang kinyerése
            SrtJob.begin(SrtJob.ST_EXTRACT, "Hang dekódolása")
            val pcmTmp = File(cacheDir, "pcm_${System.currentTimeMillis()}.raw")
            pcmFile = pcmTmp
            AudioHelper.decodeUriToPcm16kMono(this, uri, pcmTmp, { cancelFlag.get() }) { p ->
                SrtJob.progress(p, "Hang dekódolása")
            }
            if (cancelFlag.get()) throw InterruptedException("Megszakítva")
            if (!pcmTmp.exists() || pcmTmp.length() < 1000) {
                throw IllegalStateException("Nem sikerült dekódolni a hangot. Próbálj WAV/MP3 fájlt.")
            }
            SrtJob.finish()

            SrtJob.begin(SrtJob.ST_PCM)
            val total = (pcmTmp.length() / 2).toInt()
            val shorts = ShortArray(total)
            java.io.RandomAccessFile(pcmTmp, "r").use { raf ->
                val mb = raf.channel.map(FileChannel.MapMode.READ_ONLY, 0, total * 2L).order(ByteOrder.LITTLE_ENDIAN)
                mb.asShortBuffer().get(shorts)
            }
            val durationMin = shorts.size / 16000.0 / 60.0
            audioMs = (shorts.size / 16L).coerceAtLeast(1L)
            SrtJob.finish(String.format(Locale.US, "%s hang", SrtJob.fmtDur(audioMs)))

            // ------------------------------------------------ felismerés (chunkokban)
            val sequential = asr.kind != Engines.K_VOSK
            val chunkMs = when {
                meeting -> chunkMin * 60 * 1000
                asr.kind == Engines.K_VOSK -> 4 * 60 * 1000
                else -> 3 * 60 * 1000
            }
            val overlapMs = 8_000
            histKey = "${asr.key}|$sourceLang|${if (needTranslate) "t" else "n"}"
            val ratio = hist.getFloat("$histKey|p${Perf.level}", 0f).takeIf { it > 0f } ?: hist.getFloat(histKey, 0f)
            SrtJob.histEstMs = if (ratio > 0f) (ratio * audioMs).toLong() else 0L
            val recogStart = System.currentTimeMillis()
            SrtJob.begin(
                SrtJob.ST_RECOG,
                "${asr.label} · ${langName(sourceLang)} beszéd · ${SrtJob.fmtDur(audioMs)} hang"
            )
            if (SrtJob.histEstMs > 0) {
                // kezdeti becslés a korábbi futásokból; utána a mért tempó veszi át (másodpercenként frissül)
                SrtJob.setEta(recogStart + SrtJob.histEstMs, "korábbi futások alapján")
            }

            // beszélők: egy menetben az egész felvételre (ha nem túl hosszú), különben chunkonként
            val perChunkDiar = meeting || (useDiarization && durationMin > 30)
            var globalSegs: List<SpeakerDiarizer.Segment> = emptyList()
            if (useDiarization && !perChunkDiar) {
                SrtJob.progress(0, "Beszélők elkülönítése (egész felvétel)…")
                globalSegs = DiarizerFactory.create(this, preferOnnx = preferOnnx).diarize(shorts, 16000)
                if (cancelFlag.get()) throw InterruptedException("Megszakítva")
            }

            val chunk = ParallelChunker(this, meeting && safeMode)
            chunker = chunk
            partialSupplier = { chunk.merged() }
            var doneAudio = 0L
            val partialFile = File(TranscriptStore.dir(this), "felirat_${stamp}_reszleges.srt")

            val events = object : ParallelChunker.Events {
                override fun onChunkStart(idx: Int, total: Int) {
                    if (SrtJob.chunkState.size != total) SrtJob.initChunks(total)
                    SrtJob.chunkStart(idx)
                }

                override fun onChunkDone(idx: Int, total: Int, cues: List<SrtExporter.Cue>, audioMs: Long) {
                    SrtJob.chunkFinish(idx)
                    SrtJob.preview = (SrtJob.preview + cues.map { previewLine(it) }).takeLast(8)
                    doneAudio += audioMs
                    // részeredmény mentése a belső tárba (megszakításnál / hibánál megmarad)
                    try {
                        partialFile.writeText(SrtExporter.build(chunk.merged(), mode), Charsets.UTF_8)
                    } catch (_: Exception) {
                    }
                }

                override fun onWait(reason: String, cooling: Boolean) {
                    SrtJob.cooling = cooling
                    SrtJob.waitReason = reason
                    if (cooling) SrtJob.message = reason
                    SrtJob.changed()
                }
            }

            val result = chunk.run(
                pcm = shorts, chunkMs = chunkMs, overlapMs = overlapMs,
                sequentialOnly = sequential, events = events
            ) { slice, offsetMs, idx ->
                val sliceMs = slice.size / 16L
                var segs = globalSegs
                var base0 = 0.0
                if (perChunkDiar) {
                    SrtJob.chunkProgress(idx, 0.01, "beszélők elkülönítése…", sliceMs, audioMs)
                    val raw = DiarizerFactory.create(this, preferOnnx = preferOnnx).diarize(slice, 16000)
                    segs = raw.map { it.copy(startMs = it.startMs + offsetMs, endMs = it.endMs + offsetMs) }
                    base0 = 0.1
                }
                SrtJob.chunkProgress(idx, base0, "felismerés indul…", sliceMs, audioMs)
                recognizer.recognize(
                    slice, sourceLang, asr, needTranslate, dir, offsetMs,
                    { t -> speakerAt(prefs, segs, t) }
                ) { pct, msg ->
                    SrtJob.chunkProgress(idx, base0 + (1.0 - base0) * pct / 100.0, msg, sliceMs, audioMs)
                }
            }
            if (cancelFlag.get() && result.stoppedEarly) throw InterruptedException("Megszakítva")
            val cues = result.cues
            SrtJob.cooling = false
            SrtJob.etaAtMs = 0
            SrtJob.finish("${cues.size} blokk")
            SrtJob.chunkDone = SrtJob.chunkTotal
            if (cancelFlag.get()) throw InterruptedException("Megszakítva")

            // a felismerés tényleges sebessége (idő / hanghossz) a következő becsléshez
            try {
                val recMs = SrtJob.steps[SrtJob.ST_RECOG].durMs
                if (recMs > 0 && histKey.isNotEmpty()) {
                    val key = "$histKey|p${Perf.level}"
                    val newRatio = recMs.toFloat() / audioMs
                    val old = hist.getFloat(key, 0f)
                    hist.edit().putFloat(key, if (old > 0f) (old + newRatio) / 2f else newRatio)
                        .putFloat(histKey, if (hist.getFloat(histKey, 0f) > 0f) (hist.getFloat(histKey, 0f) + newRatio) / 2f else newRatio)
                        .apply()
                }
            } catch (_: Exception) {
            }
            SrtJob.preview = cues.takeLast(8).map { previewLine(it) }

            // -------------------------------------------------------------- mentés
            SrtJob.begin(SrtJob.ST_SAVE, "SRT összeállítása…")
            val srt = SrtExporter.build(cues, mode)

            // mentés a belső tárba mindig
            SrtJob.progress(20, "Mentés az alkalmazás tárolójába…")
            val name = "felirat_$stamp.srt"
            File(TranscriptStore.dir(this), name).writeText(srt, Charsets.UTF_8)
            val md = ExportHelper.toMarkdown(cues, "Tárgyalási átirat")
            ExportHelper.write(File(TranscriptStore.dir(this), "jegyzokonyv_$stamp.md"), md)
            val plain = ExportHelper.toPlainMinutes(cues)
            ExportHelper.write(File(TranscriptStore.dir(this), "jegyzokonyv_$stamp.txt"), plain)
            try { partialFile.delete() } catch (_: Exception) {}

            // …és a beállított helyre (a videó mellé / alapértelmezett mappába)
            SrtJob.progress(60, "Mentés a beállított helyre…")
            val where = saveOutputs(kind, uri, base, langTag, srt, cues, false)

            SrtJob.complete(cues.size, srt, name, where)
            item.srt = srt
            item.srtName = srtFileName
            item.saved = where != null
            item.cueCount = cues.size
            item.detail = if (where != null) "Mentve $where"
            else "Nincs elérhető mentési hely – használd a Mentés gombot."
            item.state = SrtJob.Q_DONE
            SrtJob.changed()
        } catch (e: InterruptedException) {
            val where = salvage()
            SrtJob.partialWhere = where
            SrtJob.stopped()
            item.detail = if (where != null) "Megszakítva · a kész rész elmentve $where" else "Megszakítva"
            item.state = SrtJob.Q_STOP
            SrtJob.changed()
        } catch (e: Throwable) {
            val where = salvage()
            SrtJob.partialWhere = where
            val txt = errText(e)
            SrtJob.fail(txt)
            item.detail = txt + if (where != null) "\nA kész rész elmentve $where" else ""
            item.state = SrtJob.Q_ERR
            SrtJob.changed()
        } finally {
            try { fr?.close() } catch (_: Exception) {}
            try { pcmFile?.delete() } catch (_: Exception) {}
            chunker = null
            recRef = null
        }
    }

    private fun previewLine(c: SrtExporter.Cue): String {
        val t = SrtExporter.formatTime(c.startMs).take(8)
        val txt = c.source.ifBlank { c.target }
        val tr = if (c.target.isNotBlank() && c.target != c.source) " → ${c.target}" else ""
        val spk = if (c.speaker.isNotBlank()) "${c.speaker}: " else ""
        return "[$t] $spk$txt$tr"
    }

    private fun srtNameFor(kind: String, base: String, langTag: String, partial: Boolean): String {
        val suffix = if (partial) ".reszleges" else ""
        return if (kind == KIND_TRANSCRIPT) "${base}_atirat$suffix.srt"
        else "$base${if (langTag.isNotBlank()) ".$langTag" else ""}$suffix.srt"
    }

    /**
     * Egy fájl mentése a kiválasztott módon:
     *  - "videó mellé" (alapértelmezett): oda, ahonnan a videót betöltöttük; ha ez nem megy, az alapértelmezett mappába,
     *  - "alapértelmezett mappa": csak oda.
     * Ad: ember-olvasható hely, vagy null.
     */
    private fun saveFile(video: Uri, name: String, mime: String, bytes: ByteArray): String? {
        val tree = OutputPrefs.defaultTree(this)
        val beside = OutputPrefs.saveMode(this) == OutputPrefs.SAVE_BESIDE
        if (beside) {
            OutputWriter.saveBesideVideo(this, video, name, mime, bytes)?.let { return it }
        }
        if (tree != null) {
            val label = OutputWriter.saveInTree(this, tree, name, mime, bytes)
            if (label != null) {
                return (if (beside) "az alapértelmezett mappába (a videó mellé nem sikerült): " else "az alapértelmezett mappába: ") +
                    "$label/$name"
            }
        }
        return null
    }

    /**
     * Kimenet mentése a beállított helyre.
     *  - átirat: SRT + TXT,
     *  - felirat: SRT.
     * Null = nincs beállított / elérhető hely; ilyenkor a képernyő mentés-ablakot kínál.
     */
    private fun saveOutputs(
        kind: String, video: Uri, base: String, langTag: String, srt: String,
        cues: List<SrtExporter.Cue>, partial: Boolean
    ): String? {
        val srtBytes = srt.toByteArray(Charsets.UTF_8)
        val name = srtNameFor(kind, base, langTag, partial)
        val w = saveFile(video, name, OutputWriter.MIME_SRT, srtBytes)
        if (w != null && kind == KIND_TRANSCRIPT) {
            val suffix = if (partial) ".reszleges" else ""
            val txt = ExportHelper.toPlainMinutes(cues, "Átirat – $base").toByteArray(Charsets.UTF_8)
            saveFile(video, "${base}_atirat$suffix.txt", "text/plain", txt)
        }
        return w
    }

    private fun speakerAt(prefs: Prefs, segments: List<SpeakerDiarizer.Segment>, timeMs: Long): String {
        if (segments.isEmpty()) return ""
        val seg = segments.firstOrNull { timeMs >= it.startMs && timeMs < it.endMs }
            ?: segments.minByOrNull { kotlin.math.abs(it.startMs - timeMs) }
        if (seg == null) return ""
        val map = SpeakerNames.fromPrefs(prefs)
        return SpeakerNames.label(seg.speakerId, map)
    }
}
