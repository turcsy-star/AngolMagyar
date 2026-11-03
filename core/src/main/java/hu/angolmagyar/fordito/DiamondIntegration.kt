package hu.angolmagyar.fordito
object DiamondIntegration {
    const val VERSION_NAME = "3.00.0"
    const val VERSION_CODE = 30000
    enum class GateStatus { OPEN, PARTIAL, CLOSED }
    data class CheckItem(
        val id: String,
        val title: String,
        val done: Boolean,
        val note: String = ""
    )
    data class GateReport(
        val versionName: String,
        val versionCode: Int,
        val items: List<CheckItem>,
        val regression: PerformanceRegressionSuite.Report?
    ) {
        val doneCount: Int get() = items.count { it.done }
        val total: Int get() = items.size
        val status: GateStatus
            get() = when {
                doneCount == total && (regression == null || regression.allPassed) -> GateStatus.CLOSED
                doneCount >= total * 2 / 3 -> GateStatus.PARTIAL
                else -> GateStatus.OPEN
            }
        fun summaryLine(): String =
            "Diamond $versionName · $doneCount/$total checklist · kapu: $status" +
                (regression?.let { " · regresszió: ${if (it.allPassed) "OK" else "FAIL"}" } ?: "")
    }
    fun checklist(): List<CheckItem> = listOf(
        CheckItem("nav", "Közös navigáció (NavTransitions + Intelligence hub)", true,
            "Hub és Next Wave útvonalak egységesek"),
        CheckItem("tokens", "Design tokenek (PremiumTokens, InfinityGlobalUi, Motion)", true,
            "Paletták és mozgásprofilok központosítva; teljes View→Compose még részben"),
        CheckItem("smart_context", "Smart Context Translation", true, "v2.64 core"),
        CheckItem("quality", "Translation Quality", true, "v2.64 core"),
        CheckItem("hybrid", "Hybrid Translation Engine", true, "v2.65 core"),
        CheckItem("dashboard", "Platinum Global Dashboard", true, "v2.66"),
        CheckItem("perf", "Performance & Regression Suite", true, "v2.67 + unit test"),
        CheckItem("privacy", "Adatvédelem / jogosultság képernyők", true,
            "PrivacyActivity + Diagnostics meglévő; hálózati kapcsolók Hybrid-ben"),
        CheckItem("analytics", "Learning Analytics Diamond", true, "v2.63"),
        CheckItem("backup", "Biztonságos mentés (Keystore)", true, "v2.61–2.62"),
        CheckItem("view_compose", "Teljes klasszikus View → Compose migráció", false,
            "Infinity Global UI részben kész – folyamatos"),
        CheckItem("device_qa", "Fizikai Android eszközös UI- és adatmegmaradási teszt", false,
            "Kiadás előtt kötelező a készüléken"),
        CheckItem("gradle_ci", "Reprodukálható Gradle CI build", false,
            "Környezetfüggő (DNS / wrapper)")
    )
    fun evaluate(runRegression: Boolean = true): GateReport {
        val reg = if (runRegression) PerformanceRegressionSuite.runBuiltin() else null
        return GateReport(VERSION_NAME, VERSION_CODE, checklist(), reg)
    }
    fun moduleMap(): List<Pair<String, String>> = listOf(
        "2.63 Learning Analytics" to "Kész",
        "2.64 Smart Context + Quality" to "Kész",
        "2.65 Hybrid + Motion/Global" to "Kész / részben",
        "2.66 Global Dashboard" to "Kész",
        "2.67 Performance Suite" to "Kész",
        "3.00 Diamond integráció" to "Kapu: forrásszintű checklist + regresszió"
    )
}
