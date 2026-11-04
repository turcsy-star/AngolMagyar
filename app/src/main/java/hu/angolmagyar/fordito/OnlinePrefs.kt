package hu.angolmagyar.fordito

import android.content.Context

/**
 * Online szolgáltatók beállításai. A titkos kulcsok kizárólag SecretStore-on
 * keresztül, Android Keystore által titkosítva kerülnek a SharedPreferences-be.
 */
object OnlinePrefs {
    const val OFF = 0
    const val AUTO = 1
    private const val FILE = "online_translation"

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun keyName(provider: OnlineTranslator.Provider): String = when (provider) {
        OnlineTranslator.Provider.ANTHROPIC -> "key_anthropic"
        OnlineTranslator.Provider.OPENAI -> "key_openai"
        OnlineTranslator.Provider.DEEPL -> "key_deepl"
        OnlineTranslator.Provider.GEMINI -> "key_gemini"
        OnlineTranslator.Provider.MISTRAL -> "key_mistral"
    }

    fun provider(ctx: Context): OnlineTranslator.Provider =
        runCatching {
            OnlineTranslator.Provider.valueOf(
                prefs(ctx).getString("provider", OnlineTranslator.Provider.GEMINI.name)
                    ?: OnlineTranslator.Provider.GEMINI.name
            )
        }.getOrDefault(OnlineTranslator.Provider.GEMINI)

    fun setProvider(ctx: Context, provider: OnlineTranslator.Provider) {
        prefs(ctx).edit().putString("provider", provider.name).apply()
    }

    fun apiKey(ctx: Context): String = apiKeyFor(ctx, provider(ctx))

    fun apiKeyFor(ctx: Context, provider: OnlineTranslator.Provider): String =
        SecretStore.get(prefs(ctx), keyName(provider))

    fun setApiKey(ctx: Context, value: String): Boolean = setApiKeyFor(ctx, provider(ctx), value)

    fun setApiKeyFor(ctx: Context, provider: OnlineTranslator.Provider, value: String): Boolean =
        SecretStore.set(prefs(ctx), keyName(provider), value)

    fun model(ctx: Context): String =
        prefs(ctx).getString("model_${provider(ctx).name}", defaultModel(provider(ctx))) ?: defaultModel(provider(ctx))

    fun setModel(ctx: Context, value: String) {
        prefs(ctx).edit().putString("model_${provider(ctx).name}", value.trim()).apply()
    }

    fun baseUrl(ctx: Context): String = prefs(ctx).getString("baseUrl", "") ?: ""
    fun setBaseUrl(ctx: Context, value: String) { prefs(ctx).edit().putString("baseUrl", value.trim()).apply() }
    fun hint(ctx: Context): String = prefs(ctx).getString("hint", "") ?: ""
    fun setHint(ctx: Context, value: String) { prefs(ctx).edit().putString("hint", value.trim()).apply() }
    fun consent(ctx: Context): Boolean = prefs(ctx).getBoolean("onlineConsent", false)
    fun setConsent(ctx: Context, value: Boolean) { prefs(ctx).edit().putBoolean("onlineConsent", value).apply() }
    fun dictEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("onlineDictionary", false)
    fun setDictEnabled(ctx: Context, value: Boolean) { prefs(ctx).edit().putBoolean("onlineDictionary", value).apply() }
    fun mode(ctx: Context): Int = prefs(ctx).getInt("onlineMode", OFF)
    fun setMode(ctx: Context, value: Int) { prefs(ctx).edit().putInt("onlineMode", value.coerceIn(OFF, AUTO)).apply() }

    fun usable(ctx: Context): Boolean =
        provider(ctx) != OnlineTranslator.Provider.GEMINI && provider(ctx) != OnlineTranslator.Provider.MISTRAL ||
            apiKey(ctx).isNotBlank()

    fun missing(ctx: Context): List<String> = buildList {
        if (apiKey(ctx).isBlank()) add("API-kulcs")
        if (provider(ctx) == OnlineTranslator.Provider.ANTHROPIC && model(ctx).isBlank()) add("modell")
    }

    fun config(ctx: Context, srcLang: String, tgtLang: String): OnlineTranslator.Config? {
        val p = provider(ctx)
        val key = apiKeyFor(ctx, p)
        if (key.isBlank()) return null
        return OnlineTranslator.Config(
            provider = p,
            apiKey = key,
            model = model(ctx).ifBlank { defaultModel(p) },
            baseUrl = baseUrl(ctx),
            srcLang = srcLang,
            tgtLang = tgtLang,
            hint = hint(ctx)
        )
    }

    fun fallbackConfig(ctx: Context, srcLang: String, tgtLang: String): OnlineTranslator.Config? {
        val fallback = when (provider(ctx)) {
            OnlineTranslator.Provider.GEMINI -> OnlineTranslator.Provider.MISTRAL
            OnlineTranslator.Provider.MISTRAL -> OnlineTranslator.Provider.GEMINI
            else -> return null
        }
        val key = apiKeyFor(ctx, fallback)
        if (key.isBlank()) return null
        return OnlineTranslator.Config(
            provider = fallback,
            apiKey = key,
            model = defaultModel(fallback),
            baseUrl = if (fallback == OnlineTranslator.Provider.MISTRAL) OnlineTranslator.DEFAULT_MISTRAL_BASE else "",
            srcLang = srcLang,
            tgtLang = tgtLang,
            hint = hint(ctx)
        )
    }

    private fun defaultModel(provider: OnlineTranslator.Provider): String = when (provider) {
        OnlineTranslator.Provider.ANTHROPIC -> OnlineTranslator.DEFAULT_ANTHROPIC_MODEL
        OnlineTranslator.Provider.OPENAI -> "gpt-4o-mini"
        OnlineTranslator.Provider.DEEPL -> ""
        OnlineTranslator.Provider.GEMINI -> OnlineTranslator.DEFAULT_GEMINI_MODEL
        OnlineTranslator.Provider.MISTRAL -> OnlineTranslator.DEFAULT_MISTRAL_MODEL
    }
}
