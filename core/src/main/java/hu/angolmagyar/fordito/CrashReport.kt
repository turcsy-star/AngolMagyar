package hu.angolmagyar.fordito

/** Összeomlás-napló szövegezése és méretkorlátozása (tiszta Kotlin, külön tesztelhető). */
object CrashReport {
    const val MAX_CHARS = 60_000
    const val SEPARATOR = "=== ÖSSZEOMLÁS ==="
    private const val MAX_FRAMES = 40
    private const val MAX_CAUSES = 3

    fun stack(t: Throwable): String {
        val sb = StringBuilder()
        var cur: Throwable? = t
        var depth = 0
        while (cur != null && depth <= MAX_CAUSES) {
            if (depth > 0) sb.append("Oka: ")
            sb.append(cur.javaClass.name).append(": ").append(cur.message ?: "").append('\n')
            val st = cur.stackTrace
            for (i in 0 until minOf(st.size, MAX_FRAMES)) sb.append("    at ").append(st[i]).append('\n')
            if (st.size > MAX_FRAMES) sb.append("    … még ").append(st.size - MAX_FRAMES).append(" sor\n")
            cur = cur.cause?.takeIf { it !== cur }
            depth++
        }
        return sb.toString()
    }

    fun format(time: String, thread: String, t: Throwable, meta: Map<String, String> = emptyMap()): String {
        val sb = StringBuilder()
        sb.append(SEPARATOR).append('\n')
        sb.append("Idő: ").append(time).append('\n')
        sb.append("Szál: ").append(thread).append('\n')
        for ((k, v) in meta) sb.append(k).append(": ").append(v).append('\n')
        sb.append(stack(t))
        return sb.toString()
    }

    /** Hozzáfűz, és a legrégebbi bejegyzések elhagyásával a korlát alatt tartja. */
    fun append(existing: String, entry: String, maxChars: Int = MAX_CHARS): String {
        if (maxChars <= 0) return ""
        val all = if (existing.isBlank()) entry else existing.trimEnd() + "\n" + entry
        if (all.length <= maxChars) return all

        // Teljes bejegyzéseket dobunk el, nem egy karakterpozícióból vágunk.
        // Így a megmaradó napló mindig a legfrissebb teljes bejegyzésekkel kezdődik.
        var start = all.indexOf(SEPARATOR)
        if (start < 0) return all.takeLast(maxChars)

        while (true) {
            val next = all.indexOf(SEPARATOR, start + SEPARATOR.length)
            if (next < 0 || all.length - start <= maxChars) break
            start = next
        }

        val retained = all.substring(start)
        // Ha már az egyetlen megmaradó (legfrissebb) bejegyzés önmagában is
        // nagyobb a keretnél, annak elejét tartjuk meg: az időbélyeg és a
        // kivétel típusa fontosabb, mint a stack trace vége.
        return if (retained.length <= maxChars) retained else retained.take(maxChars)
    }

    fun entryCount(log: String): Int = log.split(SEPARATOR).size - 1

    /** Az utolsó bejegyzés első sora, rövid összefoglalónak a párbeszédablakhoz. */
    fun lastSummary(log: String): String {
        val last = log.substringAfterLast(SEPARATOR, "")
        val time = last.lineSequence().firstOrNull { it.startsWith("Idő:") }?.removePrefix("Idő:")?.trim().orEmpty()
        val err = last.lineSequence().firstOrNull { it.contains("Exception") || it.contains("Error") }?.trim().orEmpty()
        return listOf(time, err).filter { it.isNotEmpty() }.joinToString(" · ").take(200)
    }
}
