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

    /** Tegező párbeszéd: a gépi fordító magázó alakjait (Segítsen, Tud olvasni?) tegezőre javítja. */
    fun informalHu(ctx: Context): Boolean = p(ctx).getBoolean("subInformalHu", true)
    fun setInformalHu(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subInformalHu", v).apply() }

    /** Hallucináció-szűrő: ismétlődő mondatok és magányos, értelmetlen sorok kidobása. */
    /** A bizonytalan szakaszok pontosító második menete (lassabb, de kevesebb a felismerési hiba). */
    fun secondPass(ctx: Context): Boolean = p(ctx).getBoolean("subSecondPass", false)
    fun setSecondPass(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subSecondPass", v).apply() }

    fun hallucFilter(ctx: Context): Boolean = p(ctx).getBoolean("subHalluc", true)
    fun setHallucFilter(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subHalluc", v).apply() }

    /** Beszélőnként más szín az SRT-ben (<font color>). */
    fun speakerColors(ctx: Context): Boolean = p(ctx).getBoolean("subSpkColors", false)
    fun setSpeakerColors(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subSpkColors", v).apply() }

    /** Háttérfutás-kényszerítés (néma hangfolyam) – ha a telefon zárolt képernyőnél lefagyasztja az appot. */

    fun profile(ctx: Context): String = p(ctx).getString("subProfile", "custom") ?: "custom"
    fun setProfile(ctx: Context, id: String) { p(ctx).edit().putString("subProfile", id).apply() }
}

/** Online (LLM-es) fordítás beállításai. A kulcs csak az app saját, privát tárolójában van. */
object OnlinePrefs {
    const val OFF = 0      // csak offline (ML Kit)
    const val ONLINE = 1   // csak online; a sikertelen sorok forrásszövege marad
    const val AUTO = 2     // online, hiba esetén offline tartalék

    private fun p(ctx: Context) = Prefs(ctx).raw()

    fun mode(ctx: Context): Int = p(ctx).getInt("trMode", OFF)
    fun setMode(ctx: Context, v: Int) { p(ctx).edit().putInt("trMode", v).apply() }

    fun provider(ctx: Context): OnlineTranslator.Provider =
        if (p(ctx).getString("trProvider", "anthropic") == "openai") OnlineTranslator.Provider.OPENAI
        else OnlineTranslator.Provider.ANTHROPIC
    fun setProvider(ctx: Context, v: OnlineTranslator.Provider) {
        p(ctx).edit().putString("trProvider", if (v == OnlineTranslator.Provider.OPENAI) "openai" else "anthropic").apply()
    }

    fun apiKey(ctx: Context): String = p(ctx).getString("trKey", "") ?: ""
    fun setApiKey(ctx: Context, v: String) { p(ctx).edit().putString("trKey", v.trim()).apply() }

    fun model(ctx: Context): String = p(ctx).getString("trModel", "") ?: ""
    fun setModel(ctx: Context, v: String) { p(ctx).edit().putString("trModel", v.trim()).apply() }

    fun baseUrl(ctx: Context): String = p(ctx).getString("trBase", "") ?: ""
    fun setBaseUrl(ctx: Context, v: String) { p(ctx).edit().putString("trBase", v.trim()).apply() }

    /** Szabad szöveges háttér a fordítónak (téma, nevek). */
    fun hint(ctx: Context): String = p(ctx).getString("trHint", "") ?: ""
    fun setHint(ctx: Context, v: String) { p(ctx).edit().putString("trHint", v.trim()).apply() }

    /** A felhasználó tudomásul vette, hogy a felismert szöveg elhagyja a telefont. */
    fun consent(ctx: Context): Boolean = p(ctx).getBoolean("trConsent", false)
    fun setConsent(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("trConsent", v).apply() }

    /** Használható-e az online fordítás a jelenlegi beállításokkal? */
    fun usable(ctx: Context): Boolean {
        if (!consent(ctx)) return false
        val prov = provider(ctx)
        val keyOk = apiKey(ctx).isNotBlank() || (prov == OnlineTranslator.Provider.OPENAI && baseUrl(ctx).isNotBlank())
        val modelOk = model(ctx).isNotBlank() || prov == OnlineTranslator.Provider.ANTHROPIC
        return keyOk && modelOk
    }

    fun config(ctx: Context, srcName: String, tgtName: String): OnlineTranslator.Config? {
        if (!usable(ctx)) return null
        val prov = provider(ctx)
        val vocab = TextPolish.parseVocab(Prefs(ctx).customVocab)
        return OnlineTranslator.Config(
            provider = prov,
            apiKey = apiKey(ctx),
            model = model(ctx).ifBlank { OnlineTranslator.DEFAULT_ANTHROPIC_MODEL },
            baseUrl = baseUrl(ctx),
            srcLang = srcName,
            tgtLang = tgtName,
            informal = SubPrefs.informalHu(ctx),
            glossary = vocab,
            hint = listOf(hint(ctx), Projects.contextHint(ctx)).filter { it.isNotBlank() }.joinToString("\n")
        )
    }
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
