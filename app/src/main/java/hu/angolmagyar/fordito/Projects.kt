package hu.angolmagyar.fordito

import android.content.Context

/**
 * Projekt (pl. egy sorozat): saját szereplő-lista, téma, beszélő-stílus és szótár-javítások.
 * Projekt nélkül ("") a globális beállítások érvényesek.
 */
object Projects {
    private fun p(ctx: Context) = Prefs(ctx).raw()

    fun list(ctx: Context): List<String> =
        (p(ctx).getString("projList", "") ?: "").lines().map { it.trim() }.filter { it.isNotEmpty() }

    fun current(ctx: Context): String = p(ctx).getString("projCur", "") ?: ""

    fun setCurrent(ctx: Context, name: String) { p(ctx).edit().putString("projCur", name.trim()).apply() }

    fun add(ctx: Context, name: String) {
        val n = name.trim().replace('\n', ' ')
        if (n.isEmpty()) return
        val l = list(ctx)
        if (l.none { it.equals(n, true) }) p(ctx).edit().putString("projList", (l + n).joinToString("\n")).apply()
        setCurrent(ctx, n)
    }

    private fun key(ctx: Context, field: String) = "proj.${current(ctx)}.$field"

    /** A jelenlegi projekt mezője; projekt nélkül a globális mező. */
    fun get(ctx: Context, field: String): String = p(ctx).getString(key(ctx, field), "") ?: ""

    fun set(ctx: Context, field: String, value: String) {
        p(ctx).edit().putString(key(ctx, field), value).apply()
    }

    /** Szabályok: globális + a projekté. */
    fun huRules(ctx: Context): String {
        val g = Prefs(ctx).huFixes
        val pr = get(ctx, "huFixes")
        return if (pr.isBlank()) g else g + "\n" + pr
    }

    /** Szabály hozzáadása a projekt (ha van) vagy a globális listához. */
    fun addHuRule(ctx: Context, from: String, to: String): Boolean {
        if (current(ctx).isNotEmpty()) {
            val r = RuleSuggest.add(get(ctx, "huFixes"), from, to)
            set(ctx, "huFixes", r.first)
            return r.second
        }
        val r = RuleSuggest.add(Prefs(ctx).huFixes, from, to)
        Prefs(ctx).huFixes = r.first
        return r.second
    }

    fun names(ctx: Context): List<String> = NameFix.parseNames(get(ctx, "cast"))

    /** Háttér az online fordítónak: téma, szereplők, beszélők viszonya. */
    fun contextHint(ctx: Context): String = buildString {
        val topic = get(ctx, "topic").trim()
        val cast = get(ctx, "cast").trim()
        val style = get(ctx, "style").trim()
        if (topic.isNotEmpty()) append("Téma: ").append(topic).append('\n')
        if (cast.isNotEmpty()) append("Szereplők / nevek (írd pontosan így, ne fordítsd): ").append(cast).append('\n')
        if (style.isNotEmpty()) append("Beszélők viszonya, stílus: ").append(style).append('\n')
    }.trim()
}

/** Fordítási memória tárolása (app privát fájl) + egyszerű gyorsítótár.
 * v2.21.0: dual-write a Room-alapú TranslationMemoryRepo felé is (háttérben). */
object TranslationMemoryStore {
    private var cache: TranslationMemory? = null
    private fun file(ctx: Context) = java.io.File(ctx.filesDir, "tm.tsv")

    fun enabled(ctx: Context): Boolean = Prefs(ctx).raw().getBoolean("tmEnabled", true)
    fun setEnabled(ctx: Context, v: Boolean) { Prefs(ctx).raw().edit().putBoolean("tmEnabled", v).apply() }

    @Synchronized
    fun get(ctx: Context): TranslationMemory {
        cache?.let { return it }
        val tm = try { if (file(ctx).exists()) TranslationMemory.fromText(file(ctx).readText(Charsets.UTF_8)) else TranslationMemory() }
        catch (_: Exception) { TranslationMemory() }
        cache = tm
        return tm
    }

    @Synchronized
    fun record(ctx: Context, cues: List<SrtExporter.Cue>) {
        if (!enabled(ctx)) return
        val tm = get(ctx)
        for (c in cues) if (c.source.isNotBlank() && c.target.isNotBlank() && c.source != c.target) tm.put(c.source, c.target)
        try { file(ctx).writeText(tm.toText(), Charsets.UTF_8) } catch (_: Exception) {}

        // v2.21 dual-write → Room (fire-and-forget)
        try {
            val appCtx = ctx.applicationContext
            Thread {
                try {
                    val repo = hu.angolmagyar.fordito.core.TranslationMemoryRepo(appCtx)
                    kotlinx.coroutines.runBlocking {
                        for (c in cues) {
                            if (c.source.isNotBlank() && c.target.isNotBlank() && c.source != c.target) {
                                repo.put(c.source, c.target)
                            }
                        }
                    }
                } catch (_: Exception) { }
            }.start()
        } catch (_: Exception) { }
    }

    @Synchronized
    fun clear(ctx: Context) {
        cache = TranslationMemory()
        try { file(ctx).delete() } catch (_: Exception) {}
        try {
            val appCtx = ctx.applicationContext
            Thread {
                try {
                    kotlinx.coroutines.runBlocking {
                        hu.angolmagyar.fordito.core.db.AppDatabase.get(appCtx).translationDao().clear()
                    }
                } catch (_: Exception) { }
            }.start()
        } catch (_: Exception) { }
    }
}
