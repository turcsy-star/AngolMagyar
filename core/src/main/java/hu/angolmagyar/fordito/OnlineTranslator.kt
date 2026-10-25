package hu.angolmagyar.fordito

/**
 * Online (nagy nyelvi modellel végzett) felirat-fordítás. A blokkokat kötegekben küldi, a környező
 * sorokat csak kontextusként adja mellé, így a mondat közepén vágott blokkok is természetesen fordulnak.
 * Két szolgáltatói forma: Anthropic Messages API és OpenAI-kompatibilis (felhő vagy saját / helyi szerver).
 * Hibánál nem dob kivételt: a sikertelen sorok indexét adja vissza, a hívó (offline) tartalékra válthat.
 */
class OnlineTranslator(private val cfg: Config, private val http: Http = UrlHttp) {

    enum class Provider { ANTHROPIC, OPENAI }

    data class Config(
        val provider: Provider,
        val apiKey: String,
        val model: String,
        val baseUrl: String = "",
        val srcLang: String = "angol",
        val tgtLang: String = "magyar",
        val informal: Boolean = true,
        /** forrásszó → kötelező fordítás */
        val glossary: List<Pair<String, String>> = emptyList(),
        /** szabad szöveges háttér: téma, szereplők nevei stb. */
        val hint: String = "",
        val batchSize: Int = 30,
        val timeoutMs: Int = 90_000
    )

    interface Http {
        /** @return (HTTP-állapotkód, válasz-törzs); hálózati hibánál kivételt dob */
        fun post(url: String, headers: Map<String, String>, body: String, timeoutMs: Int): Pair<Int, String>
    }

    /** Az utolsó hiba (felhasználónak mutatható), üres, ha nem volt. */
    @Volatile var lastError: String = ""
        private set

    /** Végzetes hiba (pl. rossz kulcs): nincs értelme tovább próbálkozni. */
    @Volatile var fatal: Boolean = false
        private set

    companion object {
        const val DEFAULT_ANTHROPIC_MODEL = "claude-sonnet-5-5"
        const val DEFAULT_OPENAI_BASE = "https://api.openai.com/v1"
        private const val CTX_BEFORE = 4
        private const val CTX_AFTER = 3

        fun systemPrompt(cfg: Config): String {
            val sb = StringBuilder()
            sb.append("You are a professional subtitle translator. Translate subtitle blocks from ${cfg.srcLang} to ${cfg.tgtLang}.\n")
            sb.append("Rules:\n")
            sb.append("- Translate faithfully and naturally, as the lines would be spoken; keep the register, slang and tone. Add no commentary and do not soften or censor.\n")
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

        /** A modell szövegéből a fordítások listája; null, ha nem értelmezhető vagy a darabszám nem stimmel. */
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

        /** Válasz-törzsből a modell szövege (provider szerint). */
        fun extractText(provider: Provider, body: String): String? {
            val root = try { MiniJson.parse(body) } catch (_: Exception) { return null } as? Map<*, *> ?: return null
            return when (provider) {
                Provider.ANTHROPIC -> {
                    val parts = root["content"] as? List<*> ?: return null
                    parts.filterIsInstance<Map<*, *>>().filter { it["type"] == "text" }
                        .joinToString("") { it["text"]?.toString() ?: "" }.ifBlank { null }
                }
                Provider.OPENAI -> {
                    val ch = (root["choices"] as? List<*>)?.firstOrNull() as? Map<*, *> ?: return null
                    ((ch["message"] as? Map<*, *>)?.get("content") as? String)?.ifBlank { null }
                }
            }
        }

        fun errorMessage(body: String): String {
            val root = try { MiniJson.parse(body) } catch (_: Exception) { return body.take(160) } as? Map<*, *> ?: return body.take(160)
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
    }

    private fun requestBody(system: String, user: String): String = when (cfg.provider) {
        Provider.ANTHROPIC ->
            "{\"model\":${MiniJson.quote(cfg.model)},\"max_tokens\":4096,\"system\":${MiniJson.quote(system)}," +
                "\"messages\":[{\"role\":\"user\",\"content\":${MiniJson.quote(user)}}]}"
        Provider.OPENAI ->
            "{\"model\":${MiniJson.quote(cfg.model)},\"temperature\":0.2,\"messages\":[" +
                "{\"role\":\"system\",\"content\":${MiniJson.quote(system)}}," +
                "{\"role\":\"user\",\"content\":${MiniJson.quote(user)}}]}"
    }

    private fun headers(): Map<String, String> = when (cfg.provider) {
        Provider.ANTHROPIC -> mapOf(
            "content-type" to "application/json", "x-api-key" to cfg.apiKey, "anthropic-version" to "2023-06-01"
        )
        Provider.OPENAI -> {
            val h = LinkedHashMap<String, String>()
            h["content-type"] = "application/json"
            if (cfg.apiKey.isNotBlank()) h["authorization"] = "Bearer ${cfg.apiKey}"
            h
        }
    }

    /** Egy kérés (újrapróbálkozással). @return a modell szövege, vagy null (lastError / fatal beállítva) */
    private fun call(system: String, user: String, cancel: () -> Boolean): String? {
        var attempt = 0
        while (attempt < 3 && !cancel()) {
            attempt++
            try {
                val (code, body) = http.post(endpoint(), headers(), requestBody(system, user), cfg.timeoutMs)
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
                    code == 429 || code >= 500 -> {
                        lastError = "A szolgáltató elfoglalt (HTTP $code), újrapróbálom…"
                        try { Thread.sleep(1500L * attempt) } catch (_: InterruptedException) { return null }
                    }
                    else -> { lastError = "HTTP $code: ${errorMessage(body)}"; return null }
                }
            } catch (e: Exception) {
                lastError = "Hálózati hiba: ${e.message ?: e.javaClass.simpleName}"
                try { Thread.sleep(1000L * attempt) } catch (_: InterruptedException) { return null }
            }
        }
        return null
    }

    /**
     * A [indices] sorok fordítása; a siker a [hu] listában jelenik meg (a környező sorok fordítása kontextus).
     * @return a sikertelen sorok indexei (üres = minden rendben)
     */
    fun translate(
        en: List<String>, hu: MutableList<String>, indices: List<Int>,
        onDone: (done: Int, total: Int) -> Unit = { _, _ -> },
        cancel: () -> Boolean = { false }
    ): List<Int> {
        lastError = ""
        fatal = false
        val failed = ArrayList<Int>()
        val system = systemPrompt(cfg)
        var done = 0
        onDone(0, indices.size)
        val batches = indices.chunked(cfg.batchSize.coerceAtLeast(1))
        for ((bi, batch) in batches.withIndex()) {
            if (cancel()) { failed.addAll(batches.drop(bi).flatten()); break }
            if (fatal) { failed.addAll(batch); continue }
            // az üres forrássort nem küldjük
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

    /** Kapcsolat- és kulcsellenőrzés: egy rövid mondat fordítása. @return null = rendben, különben a hiba szövege */
    fun test(): String? {
        val hu = mutableListOf("")
        val failed = translate(listOf("Hello, how are you today?"), hu, listOf(0))
        return if (failed.isEmpty() && hu[0].isNotBlank()) null else lastError.ifBlank { "Nem érkezett fordítás." }
    }
}

/** Alapértelmezett HTTP: HttpURLConnection (Androidon és JVM-en is elérhető). */
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
