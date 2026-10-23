package hu.angolmagyar.fordito

import android.content.Context

/** Az app beállításai (SharedPreferences). */
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

        // ML Kit célnyelvek (forrás: en vagy hu)
        const val LANG_HU = "hu"
        const val LANG_EN = "en"
        const val LANG_DE = "de"
        const val LANG_FR = "fr"
        const val LANG_ES = "es"
        const val LANG_IT = "it"
        const val LANG_UK = "uk"

        /** 0=bal, 1=közép, 2=jobb */
        const val ALIGN_LEFT = 0
        const val ALIGN_CENTER = 1
        const val ALIGN_RIGHT = 2

        const val STYLE_DEFAULT = 0
        const val STYLE_NETFLIX = 1
        const val STYLE_YOUTUBE = 2
        const val STYLE_CONCERT = 3
        const val STYLE_HC = 4
    }

    /** Magyar felismerés online (Google felhő) tartalékként – a hang a Google szervereire kerül. */
    var allowOnlineAsr: Boolean
        get() = p.getBoolean("allowOnlineAsr", false)
        set(v) { p.edit().putBoolean("allowOnlineAsr", v).apply() }

    /** Tolmács mód rögzített irányánál: true = magyar → angol a kezdőirány. */
    var convStartHu: Boolean
        get() = p.getBoolean("convStartHu", false)
        set(v) { p.edit().putBoolean("convStartHu", v).apply() }

    /** Az első indítási előkészítő képernyő már megnyílt-e. */
    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) { p.edit().putBoolean("onboarded", v).apply() }

    /** Utoljára nyitott Beállítások-fül. */
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

    var atTop: Boolean
        get() = p.getBoolean("atTop", false)
        set(v) { p.edit().putBoolean("atTop", v).apply() }

    var overlayY: Int
        get() = p.getInt("overlayY", -1)
        set(v) { p.edit().putInt("overlayY", v).apply() }

    /** 0=bal, 1=közép, 2=jobb */
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

    /** Célnyelv ML Kit kód: hu, de, fr, es, it, uk, en */
    var targetLang: String
        get() = p.getString("targetLang", LANG_HU) ?: LANG_HU
        set(v) { p.edit().putString("targetLang", v).apply() }

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

    /** Hosszú felvétel: chunk + thermal/akku kapu (7–8 órához). */
    var meetingSafeMode: Boolean
        get() = p.getBoolean("meetingSafeMode", true)
        set(v) { p.edit().putBoolean("meetingSafeMode", v).apply() }

    /** Chunk hossza percben (8–20). */
    var meetingChunkMinutes: Int
        get() = p.getInt("meetingChunkMinutes", 12)
        set(v) { p.edit().putInt("meetingChunkMinutes", v.coerceIn(5, 20)).apply() }

    var meetingMode: Boolean
        get() = p.getBoolean("meetingMode", false)
        set(v) { p.edit().putBoolean("meetingMode", v).apply() }

    /** Tárgyalás: csend küszöb ms új blokkhoz (fájl átirat). */
    var meetingSilenceMs: Int
        get() = p.getInt("meetingSilenceMs", 1200)
        set(v) { p.edit().putInt("meetingSilenceMs", v.coerceIn(400, 4000)).apply() }

    /** TTS: a lefordított szöveg felolvasása (Android TextToSpeech). */
    var ttsEnabled: Boolean
        get() = p.getBoolean("ttsEnabled", false)
        set(v) { p.edit().putBoolean("ttsEnabled", v).apply() }

    /** Overlay: maximális megjelenő magyar sorok száma (2–6). */
    var maxOverlayLines: Int
        get() = p.getInt("maxOverlayLines", 3)
        set(v) { p.edit().putInt("maxOverlayLines", v.coerceIn(2, 6)).apply() }

    /** Overlay vezérlősáv automatikus elrejtése ms-ben. */
    var autoCollapseMs: Int
        get() = p.getInt("autoCollapseMs", 5000)
        set(v) { p.edit().putInt("autoCollapseMs", v.coerceIn(2000, 15000)).apply() }

    /** Első indítás onboarding megjelenítve. */
    var onboardingDone: Boolean
        get() = p.getBoolean("onboardingDone", false)
        set(v) { p.edit().putBoolean("onboardingDone", v).apply() }

    /** Személyes szótár / gyakori kifejezések (soronként: forrás=cél vagy csak szó). */
    var customVocab: String
        get() = p.getString("customVocab", "") ?: ""
        set(v) { p.edit().putString("customVocab", v).apply() }

    /** Beszélgetés mód: automatikus irányváltás nyelv-tipp alapján. */
    var autoLangSwitch: Boolean
        get() = p.getBoolean("autoLangSwitch", true)
        set(v) { p.edit().putBoolean("autoLangSwitch", v).apply() }

    /** Beszélőnevek (soronként S1=Anna formátum). */
    var speakerNames: String
        get() = p.getString("speakerNames", "") ?: ""
        set(v) { p.edit().putString("speakerNames", v).apply() }

    /** ONNX speaker embedding preferálása (ha modell telepítve). */
    var preferOnnx: Boolean
        get() = p.getBoolean("preferOnnx", true)
        set(v) { p.edit().putBoolean("preferOnnx", v).apply() }

    /** Futás közben a képernyő ébren tartása (opcionális). */
    var keepScreenOn: Boolean
        get() = p.getBoolean("keepScreenOn", false)
        set(v) { p.edit().putBoolean("keepScreenOn", v).apply() }

    /** ML Kit Language ID használata auto irányváltáshoz. */
    var useMlLangId: Boolean
        get() = p.getBoolean("useMlLangId", true)
        set(v) { p.edit().putBoolean("useMlLangId", v).apply() }

    /** Téma: 0 = rendszer, 1 = világos, 2 = sötét. */
    var themeMode: Int
        get() = p.getInt("themeMode", 2)
        set(v) { p.edit().putInt("themeMode", v.coerceIn(0, 2)).apply() }

    /** A hang tömörített mentése az átirat mellé (visszajátszáshoz). Alapból ki. */
    var saveAudio: Boolean
        get() = p.getBoolean("saveAudio", false)
        set(v) { p.edit().putBoolean("saveAudio", v).apply() }

    /** Új munkamenet az utolsó átirat fájlba folytatódik. */
    var resumeLast: Boolean
        get() = p.getBoolean("resumeLast", false)
        set(v) { p.edit().putBoolean("resumeLast", v).apply() }

    /** Felirat érintés-átengedő mód (a feliratot nem lehet megérinteni, a vezérlés az értesítésből megy). */
    var touchThrough: Boolean
        get() = p.getBoolean("touchThrough", false)
        set(v) { p.edit().putBoolean("touchThrough", v).apply() }

    /** Felhasználói profilok (JSON tömb). */
    var profilesJson: String
        get() = p.getString("profilesJson", "") ?: ""
        set(v) { p.edit().putString("profilesJson", v).apply() }

    var lastProfile: String
        get() = p.getString("lastProfile", "") ?: ""
        set(v) { p.edit().putString("lastProfile", v).apply() }

    /** Átirat-szerkesztésekből tanult javítások számlálói (JSON). */
    var fixLearnJson: String
        get() = p.getString("fixLearnJson", "") ?: ""
        set(v) { p.edit().putString("fixLearnJson", v).apply() }

    /** Melyik verzió tippjeit látta már a felhasználó. */
    var tipsSeenVersion: Int
        get() = p.getInt("tipsSeenVersion", 0)
        set(v) { p.edit().putInt("tipsSeenVersion", v).apply() }

    /** Tárgyalás mód ideiglenes (a fájl-átirat képernyő kapcsolta be). */
    var meetingTemporary: Boolean
        get() = p.getBoolean("meetingTemporary", false)
        set(v) { p.edit().putBoolean("meetingTemporary", v).apply() }

    /** A tárgyalás mód bekapcsolása előtti beállítások mentése (JSON). */
    var meetingBackup: String
        get() = p.getString("meetingBackup", "") ?: ""
        set(v) { p.edit().putString("meetingBackup", v).apply() }

    /** Nyers hozzáférés a MeetingMode / Profiles számára. */
    fun raw(): android.content.SharedPreferences = p

    val textColor: Int
        get() = TEXT_COLORS[textColorIdx.coerceIn(0, TEXT_COLORS.size - 1)]

    fun spec(): ModelManager.Spec = ModelManager.byId(modelId)

    /**
     * A Vosk-modell, amit az élő mód a "másik" (nem magyar) oldalon használ.
     * A kiválasztott angol modell (small/large) számít; takarékos módban a kicsi.
     */
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

    /** Stíluspreset alkalmazása a többi mezőre. */
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
            else -> { /* default – user values */ }
        }
    }
}
