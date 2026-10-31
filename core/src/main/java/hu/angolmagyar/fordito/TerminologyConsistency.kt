package hu.angolmagyar.fordito

import java.util.Locale

/**
 * Második menet: a fordított felirat egységesítése az egész videóra.
 *  1) Tulajdonnevek írásváltozatai (pl. "Katherin" / "Katherine") → a leggyakoribb alak;
 *  2) azonos forrásmondat eltérő fordításai → a leggyakoribb fordítás.
 * Óvatos: csak azokban a blokkokban javít, ahol a forrás tartalmazza a nevet / ugyanaz a mondat; a szöveget
 * nem találgatja, a változtatásokat listázza.
 */
object TerminologyConsistency {
    data class Report(val cues: List<SrtExporter.Cue>, val nameFixes: List<String>, val sentenceFixes: Int) {
        val changed: Boolean get() = nameFixes.isNotEmpty() || sentenceFixes > 0
    }

    fun apply(cues: List<SrtExporter.Cue>): Report {
        if (cues.none { it.target.isNotBlank() }) return Report(cues, emptyList(), 0)
        val a = unifyNames(cues)
        val b = unifySentences(a.first)
        return Report(b.first, a.second, b.second)
    }

    private val TOKEN = Regex("[\\p{L}\\p{N}]+(?:[-'’][\\p{L}\\p{N}]+)*")

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            System.arraycopy(cur, 0, prev, 0, prev.size)
        }
        return prev[b.length]
    }

    internal fun unifyNames(cues: List<SrtExporter.Cue>): Pair<List<SrtExporter.Cue>, List<String>> {
        val names = LinkedHashSet<String>()
        cues.forEach { c -> names.addAll(TranslationCheck.properNouns(c.source).filter { n -> n.length >= 4 }) }
        if (names.isEmpty()) return cues to emptyList()
        val out = cues.toMutableList()
        val fixes = ArrayList<String>()
        for (name in names) {
            val idxs = out.indices.filter { Regex("(?<![\\p{L}])${Regex.escape(name)}").containsMatchIn(out[it].source) }
            if (idxs.size < 2) continue
            val maxDist = maxOf(1, name.length / 5)
            // jelölt alapalakok: a tokenek első (név hossza ±1) karaktere, ha elég közel van a névhez
            data class Hit(val cue: Int, val range: IntRange, val base: String)
            val hits = ArrayList<Hit>()
            for (i in idxs) {
                for (m in TOKEN.findAll(out[i].target)) {
                    val tok = m.value
                    if (!tok[0].isUpperCase()) continue
                    var best: String? = null
                    var bestD = Int.MAX_VALUE
                    for (len in (name.length - 1)..(name.length + 1)) {
                        if (len < 3 || len > tok.length) continue
                        val base = tok.substring(0, len)
                        val d = levenshtein(base.lowercase(Locale.ROOT), name.lowercase(Locale.ROOT))
                        if (d < bestD || (d == bestD && len == name.length)) { bestD = d; best = base }
                    }
                    if (best != null && bestD <= maxDist) hits.add(Hit(i, m.range.first until (m.range.first + best.length), best))
                }
            }
            if (hits.size < 2) continue
            val counts = hits.groupingBy { it.base }.eachCount()
            if (counts.size < 2) continue
            val top = counts.maxOf { it.value }
            val leaders = counts.filter { it.value == top }.keys
            val canonical = when {
                name in leaders -> name
                else -> leaders.first()
            }
            // csak akkor, ha a kanonikus alak egyértelműen vezet (nem döntetlen két idegen alak között)
            if (leaders.size > 1 && name !in leaders) continue
            var changed = 0
            for (cueIdx in hits.map { it.cue }.distinct()) {
                var text = out[cueIdx].target
                for (h in hits.filter { it.cue == cueIdx && it.base != canonical }.sortedByDescending { it.range.first }) {
                    text = text.replaceRange(h.range, canonical); changed++
                }
                if (text != out[cueIdx].target) out[cueIdx] = out[cueIdx].copy(target = text)
            }
            if (changed > 0) fixes.add("$name → $canonical ($changed javítás)")
        }
        return out to fixes
    }

    private fun key(s: String) = WordErrorRate.normalize(s).joinToString(" ")

    internal fun unifySentences(cues: List<SrtExporter.Cue>): Pair<List<SrtExporter.Cue>, Int> {
        val groups = cues.indices.filter { cues[it].target.isNotBlank() && key(cues[it].source).split(' ').size >= 2 }
            .groupBy { key(cues[it].source) }
        val out = cues.toMutableList()
        var fixes = 0
        for ((_, idxs) in groups) {
            if (idxs.size < 2) continue
            val counts = LinkedHashMap<String, Int>()
            idxs.forEach { counts.merge(out[it].target.trim(), 1, Int::plus) }
            if (counts.size < 2) continue
            val top = counts.maxOf { it.value }
            val leaders = counts.filter { it.value == top }.keys
            if (leaders.size > 1 || top < 2) continue // döntetlen vagy nincs ismétlődő változat: nem találgatunk
            val best = leaders.first()
            for (i in idxs) if (out[i].target.trim() != best) { out[i] = out[i].copy(target = best); fixes++ }
        }
        return out to fixes
    }
}
