package hu.angolmagyar.fordito

/**
 * Whisper-modell ajánlás a készülék alapján. A sebességértékek tájékoztató becslések (nem mért benchmark):
 * a tényleges idő a telefontól, a hangtól és a hőállapottól függ.
 */
object ModelAdvisor {
    data class Candidate(
        val id: String,
        val label: String,
        val sizeMb: Int,
        val minRamMb: Int,
        /** Pontosság-rangsor (nagyobb = pontosabb). */
        val quality: Int,
        /** Becsült feldolgozási idő / hanghossz egy 8 magos készüléken (1.0 = valós idejű). */
        val rtf8: Double
    )

    data class Device(
        val ramMb: Int,
        val cores: Int,
        val freeStorageMb: Long,
        val thermalStatus: Int = 0,
        val installedIds: Set<String> = emptySet()
    )

    data class Advice(
        val pick: Candidate,
        val estimatedRtf: Double,
        val estimatedMinutes: Int?,
        val reason: String,
        val needsDownload: Boolean
    )

    val DEFAULTS = listOf(
        Candidate("wh_base", "Whisper base", 57, 1500, 1, 0.15),
        Candidate("wh_small", "Whisper small", 181, 2500, 2, 0.4),
        Candidate("wh_medium", "Whisper medium", 515, 4000, 3, 1.4),
        Candidate("wh_turbo", "Whisper large-v3-turbo", 574, 4000, 4, 1.0),
        Candidate("wh_large", "Whisper large-v3", 1100, 6000, 5, 2.2)
    )

    fun estimateRtf(c: Candidate, d: Device): Double {
        val cores = d.cores.coerceIn(2, 8)
        val thermal = 1.0 + 0.15 * d.thermalStatus.coerceIn(0, 4)
        return c.rtf8 * (8.0 / cores) * thermal
    }

    /**
     * @param maxRtf a még elfogadott idő/hanghossz arány (alapértelmezés: legfeljebb 2× hosszabb a feldolgozás a hangnál)
     * @param audioMinutes ha ismert, a becsült feldolgozási időt is megadja
     */
    fun advise(
        device: Device,
        audioMinutes: Int? = null,
        maxRtf: Double = 2.0,
        candidates: List<Candidate> = DEFAULTS
    ): Advice {
        fun fits(c: Candidate): Boolean {
            val ramOk = device.ramMb >= c.minRamMb
            val storageOk = c.id in device.installedIds || device.freeStorageMb >= c.sizeMb * 1.3
            return ramOk && storageOk
        }
        val viable = candidates.filter { fits(it) }.sortedByDescending { it.quality }
        val fast = viable.firstOrNull { estimateRtf(it, device) <= maxRtf }
        val pick = fast ?: viable.minByOrNull { estimateRtf(it, device) } ?: candidates.minByOrNull { it.minRamMb }!!
        val rtf = estimateRtf(pick, device)
        val reason = when {
            viable.isEmpty() -> "Kevés a memória vagy a tárhely; a legkisebb modell az egyetlen reális választás."
            fast == null -> "Minden elérhető modell lassabb a megadott határnál; a leggyorsabb elérhetőt ajánljuk."
            fast.id == candidates.maxByOrNull { it.quality }?.id -> "A készülék bírja a legpontosabb modellt."
            else -> "Legpontosabb modell, amely a memóriába, a tárhelyre és az időkeretbe belefér."
        }
        return Advice(pick, rtf, audioMinutes?.let { kotlin.math.ceil(it * rtf).toInt() }, reason, pick.id !in device.installedIds)
    }
}
