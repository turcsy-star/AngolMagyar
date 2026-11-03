package hu.angolmagyar.fordito

/** Szótár-importnál: a meglévő sorokhoz hozzáfűzi az újakat (kis/nagybetűre érzéketlen duplikátumszűréssel). */
object GlossaryMerge {
    fun merge(old: String, add: String): String {
        val have = old.lines().map { it.trim().lowercase() }.toSet()
        val seen = HashSet<String>()
        val extra = add.lines().map { it.trim() }.filter {
            val k = it.lowercase()
            it.isNotEmpty() && k !in have && seen.add(k)
        }
        return if (extra.isEmpty()) old else (old.trimEnd() + "\n" + extra.joinToString("\n")).trim()
    }
}
