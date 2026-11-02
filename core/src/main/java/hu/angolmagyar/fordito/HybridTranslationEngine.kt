package hu.angolmagyar.fordito
object HybridTranslationEngine {
    enum class Mode {
        OFFLINE_ONLY,
        PREFER_OFFLINE,
        PREFER_ONLINE,
        ONLINE_ONLY
    }
    enum class Source { OFFLINE, ONLINE, FALLBACK_OFFLINE, FALLBACK_ONLINE, EMPTY }
    data class PrivacyFlags(
        val allowNetwork: Boolean = false,
        val redactLogs: Boolean = true,
        val minimizePayload: Boolean = true
    )
    data class Request(
        val sourceTexts: List<String>,
        val mode: Mode = Mode.PREFER_OFFLINE,
        val privacy: PrivacyFlags = PrivacyFlags(),
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
    fun interface OfflineTranslator {
        fun translate(sources: List<String>): List<String>
    }
    interface OnlineAdapter {
        fun translate(sources: List<String>): List<String>?
        val lastError: String
        val isFatal: Boolean
    }
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
    fun modeFromPrefs(preferOnline: Boolean, allowNetwork: Boolean): Mode = when {
        !allowNetwork -> Mode.OFFLINE_ONLY
        preferOnline -> Mode.PREFER_ONLINE
        else -> Mode.PREFER_OFFLINE
    }
}
