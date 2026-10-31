package hu.angolmagyar.fordito

import android.app.usage.UsageStatsManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnosztikai napló: mit csinált a feldolgozás zárolt képernyő alatt, mikor lassult / állt meg,
 * milyen az energiaállapot. A napló a lemezre is íródik, a hibajelentés gombbal megosztható.
 */
object DiagLog {
    private const val MAX_MEM = 500
    private const val MAX_FILE = 300_000L
    private val mem = ArrayDeque<String>()
    private var file: File? = null
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Volatile var screenOn = true
        private set
    private var offAt = 0L
    private var offOverall = 0
    private var offChunks = 0
    private var offRecogPct = 0

    fun init(ctx: Context) {
        if (file != null) return
        val f = File(ctx.filesDir, "diag.log")
        try {
            if (f.exists() && f.length() > MAX_FILE) {
                val keep = f.readLines(Charsets.UTF_8).takeLast(800)
                f.writeText(keep.joinToString("\n") + "\n", Charsets.UTF_8)
            }
        } catch (_: Exception) {
        }
        file = f
        try { screenOn = (ctx.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive } catch (_: Exception) {}
    }

    private val lastSwallow = HashMap<String, Long>()

    /** Lenyelt kivétel naplózása (ugyanaz 5 mp-en belül csak egyszer, hogy ciklusban se árassza el a naplót). */
    @Synchronized
    fun swallow(tag: String, e: Throwable) {
        val k = tag + e.javaClass.simpleName + e.message
        val now = System.currentTimeMillis()
        if (now - (lastSwallow[k] ?: 0L) < 5_000L) return
        lastSwallow[k] = now
        if (lastSwallow.size > 200) lastSwallow.clear()
        log("⚠ $tag: ${e.javaClass.simpleName}: ${e.message}")
    }

    @Synchronized
    fun log(msg: String) {
        val line = "${fmt.format(Date())} $msg"
        mem.addLast(line)
        while (mem.size > MAX_MEM) mem.removeFirst()
        try { file?.appendText(line + "\n", Charsets.UTF_8) } catch (_: Exception) {}
    }

    /** A képernyő be-/kikapcsolása: a kikapcsolt idő alatti haladást naplózzuk. */
    fun screenChanged(on: Boolean) {
        val now = System.currentTimeMillis()
        if (!on && screenOn) {
            offAt = now
            offOverall = SrtJob.overall
            offChunks = SrtJob.chunkDone
            offRecogPct = SrtJob.steps[SrtJob.ST_RECOG].pct
            log("KÉPERNYŐ KI · folyamat ${SrtJob.overall}% · kész darab ${SrtJob.chunkDone}/${SrtJob.chunkTotal}")
        } else if (on && !screenOn && offAt > 0) {
            val secs = (now - offAt) / 1000
            val dOverall = SrtJob.overall - offOverall
            val dChunks = SrtJob.chunkDone - offChunks
            val verdict = when {
                !SrtJob.running -> "nem futott feldolgozás"
                secs >= 60 && dOverall <= 0 && dChunks <= 0 -> "⚠ NEM HALADT zárolt képernyő alatt (a rendszer leállíthatta / lelassíthatta)"
                else -> "haladt"
            }
            log("KÉPERNYŐ BE · ki volt ${secs}s · folyamat +$dOverall% · +$dChunks darab · $verdict")
        }
        screenOn = on
    }

    fun battery(ctx: Context): String = try {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val lvl = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val plug = (i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        "akku ${if (lvl >= 0) lvl * 100 / scale.coerceAtLeast(1) else -1}%${if (plug) " (tölt)" else ""}"
    } catch (_: Exception) { "akku ?" }

    /** Egysoros energiaállapot-összefoglaló (a rendszer szigorúsága szerint). */
    fun powerState(ctx: Context): String {
        val parts = ArrayList<String>()
        try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            parts.add("képernyő=${if (pm.isInteractive) "be" else "ki"}")
            parts.add("doze=${pm.isDeviceIdleMode}")
            parts.add("takarékos=${pm.isPowerSaveMode}")
            if (Build.VERSION.SDK_INT >= 29) parts.add("hő=${pm.currentThermalStatus}")
            parts.add("optim.kivétel=${pm.isIgnoringBatteryOptimizations(ctx.packageName)}")
        } catch (_: Exception) {
        }
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val um = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                // 10 = ACTIVE, 20 = WORKING_SET, 30 = FREQUENT, 40 = RARE, 45 = RESTRICTED
                parts.add("standby-vödör=${um.appStandbyBucket}")
            } catch (_: Exception) {
            }
        }
        parts.add(battery(ctx))
        return parts.joinToString(" · ")
    }

    /** Időszakos állapotsor a naplóba (a szolgáltatás hívja ~30 mp-enként). */
    fun tick(ctx: Context) {
        val now = System.currentTimeMillis()
        val idle = (now - SrtJob.lastProgressMs) / 1000
        log(
            "TICK lépés=${SrtJob.curStep} folyamat=${SrtJob.overall}% darab=${SrtJob.curChunkNo}/${SrtJob.chunkTotal} " +
                "(${SrtJob.curChunkPct}%) kész=${SrtJob.chunkDone} nincs_haladás=${idle}s · ${powerState(ctx)}"
        )
    }

    @Synchronized
    private fun tail(n: Int): List<String> = mem.toList().takeLast(n)

    fun report(ctx: Context): String {
        val sb = StringBuilder()
        sb.append("Angol–Magyar élő fordító – hibajelentés\n")
        try {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            sb.append("Verzió: ").append(pi.versionName).append('\n')
        } catch (_: Exception) {
        }
        sb.append("Telefon: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
        sb.append("Energia: ").append(powerState(ctx)).append('\n')
        sb.append("Teljesítmény: ").append(Perf.describe()).append('\n')
        val s = SrtJob
        sb.append("Állapot: fut=${s.running} lépés=${s.curStep} folyamat=${s.overall}% darab=${s.chunkDone}/${s.chunkTotal} ")
        sb.append("üzenet=\"${s.message}\" hiba=${s.error ?: "-"}\n")
        sb.append("Felismerő: ${s.engineLabel} · nyelv: ${s.langLabel}\n")
        sb.append("\n--- napló (utolsó sorok) ---\n")
        sb.append(tail(250).joinToString("\n"))
        return sb.toString()
    }

    /** Megosztás (levél, üzenet…) – a jelentés szövegként megy. */
    fun share(ctx: Context) {
        val text = report(ctx)
        try {
            val i = Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Hibajelentés – Angol–Magyar fordító")
                .putExtra(Intent.EXTRA_TEXT, text.takeLast(90_000))
            ctx.startActivity(Intent.createChooser(i, "Hibajelentés küldése").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    fun copy(ctx: Context) {
        try {
            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Hibajelentés", report(ctx).takeLast(90_000)))
        } catch (_: Exception) {
        }
    }
}
