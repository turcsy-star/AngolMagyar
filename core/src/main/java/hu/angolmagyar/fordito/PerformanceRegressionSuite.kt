package hu.angolmagyar.fordito
object PerformanceRegressionSuite {
    data class Timing(val name: String, val ms: Long, val ok: Boolean = true, val detail: String = "")
    data class Check(val name: String, val passed: Boolean, val detail: String = "")
    data class Report(
        val timings: List<Timing>,
        val checks: List<Check>,
        val heapUsedMb: Double,
        val heapMaxMb: Double,
        val elapsedMs: Long
    ) {
        val allPassed: Boolean get() = checks.all { it.passed } && timings.all { it.ok }
        val failedChecks: List<Check> get() = checks.filter { !it.passed }
        fun summaryLine(): String {
            val fail = failedChecks.size
            return "Idő: ${elapsedMs} ms · Tesztek: ${checks.size - fail}/${checks.size} OK · Heap: ${"%.1f".format(heapUsedMb)}/${"%.1f".format(heapMaxMb)} MB"
        }
    }
    private fun heapMb(): Pair<Double, Double> {
        val rt = Runtime.getRuntime()
        val used = (rt.totalMemory() - rt.freeMemory()) / (1024.0 * 1024.0)
        val max = rt.maxMemory() / (1024.0 * 1024.0)
        return used to max
    }
    private inline fun time(name: String, budgetMs: Long = Long.MAX_VALUE, block: () -> Unit): Timing {
        val t0 = System.nanoTime()
        block()
        val ms = (System.nanoTime() - t0) / 1_000_000L
        return Timing(name, ms, ok = ms <= budgetMs, detail = if (ms > budgetMs) "túllépte a ${budgetMs} ms keretet" else "")
    }
    fun runBuiltin(): Report {
        val tStart = System.nanoTime()
        val timings = mutableListOf<Timing>()
        val checks = mutableListOf<Check>()
        timings += time("SmartContext.suggest") {
            val s = SmartContextTranslation.suggest(
                "Please check the bank balance and the light on the right.",
                null,
                SmartContextTranslation.Tone.FORMAL
            )
            checks += Check(
                "SmartContext.ambiguity",
                s.ambiguity != SmartContextTranslation.AmbiguityLevel.NONE,
                "ambiguity=${s.ambiguity}"
            )
            checks += Check("SmartContext.notes", s.notes.isNotEmpty(), "notes=${s.notes.size}")
        }
        checks += Check(
            "SmartContext.hasAmbiguity",
            SmartContextTranslation.hasAmbiguity("set the charge on the match"),
            "expected true"
        )
        timings += time("TranslationQuality.analyze") {
            val empty = TranslationQuality.analyze("Hello world", "")
            checks += Check("Quality.emptyTarget", empty.needsHumanReview && empty.criticalCount >= 1, TranslationQuality.summaryLine(empty))
            val ok = TranslationQuality.analyze("There are 350 apples.", "Van 350 alma.")
            checks += Check("Quality.numberOk", ok.warnings.none { it.code == "NUMBER_MISMATCH" }, TranslationQuality.summaryLine(ok))
            val badNum = TranslationQuality.analyze("There are 350 apples.", "Van alma.")
            checks += Check("Quality.numberMismatch", badNum.warnings.any { it.code == "NUMBER_MISMATCH" }, TranslationQuality.summaryLine(badNum))
        }
        timings += time("HybridTranslationEngine") {
            val offline = HybridTranslationEngine.OfflineTranslator { srcs -> srcs.map { "[HU]$it" } }
            val req = HybridTranslationEngine.Request(
                sourceTexts = listOf("Hello", "World", ""),
                mode = HybridTranslationEngine.Mode.OFFLINE_ONLY,
                privacy = HybridTranslationEngine.PrivacyFlags(allowNetwork = false)
            )
            val res = HybridTranslationEngine.translate(req, offline, null)
            checks += Check("Hybrid.offline", res.segments[0].translation == "[HU]Hello", res.summary)
            checks += Check("Hybrid.noOnline", !res.onlineAttempted, res.summary)
            checks += Check("Hybrid.blankSource", res.segments[2].translation.isEmpty(), "blank ok")
        }
        run {
            val offline = HybridTranslationEngine.OfflineTranslator { srcs -> srcs.map { "" } }
            val req = HybridTranslationEngine.Request(
                sourceTexts = listOf("API key"),
                mode = HybridTranslationEngine.Mode.OFFLINE_ONLY,
                glossary = listOf("API key" to "API-kulcs")
            )
            val res = HybridTranslationEngine.translate(req, offline, null)
            checks += Check("Hybrid.glossary", res.segments[0].translation == "API-kulcs", res.summary)
        }
        timings += time("SystemStatusSnapshot") {
            val snap = SystemStatusSnapshot.build("2.67.0", 6700)
            checks += Check("Snapshot.modules", snap.modules.isNotEmpty(), "n=${snap.modules.size}")
            checks += Check("Snapshot.actions", snap.quickActions.isNotEmpty(), "n=${snap.quickActions.size}")
        }
        timings += time("TranslationCheck") {
            val issues = TranslationCheck.check("There are 500 dogs.", "Van kutya.")
            checks += Check("TranslationCheck.number", "szám eltér" in issues, issues.toString())
        }
        val (used, max) = heapMb()
        val elapsed = (System.nanoTime() - tStart) / 1_000_000L
        return Report(timings, checks, used, max, elapsed)
    }
}
