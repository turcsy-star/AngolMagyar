package hu.angolmagyar.fordito

import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Az SRT-készítés közös állapota. A munkát a SrtJobService végzi előtérben (az app
 * váltása / képernyőzár nem állítja meg), a képernyő (FileSubtitleActivity) csak megjeleníti.
 * Az Activity bármikor újra megnyitható: az állapot itt marad, amíg a folyamat él.
 *
 * Több fájl egymás után: a [queue] a közös fájllista; a szolgáltatás sorban dolgozza fel őket.
 */
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

    /**
     * Egy lépés súlya a teljes folyamatban (%). A hang kinyerése a mért adatok szerint a teljes idő
     * kb. 8–10%-a, a felismerés a nagyja – ez adja a valósághoz közeli "Teljes" százalékot.
     */
    val WEIGHTS = intArrayOf(2, 8, 2, 86, 2)

    /** Lépésenkénti színkód (a Whisper-dashboard mintájára). */
    val COLORS = intArrayOf(
        0xFF8B9CB3.toInt(), // Előkészítés – szürkéskék
        0xFFE28D5A.toInt(), // Hang kinyerése – narancs
        0xFFC6A210.toInt(), // PCM betöltése – borostyán
        0xFF2F9ADA.toInt(), // Felismerés – kék
        0xFFAB47BC.toInt()  // SRT mentése – lila
    )

    class Step {
        @Volatile var state = PENDING
        @Volatile var durMs = 0L
        @Volatile var comment = ""
        /** -1 = nem ismert (pörgő jelző) */
        @Volatile var pct = -1
    }

    val steps = Array(5) { Step() }

    // ------------------------------------------------------------------ fájllista

    const val Q_WAIT = 0
    const val Q_RUN = 1
    const val Q_DONE = 2
    const val Q_ERR = 3
    const val Q_STOP = 4

    /** A sor egy eleme: egy hang-/videófájl és az eredménye. */
    class QItem(val uri: Uri, val name: String) {
        @Volatile var state = Q_WAIT
        @Volatile var detail = ""
        @Volatile var srt: String? = null
        @Volatile var srtName = ""
        @Volatile var saved = false
        @Volatile var cueCount = 0
        /** A kész blokkok (angol eredeti + fordítás) az ellenőrző nézethez; null = nincs (régi / részleges). */
        @Volatile var cues: List<SrtExporter.Cue>? = null
        /** A hallucináció-szűrő által kivett blokkok (visszaállíthatók). */
        @Volatile var dropped: List<SrtExporter.Cue> = emptyList()
        @Volatile var mode: SrtExporter.Mode = SrtExporter.Mode.TARGET_ONLY
        @Volatile var dir: String = Prefs.DIR_EN_HU
        /** A belső tárba mentett SRT fájlneve (előzményekhez, lejátszóban megnyitáshoz). */
        @Volatile var localFile: String = ""
    }

    val queue = CopyOnWriteArrayList<QItem>()

    // ---- melyik fordítómotor dolgozott (összesítés a futás alatt)
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

    /** A teljes sor (több fájl) feldolgozása folyamatban van. */
    @Volatile var batchRunning = false
    @Volatile var batchStartMs = 0L
    @Volatile var current: QItem? = null

    @Synchronized
    fun addItems(items: List<QItem>) {
        for (it in items) {
            // ugyanaz a fájl kétszer a várakozók között nem kell
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

    /** A kész / hibás / megszakított elemek törlése (új feldolgozás indításához). */
    @Synchronized
    fun clearFinished() {
        if (batchRunning) return
        queue.removeAll { it.state != Q_WAIT && it.state != Q_RUN }
        changed()
    }

    /** A következő várakozó fájl (futóra állítja), vagy null, ha nincs több. */
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
    /** A futó fájl sorszáma (1-től). */
    val fileNo: Int get() = (current?.let { queue.indexOf(it) } ?: -1) + 1

    // --------------------------------------------------------------- egy fájl állapota

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
    /** Várható befejezés (abszolút idő, ms); 0 = még nincs becslés. */
    @Volatile var etaAtMs = 0L
    val etaMs: Long get() = if (etaAtMs > 0) (etaAtMs - System.currentTimeMillis()).coerceAtLeast(0) else 0L
    /** Hogyan készült a becslés. */
    @Volatile var etaBasis = ""
    /** Chunkonként: 0 = vár, 1 = dolgozik, 2 = kész. */
    @Volatile var chunkState = IntArray(0)
    /** Egy darab adatai a képernyő darab-listájához (a haladás-állapotot a chunkState / chunkFrac őrzi). */
    class ChunkInfo {
        /** A darab eredeti hossza a csendekkel együtt (ms). */
        @Volatile var spanMs = 0L
        /** Csendek nélküli hossz (ms) – amit a felismerő tényleg feldolgoz. */
        @Volatile var speechMs = 0L
        @Volatile var startMs = 0L
        /** Ennyi ideig tartott a darab (ms); 0 = még nem kész. */
        @Volatile var tookMs = 0L
        /** Feliratblokkok száma a darabban; -1 = még nem ismert. */
        @Volatile var cues = -1
        /** 0..1 haladás (csak kijelzéshez). */
        @Volatile var frac = 0.0
    }
    @Volatile var chunkInfo: Array<ChunkInfo> = emptyArray()
    /** Az aktív chunkok pillanatnyi tevékenysége (chunk sorszám → szöveg). */
    val phases = java.util.concurrent.ConcurrentHashMap<Int, String>()
    /** Chunkonkénti haladás 0..1 (a teljes felismerési %-hoz). */
    private var chunkFrac = DoubleArray(0)
    private var chunkStartAt = LongArray(0)
    /** Chunkonkénti hanghossz (ms): a felismerési % ebből súlyozódik (intelligens darabolásnál a darabok nem egyformák). */
    private var chunkLenMs = LongArray(0)
    @Volatile var waitReason = ""
    @Volatile var engineLabel = ""
    /** pl. "beszéd 6:27 / 30:37 (21%) · 12 darab" – a csendkiszűrés eredménye */
    @Volatile var speechInfo = ""
    /** Felismerés nyelve (pl. "angol") – a képernyőn is látszik, hogy nem tévedhet a program. */
    @Volatile var langLabel = ""
    @Volatile var message = ""
    /** A fordításról szóló tájékoztató / figyelmeztetés (pl. online hiba, tartalék használata). */
    @Volatile var translateNote = ""
    @Volatile var chunkCur = 0
    @Volatile var chunkTotal = 0
    @Volatile var cooling = false
    @Volatile var partial = false
    /** Hány chunk készült el teljesen (a chunk-térképhez). */
    @Volatile var chunkDone = 0
    /** Az "aktuális darab": a legkisebb sorszámú dolgozó chunk haladása. */
    @Volatile var curChunkNo = 0
    @Volatile var curChunkPct = 0
    @Volatile var curChunkStartMs = 0L
    /** Az utolsó felismert sorok élő előnézete. */
    @Volatile var preview: List<String> = emptyList()
    /** Ugyanez szerkezetesen (átirat-folyam). */
    @Volatile var previewCues: List<SrtExporter.Cue> = emptyList()
    /** Élő szöveg a képernyőhöz: az éppen hallott mondat az időpontjával (ms), és a legutóbb lefordított mondat. */
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
    /** Hullámforma-nézet: a hang burkolója (0..1), a teljes hossz és a darabok időtartományai (kezdet, vég – ms). */
    @Volatile var waveEnv: FloatArray = FloatArray(0)
    @Volatile var waveTotalMs = 0L
    @Volatile var waveRanges: LongArray = LongArray(0)
    /** Darabonkénti haladás (másolat) a hullámformához. */
    @Synchronized fun chunkFracCopy(): DoubleArray = chunkFrac.copyOf()
    /** Hová mentette a szolgáltatás az eredményt (null = még nincs mentve, a képernyő kérdez). */
    @Volatile var savedWhere: String? = null
    @Volatile var partialWhere: String? = null
    /** Korábbi futások alapján becsült felismerési idő (ms), 0 = nincs adat. */
    @Volatile var histEstMs = 0L
    /** Korábbi futásokból: egy 30 mp-es Whisper-ablak átlagos ideje (ms); 0 = nincs adat. */
    @Volatile var histMsPerWindow = 0L
    /** Az utolsó észlelt haladás ideje (a "megállt a háttérben?" jelzéshez). */
    @Volatile var lastProgressMs = 0L
    /** Hány darabot (részt) kellett kihagyni, mert a felismerés elakadt (elakadás-őr). */
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

    /** Értesíti a megjelenítőket. A folyamatos haladás (force=false) legfeljebb másodpercenként. */
    fun changed(force: Boolean = true) {
        val now = System.currentTimeMillis()
        if (!force && now - lastChangedMs < 1000) return
        lastChangedMs = now
        main.post { for (l in listeners) try { l() } catch (e: Exception) { DiagLog.swallow("SrtJob", e) } }
    }

    // a felismerés haladásának mintái (idő, %) a folyamatos ETA-hoz; a legutóbbi ~60 mp tempója számít
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

    /** Új lépés; az előző (ha még aktív) késznek számít. */
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

    /** Az aktív lépés haladása (0–100); a teljes % a lépések súlyából számolódik. */
    @Synchronized
    fun progress(pct: Int, msg: String) {
        val idx = curStep
        val st = steps.getOrNull(idx) ?: return
        if (st.state != ACTIVE) return
        val p = pct.coerceIn(0, 100)
        val now = System.currentTimeMillis()
        lastProgressMs = now
        st.pct = p
        st.comment = msg
        message = msg
        overall = (cumBefore(idx) + WEIGHTS[idx] * p / 100).coerceIn(0, 99)
        if (idx == ST_RECOG && now - lastEtaMs >= 1000) {
            lastEtaMs = now
            // tört százalékkal számolunk (az egész %-os lépcső 1–2 perces ablakban hatalmas hibát adna)
            val pe = if (pctExact >= 0.0) pctExact else p.toDouble()
            samples.add(now to pe)
            while (samples.size > 2 && now - samples[0].first > 120_000) samples.removeAt(0)
            val first = samples.first()
            val dt = now - first.first
            val dp = pe - first.second
            val el = now - stepStartMs
            // (a) átlagos tempó a lépés kezdete óta; (b) friss tempó az utolsó ~2 percből
            val restLin = if (pe >= 1.0 && el >= 10_000) (el * (100.0 - pe) / pe).toLong() else -1L
            val restWin = if (dt >= 20_000 && dp >= 0.3) ((100.0 - pe) * dt / dp).toLong() else -1L
            val rest = when {
                restLin > 0 && restWin > 0 -> {
                    // a friss tempó legfeljebb 2,5-szeresen térhet el az átlagtól, majd átlagoljuk
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

    /** Ablak-alapú hátralévő idő a darabok állapotából (EtaModel); -1 = még nem becsülhető. */
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

    /** A darabok hossza (ms) – a felismerés haladása és az ETA a tényleges beszédhossz arányában számol. */
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

    /** A darab hossza: [speechMs] csendek nélkül, [spanMs] az eredeti (csendekkel). */
    @Synchronized
    fun chunkSize(i: Int, speechMs: Long, spanMs: Long) {
        val ci = chunkInfo.getOrNull(i) ?: return
        ci.speechMs = speechMs
        ci.spanMs = spanMs
        changed(false)
    }

    /** Az "aktuális darab" (legkisebb sorszámú dolgozó chunk) adatainak frissítése. */
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

    /** Egy chunk haladása (0..1) és mit csinál éppen; a felismerési lépés %-át ebből számoljuk. */
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

    /** Idő kijelzése óra-formátumban: 1:30, 12:05, 1:02:15. */
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
