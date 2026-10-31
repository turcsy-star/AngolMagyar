package hu.angolmagyar.fordito

/**
 * Hybrid Translation Engine – v2.65.0
 *
 * Egységes offline / online motorválasztás, fallback, eredményformátum és adatvédelmi kapcsolók.
 * Nem indít hálózati hívást maga – a hívó adja át az online fordítót (pl. OnlineTranslator),
 * és egy offline fordító függvényt. A döntés és a naplózás itt történik.
 */
object HybridTranslationEngine {

    enum class Mode {
        /** Csak helyi / offline. Hálózat soha. */
        OFFLINE_ONLY,
        /** Először offline, sikertelen vagy üres eredménynél online (ha engedélyezett). */
        PREFER_OFFLINE,
        /** Először online, hiba esetén offline tartalék. */
        PREFER_ONLINE,
        /** Csak online; offline nem próbálkozik. */
        ONLINE_ONLY
    }

    enum class Source { OFFLINE, ONLINE, FALLBACK_OFFLINE, FALLBACK_ONLINE, EMPTY }

    data class PrivacyFlags(
        /** Online hívás engedélyezve (felhasználói kapcsoló). */
        val allowNetwork: Boolean = false,
        /** API-kulcs / szöveg naplózása tilos. */
        val redactLogs: Boolean = true,
        /** Online fordításnál a forrásszöveg csak a szükséges kötegben mehet ki. */
        val minimizePayload: Boolean = true
    )

    data class Request(
        val sourceTexts: List<String>,
        val mode: Mode = Mode.PREFER_OFFLINE,
        val privacy: PrivacyFlags = PrivacyFlags(),
        /** Opcionális projekt-szintű szótár (forrás → cél). */
        val glossary: List<Pair<String, String>> = emptyList()
    )

    data class SegmentResult(
        val index: Int,
        val source: String,
        val translation: String,
        val sourceEngine: Source,
        val notes: List<String> = emptyList()
    )

    data class Result(
        val segments: List<SegmentResult>,
        val modeUsed: Mode,
        val onlineAttempted: Boolean,
        val offlineAttempted: Boolean,
        val failedIndices: List<Int>,
        val summary: String
    ) {
        val allTranslated: Boolean get() = failedIndices.isEmpty() && segments.all { it.translation.isNotBlank() || it.source.isBlank() }
    }

    /**
     * Offline fordító: (forrásszövegek) → célszövegek ugyanolyan hosszal.
     * Üres string = nem sikerült az adott indexen.
     */
    fun interface OfflineTranslator {
        fun translate(sources: List<String>): List<String>
    }

    /**
     * Online fordító adapter: (forrásszövegek) → célszövegek, vagy null ha a hívás nem indult / fatális hiba.
     * A implementáció felelős a hálózati hívásért (pl. OnlineTranslator).
     */
    interface OnlineAdapter {
        fun translate(sources: List<String>): List<String>?
        val lastError: String
        val isFatal: Boolean
    }

    /**
     * Fő belépési pont.
     * @param offline kötelező offline motor (helyi ML Kit, szabályalapú, TM stb.)
     * @param online opcionális online adapter; null = nincs online lehetőség
     */
    fun translate(
        request: Request,
        offline: OfflineTranslator,
        online: OnlineAdapter? = null
    ): Result {
        val sources = request.sourceTexts
        if (sources.isEmpty()) {
            return Result(emptyList(), request.mode, false, false, emptyList(), "Üres kérés.")
        }

        val n = sources.size
        val out = Array(n) { "" }
        val engine = Array(n) { Source.EMPTY }
        val notes = Array(n) { mutableListOf<String>() }
        var onlineAttempted = false
        var offlineAttempted = false
        val failed = mutableListOf<Int>()

        fun applyOffline(indices: List<Int>) {
            if (indices.isEmpty()) return
            offlineAttempted = true
            val batch = indices.map { sources[it] }
            val results = try {
                offline.translate(batch)
            } catch (e: Exception) {
                indices.forEach { notes[it] += "Offline hiba: ${e.message ?: "ismeretlen"}" }
                return
            }
            for ((k, i) in indices.withIndex()) {
                val t = results.getOrNull(k)?.trim().orEmpty()
                if (t.isNotEmpty()) {
                    out[i] = t
                    engine[i] = if (engine[i] == Source.EMPTY) Source.OFFLINE else Source.FALLBACK_OFFLINE
                }
            }
        }

        fun applyOnline(indices: List<Int>) {
            if (indices.isEmpty() || online == null) return
            if (!request.privacy.allowNetwork) {
                indices.forEach { notes[it] += "Online tiltva (adatvédelmi kapcsoló)." }
                return
            }
            onlineAttempted = true
            val batch = indices.map { sources[it] }
            val results = try {
                online.translate(batch)
            } catch (e: Exception) {
                indices.forEach { notes[it] += "Online hiba: ${e.message ?: "ismeretlen"}" }
                return
            }
            if (results == null) {
                val err = online.lastError.ifBlank { "Online válasz üres vagy hibás." }
                indices.forEach { notes[it] += err }
                return
            }
            for ((k, i) in indices.withIndex()) {
                val t = results.getOrNull(k)?.trim().orEmpty()
                if (t.isNotEmpty()) {
                    out[i] = t
                    engine[i] = if (engine[i] == Source.EMPTY) Source.ONLINE else Source.FALLBACK_ONLINE
                }
            }
        }

        // Szótár-előtöltés (egyszerű exact match)
        if (request.glossary.isNotEmpty()) {
            val map = request.glossary.associate { it.first.trim().lowercase() to it.second.trim() }
            for (i in sources.indices) {
                val key = sources[i].trim().lowercase()
                val g = map[key]
                if (!g.isNullOrEmpty()) {
                    out[i] = g
                    engine[i] = Source.OFFLINE
                    notes[i] += "Szótár-találat."
                }
            }
        }

        val pending = sources.indices.filter { out[it].isEmpty() && sources[it].isNotBlank() }.toMutableList()
        val blanks = sources.indices.filter { sources[it].isBlank() }

        when (request.mode) {
            Mode.OFFLINE_ONLY -> {
                applyOffline(pending)
            }
            Mode.ONLINE_ONLY -> {
                applyOnline(pending)
            }
            Mode.PREFER_OFFLINE -> {
                applyOffline(pending)
                val still = pending.filter { out[it].isEmpty() }
                if (still.isNotEmpty()) applyOnline(still)
            }
            Mode.PREFER_ONLINE -> {
                applyOnline(pending)
                val still = pending.filter { out[it].isEmpty() }
                if (still.isNotEmpty()) applyOffline(still)
            }
        }

        for (i in sources.indices) {
            if (sources[i].isNotBlank() && out[i].isEmpty()) failed += i
        }

        val segments = sources.indices.map { i ->
            SegmentResult(
                index = i,
                source = sources[i],
                translation = out[i],
                sourceEngine = engine[i],
                notes = notes[i]
            )
        }

        val summary = buildString {
            append("Mód: ${request.mode.name}. ")
            append("Offline: ${if (offlineAttempted) "igen" else "nem"}, ")
            append("Online: ${if (onlineAttempted) "igen" else "nem"}. ")
            append("Sikeres: ${n - failed.size - blanks.size}/$n")
            if (failed.isNotEmpty()) append(" · Sikertelen indexek: ${failed.joinToString()}")
            if (!request.privacy.allowNetwork && request.mode != Mode.OFFLINE_ONLY) {
                append(" · Hálózat tiltva")
            }
        }

        return Result(
            segments = segments,
            modeUsed = request.mode,
            onlineAttempted = onlineAttempted,
            offlineAttempted = offlineAttempted,
            failedIndices = failed,
            summary = summary
        )
    }

    /** Egyszerű szöveges módválasztó a beállításokhoz. */
    fun modeFromPrefs(preferOnline: Boolean, allowNetwork: Boolean): Mode = when {
        !allowNetwork -> Mode.OFFLINE_ONLY
        preferOnline -> Mode.PREFER_ONLINE
        else -> Mode.PREFER_OFFLINE
    }
}
