package hu.angolmagyar.fordito
import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList
object SrtJob {
    const val ST_PREP = 0
    const val ST_EXTRACT = 1
    const val ST_PCM = 2
    const val ST_RECOG = 3
    const val ST_SAVE = 4
    const val PENDING = 0
    const val ACTIVE = 1
    const val DONE = 2
    const val ERROR = 3
    const val STOPPED = 4
    val WEIGHTS = intArrayOf(2, 8, 2, 86, 2)
    val COLORS = intArrayOf(
        0xFF8B9CB3.toInt(),
        0xFFE28D5A.toInt(),
        0xFFC6A210.toInt(),
        0xFF2F9ADA.toInt(),
        0xFFAB47BC.toInt()
    )
    class Step {
        @Volatile var state = PENDING
        @Volatile var durMs = 0L
        @Volatile var comment = ""
        @Volatile var pct = -1
    }
    val steps = Array(5) { Step() }
    const val Q_WAIT = 0
    const val Q_RUN = 1
    const val Q_DONE = 2
    const val Q_ERR = 3
    const val Q_STOP = 4
    class QItem(val uri: Uri, val name: String) {
        @Volatile var state = Q_WAIT
        @Volatile var detail = ""
        @Volatile var srt: String? = null
        @Volatile var srtName = ""
        @Volatile var saved = false
        @Volatile var cueCount = 0
        @Volatile var cues: List<SrtExporter.Cue>? = null
        @Volatile var dropped: List<SrtExporter.Cue> = emptyList()
        @Volatile var mode: SrtExporter.Mode = SrtExporter.Mode.TARGET_ONLY
        @Volatile var dir: String = Prefs.DIR_EN_HU
        @Volatile var localFile: String = ""
    }
    val queue = CopyOnWriteArrayList<QItem>()
    @Volatile var engOnlineName = ""
    @Volatile var engOnline = 0
    @Volatile var engOffline = 0
    @Volatile var names: List<String> = emptyList()
    @Synchronized fun addEngine(onlineName: String, online: Int, offline: Int) {
        if (online > 0) { engOnlineName = onlineName; engOnline += online }
        engOffline += offline
    }
    fun engineText(): String {
        val parts = ArrayList<String>()
        if (engOnline > 0) parts.add("$engOnlineName: $engOnline sor")
        if (engOffline > 0) parts.add("offline (ML Kit): $engOffline sor")
        return if (parts.isEmpty()) "" else "Fordítómotor – " + parts.joinToString(" · ")
    }
    @Volatile var batchRunning = false
    @Volatile var batchStartMs = 0L
    @Volatile var current: QItem? = null
    @Synchronized
    fun addItems(items: List<QItem>) {
        for (it in items) {
            if (queue.any { q -> q.state == Q_WAIT && q.uri == it.uri }) continue
            queue.add(it)
        }
        changed()
    }
    @Synchronized
    fun removeWaiting(item: QItem) {
        if (item.state == Q_WAIT) queue.remove(item)
        changed()
    }
    @Synchronized
    fun clearFinished() {
        if (batchRunning) return
        queue.removeAll { it.state != Q_WAIT && it.state != Q_RUN }
        changed()
    }
    @Synchronized
    fun takeNext(): QItem? {
        val n = queue.firstOrNull { it.state == Q_WAIT } ?: return null
        n.state = Q_RUN
        n.detail = ""
        current = n
        return n
    }
    @Synchronized
    fun stopWaiting() {
        for (q in queue) if (q.state == Q_WAIT) { q.state = Q_STOP; q.detail = "Megszakítva" }
        changed()
    }
    @Synchronized
    fun startBatch() {
        batchRunning = true
        batchStartMs = System.currentTimeMillis()
        changed()
    }
    @Synchronized
    fun endBatch() {
        batchRunning = false
        current = null
        changed()
    }
    val fileTotal: Int get() = queue.size
    val fileDone: Int get() = queue.count { it.state == Q_DONE || it.state == Q_ERR || it.state == Q_STOP }
    val fileNo: Int get() = (current?.let { queue.indexOf(it) } ?: -1) + 1
    @Volatile var running = false
    @Volatile var finished = false
    @Volatile var cancelled = false
    @Volatile var error: String? = null
    @Volatile var withTranslation = false
    @Volatile var curStep = -1
    @Volatile var startMs = 0L
    @Volatile var endMs = 0L
    @Volatile var stepStartMs = 0L
    @Volatile var overall = 0
    @Volatile var etaAtMs = 0L
    val etaMs: Long get() = if (etaAtMs > 0) (etaAtMs - System.currentTimeMillis()).coerceAtLeast(0) else 0L
    @Volatile var etaBasis = ""
    @Volatile var chunkState = IntArray(0)
    class ChunkInfo {
        @Volatile var spanMs = 0L
        @Volatile var speechMs = 0L
        @Volatile var startMs = 0L
        @Volatile var tookMs = 0L
        @Volatile var cues = -1
        @Volatile var frac = 0.0
    }
    @Volatile var chunkInfo: Array<ChunkInfo> = emptyArray()
    val phases = java.util.concurrent.ConcurrentHashMap<Int, String>()
    private var chunkFrac = DoubleArray(0)
    private var chunkStartAt = LongArray(0)
    private var chunkLenMs = LongArray(0)
    @Volatile var waitReason = ""
    @Volatile var engineLabel = ""
    @Volatile var speechInfo = ""
    @Volatile var langLabel = ""
    @Volatile var message = ""
    @Volatile var translateNote = ""
    @Volatile var chunkCur = 0
    @Volatile var chunkTotal = 0
    @Volatile var cooling = false
    @Volatile var partial = false
    @Volatile var chunkDone = 0
    @Volatile var curChunkNo = 0
    @Volatile var curChunkPct = 0
    @Volatile var curChunkStartMs = 0L
    @Volatile var preview: List<String> = emptyList()
    @Volatile var previewCues: List<SrtExporter.Cue> = emptyList()
    @Volatile var liveHeard = ""
    @Volatile var liveHeardStartMs = 0L
    @Volatile var liveHeardEndMs = 0L
    @Volatile var liveSource = ""
    @Volatile var liveTranslated = ""
    fun setLiveHeard(text: String, startMs: Long, endMs: Long) {
        liveHeardStartMs = startMs; liveHeardEndMs = endMs; liveHeard = text
    }
    fun setLiveTranslated(source: String, translated: String) {
        liveSource = source; liveTranslated = translated
    }
    @Volatile var waveEnv: FloatArray = FloatArray(0)
    @Volatile var waveTotalMs = 0L
    @Volatile var waveRanges: LongArray = LongArray(0)
    @Volatile var waveSpec: Spectrogram.Grid? = null
    @Synchronized fun chunkFracCopy(): DoubleArray = chunkFrac.copyOf()
    @Volatile var savedWhere: String? = null
    @Volatile var partialWhere: String? = null
    @Volatile var histEstMs = 0L
    @Volatile var histMsPerWindow = 0L
    @Volatile var lastProgressMs = 0L
    @Volatile var maxGapMs = 0L
    fun stallLimitMs(): Long = maxOf(SrtJobService.STALL_WARN_MS, 2 * maxGapMs)
    @Volatile var skippedParts = 0
    @Volatile var resultSrt: String? = null
    @Volatile var resultName = ""
    @Volatile var resultCount = 0
    @Volatile var saved = false
    private val main = Handler(Looper.getMainLooper())
    private val listeners = CopyOnWriteArrayList<() -> Unit>()
    fun addListener(l: () -> Unit) {
        if (!listeners.contains(l)) listeners.add(l)
    }
    fun removeListener(l: () -> Unit) {
        listeners.remove(l)
    }
    @Volatile private var lastChangedMs = 0L
    fun changed(force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!force && now - lastChangedMs < 1000) return
        lastChangedMs = now
        main.post { for (l in listeners) try { l() } catch (e: Exception) { DiagLog.swallow("SrtJob", e) } }
    }
    private val samples = ArrayList<Pair<Long, Double>>()
    private var pctExact = -1.0
    private var lastEtaMs = 0L
    fun label(idx: Int): String = when (idx) {
        ST_PREP -> "Előkészítés"
        ST_EXTRACT -> "Hang kinyerése a fájlból"
        ST_PCM -> "PCM betöltése"
        ST_RECOG -> if (withTranslation) "Felismerés + fordítás" else "Felismerés"
        ST_SAVE -> "SRT mentése"
        else -> ""
    }
    @Synchronized
    fun reset(withTr: Boolean) {
        engOnlineName = ""; engOnline = 0; engOffline = 0; names = emptyList()
        for (s in steps) { s.state = PENDING; s.durMs = 0; s.comment = ""; s.pct = -1 }
        withTranslation = withTr
        running = true
        finished = false
        cancelled = false
        error = null
        curStep = -1
        startMs = System.currentTimeMillis()
        endMs = 0
        stepStartMs = startMs
        lastProgressMs = startMs
        maxGapMs = 0L
        overall = 0
        etaAtMs = 0
        etaBasis = ""
        chunkState = IntArray(0)
        chunkFrac = DoubleArray(0)
        chunkStartAt = LongArray(0)
        chunkLenMs = LongArray(0)
        chunkInfo = emptyArray()
        phases.clear()
        waitReason = ""
        engineLabel = ""
        speechInfo = ""
        langLabel = ""
        message = "Indítás…"
        translateNote = ""
        chunkCur = 0
        chunkTotal = 0
        cooling = false
        partial = false
        chunkDone = 0
        curChunkNo = 0
        curChunkPct = 0
        curChunkStartMs = 0
        preview = emptyList()
        previewCues = emptyList()
        liveHeard = ""
        liveHeardStartMs = 0L
        liveHeardEndMs = 0L
        liveSource = ""
        liveTranslated = ""
        waveEnv = FloatArray(0)
        waveTotalMs = 0L
        waveRanges = LongArray(0)
        waveSpec = null
        savedWhere = null
        partialWhere = null
        histEstMs = 0
        skippedParts = 0
        resultSrt = null
        resultName = ""
        resultCount = 0
        saved = false
        changed()
    }
    private fun cumBefore(idx: Int): Int {
        var c = 0
        for (i in 0 until idx) c += WEIGHTS[i]
        return c
    }
    @Synchronized
    fun begin(idx: Int, note: String = "") {
        val now = System.currentTimeMillis()
        steps.getOrNull(curStep)?.let {
            if (it.state == ACTIVE) { it.state = DONE; it.durMs = now - stepStartMs; it.pct = 100 }
        }
        curStep = idx
        stepStartMs = now
        lastProgressMs = now
        if (idx == ST_RECOG) { samples.clear(); lastEtaMs = 0L }
        val st = steps[idx]
        st.state = ACTIVE
        st.pct = -1
        st.comment = note
        overall = cumBefore(idx).coerceAtMost(99)
        if (note.isNotEmpty()) message = note
        changed()
    }
    @Synchronized
    fun finish(extra: String = "") {
        val st = steps.getOrNull(curStep) ?: return
        st.state = DONE
        st.durMs = System.currentTimeMillis() - stepStartMs
        st.pct = 100
        st.comment = extra
        lastProgressMs = System.currentTimeMillis()
        changed()
    }
    @Synchronized
    fun progress(pct: Int, msg: String) {
        val idx = curStep
        val st = steps.getOrNull(idx) ?: return
        if (st.state != ACTIVE) return
        val p = pct.coerceIn(0, 100)
        val now = System.currentTimeMillis()
        val gap = now - lastProgressMs
        if (gap in 1..3_600_000L && gap > maxGapMs) maxGapMs = gap
        lastProgressMs = now
        st.pct = p
        st.comment = msg
        message = msg
        overall = (cumBefore(idx) + WEIGHTS[idx] * p / 100).coerceIn(0, 99)
        if (idx == ST_RECOG && now - lastEtaMs >= 1000) {
            lastEtaMs = now
            val pe = if (pctExact >= 0.0) pctExact else p.toDouble()
            samples.add(now to pe)
            while (samples.size > 2 && now - samples[0].first > 120_000) samples.removeAt(0)
            val first = samples.first()
            val dt = now - first.first
            val dp = pe - first.second
            val el = now - stepStartMs
            val restLin = if (pe >= 1.0 && el >= 10_000) (el * (100.0 - pe) / pe).toLong() else -1L
            val restWin = if (dt >= 20_000 && dp >= 0.3) ((100.0 - pe) * dt / dp).toLong() else -1L
            val rest = when {
                restLin > 0 && restWin > 0 -> {
                    val w = restWin.coerceIn((restLin / 2.5).toLong(), (restLin * 2.5).toLong())
                    (w + restLin) / 2
                }
                restLin > 0 -> restLin
                restWin > 0 -> restWin
                else -> -1L
            }
            val byWin = windowEtaMs(now)
            if (byWin > 0) {
                etaAtMs = now + byWin
                etaBasis = "a 30 mp-es Whisper-ablakok mért ideje alapján"
            } else if (rest > 0) {
                etaAtMs = now + rest
                etaBasis = "az eddigi és a friss tempó alapján"
            } else if (histEstMs > 0 && etaAtMs == 0L) {
                etaAtMs = stepStartMs + histEstMs
                etaBasis = "korábbi futások alapján"
            }
        }
        changed(false)
    }
    private fun windowEtaMs(now: Long): Long {
        val n = chunkState.size
        if (n == 0 || chunkLenMs.size != n || chunkFrac.size != n || chunkInfo.size != n) return -1L
        val st = chunkState.copyOf()
        val frac = chunkFrac.copyOf()
        val took = LongArray(n) { chunkInfo[it].tookMs }
        val el = LongArray(n) { if (st[it] == 1) now - chunkStartAt.getOrElse(it) { now } else 0L }
        val total = EtaModel.remainingMs(chunkLenMs.copyOf(), st, frac, took, el, histMsPerWindow)
        if (total < 0) return -1L
        val parallel = st.count { it == 1 }.coerceAtLeast(1)
        return (total - EtaModel.TAIL_MS) / parallel + EtaModel.TAIL_MS
    }
    @Synchronized
    fun fail(msg: String) {
        val now = System.currentTimeMillis()
        steps.getOrNull(curStep)?.let {
            if (it.state == ACTIVE) { it.state = ERROR; it.durMs = now - stepStartMs; it.comment = msg }
        }
        error = msg
        message = msg
        running = false
        endMs = now
        changed()
    }
    @Synchronized
    fun stopped() {
        val now = System.currentTimeMillis()
        steps.getOrNull(curStep)?.let {
            if (it.state == ACTIVE) { it.state = STOPPED; it.durMs = now - stepStartMs }
        }
        cancelled = true
        message = "Megszakítva"
        running = false
        endMs = now
        changed()
    }
    @Synchronized
    fun complete(count: Int, srt: String, name: String, where: String?) {
        savedWhere = where
        saved = where != null
        finish("$count feliratblokk")
        overall = 100
        etaAtMs = 0
        resultSrt = srt
        resultName = name
        resultCount = count
        message = "Kész – $count feliratblokk"
        finished = true
        running = false
        endMs = System.currentTimeMillis()
        changed()
    }
    @Synchronized
    fun setEta(atMs: Long, basis: String) {
        etaAtMs = atMs
        etaBasis = basis
        changed()
    }
    @Synchronized
    fun setChunkLens(lens: LongArray) {
        chunkLenMs = lens
    }
    @Synchronized
    fun initChunks(n: Int) {
        chunkState = IntArray(n)
        chunkFrac = DoubleArray(n)
        chunkStartAt = LongArray(n)
        chunkInfo = Array(n) { ChunkInfo() }
        chunkTotal = n
        chunkDone = 0
        changed()
    }
    @Synchronized
    fun chunkSize(i: Int, speechMs: Long, spanMs: Long) {
        val ci = chunkInfo.getOrNull(i) ?: return
        ci.speechMs = speechMs
        ci.spanMs = spanMs
        changed(false)
    }
    private fun refreshCurrent() {
        val act = chunkState.indices.firstOrNull { chunkState[it] == 1 }
        if (act != null) {
            curChunkNo = act + 1
            curChunkPct = (chunkFrac[act] * 100).toInt().coerceIn(0, 99)
            curChunkStartMs = chunkStartAt.getOrElse(act) { 0L }
        }
    }
    @Synchronized
    fun chunkStart(i: Int) {
        if (i in chunkState.indices) {
            chunkState[i] = 1
            chunkFrac[i] = 0.0
            chunkStartAt[i] = System.currentTimeMillis()
            chunkInfo.getOrNull(i)?.let { it.startMs = chunkStartAt[i]; it.tookMs = 0L; it.cues = -1; it.frac = 0.0 }
        }
        chunkCur = i + 1
        lastProgressMs = System.currentTimeMillis()
        refreshCurrent()
        DiagLog.log("DARAB ${i + 1}/$chunkTotal indul")
        changed()
    }
    @Synchronized
    fun chunkProgress(i: Int, frac: Double, what: String, chunkAudioMs: Long, totalAudioMs: Long) {
        if (i !in chunkFrac.indices) return
        chunkFrac[i] = frac.coerceIn(0.0, 1.0)
        chunkInfo.getOrNull(i)?.frac = chunkFrac[i]
        phases[i] = what
        val lens = chunkLenMs
        val pctD: Double
        if (lens.size == chunkFrac.size && lens.isNotEmpty() && lens.sum() > 0L) {
            var doneMs = 0.0
            var sumMs = 0.0
            for (k in chunkFrac.indices) { doneMs += chunkFrac[k] * lens[k]; sumMs += lens[k] }
            pctD = doneMs * 100.0 / sumMs
        } else {
            var doneMs = 0.0
            val per = if (chunkFrac.isNotEmpty()) totalAudioMs.toDouble() / chunkFrac.size else 1.0
            for (f in chunkFrac) doneMs += f * per
            pctD = if (totalAudioMs > 0) doneMs * 100.0 / totalAudioMs else 0.0
        }
        refreshCurrent()
        val msg = phases.entries.sortedBy { it.key }.take(4)
            .joinToString("  ·  ") { "#${it.key + 1} ${it.value}" }
        pctExact = pctD.coerceIn(0.0, 99.0)
        try {
            progress(pctD.toInt().coerceIn(0, 99), msg.ifBlank { "Feldolgozás…" })
        } finally {
            pctExact = -1.0
        }
    }
    @Synchronized
    fun chunkFinish(i: Int, cues: Int = -1) {
        if (i in chunkState.indices) {
            val tookMs = System.currentTimeMillis() - chunkStartAt.getOrElse(i) { 0L }
            DiagLog.log("DARAB ${i + 1}/$chunkTotal kész · ${tookMs / 1000}s")
            chunkState[i] = 2; chunkFrac[i] = 1.0
            chunkInfo.getOrNull(i)?.let { it.tookMs = tookMs.coerceAtLeast(1L); it.cues = cues; it.frac = 1.0 }
        }
        phases.remove(i)
        chunkDone = chunkState.count { it == 2 }
        lastProgressMs = System.currentTimeMillis()
        if (chunkState.none { it == 1 }) curChunkPct = 100 else refreshCurrent()
        changed()
    }
    fun fmtDur(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }
    fun fmtClock(atMs: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = atMs
        return String.format(Locale.US, "%02d:%02d", cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }
}
