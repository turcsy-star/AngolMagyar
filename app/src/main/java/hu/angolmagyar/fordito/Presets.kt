package hu.angolmagyar.fordito
import android.content.Context
object SubPrefs {
    private fun p(ctx: Context) = Prefs(ctx).raw()
    fun reflow(ctx: Context): Boolean = p(ctx).getBoolean("subReflow", true)
    fun setReflow(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subReflow", v).apply() }
    fun contextTranslate(ctx: Context): Boolean = p(ctx).getBoolean("subContextTr", true)
    fun setContextTranslate(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subContextTr", v).apply() }
    fun informalHu(ctx: Context): Boolean = p(ctx).getBoolean("subInformalHu", true)
    fun setInformalHu(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subInformalHu", v).apply() }
    fun secondPass(ctx: Context): Boolean = p(ctx).getBoolean("subSecondPass", true)
    fun setSecondPass(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subSecondPass", v).apply() }
    fun hallucFilter(ctx: Context): Boolean = p(ctx).getBoolean("subHalluc", true)
    fun setHallucFilter(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subHalluc", v).apply() }
    fun consistency(ctx: Context): Boolean = p(ctx).getBoolean("subConsistency", true)
    fun setConsistency(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subConsistency", v).apply() }
    fun dialogueDash(ctx: Context): Boolean = p(ctx).getBoolean("subDialogDash", false)
    fun setDialogueDash(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subDialogDash", v).apply() }
    fun speakerColors(ctx: Context): Boolean = p(ctx).getBoolean("subSpkColors", false)
    fun setSpeakerColors(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subSpkColors", v).apply() }
    fun audioCondition(ctx: Context): Boolean = p(ctx).getBoolean("subAudioCond", true)
    fun setAudioCondition(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subAudioCond", v).apply() }
    fun huOrtho(ctx: Context): Boolean = p(ctx).getBoolean("subHuOrtho", true)
    fun setHuOrtho(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("subHuOrtho", v).apply() }
    fun profile(ctx: Context): String = p(ctx).getString("subProfile", "precise") ?: "precise"
    fun setProfile(ctx: Context, id: String) { p(ctx).edit().putString("subProfile", id).apply() }
}
object OnlinePrefs {
    const val OFF = 0
    const val ONLINE = 1
    const val AUTO = 2
    private fun p(ctx: Context) = Prefs(ctx).raw()
    private fun keyName(provider: OnlineTranslator.Provider): String = "trKey_${provider.name.lowercase()}"

    fun mode(ctx: Context): Int = p(ctx).getInt("trMode", AUTO).coerceIn(OFF, AUTO)
    fun setMode(ctx: Context, v: Int) { p(ctx).edit().putInt("trMode", v.coerceIn(OFF, AUTO)).apply() }
    fun translationStyle(ctx: Context): TranslationStyle =
        TranslationStyle.fromStorage(p(ctx).getString("trStyle", TranslationStyle.NATURAL.storageKey))
    fun setTranslationStyle(ctx: Context, style: TranslationStyle) {
        p(ctx).edit().putString("trStyle", style.storageKey).apply()
    }

    fun provider(ctx: Context): OnlineTranslator.Provider =
        when (p(ctx).getString("trProvider", "gemini")) {
            "gemini" -> OnlineTranslator.Provider.GEMINI
            "mistral" -> OnlineTranslator.Provider.MISTRAL
            "openai" -> OnlineTranslator.Provider.OPENAI
            "deepl" -> OnlineTranslator.Provider.DEEPL
            else -> OnlineTranslator.Provider.ANTHROPIC
        }

    fun setProvider(ctx: Context, v: OnlineTranslator.Provider) {
        val value = when (v) {
            OnlineTranslator.Provider.GEMINI -> "gemini"
            OnlineTranslator.Provider.MISTRAL -> "mistral"
            OnlineTranslator.Provider.OPENAI -> "openai"
            OnlineTranslator.Provider.DEEPL -> "deepl"
            OnlineTranslator.Provider.ANTHROPIC -> "anthropic"
        }
        p(ctx).edit().putString("trProvider", value).apply()
    }

    fun apiKey(ctx: Context): String = apiKeyFor(ctx, provider(ctx))
    fun apiKeyFor(ctx: Context, provider: OnlineTranslator.Provider): String {
        val prefs = p(ctx)
        val saved = SecretStore.get(prefs, keyName(provider))
        if (saved.isNotBlank()) return saved
        // A korábbi verzió egyetlen trKey mezőt használt; első olvasáskor migráljuk a kiválasztott szolgáltatóhoz.
        if (provider == provider(ctx)) {
            val legacy = SecretStore.get(prefs, "trKey")
            if (legacy.isNotBlank()) {
                SecretStore.set(prefs, keyName(provider), legacy)
                SecretStore.set(prefs, "trKey", "")
                return legacy
            }
        }
        return ""
    }
    fun setApiKey(ctx: Context, v: String): Boolean = setApiKeyFor(ctx, provider(ctx), v)
    fun setApiKeyFor(ctx: Context, provider: OnlineTranslator.Provider, v: String): Boolean =
        SecretStore.set(p(ctx), keyName(provider), v)

    fun model(ctx: Context): String {
        val prov = provider(ctx)
        val stored = p(ctx).getString("trModel_${prov.name.lowercase()}", null)
            ?: p(ctx).getString("trModel", "") ?: ""
        return stored.ifBlank {
            when (prov) {
                OnlineTranslator.Provider.GEMINI -> OnlineTranslator.DEFAULT_GEMINI_MODEL
                OnlineTranslator.Provider.MISTRAL -> OnlineTranslator.DEFAULT_MISTRAL_MODEL
                OnlineTranslator.Provider.OPENAI -> ""
                OnlineTranslator.Provider.ANTHROPIC -> OnlineTranslator.DEFAULT_ANTHROPIC_MODEL
                OnlineTranslator.Provider.DEEPL -> ""
            }
        }
    }
    fun setModel(ctx: Context, v: String) {
        p(ctx).edit().putString("trModel_${provider(ctx).name.lowercase()}", v.trim()).apply()
    }
    fun baseUrl(ctx: Context): String = p(ctx).getString("trBase", "") ?: ""
    fun setBaseUrl(ctx: Context, v: String) { p(ctx).edit().putString("trBase", v.trim()).apply() }
    fun hint(ctx: Context): String = p(ctx).getString("trHint", "") ?: ""
    fun setHint(ctx: Context, v: String) { p(ctx).edit().putString("trHint", v.trim()).apply() }
    fun consent(ctx: Context): Boolean = p(ctx).getBoolean("trConsent", false)
    fun setConsent(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("trConsent", v).apply() }
    fun dictEnabled(ctx: Context): Boolean = p(ctx).getBoolean("onlineDict", false)
    fun setDictEnabled(ctx: Context, v: Boolean) { p(ctx).edit().putBoolean("onlineDict", v).apply() }

    fun missing(ctx: Context): List<String> {
        val out = ArrayList<String>()
        val prov = provider(ctx)
        if (!EndpointPolicy.isAllowed(baseUrl(ctx)))
            out.add("biztonságos végpont (távoli szerverhez HTTPS kell; HTTP csak helyi hálózaton engedélyezett)")
        if (apiKey(ctx).isBlank() && !(prov == OnlineTranslator.Provider.OPENAI && baseUrl(ctx).isNotBlank()))
            out.add("API-kulcs")
        if (prov == OnlineTranslator.Provider.OPENAI && model(ctx).isBlank()) out.add("modellnév")
        if (!consent(ctx)) out.add("tudomásulvétel (kapcsoló)")
        return out
    }

    fun usable(ctx: Context): Boolean {
        if (!consent(ctx) || !EndpointPolicy.isAllowed(baseUrl(ctx))) return false
        val prov = provider(ctx)
        val keyOk = apiKey(ctx).isNotBlank() || (prov == OnlineTranslator.Provider.OPENAI && baseUrl(ctx).isNotBlank())
        val modelOk = model(ctx).isNotBlank() || prov == OnlineTranslator.Provider.DEEPL
        return keyOk && modelOk
    }

    private fun configFor(ctx: Context, prov: OnlineTranslator.Provider, srcName: String, tgtName: String): OnlineTranslator.Config? {
        val key = apiKeyFor(ctx, prov)
        if (key.isBlank()) return null
        val vocab = TextPolish.parseVocab(Prefs(ctx).customVocab)
        val modelName = when (prov) {
            OnlineTranslator.Provider.GEMINI -> OnlineTranslator.DEFAULT_GEMINI_MODEL
            OnlineTranslator.Provider.MISTRAL -> OnlineTranslator.DEFAULT_MISTRAL_MODEL
            OnlineTranslator.Provider.ANTHROPIC -> OnlineTranslator.DEFAULT_ANTHROPIC_MODEL
            else -> model(ctx)
        }
        val endpoint = when (prov) {
            OnlineTranslator.Provider.MISTRAL -> baseUrl(ctx).ifBlank { OnlineTranslator.DEFAULT_MISTRAL_BASE }
            else -> baseUrl(ctx)
        }
        return OnlineTranslator.Config(
            provider = prov,
            apiKey = key,
            model = modelName,
            baseUrl = endpoint,
            srcLang = srcName,
            tgtLang = tgtName,
            informal = SubPrefs.informalHu(ctx),
            glossary = vocab,
            hint = listOf(hint(ctx), Projects.contextHint(ctx)).filter { it.isNotBlank() }.joinToString("\n"),
            style = translationStyle(ctx)
        )
    }

    fun config(ctx: Context, srcName: String, tgtName: String): OnlineTranslator.Config? {
        if (!usable(ctx)) return null
        return configFor(ctx, provider(ctx), srcName, tgtName)
    }

    fun fallbackConfig(ctx: Context, srcName: String, tgtName: String): OnlineTranslator.Config? {
        if (!consent(ctx)) return null
        val fallback = when (provider(ctx)) {
            OnlineTranslator.Provider.GEMINI -> OnlineTranslator.Provider.MISTRAL
            OnlineTranslator.Provider.MISTRAL -> OnlineTranslator.Provider.GEMINI
            else -> return null
        }
        return configFor(ctx, fallback, srcName, tgtName)
    }
}
object Presets {
    data class P(
        val id: String, val label: String, val desc: String,
        val perf: Int, val beam: Int,
        val model: String
    )
    val ALL = listOf(
        P("precise", "Pontos", "large-v3 modell, pontosabb (lassabb) keresés", 4, 5, "best")
    )
    fun byId(id: String): P? = ALL.firstOrNull { it.id == id }
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
    fun describe(ctx: Context, p: P): String {
        val m = modelFor(ctx, p)
        val mt = if (m != null) " · modell: ${m.label.substringBefore("(").trim()}" else ""
        return "${p.desc}$mt"
    }
}
