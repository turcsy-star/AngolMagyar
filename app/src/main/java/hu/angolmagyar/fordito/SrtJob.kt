package hu.angolmagyar.fordito

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Az SRT-készítés közös állapota. A munkát a SrtJobService végzi előtérben (az app
 * váltása / képernyőzár nem állítja meg), a képernyő (FileSubtitleActivity) csak megjeleníti.
 * Az Activity bármikor újra megnyitható: az állapot itt marad, amíg a folyamat él.
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

    /** Egy lépés súlya a teljes folyamatban (%). */
    val WEIGHTS = intArrayOf(2, 18, 5, 72, 3)

    /** Lépésenkénti színkód (a Whisper-dashboard mintájára): sor-csík, jelző, sáv-szegmens. */
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
    /** Várható befejezés (abszolút idő, ms); 0 = még nincs becslés. Csak a hang kinyerése után és chunk-végeken frissül. */
    @Volatile var etaAtMs = 0L
    val etaMs: Long get() = if (etaAtMs > 0) (etaAtMs - System.currentTimeMillis()).coerceAtLeast(0) else 0L
    /** Hogyan készült a becslés (pl. "korábbi futások alapján" / "a legutóbbi chunkok tempója alapján"). */
    @Volatile var etaBasis = ""
    /** Chunkonként: 0 = vár, 1 = dolgozik, 2 = kész. */
    @Volatile var chunkState = IntArray(0)
    /** Az aktív chunkok pillanatnyi tevékenysége (chunk sorszám → szöveg). */
    val phases = java.util.concurrent.ConcurrentHashMap<Int, String>()
    /** Chunkonkénti haladás 0..1 (a teljes felismerési %-hoz). */
    private var chunkFrac = DoubleArray(0)
    @Volatile var waitReason = ""
    @Volatile var engineLabel = ""
    @Volatile var message = ""
    @Volatile var chunkCur = 0
    @Volatile var chunkTotal = 0
    @Volatile var cooling = false
    @Volatile var partial = false
    /** Hány chunk készült el teljesen (a chunk-térképhez). */
    @Volatile var chunkDone = 0
    /** Az utolsó felismert sorok élő előnézete. */
    @Volatile var preview: List<String> = emptyList()
    /** Hová mentette a szolgáltatás az eredményt (null = még nincs mentve, a képernyő kérdez). */
    @Volatile var savedWhere: String? = null
    @Volatile var partialWhere: String? = null
    /** Korábbi futások alapján becsült felismerési idő (ms), 0 = nincs adat. */
    @Volatile var histEstMs = 0L

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
        main.post { for (l in listeners) try { l() } catch (_: Exception) {} }
    }

    // a felismerés haladásának mintái (idő, %) a folyamatos ETA-hoz; a legutóbbi ~60 mp tempója számít
    private val samples = ArrayList<Pair<Long, Int>>()
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
        overall = 0
        etaAtMs = 0
        etaBasis = ""
        chunkState = IntArray(0)
        chunkFrac = DoubleArray(0)
        phases.clear()
        waitReason = ""
        engineLabel = ""
        message = "Indítás…"
        chunkCur = 0
        chunkTotal = 0
        cooling = false
        partial = false
        chunkDone = 0
        preview = emptyList()
        savedWhere = null
        partialWhere = null
        histEstMs = 0
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
        changed()
    }

    /** Az aktív lépés haladása (0–100); a teljes % a lépések súlyából számolódik. */
    @Synchronized
    fun progress(pct: Int, msg: String) {
        val idx = curStep
        val st = steps.getOrNull(idx) ?: return
        if (st.state != ACTIVE) return
        val p = pct.coerceIn(0, 100)
        st.pct = p
        st.comment = msg
        message = msg
        overall = (cumBefore(idx) + WEIGHTS[idx] * p / 100).coerceIn(0, 99)
        val now = System.currentTimeMillis()
        if (idx == ST_RECOG && now - lastEtaMs >= 1000) {
            lastEtaMs = now
            samples.add(now to p)
            while (samples.size > 2 && now - samples[0].first > 60_000) samples.removeAt(0)
            val first = samples.first()
            val dt = now - first.first
            val dp = p - first.second
            if (p >= 1 && dt >= 8_000 && dp > 0) {
                val rest = (100 - p).toLong() * dt / dp
                etaAtMs = now + rest
                etaBasis = "az utolsó percek tempója alapján"
            } else if (histEstMs > 0 && etaAtMs == 0L) {
                etaAtMs = stepStartMs + histEstMs
                etaBasis = "korábbi futások alapján"
            }
        }
        changed(false)
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
    fun initChunks(n: Int) {
        chunkState = IntArray(n)
        chunkFrac = DoubleArray(n)
        chunkTotal = n
        chunkDone = 0
        changed()
    }

    @Synchronized
    fun chunkStart(i: Int) {
        if (i in chunkState.indices) { chunkState[i] = 1; chunkFrac[i] = 0.0 }
        chunkCur = i + 1
        changed()
    }

    /** Egy chunk haladása (0..1) és mit csinál éppen; a felismerési lépés %-át ebből számoljuk. */
    @Synchronized
    fun chunkProgress(i: Int, frac: Double, what: String, chunkAudioMs: Long, totalAudioMs: Long) {
        if (i !in chunkFrac.indices) return
        chunkFrac[i] = frac.coerceIn(0.0, 1.0)
        phases[i] = "#${i + 1} $what"
        var doneMs = 0.0
        val per = if (chunkFrac.isNotEmpty()) totalAudioMs.toDouble() / chunkFrac.size else 1.0
        for (f in chunkFrac) doneMs += f * per
        val pct = if (totalAudioMs > 0) (doneMs * 100.0 / totalAudioMs).toInt() else 0
        val msg = phases.entries.sortedBy { it.key }.take(4).joinToString("  ·  ") { it.value }
        progress(pct.coerceIn(0, 99), msg.ifBlank { "Feldolgozás…" })
        // (chunkAudioMs jelenleg csak a hívó számára áll rendelkezésre; a súlyozás egyenletes)
    }

    @Synchronized
    fun chunkFinish(i: Int) {
        if (i in chunkState.indices) { chunkState[i] = 2; chunkFrac[i] = 1.0 }
        phases.remove(i)
        chunkDone = chunkState.count { it == 2 }
        changed()
    }

    fun fmtDur(ms: Long): String {
        val sec = (ms / 1000).coerceAtLeast(0)
        val h = sec / 3600
        val m = (sec % 3600) / 60
        val s = sec % 60
        return when {
            h > 0 -> "%d ó %d p".format(h, m)
            m > 0 -> "%d p %02d mp".format(m, s)
            else -> "%d mp".format(s)
        }
    }

    fun fmtClock(atMs: Long): String {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = atMs
        return "%02d:%02d".format(cal.get(java.util.Calendar.HOUR_OF_DAY), cal.get(java.util.Calendar.MINUTE))
    }
}
