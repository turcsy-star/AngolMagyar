package hu.angolmagyar.fordito

/** A kézi javítások gyakorisága: ami többször ismétlődik, abból szabályt javasol az app. */
object RuleStats {
    /** Sorok: "régi=új\tdarab" */
    fun parse(text: String): LinkedHashMap<String, Int> {
        val m = LinkedHashMap<String, Int>()
        for (l in text.lines()) {
            val i = l.lastIndexOf('\t')
            if (i <= 0) continue
            val n = l.substring(i + 1).trim().toIntOrNull() ?: continue
            m[l.substring(0, i)] = n
        }
        return m
    }

    fun serialize(m: Map<String, Int>): String = m.entries.joinToString("\n") { it.key + "\t" + it.value }

    /** Egy javítás rögzítése; a lista legfeljebb [cap] elemű (a legritkábbak esnek ki). */
    fun record(m: LinkedHashMap<String, Int>, from: String, to: String, cap: Int = 300) {
        val k = "$from=$to"
        m[k] = (m[k] ?: 0) + 1
        if (m.size > cap) {
            val drop = m.entries.minByOrNull { it.value }?.key
            if (drop != null && drop != k) m.remove(drop)
        }
    }

    /** Legalább [min] alkalommal előfordult, de még nincs a szabályok között. */
    fun candidates(m: Map<String, Int>, existingRules: String, min: Int = 2): List<String> {
        val have = existingRules.lines().map { it.trim().lowercase() }.toHashSet()
        return m.filter { it.value >= min && it.key.lowercase() !in have }.keys.toList()
    }
}
