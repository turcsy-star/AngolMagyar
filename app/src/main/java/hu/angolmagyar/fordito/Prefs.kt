package hu.angolmagyar.fordito
import android.content.Context
class Prefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
    companion object {
        val TEXT_COLORS = intArrayOf(
            0xFFFFFFFF.toInt(),
            0xFFFFE066.toInt(),
            0xFF7FE3D0.toInt(),
            0xFFFFB366.toInt()
        )
        val COLOR_NAMES = listOf("Fehér", "Sárga", "Türkiz", "Narancs")
        const val DIR_EN_HU = "en_hu"
        const val DIR_HU_EN = "hu_en"
        const val DIR_NONE = "none"
        const val DIR_CONVERSATION = "conversation"
        const val LANG_HU = "hu"
        const val LANG_EN = "en"
        const val LANG_DE = "de"
        const val LANG_FR = "fr"
        const val LANG_ES = "es"
        const val LANG_IT = "it"
        const val LANG_UK = "uk"
        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2
        const val STYLE_DEFAULT = 0
        const val STYLE_NETFLIX = 1
        const val STYLE_YOUTUBE = 2
        const val STYLE_CONCERT = 3
        const val STYLE_HC = 4
    }
    var allowOnlineAsr: Boolean
        get() = p.getBoolean("allowOnlineAsr", false)
        set(v) { p.edit().putBoolean("allowOnlineAsr", v).apply() }
    var convStartHu: Boolean
        get() = p.getBoolean("convStartHu", false)
        set(v) { p.edit().putBoolean("convStartHu", v).apply() }
    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) { p.edit().putBoolean("onboarded", v).apply() }
    var settingsTab: Int
        get() = p.getInt("settingsTab", 0)
        set(v) { p.edit().putInt("settingsTab", v).apply() }
    var modelId: String
        get() = p.getString("model", "small") ?: "small"
        set(v) { p.edit().putString("model", v).apply() }
    var textSizeSp: Int
        get() = p.getInt("textSizeSp", 20)
        set(v) { p.edit().putInt("textSizeSp", v.coerceIn(12, 40)).apply() }
    var bgAlphaPct: Int
        get() = p.getInt("bgAlphaPct", 80)
        set(v) { p.edit().putInt("bgAlphaPct", v.coerceIn(20, 100)).apply() }
    var textColorIdx: Int
        get() = p.getInt("textColorIdx", 0)
        set(v) { p.edit().putInt("textColorIdx", v).apply() }
    var showEnglish: Boolean
        get() = p.getBoolean("showEnglish", true)
        set(v) { p.edit().putBoolean("showEnglish", v).apply() }
    var highContrast: Boolean
        get() = p.getBoolean("highContrast", false)
        set(v) { p.edit().putBoolean("highContrast", v).apply() }
    var premiumProcessingUi: Boolean
        get() = p.getBoolean("premiumProcessingUi", true)
        set(v) { p.edit().putBoolean("premiumProcessingUi", v).apply() }
    var platinumTheme: Boolean
        get() = p.getBoolean("platinumTheme", true)
        set(v) { p.edit().putBoolean("platinumTheme", v).apply() }
    var luxuryThemeId: String
        get() = p.getString("luxuryThemeId", "ultimate") ?: "ultimate"
        set(v) { p.edit().putString("luxuryThemeId", v).apply() }
    var reduceMotion: Boolean
        get() = p.getBoolean("reduceMotion", false)
        set(v) { p.edit().putBoolean("reduceMotion", v).apply() }
    var motionProfile: String
        get() = p.getString("motionProfile", "subtle") ?: "subtle"
        set(v) { p.edit().putString("motionProfile", v).apply() }
    var diamondCinemaMode: Boolean
        get() = p.getBoolean("diamondCinemaMode", true)
        set(v) { p.edit().putBoolean("diamondCinemaMode", v).apply() }
    var atTop: Boolean
        get() = p.getBoolean("atTop", false)
        set(v) { p.edit().putBoolean("atTop", v).apply() }
    var overlayY: Int
        get() = p.getInt("overlayY", -1)
        set(v) { p.edit().putInt("overlayY", v).apply() }
    var hAlign: Int
        get() = p.getInt("hAlign", ALIGN_CENTER)
        set(v) { p.edit().putInt("hAlign", v.coerceIn(0, 2)).apply() }
    var stylePreset: Int
        get() = p.getInt("stylePreset", STYLE_DEFAULT)
        set(v) { p.edit().putInt("stylePreset", v).apply() }
    var eco: Boolean
        get() = p.getBoolean("eco", false)
        set(v) { p.edit().putBoolean("eco", v).apply() }
    var adaptiveEco: Boolean
        get() = p.getBoolean("adaptiveEco", true)
        set(v) { p.edit().putBoolean("adaptiveEco", v).apply() }
    var saveTranscript: Boolean
        get() = p.getBoolean("saveTranscript", true)
        set(v) { p.edit().putBoolean("saveTranscript", v).apply() }
    var lastMode: String
        get() = p.getString("lastMode", TranslateService.MODE_INTERNAL) ?: TranslateService.MODE_INTERNAL
        set(v) { p.edit().putString("lastMode", v).apply() }
    var hintShown: Boolean
        get() = p.getBoolean("hintShown", false)
        set(v) { p.edit().putBoolean("hintShown", v).apply() }
    var enFixes: String
        get() = p.getString("enFixes", "") ?: ""
        set(v) { p.edit().putString("enFixes", v).apply() }
    var huFixes: String
        get() = p.getString("huFixes", "") ?: ""
        set(v) { p.edit().putString("huFixes", v).apply() }
    var targetLang: String
        get() = p.getString("targetLang", LANG_HU) ?: LANG_HU
        set(v) { p.edit().putString("targetLang", v).apply() }
    var polyMode: Boolean
        get() = p.getBoolean("polyMode", true)
        set(v) { p.edit().putBoolean("polyMode", v).apply() }
    var polyTarget: String
        get() = p.getString("polyTarget", "hu") ?: "hu"
        set(v) { p.edit().putString("polyTarget", v).apply() }
    var sourceLang: String
        get() = p.getString("sourceLang", LANG_EN) ?: LANG_EN
        set(v) { p.edit().putString("sourceLang", v).apply() }
    var translateDir: String
        get() = p.getString("translateDir", DIR_EN_HU) ?: DIR_EN_HU
        set(v) { p.edit().putString("translateDir", v).apply() }
    var forcedEco: Boolean
        get() = p.getBoolean("forcedEco", false)
        set(v) { p.edit().putBoolean("forcedEco", v).apply() }
    var delayMs: Int
        get() = p.getInt("delayMs", 0)
        set(v) { p.edit().putInt("delayMs", v.coerceIn(0, 3000)).apply() }
    var confidenceMin: Int
        get() = p.getInt("confidenceMin", 0)
        set(v) { p.edit().putInt("confidenceMin", v.coerceIn(0, 100)).apply() }
    var pushToTalk: Boolean
        get() = p.getBoolean("pushToTalk", false)
        set(v) { p.edit().putBoolean("pushToTalk", v).apply() }
    var haptics: Boolean
        get() = p.getBoolean("haptics", true)
        set(v) { p.edit().putBoolean("haptics", v).apply() }
    var meetingSafeMode: Boolean
        get() = p.getBoolean("meetingSafeMode", true)
        set(v) { p.edit().putBoolean("meetingSafeMode", v).apply() }
    var meetingChunkMinutes: Int
        get() = p.getInt("meetingChunkMinutes", 12)
        set(v) { p.edit().putInt("meetingChunkMinutes", v.coerceIn(5, 20)).apply() }
    var meetingMode: Boolean
        get() = p.getBoolean("meetingMode", false)
        set(v) { p.edit().putBoolean("meetingMode", v).apply() }
    var meetingSilenceMs: Int
        get() = p.getInt("meetingSilenceMs", 1200)
        set(v) { p.edit().putInt("meetingSilenceMs", v.coerceIn(400, 4000)).apply() }
    var ttsEnabled: Boolean
        get() = p.getBoolean("ttsEnabled", false)
        set(v) { p.edit().putBoolean("ttsEnabled", v).apply() }
    var maxOverlayLines: Int
        get() = p.getInt("maxOverlayLines", 3)
        set(v) { p.edit().putInt("maxOverlayLines", v.coerceIn(2, 6)).apply() }
    var autoCollapseMs: Int
        get() = p.getInt("autoCollapseMs", 5000)
        set(v) { p.edit().putInt("autoCollapseMs", v.coerceIn(2000, 15000)).apply() }
    var onboardingDone: Boolean
        get() = p.getBoolean("onboardingDone", false)
        set(v) { p.edit().putBoolean("onboardingDone", v).apply() }
    var customVocab: String
        get() = p.getString("customVocab", "") ?: ""
        set(v) { p.edit().putString("customVocab", v).apply() }
    var autoLangSwitch: Boolean
        get() = p.getBoolean("autoLangSwitch", true)
        set(v) { p.edit().putBoolean("autoLangSwitch", v).apply() }
    var speakerNames: String
        get() = p.getString("speakerNames", "") ?: ""
        set(v) { p.edit().putString("speakerNames", v).apply() }
    var preferOnnx: Boolean
        get() = p.getBoolean("preferOnnx", true)
        set(v) { p.edit().putBoolean("preferOnnx", v).apply() }
    var keepScreenOn: Boolean
        get() = p.getBoolean("keepScreenOn", false)
        set(v) { p.edit().putBoolean("keepScreenOn", v).apply() }
    var useMlLangId: Boolean
        get() = p.getBoolean("useMlLangId", true)
        set(v) { p.edit().putBoolean("useMlLangId", v).apply() }
    var themeMode: Int
        get() = p.getInt("themeMode", 2)
        set(v) { p.edit().putInt("themeMode", v.coerceIn(0, 2)).apply() }
    var oledBlack: Boolean
        get() = p.getBoolean("oledBlack", false)
        set(v) { p.edit().putBoolean("oledBlack", v).apply() }
    var saveAudio: Boolean
        get() = p.getBoolean("saveAudio", false)
        set(v) { p.edit().putBoolean("saveAudio", v).apply() }
    var resumeLast: Boolean
        get() = p.getBoolean("resumeLast", false)
        set(v) { p.edit().putBoolean("resumeLast", v).apply() }
    var touchThrough: Boolean
        get() = p.getBoolean("touchThrough", false)
        set(v) { p.edit().putBoolean("touchThrough", v).apply() }
    var profilesJson: String
        get() = p.getString("profilesJson", "") ?: ""
        set(v) { p.edit().putString("profilesJson", v).apply() }
    var lastProfile: String
        get() = p.getString("lastProfile", "") ?: ""
        set(v) { p.edit().putString("lastProfile", v).apply() }
    var fixLearnJson: String
        get() = p.getString("fixLearnJson", "") ?: ""
        set(v) { p.edit().putString("fixLearnJson", v).apply() }
    var tipsSeenVersion: Int
        get() = p.getInt("tipsSeenVersion", 0)
        set(v) { p.edit().putInt("tipsSeenVersion", v).apply() }
    var meetingTemporary: Boolean
        get() = p.getBoolean("meetingTemporary", false)
        set(v) { p.edit().putBoolean("meetingTemporary", v).apply() }
    var meetingBackup: String
        get() = p.getString("meetingBackup", "") ?: ""
        set(v) { p.edit().putString("meetingBackup", v).apply() }
    fun raw(): android.content.SharedPreferences = p
    val textColor: Int
        get() = TEXT_COLORS[textColorIdx.coerceIn(0, TEXT_COLORS.size - 1)]
    fun spec(): ModelManager.Spec = ModelManager.byId(modelId)
    fun effectiveSpec(ctx: Context): ModelManager.Spec {
        if (meetingMode) {
            return Engines.voskSpecFor(ctx, this, "hu") ?: ModelManager.HU_LARGE
        }
        val lang = Engines.sides(this).otherLang
        val useEco = eco || forcedEco
        if (useEco && lang == "en" && ModelManager.isInstalled(ctx, ModelManager.SMALL)) {
            return ModelManager.SMALL
        }
        return Engines.voskSpecFor(ctx, this, lang) ?: ModelManager.SMALL
    }
    fun isEnToHu(): Boolean = translateDir == DIR_EN_HU || translateDir == DIR_CONVERSATION
    fun isHuToEn(): Boolean = translateDir == DIR_HU_EN
    fun isConversation(): Boolean = translateDir == DIR_CONVERSATION
    fun needsTranslation(): Boolean = translateDir != DIR_NONE
    fun applyStylePreset(preset: Int) {
        stylePreset = preset
        when (preset) {
            STYLE_NETFLIX -> {
                textSizeSp = 22; bgAlphaPct = 70; textColorIdx = 0; highContrast = false; showEnglish = false
            }
            STYLE_YOUTUBE -> {
                textSizeSp = 18; bgAlphaPct = 85; textColorIdx = 0; highContrast = false; showEnglish = true
            }
            STYLE_CONCERT -> {
                textSizeSp = 28; bgAlphaPct = 90; textColorIdx = 1; highContrast = false; showEnglish = false
            }
            STYLE_HC -> {
                textSizeSp = 24; bgAlphaPct = 100; textColorIdx = 0; highContrast = true; showEnglish = true
            }
            else -> {   }
        }
    }
}
