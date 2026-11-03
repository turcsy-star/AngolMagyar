package hu.angolmagyar.fordito
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
/**
 * Online szótáras szöveghiba-javítás.
 *
 * A felismert (angol) szövegben csak a gyanús, ritka szavakat ellenőrzi egyenként a nyilvános Datamuse szótár-API-val
 * (api.datamuse.com – kulcs nélkül, csak maga a szó megy ki, mondat nem). Ha a szó nincs a szótárban, de van hozzá
 * nagyon hasonló, gyakori szó, akkor javítási javaslatot ad. A név-szerű (nagybetűs) szavakat, rövidítéseket és a
 * beszélt nyelvi alakokat (gonna, wanna…) nem bántja.
 */
object OnlineDictionary {
    interface Get {
        fun get(url: String, timeoutMs: Int): Pair<Int, String>
    }
    object UrlGet : Get {
        override fun get(url: String, timeoutMs: Int): Pair<Int, String> {
            val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
            try {
                c.requestMethod = "GET"
                c.connectTimeout = 8_000
                c.readTimeout = timeoutMs
                c.setRequestProperty("accept", "application/json")
                c.setRequestProperty("user-agent", "AngolMagyar/4 (feliratjavito)")
                val code = c.responseCode
                val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
                return code to stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                c.disconnect()
            }
        }
    }
    const val BASE = "https://api.datamuse.com/words"
    data class Suspect(val lower: String, val surfaces: Set<String>, val count: Int)
    data class Outcome(
        val fixes: Map<String, String>,
        val checked: Int,
        val unreachable: Boolean,
        val note: String
    )
    private val TOKEN = Regex("[\\p{L}][\\p{L}'’-]*")
    /** Beszélt nyelvi / szleng alakok, amelyek valódi szavak, de a szótárak gyakran nem ismerik őket. */
    private val INFORMAL = setOf(
        "gonna", "wanna", "gotta", "kinda", "sorta", "lemme", "gimme", "dunno", "yeah", "yeap", "yep", "nope", "okay",
        "outta", "lotta", "hafta", "shoulda", "coulda", "woulda", "musta", "mighta", "cuz", "coz", "tryna", "finna",
        "whoa", "woah", "hmmm", "ughh", "ahem", "aaah", "ooooh", "bruh", "dude", "dudes", "mmhmm", "huh", "nah",
        "alright", "yknow", "whatcha", "gotcha", "betcha", "howdy", "wassup", "ya'll", "yall", "aight", "tho", "thru"
    )
    private fun isSentenceStart(text: String, at: Int): Boolean {
        var i = at - 1
        while (i >= 0 && (text[i].isWhitespace() || text[i] == '"' || text[i] == '“' || text[i] == '‘' || text[i] == '-' || text[i] == '–' || text[i] == '—')) i--
        return i < 0 || text[i] in ".?!…:"
    }
    /**
     * Offline előszűrés: mely szavakat érdemes online ellenőrizni.
     * [priority] a felismerő által bizonytalannak jelölt szavak (kisbetűs), ezek előre kerülnek.
     */
    fun suspects(texts: List<String>, limit: Int = 80, priority: Set<String> = emptySet()): List<Suspect> {
        val counts = HashMap<String, Int>()
        val surfaces = HashMap<String, MutableSet<String>>()
        val nameLike = HashSet<String>()
        for (t in texts) {
            for (m in TOKEN.findAll(t)) {
                val raw = m.value
                if (raw.any { it == '\'' || it == '’' || it == '-' }) continue
                if (raw.length < 4 || !raw.all { it in 'A'..'Z' || it in 'a'..'z' }) continue
                if (raw.all { it.isUpperCase() }) continue
                val lower = raw.lowercase()
                counts[lower] = (counts[lower] ?: 0) + 1
                surfaces.getOrPut(lower) { LinkedHashSet() }.add(raw)
                if (raw[0].isUpperCase() && !isSentenceStart(t, m.range.first)) nameLike.add(lower)
            }
        }
        return counts.entries
            .filter { it.value <= 2 && it.key !in nameLike && it.key !in INFORMAL }
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> { it.key in priority }
                    .thenBy { it.value }
                    .thenByDescending { it.key.length }
                    .thenBy { it.key }
            )
            .take(limit)
            .map { Suspect(it.key, surfaces[it.key] ?: setOf(it.key), it.value) }
    }
    private fun lev(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
    private data class Cand(val word: String, val freq: Double)
    private fun parseCandidates(body: String): List<Cand>? {
        val root = try { MiniJson.parse(body) } catch (_: Exception) { return null } as? List<*> ?: return null
        val out = ArrayList<Cand>()
        for (x in root) {
            val m = x as? Map<*, *> ?: continue
            val w = m["word"] as? String ?: continue
            var f = 0.0
            for (t in (m["tags"] as? List<*>).orEmpty()) {
                val s = t as? String ?: continue
                if (s.startsWith("f:")) f = s.substring(2).toDoubleOrNull() ?: 0.0
            }
            out.add(Cand(w, f))
        }
        return out
    }
    /**
     * A szótár válaszából javítást választ. null = a szó helyes (megvan a szótárban) vagy nincs elég biztos javaslat.
     */
    fun pick(word: String, body: String): String? {
        val lower = word.lowercase()
        val cands = parseCandidates(body) ?: return null
        if (cands.any { it.word.equals(lower, ignoreCase = true) }) return null
        val maxDist = if (lower.length <= 5) 1 else 2
        var best: Cand? = null
        var bestDist = Int.MAX_VALUE
        for (c in cands) {
            val cw = c.word.lowercase()
            if (cw.length < 3 || !cw.all { it in 'a'..'z' }) continue
            if (c.freq < 0.5) continue
            val d = lev(lower, cw)
            if (d < 1 || d > maxDist) continue
            if (cw[0] != lower[0] && d > 1) continue
            if (d < bestDist || (d == bestDist && c.freq > (best?.freq ?: 0.0))) {
                best = c
                bestDist = d
            }
        }
        return best?.word?.lowercase()
    }
    private fun matchCase(surface: String, fix: String): String =
        if (surface.isNotEmpty() && surface[0].isUpperCase()) fix.replaceFirstChar { it.uppercase() } else fix
    private sealed class Res {
        object Ok : Res()
        object NoFix : Res()
        object Net : Res()
        class Fix(val word: String) : Res()
    }
    private fun url(word: String) =
        BASE + "?sp=" + java.net.URLEncoder.encode(word, "UTF-8") + "&max=8&md=f"
    /**
     * Megkeresi a javítandó szavakat a szövegekben (hálózati hívásokkal, ezért NEM főszálról hívandó).
     * A visszaadott térkép: a szövegben szereplő alak → javított alak (kis/nagybetű megtartva).
     */
    fun findFixes(
        texts: List<String>,
        http: Get = UrlGet,
        priority: Set<String> = emptySet(),
        limit: Int = 80,
        budgetMs: Long = 40_000L,
        cancel: () -> Boolean = { false }
    ): Outcome {
        val sus = suspects(texts, limit, priority)
        if (sus.isEmpty()) return Outcome(emptyMap(), 0, false, "")
        val netFails = AtomicInteger(0)
        val checked = AtomicInteger(0)
        val results = ConcurrentHashMap<String, Res>()
        val pool = Executors.newFixedThreadPool(4)
        try {
            val tasks = sus.map { s ->
                Callable {
                    if (cancel() || netFails.get() >= 3) return@Callable null
                    val res: Res = try {
                        val (code, body) = http.get(url(s.lower), 10_000)
                        if (code !in 200..299) Res.Net
                        else {
                            val fix = pick(s.lower, body)
                            if (fix == null) Res.Ok else Res.Fix(fix)
                        }
                    } catch (_: Exception) {
                        Res.Net
                    }
                    if (res is Res.Net) netFails.incrementAndGet() else { netFails.set(0); checked.incrementAndGet() }
                    results[s.lower] = res
                    null
                }
            }
            pool.invokeAll(tasks, budgetMs, TimeUnit.MILLISECONDS)
        } finally {
            pool.shutdownNow()
        }
        val fixes = LinkedHashMap<String, String>()
        for (s in sus) {
            val r = results[s.lower] as? Res.Fix ?: continue
            for (surface in s.surfaces) fixes[surface] = matchCase(surface, r.word)
        }
        val unreachable = netFails.get() >= 3 || (checked.get() == 0 && results.isNotEmpty())
        val note = when {
            unreachable -> "Az online szótár nem érhető el (nincs internet vagy a szolgáltatás nem válaszol)."
            fixes.isNotEmpty() -> "${fixes.size} szó javítva az online szótár alapján."
            else -> ""
        }
        return Outcome(fixes, checked.get(), unreachable, note)
    }
    /** Kapcsolatpróba az online szótárhoz. null = működik, különben a hiba szövege. */
    fun selfTest(http: Get = UrlGet): String? = try {
        val (code, body) = http.get(url("hipopotamus"), 10_000)
        when {
            code !in 200..299 -> "Az online szótár HTTP $code kódot adott."
            parseCandidates(body) == null -> "Az online szótár válasza nem értelmezhető."
            else -> null
        }
    } catch (e: Exception) {
        OnlineTranslator.netErrorText(e)
    }
}
/** Feliratsorokra alkalmazott online szótáras javítás: visszaadja a javított sorokat és a módosult sorok indexét. */
object OnlineSpellFix {
    data class Result(val cues: List<SrtExporter.Cue>, val changed: List<Int>, val outcome: OnlineDictionary.Outcome)
    fun run(
        cues: List<SrtExporter.Cue>,
        http: OnlineDictionary.Get = OnlineDictionary.UrlGet,
        cancel: () -> Boolean = { false }
    ): Result {
        val priority = HashSet<String>()
        for (c in cues) {
            if (c.wordConfidence.any { TokenQuality.needsReview(it) }) {
                for (m in Regex("[A-Za-z]+").findAll(c.source)) priority.add(m.value.lowercase())
            }
        }
        val out = OnlineDictionary.findFixes(cues.map { it.source }, http, priority, cancel = cancel)
        if (out.fixes.isEmpty()) return Result(cues, emptyList(), out)
        val changed = ArrayList<Int>()
        val fixed = cues.mapIndexed { i, c ->
            val ns = SpellCluster.apply(c.source, out.fixes)
            if (ns != c.source) {
                changed.add(i)
                c.copy(source = ns)
            } else c
        }
        return Result(fixed, changed, out)
    }
}
