package hu.angolmagyar.fordito

import android.content.Context

/** A feliratkészítés finomhangolható kapcsolói (a Prefs közös tárolójában). */
object SubPrefs {
    private fun p(ctx: Context) = Prefs(ctx).raw()

    /** Hosszú blokkok szétvágása, olvasható időzítés, kiegyensúlyozott sortörés. */
    fun reflow(ctx: Context): Boolean = p(ctx).getBoolean("subReflow", true)
    fun setReflow(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subReflow", v).apply() }

    /** Mondatokká összefogott (kontextusos) fordítás. */
    fun contextTranslate(ctx: Context): Boolean = p(ctx).getBoolean("subContextTr", true)
    fun setContextTranslate(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subContextTr", v).apply() }

    /** Beszélőnként más szín az SRT-ben (<font color>). */
    fun speakerColors(ctx: Context): Boolean = p(ctx).getBoolean("subSpkColors", false)
    fun setSpeakerColors(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subSpkColors", v).apply() }

    /** Háttérfutás-kényszerítés (néma hangfolyam) – ha a telefon zárolt képernyőnél lefagyasztja az appot. */
    fun keepAlive(ctx: Context): Boolean = p(ctx).getBoolean("subKeepAlive", true)
    fun setKeepAlive(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subKeepAlive", v).apply() }

    fun profile(ctx: Context): String = p(ctx).getString("subProfile", "custom") ?: "custom"
    fun setProfile(ctx: Context, id: String) { p(ctx).edit().putString("subProfile", id).apply() }
}

/** Feldolgozási profilok: egy érintéssel a teljesítmény, a Whisper-modell és a keresés pontossága. */
object Presets {
    data class P(
        val id: String, val label: String, val desc: String,
        val perf: Int, val beam: Int,
        /** "fast" = kisebb modell, "best" = a legjobb telepített, "keep" = a jelenlegi marad */
        val model: String
    )

    val ALL = listOf(
        P("draft", "Gyors vázlat", "kis modell (ha van), gyors keresés, maximum teljesítmény", 5, 1, "fast"),
        P("balanced", "Kiegyensúlyozott", "a kiválasztott modell, gyors keresés", 4, 1, "keep"),
        P("precise", "Pontos", "a legjobb telepített modell, pontosabb (lassabb) keresés", 4, 5, "best"),
        P("night", "Éjszakai", "pontos beállítás, takarékos CPU – töltőn hagyd", 2, 5, "best")
    )

    fun byId(id: String): P? = ALL.firstOrNull { it.id == id }

    /** A profil által használt Whisper-modell (telepítettek közül), vagy null, ha marad a jelenlegi. */
    fun modelFor(ctx: Context, p: P): ModelManager.Spec? {
        val inst = ModelManager.WHISPER.filter { ModelManager.isInstalled(ctx, it) }
        return when (p.model) {
            "fast" -> listOf(ModelManager.WH_SMALL, ModelManager.WH_BASE).firstOrNull { it in inst }
            "best" -> listOf(ModelManager.WH_LARGE, ModelManager.WH_TURBO, ModelManager.WH_MEDIUM, ModelManager.WH_SMALL)
                .firstOrNull { it in inst }
            else -> null
        }
    }

    fun apply(ctx: Context, id: String) {
        val p = byId(id) ?: return
        Perf.set(ctx, p.perf)
        AsrPrefs.setBeam(ctx, p.beam)
        modelFor(ctx, p)?.let { AsrPrefs.setWhisperId(ctx, it.id) }
        SubPrefs.setProfile(ctx, id)
    }

    /** Rövid leírás a képernyőre (a választott modell nevével). */
    fun describe(ctx: Context, p: P): String {
        val m = modelFor(ctx, p)
        val mt = if (m != null) " · modell: ${m.label.substringBefore("(").trim()}" else ""
        return "${p.desc}$mt"
    }
}
