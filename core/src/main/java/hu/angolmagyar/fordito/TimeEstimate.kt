package hu.angolmagyar.fordito
object TimeEstimate {
    fun factor(modelId: String): Double = when (modelId) {
        "wh_base" -> 0.3
        "wh_small" -> 0.55
        "wh_turbo" -> 1.0
        "wh_medium" -> 1.5
        "wh_large" -> 2.0
        else -> 1.0
    }
    fun msPerWindow(modelId: String, measured: Map<String, Long>): Long {
        measured[modelId]?.takeIf { it > 0 }?.let { return it }
        val any = measured.entries.firstOrNull { it.value > 0 } ?: return 0L
        return (any.value * factor(modelId) / factor(any.key)).toLong()
    }
    fun estimateMs(audioMs: Long, msPerWindow: Long, speechRatio: Double = 0.8): Long {
        if (audioMs <= 0 || msPerWindow <= 0) return -1L
        return EtaModel.windows((audioMs * speechRatio).toLong()) * msPerWindow + EtaModel.TAIL_MS
    }
    fun label(ms: Long): String {
        if (ms < 0) return "?"
        val min = (ms + 30_000) / 60_000
        return when {
            min < 1 -> "1 percnél kevesebb"
            min < 60 -> "kb. $min perc"
            else -> "kb. ${min / 60} óra ${min % 60} perc"
        }
    }
}
