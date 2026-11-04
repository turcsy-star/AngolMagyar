package hu.angolmagyar.fordito
class OnlineTranslator(private val cfg: Config, private val http: Http = UrlHttp) {
    enum class Provider { ANTHROPIC, OPENAI, DEEPL, GEMINI, MISTRAL }
    data class Config(
        val provider: Provider,
        val apiKey: String,
        val model: String,
        val baseUrl: String = "",
        val srcLang: String = "angol",
        val tgtLang: String = "magyar",
        val informal: Boolean = true,
        val glossary: List<Pair<String, String>> = emptyList(),
        val hint: String = "",
        val style: TranslationStyle = TranslationStyle.NATURAL,
        val batchSize: Int = 30,
        val timeoutMs: Int = 90_000
    )
    interface Http {
        fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): Pair<Int, String>
    }
    @Volatile var lastError: String = ""
        private set
    @Volatile var fatal: Boolean = false
        private set
    companion object {
        const val DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-5-5"
        const val DEFAULT_OPENAI_BASE = "https://api.openai.com/v1"
        const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        const val DEFAULT_MISTRAL_MODEL = "mistral-small-latest"
        const val DEFAULT_MISTRAL_BASE = "https://api.mistral.ai/v1"
        private const val CTX_BEFORE = 4
        private const val CTX_AFTER = 3
        fun systemPrompt(cfg: Config): String {
            val sb = StringBuilder()
            sb.append("You are a professional subtitle translator. Translate subtitle blocks from ${cfg.srcLang} to ${cfg.tgtLang}.\n")
            sb.append("Rules:\n")
            sb.append("- Translate faithfully and naturally, as the lines would be spoken; keep the register, slang and tone. Add no commentary and do not soften or censor.\n")
            sb.append("- Requested translation style: ").append(cfg.style.instruction).append("\n")
            sb.append("- Consecutive blocks are often fragments of one sentence. Use the neighbouring blocks (given as context) so the sentence reads naturally, but give each block only the translation of its own part, short enough for a subtitle.\n")
            sb.append("- Do not translate or alter personal names; keep interjections short (Oh → Ó, Yeah → Igen).\n")
            if (cfg.informal && cfg.tgtLang.equals("magyar", ignoreCase = true))
                sb.append("- Use informal address (tegezés: te, -sz, -d forms) unless the speakers are clearly formal with each other.\n")
            if (cfg.glossary.isNotEmpty()) {
                sb.append("- Always use these translations:\n")
                for ((a, b) in cfg.glossary.take(60)) sb.append("    ").append(a).append(" => ").append(b).append('\n')
            }
            if (cfg.hint.isNotBlank()) sb.append("- Background: ").append(cfg.hint.trim()).append('\n')
            sb.append("Input is a JSON object: \"translate\" = blocks to translate ({\"id\", \"text\"}), \"before\" / \"after\" = neighbouring blocks for context only.\n")
            sb.append("Output ONLY a JSON array of strings: the translations of the \"translate\" blocks, same count and same order. No other text.")
            return sb.toString()
        }
        fun userPayload(en: List<String>, hu: List<String>, batch: List<Int>): String {
            val first = batch.first()
            val last = batch.last()
            val sb = StringBuilder("{\"before\":[")
            val b0 = (first - CTX_BEFORE).coerceAtLeast(0)
            for (i in b0 until first) {
                if (i > b0) sb.append(',')
                sb.append("{\"text\":").append(MiniJson.quote(en[i]))
                if (hu[i].isNotBlank()) sb.append(",\"translation\":").append(MiniJson.quote(hu[i]))
                sb.append('}')
            }
            sb.append("],\"translate\":[")
            for ((k, i) in batch.withIndex()) {
                if (k > 0) sb.append(',')
                sb.append("{\"id\":").append(k).append(",\"text\":").append(MiniJson.quote(en[i])).append('}')
            }
            sb.append("],\"after\":[")
            val a1 = (last + 1 + CTX_AFTER).coerceAtMost(en.size)
            for (i in last + 1 until a1) {
                if (i > last + 1) sb.append(',')
                sb.append(MiniJson.quote(en[i]))
            }
            sb.append("]}")
            return sb.toString()
        }
        fun parseTranslations(text: String, expected: Int): List<String>? {
            val arrText = MiniJson.extractArray(text) ?: return null
            val v = try { MiniJson.parse(arrText) } catch (_: Exception) { return null }
            val list = v as? List<*> ?: return null
            if (list.size != expected) return null
            val out = ArrayList<String>(expected)
            for (x in list) {
                when (x) {
                    is String -> out.add(x.trim())
                    is Map<*, *> -> out.add((x["text"] ?: x["translation"])?.toString()?.trim() ?: return null)
                    else -> return null
                }
            }
            return out
        }
        fun extractText(provider: Provider, body: String): String? {
            val root = try { MiniJson.parse(body) } catch (_: Exception) { return null } as? Map<*, *> ?: return null
            return when (provider) {
                Provider.ANTHROPIC -> {
                    val parts = root["content"] as? List<*> ?: return null
                    parts.filterIsInstance<Map<*, *>>().filter { it["type"] == "text" }
                        .joinToString("") { it["text"]?.toString() ?: "" }.ifBlank { null }
                }
                Provider.DEEPL -> body
                Provider.OPENAI, Provider.MISTRAL -> {
                    val ch = (root["choices"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return null
                    ((ch["message"] as? Map<*, *>)?.get("content") as? String)?.ifBlank { null }
                }
                Provider.GEMINI -> {
                    val candidate = (root["candidates"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return null
                    val content = candidate["content"] as? Map<*, *> ?: return null
                    val parts = content["parts"] as? List<*> ?: return null
                    parts.filterIsInstance<Map<*, *>>().joinToString("") { it["text"]?.toString() ?: "" }.ifBlank { null }
                }
            }
        }
        private val DEEPL_SOURCE = setOf(
            "AR", "BG", "CS", "DA", "DE", "EL", "EN", "ES", "ET", "FI", "FR", "HU", "ID", "IT", "JA", "KO", "LT",
            "LV", "NB", "NL", "PL", "PT", "RO", "RU", "SK", "SL", "SV", "TR", "UK", "ZH"
        )
        fun deeplCode(name: String, target: Boolean): String? {
            val n = name.trim().lowercase()
            val code = LangCatalog.ALL.firstOrNull { it.second == n || it.first == n }?.first
                ?: (if (n.length in 2..3) n else return null)
            val up = LangCatalog.norm(code).uppercase().let { if (it == "NO") "NB" else it }
            if (up !in DEEPL_SOURCE) return null
            if (!target) return up
            return when (up) { "EN" -> "EN-GB"; "PT" -> "PT-PT"; "ZH" -> "ZH-HANS"; else -> up }
        }
        fun deeplEndpoint(apiKey: String, baseUrl: String): String =
            (baseUrl.ifBlank { if (apiKey.trim().endsWith(":fx")) "https://api-free.deepl.com" else "https://api.deepl.com" })
                .trimEnd('/') + "/v2/translate"
        fun deeplRequest(texts: List<String>, srcCode: String?, tgtCode: String, context: String, informal: Boolean): String {
            val sb = StringBuilder("{\"text\":[")
            for ((i, t) in texts.withIndex()) { if (i > 0) sb.append(','); sb.append(MiniJson.quote(t)) }
            sb.append("],\"target_lang\":").append(MiniJson.quote(tgtCode))
            if (srcCode != null) sb.append(",\"source_lang\":").append(MiniJson.quote(srcCode))
            if (context.isNotBlank()) sb.append(",\"context\":").append(MiniJson.quote(context.take(1500)))
            if (informal) sb.append(",\"formality\":\"prefer_less\"")
            sb.append(",\"split_sentences\":\"nonumerics\"}")
            return sb.toString()
        }
        fun parseDeepL(body: String, expected: Int): List<String>? {
            val root = try { MiniJson.parse(body) } catch (_: Exception) { return null } as? Map<*, *> ?: return null
            val list = root["translations"] as? List<*> ?: return null
            if (list.size != expected) return null
            val out = ArrayList<String>(expected)
            for (x in list) out.add(((x as? Map<*, *>)?.get("text") as? String)?.trim() ?: return null)
            return out
        }
        fun deeplContext(hint: String, en: List<String>, first: Int, last: Int): String {
            val sb = StringBuilder()
            if (hint.isNotBlank()) sb.append(hint.trim()).append('\n')
            val b0 = (first - CTX_BEFORE).coerceAtLeast(0)
            val before = (b0 until first).map { en[it] }.filter { it.isNotBlank() }
            val a1 = (last + 1 + CTX_AFTER).coerceAtMost(en.size)
            val after = (last + 1 until a1).map { en[it] }.filter { it.isNotBlank() }
            if (before.isNotEmpty()) sb.append(before.joinToString(" ")).append(" … ")
            if (after.isNotEmpty()) sb.append(" … ").append(after.joinToString(" "))
            return sb.toString().trim()
        }
        /** Érthető magyar hibaüzenet hálózati kivételekből. */
        fun netErrorText(e: Throwable): String = when (e) {
            is java.net.UnknownHostException ->
                "Nincs internetkapcsolat, vagy a szolgáltató neve nem oldható fel (${e.message ?: "DNS"})."
            is java.net.SocketTimeoutException -> "Időtúllépés: lassú vagy megszakadó a hálózat."
            is javax.net.ssl.SSLException ->
                "Biztonságos kapcsolat (TLS) hiba – ellenőrizd a telefon dátumát/óráját és a hálózatot (${e.message ?: "SSL"})."
            is java.net.ConnectException -> "Nem sikerült kapcsolódni a szolgáltatóhoz (${e.message ?: "kapcsolódási hiba"})."
            is java.io.IOException -> "Hálózati hiba: ${e.message ?: e.javaClass.simpleName}"
            else -> "Hálózati hiba: ${e.message ?: e.javaClass.simpleName}"
        }
        fun errorMessage(body: String): String {
            val root = try { MiniJson.parse(body) } catch (_: Exception) { return body.take(160) } as? Map<*, *> ?: return body.take(160)
            (root["message"] as? String)?.let { if (root["error"] == null) return it }
            val e = root["error"]
            return when (e) {
                is Map<*, *> -> e["message"]?.toString() ?: body.take(160)
                is String -> e
                else -> body.take(160)
            }
        }
    }
    private fun endpoint(): String = when (cfg.provider) {
        Provider.ANTHROPIC -> (cfg.baseUrl.ifBlank { "https://api.anthropic.com" }).trimEnd('/') + "/v1/messages"
        Provider.OPENAI -> (cfg.baseUrl.ifBlank { DEFAULT_OPENAI_BASE }).trimEnd('/') + "/chat/completions"
        Provider.DEEPL -> deeplEndpoint(cfg.apiKey, cfg.baseUrl)
        Provider.GEMINI -> "https://generativelanguage.googleapis.com/v1beta/models/${cfg.model.ifBlank { DEFAULT_GEMINI_MODEL }}:generateContent"
        Provider.MISTRAL -> (cfg.baseUrl.ifBlank { DEFAULT_MISTRAL_BASE }).trimEnd('/') + "/chat/completions"
    }
    private fun requestBody(system: String, user: String): String = when (cfg.provider) {
        Provider.ANTHROPIC ->
            "{\"model\":${MiniJson.quote(cfg.model)},\"max_tokens\":4096,\"system\":${MiniJson.quote(system)}," +
                "\"messages\":[{\"role\":\"user\",\"content\":${MiniJson.quote(user)}}]}"
        Provider.DEEPL -> user
        Provider.OPENAI, Provider.MISTRAL ->
            "{\"model\":${MiniJson.quote(cfg.model)},\"temperature\":0.2,\"messages\":[" +
                "{\"role\":\"system\",\"content\":${MiniJson.quote(system)}}," +
                "{\"role\":\"user\",\"content\":${MiniJson.quote(user)}}]}"
        Provider.GEMINI ->
            "{\"systemInstruction\":{\"parts\":[{\"text\":${MiniJson.quote(system)}}]}," +
                "\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":${MiniJson.quote(user)}}]}]," +
                "\"generationConfig\":{\"temperature\":0.2,\"maxOutputTokens\":4096,\"responseMimeType\":\"application/json\"}}"
    }
    private fun headers(): Map<String, String> = when (cfg.provider) {
        Provider.ANTHROPIC -> mapOf(
            "content-type" to "application/json", "x-api-key" to cfg.apiKey, "anthropic-version" to "2023-06-01"
        )
        Provider.DEEPL -> mapOf(
            "content-type" to "application/json", "authorization" to "DeepL-Auth-Key ${cfg.apiKey.trim()}"
        )
        Provider.OPENAI, Provider.MISTRAL -> {
            val h = LinkedHashMap<String, String>()
            h["content-type"] = "application/json"
            if (cfg.apiKey.isNotBlank()) h["authorization"] = "Bearer ${cfg.apiKey}"
            h
        }
        Provider.GEMINI -> mapOf("content-type" to "application/json", "x-goog-api-key" to cfg.apiKey)
    }
    private var netDown = 0
    private fun call(system: String, user: String, cancel: () -> Boolean): String? {
        var attempt = 0
        var netError: Throwable? = null
        while (attempt < 4 && !cancel()) {
            attempt++
            try {
                val (code, body) = http.post(endpoint(), headers(), requestBody(system, user), cfg.timeoutMs)
                netError = null
                netDown = 0
                when {
                    code in 200..299 -> {
                        val t = extractText(cfg.provider, body)
                        if (t != null) return t
                        lastError = "Értelmezhetetlen válasz a szolgáltatótól."
                        return null
                    }
                    code == 401 || code == 403 -> {
                        lastError = "Az API-kulcs érvénytelen vagy nincs jogosultsága (HTTP $code): ${errorMessage(body)}"
                        fatal = true
                        return null
                    }
                    code == 400 || code == 404 -> {
                        lastError = "Hibás kérés / modellnév (HTTP $code): ${errorMessage(body)}"
                        fatal = true
                        return null
                    }
                    code == 456 -> {
                        lastError = "A DeepL havi keret elfogyott (HTTP 456)."
                        fatal = true
                        return null
                    }
                    code == 429 -> {
                        // Kvóta-/sebességkorlát esetén az ismétlés csak késlelteti a tartalék szolgáltatót.
                        lastError = "A szolgáltató kvótája vagy sebességkorlátja elérve (HTTP 429)."
                        fatal = true
                        return null
                    }
                    code >= 500 -> {
                        lastError = "A szolgáltató átmenetileg nem elérhető (HTTP $code)."
                        if (attempt < 4) {
                            try { Thread.sleep(1500L * attempt) } catch (_: InterruptedException) { return null }
                        } else {
                            fatal = true
                            return null
                        }
                    }
                    else -> { lastError = "HTTP $code: ${errorMessage(body)}"; return null }
                }
            } catch (e: Exception) {
                netError = e
                lastError = netErrorText(e)
                if (attempt < 4) {
                    try { Thread.sleep(1000L * attempt) } catch (_: InterruptedException) { return null }
                }
            }
        }
        // Két egymás utáni, teljesen sikertelen (hálózati) hívás után nem várunk tovább: az offline tartalék azonnal indulhat.
        if (netError != null && ++netDown >= 2) {
            lastError = "Nincs kapcsolat a szolgáltatóhoz – ${lastError}"
            fatal = true
        }
        return null
    }
    fun translate(
        en: List<String>, hu: MutableList<String>, indices: List<Int>,
        onDone: (done: Int, total: Int) -> Unit = { _, _ -> },
        cancel: () -> Boolean = { false }
    ): List<Int> {
        lastError = ""
        fatal = false
        netDown = 0
        var failed = pass(en, hu, indices, cfg.batchSize, onDone, cancel)
        // Átmeneti hiba (megszakadó net, túl hosszú válasz) esetén a hibás sorokat kisebb csomagokban még egyszer megpróbáljuk.
        if (failed.isNotEmpty() && !fatal && !cancel()) {
            try { Thread.sleep(2000) } catch (_: InterruptedException) { return failed }
            val firstError = lastError
            val again = pass(en, hu, failed, (cfg.batchSize / 3).coerceIn(3, 10), { _, _ -> }, cancel)
            lastError = if (again.isEmpty()) "" else lastError.ifBlank { firstError }
            failed = again
            onDone(indices.size, indices.size)
        }
        return failed
    }
    private fun pass(
        en: List<String>, hu: MutableList<String>, indices: List<Int>, batchSize: Int,
        onDone: (done: Int, total: Int) -> Unit, cancel: () -> Boolean
    ): List<Int> =
        if (cfg.provider == Provider.DEEPL) translateDeepL(en, hu, indices, batchSize, onDone, cancel)
        else translateLlm(en, hu, indices, batchSize, onDone, cancel)
    private fun translateLlm(
        en: List<String>, hu: MutableList<String>, indices: List<Int>, batchSize: Int,
        onDone: (done: Int, total: Int) -> Unit, cancel: () -> Boolean
    ): List<Int> {
        val failed = ArrayList<Int>()
        val system = systemPrompt(cfg)
        var done = 0
        onDone(0, indices.size)
        val batches = indices.chunked(batchSize.coerceAtLeast(1))
        for ((bi, batch) in batches.withIndex()) {
            if (cancel()) { failed.addAll(batches.drop(bi).flatten()); break }
            if (fatal) { failed.addAll(batch); continue }
            val real = batch.filter { en[it].isNotBlank() }
            if (real.isEmpty()) { done += batch.size; onDone(done, indices.size); continue }
            var res: List<String>? = null
            var tries = 0
            while (res == null && tries < 2 && !fatal && !cancel()) {
                tries++
                val text = call(system, userPayload(en, hu, real), cancel) ?: break
                res = parseTranslations(text, real.size)
                if (res == null) lastError = "A válasz nem a kért JSON-tömb volt."
            }
            if (res != null) {
                for ((k, i) in real.withIndex()) hu[i] = res[k]
            } else {
                failed.addAll(real)
            }
            done += batch.size
            onDone(done, indices.size)
        }
        return failed
    }
    private fun translateDeepL(
        en: List<String>, hu: MutableList<String>, indices: List<Int>, batchSize: Int,
        onDone: (done: Int, total: Int) -> Unit, cancel: () -> Boolean
    ): List<Int> {
        val failed = ArrayList<Int>()
        val tgt = deeplCode(cfg.tgtLang, true)
        if (tgt == null) {
            lastError = "A DeepL nem támogatja a célnyelvet (${cfg.tgtLang})."
            fatal = true
            return indices
        }
        val src = deeplCode(cfg.srcLang, false)
        var done = 0
        onDone(0, indices.size)
        val batches = indices.chunked(batchSize.coerceIn(1, 10))
        for ((bi, batch) in batches.withIndex()) {
            if (cancel()) { failed.addAll(batches.drop(bi).flatten()); break }
            if (fatal) { failed.addAll(batch); continue }
            val real = batch.filter { en[it].isNotBlank() }
            if (real.isNotEmpty()) {
                val ctx = deeplContext(cfg.hint, en, real.first(), real.last())
                val body = deeplRequest(real.map { en[it] }, src, tgt, ctx, cfg.informal)
                var res: List<String>? = null
                val text = call("", body, cancel)
                if (text != null) {
                    res = parseDeepL(text, real.size)
                    if (res == null) lastError = "A DeepL válasza nem értelmezhető."
                }
                if (res != null) for ((k, i) in real.withIndex()) hu[i] = res[k] else failed.addAll(real)
            }
            done += batch.size
            onDone(done, indices.size)
        }
        return failed
    }
    fun test(): String? {
        val hu = mutableListOf("")
        val failed = translate(listOf("Hello, how are you today?"), hu, listOf(0))
        return if (failed.isEmpty() && hu[0].isNotBlank()) null else lastError.ifBlank { "Nem érkezett fordítás." }
    }
}
object UrlHttp : OnlineTranslator.Http {
    override fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): Pair<Int, String> {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 20_000
            c.readTimeout = timeoutMs
            c.doOutput = true
            for ((k, v) in headers) c.setRequestProperty(k, v)
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)
            val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return code to text
        } finally {
            c.disconnect()
        }
    }
}
