package hu.angolmagyar.fordito
object SystemStatusSnapshot {
    enum class Level { OK, WARN, ERROR, UNKNOWN, DISABLED }
    data class ModuleStatus(
        val id: String,
        val title: String,
        val level: Level,
        val detail: String,
        val actionable: Boolean = false
    )
    data class QuickAction(
        val id: String,
        val label: String,
        val description: String
    )
    data class Snapshot(
        val generatedAtMs: Long,
        val appVersionName: String,
        val appVersionCode: Int,
        val modules: List<ModuleStatus>,
        val quickActions: List<QuickAction>,
        val notes: List<String> = emptyList()
    ) {
        val okCount: Int get() = modules.count { it.level == Level.OK }
        val warnCount: Int get() = modules.count { it.level == Level.WARN }
        val errorCount: Int get() = modules.count { it.level == Level.ERROR }
        val overall: Level
            get() = when {
                errorCount > 0 -> Level.ERROR
                warnCount > 0 -> Level.WARN
                modules.any { it.level == Level.UNKNOWN } -> Level.UNKNOWN
                else -> Level.OK
            }
    }
    fun templateModules(): List<ModuleStatus> = listOf(
        ModuleStatus("asr", "Beszédfelismerés (ASR)", Level.UNKNOWN, "Modell / engedély ellenőrzése"),
        ModuleStatus("translate_offline", "Offline fordítás", Level.UNKNOWN, "ML Kit / helyi motor"),
        ModuleStatus("translate_online", "Online fordítás", Level.UNKNOWN, "API kulcs és hálózat"),
        ModuleStatus("hybrid", "Hybrid Translation Engine", Level.OK, "Core v2.65 – módválasztás kész"),
        ModuleStatus("smart_context", "Smart Context", Level.OK, "Core v2.64 – offline javaslatok"),
        ModuleStatus("quality", "Translation Quality", Level.OK, "Core v2.64 – heurisztikus ellenőrzés"),
        ModuleStatus("dictionary", "Szótár / Phrasebook", Level.UNKNOWN, "Helyi adatbázis"),
        ModuleStatus("review", "Intelligens ismétlő", Level.UNKNOWN, "Esedékes kártyák"),
        ModuleStatus("analytics", "Learning Analytics", Level.OK, "v2.63 – helyi számlálók"),
        ModuleStatus("backup", "Biztonsági mentés", Level.UNKNOWN, "Keystore / export"),
        ModuleStatus("motion", "Platinum Motion", Level.OK, "Profilok és csökkentés v2.65"),
        ModuleStatus("global_ui", "Infinity Global UI", Level.WARN, "Migráció részben kész – v3.00-ig")
    )
    fun defaultQuickActions(): List<QuickAction> = listOf(
        QuickAction("diagnostics", "Diagnosztika", "Engedélyek, modellek, hálózat"),
        QuickAction("models", "Modellek", "Offline ASR / fordító modellek"),
        QuickAction("backup", "Mentés", "Szótár és ismétlési adatok"),
        QuickAction("next_wave", "Next Wave eszközök", "Smart Context, Quality, Hybrid"),
        QuickAction("privacy", "Adatvédelem", "Helyi adatok és hálózati kapcsolók"),
        QuickAction("settings", "Beállítások", "Megjelenés, mozgás, OLED")
    )
    fun build(
        versionName: String,
        versionCode: Int,
        modules: List<ModuleStatus> = templateModules(),
        actions: List<QuickAction> = defaultQuickActions(),
        notes: List<String> = emptyList()
    ): Snapshot = Snapshot(
        generatedAtMs = System.currentTimeMillis(),
        appVersionName = versionName,
        appVersionCode = versionCode,
        modules = modules,
        quickActions = actions,
        notes = notes
    )
}
