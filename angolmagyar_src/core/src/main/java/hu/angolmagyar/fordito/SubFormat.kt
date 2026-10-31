package hu.angolmagyar.fordito

import java.util.Locale

/**
 * Felirat-formázás: kiegyensúlyozott sortörés, hosszú blokkok szétvágása, olvasható időzítés,
 * beszélőszínek és az SRT visszaolvasása (a szerkesztőhöz). Tiszta Kotlin, egységtesztelhető.
 */
object SubFormat {
    const val MAX_LINE = 42
    private const val MIN_DUR_MS = 1200L
    private const val MAX_DUR_MS = 7000L

    private val PALETTE = listOf("#FFD54F", "#4FC3F7", "#81C784", "#F48FB1", "#CE93D8", "#FFB74D")

    private fun words(text: String): List<String> =
        text.trim().split(Regex("\\s+")).filter { it.isNotBlank() }

    /** Ha két sorba elfér, a két sor hossza közel egyenlő (nem az első sor a hosszú, a második csonk). */
    fun wrapBalanced(text: String, maxLen: Int = MAX_LINE): List<String> {
        val greedy = SrtExporter.wrap(text, maxLen)
        if (greedy.size != 2) return greedy
        val w = words(text)
        var best = greedy
        var bestDiff = Int.MAX_VALUE
        for (k in 1 until w.size) {
            val a = w.subList(0, k).joinToString(" ")
            val b = w.subList(k, w.size).joinToString(" ")
            if (a.length <= maxLen && b.length <= maxLen) {
                val d = kotlin.math.abs(a.length - b.length)
                if (d < bestDiff) { bestDiff = d; best = listOf(a, b) }
            }
        }
        return best
    }

    /** Hány feliratblokk kell a szöveghez (egy blokk legfeljebb 2 sor). */
    fun partsNeeded(text: String): Int {
        if (text.isBlank()) return 1
        val lines = SrtExporter.wrap(text).size
        return if (lines <= 2) 1 else (lines + 1) / 2
    }

    /** A szöveg szétvágása legfeljebb [n] részre, szóhatáron, lehetőleg írásjelnél. */
    fun splitText(text: String, n: Int): List<String> {
        val w = words(text)
        if (w.isEmpty()) return emptyList()
        if (n <= 1 || w.size <= 1) return listOf(w.joinToString(" "))
        val total = w.sumOf { it.length + 1 }
        val target = total.toDouble() / n
        val parts = ArrayList<String>()
        var cur = ArrayList<String>()
        var len = 0
        for ((i, word) in w.withIndex()) {
            cur.add(word)
            len += word.length + 1
            val remainingParts = n - parts.size - 1
            val wordsLeft = w.size - i - 1
            val punct = word.last() in ".?!,;:…"
            val full = len >= target * 1.05 || (punct && len >= target * 0.7)
            if (full && remainingParts > 0 && wordsLeft >= remainingParts) {
                parts.add(cur.joinToString(" "))
                cur = ArrayList()
                len = 0
            }
        }
        if (cur.isNotEmpty()) parts.add(cur.joinToString(" "))
        return parts
    }

    /** A túl hosszú blokkokat (2 sornál több) időarányosan több blokkra vágja; a forrás és a cél együtt. */
    fun reflow(cues: List<SrtExporter.Cue>): List<SrtExporter.Cue> {
        val out = ArrayList<SrtExporter.Cue>(cues.size)
        for (c in cues) {
            val n = maxOf(partsNeeded(c.source), partsNeeded(c.target))
            if (n <= 1) { out.add(c); continue }
            val src = splitText(c.source, n)
            val tgt = splitText(c.target, n)
            val k = maxOf(src.size, tgt.size)
            if (k <= 1) { out.add(c); continue }
            val dur = maxOf(c.endMs - c.startMs, 1000L)
            val weights = (0 until k).map { maxOf(1, maxOf(src.getOrElse(it) { "" }.length, tgt.getOrElse(it) { "" }.length)) }
            val wsum = weights.sum().toDouble()
            var acc = 0.0
            for (i in 0 until k) {
                val s = c.startMs + (dur * acc / wsum).toLong()
                acc += weights[i]
                val e = c.startMs + (dur * acc / wsum).toLong()
                out.add(SrtExporter.Cue(s, e, src.getOrElse(i) { "" }, tgt.getOrElse(i) { "" }, c.speaker))
            }
        }
        return out
    }

    /**
     * Olvasható időzítés: legalább ~1,2 mp (hosszabb szövegnél karakterszámmal arányos), nem húzódik
     * feleslegesen a csendbe, és nem lóg bele a következő blokkba.
     */
    fun fixTiming(cues: List<SrtExporter.Cue>): List<SrtExporter.Cue> {
        val out = ArrayList<SrtExporter.Cue>(cues.size)
        for ((i, c) in cues.withIndex()) {
            val chars = maxOf(c.source.length, c.target.length)
            var end = if (c.endMs <= c.startMs) c.startMs + MIN_DUR_MS else c.endMs
            val minEnd = c.startMs + minOf(MAX_DUR_MS, maxOf(MIN_DUR_MS, chars * 45L))
            if (end < minEnd) end = minEnd
            val maxEnd = c.startMs + maxOf(3500L, chars * 110L)
            if (end > maxEnd) end = maxEnd
            val next = cues.getOrNull(i + 1)?.startMs
            if (next != null && end > next - 40) {
                // ha a következő blokk közel kezdődik, nem lógunk bele (legalább 200 ms látszik)
                end = if (next - 40 >= c.startMs + 500) next - 40 else maxOf(next - 40, c.startMs + 200)
            }
            out.add(c.copy(endMs = end))
        }
        return out
    }

    /** Olvasási sebesség: ami 20 karakter/mp fölött van, annak a végét a következő blokk előtti szabad helyig kitoljuk. */
    fun fixReadingSpeed(cues: List<SrtExporter.Cue>, maxCps: Double = 20.0): List<SrtExporter.Cue> {
        val out = ArrayList<SrtExporter.Cue>(cues.size)
        for ((i, c) in cues.withIndex()) {
            val shown = (if (c.target.isNotBlank()) c.target else c.source).replace("\n", " ")
            val dur = c.endMs - c.startMs
            if (shown.isEmpty() || dur <= 0 || shown.length * 1000.0 / dur <= maxCps) { out.add(c); continue }
            val need = (shown.length * 1000.0 / maxCps).toLong()
            var end = minOf(c.startMs + need, c.startMs + 8000L)
            val next = cues.getOrNull(i + 1)?.startMs
            if (next != null && end > next - 40) end = maxOf(c.endMs, next - 40)
            out.add(c.copy(endMs = maxOf(end, c.endMs)))
        }
        return out
    }

    /** A teljes formázás: szétvágás + időzítés + olvasási sebesség. */
    fun polish(cues: List<SrtExporter.Cue>): List<SrtExporter.Cue> =
        fixReadingSpeed(fixTiming(reflow(cues.sortedBy { it.startMs })))

    /** Beszélőnként más szín (SRT <font color>), a megjelenés sorrendjében. */
    fun speakerColors(cues: List<SrtExporter.Cue>): Map<String, String> {
        val m = LinkedHashMap<String, String>()
        for (c in cues) {
            if (c.speaker.isNotBlank() && c.speaker !in m) m[c.speaker] = PALETTE[m.size % PALETTE.size]
        }
        return m
    }

    // ------------------------------------------------------------------ SRT visszaolvasás

    private val TIME = Regex("""(\d{1,2}):(\d{2}):(\d{2})[,.](\d{1,3})""")

    fun parseTime(s: String): Long? {
        val m = TIME.find(s.trim()) ?: return null
        val h = m.groupValues[1].toLong()
        val mi = m.groupValues[2].toLong()
        val se = m.groupValues[3].toLong()
        val ms = m.groupValues[4].padEnd(3, '0').toLong()
        return ((h * 60 + mi) * 60 + se) * 1000 + ms
    }

    /** SRT szöveg → blokkok (a teljes blokkszöveg a source mezőben, sortörésekkel). */
    fun parseSrt(text: String): List<SrtExporter.Cue> {
        val out = ArrayList<SrtExporter.Cue>()
        val blocks = text.replace("\r\n", "\n").replace('\r', '\n').trim().split(Regex("\n\\s*\n"))
        for (b in blocks) {
            val lines = b.split("\n")
            val ti = lines.indexOfFirst { it.contains("-->") }
            if (ti < 0) continue
            val parts = lines[ti].split("-->")
            if (parts.size < 2) continue
            val st = parseTime(parts[0]) ?: continue
            val en = parseTime(parts[1]) ?: continue
            val body = lines.drop(ti + 1).joinToString("\n").trim()
            out.add(SrtExporter.Cue(st, en, body, ""))
        }
        return out
    }

    /** Blokkok → SRT úgy, hogy a source szövegét (sortöréseivel együtt) változtatás nélkül írja. */
    fun buildRaw(cues: List<SrtExporter.Cue>): String {
        val sb = StringBuilder()
        var idx = 1
        for (c in cues) {
            if (c.source.isBlank()) continue
            val end = if (c.endMs <= c.startMs) c.startMs + 2000 else c.endMs
            sb.append(idx).append('\n')
            sb.append(SrtExporter.formatTime(c.startMs)).append(" --> ").append(SrtExporter.formatTime(end)).append('\n')
            sb.append(c.source.trim()).append("\n\n")
            idx++
        }
        return sb.toString()
    }

    /** Gyanús blokk: üres, nagyon rövid hosszú ideig, vagy ugyanaz mint az előző. */
    fun suspicious(prev: SrtExporter.Cue?, c: SrtExporter.Cue): String? {
        val t = c.source.trim()
        if (t.isEmpty()) return "üres"
        val dur = c.endMs - c.startMs
        if (t.length <= 2 && dur > 3000) return "rövid szöveg, hosszú idő"
        if (prev != null && prev.source.trim().equals(t, ignoreCase = true)) return "ismétlődés"
        if (dur <= 0) return "hibás idő"
        val cps = t.replace("\n", " ").length * 1000.0 / dur.coerceAtLeast(1)
        if (cps > 28) return String.format(Locale.US, "nagyon gyors (%.0f kar/mp)", cps)
        return null
    }
}
