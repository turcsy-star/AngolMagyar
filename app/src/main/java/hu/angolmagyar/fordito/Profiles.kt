package hu.angolmagyar.fordito

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gyorsprofilok: irány + nyelvek + stílus + eco + késleltetés + indítási mód egy névvel.
 * Beépített profilok (nem törölhetők) + a felhasználó saját mentései.
 */
object Profiles {

    data class Profile(
        val name: String,
        val mode: String,
        val values: JSONObject,
        val builtIn: Boolean
    )

    private fun built(ctx: Context): List<Profile> {
        fun p(nameRes: Int, mode: String, build: JSONObject.() -> Unit) =
            Profile(ctx.getString(nameRes), mode, JSONObject().apply(build), true)
        return listOf(
            p(R.string.profile_film, TranslateService.MODE_INTERNAL) {
                put("dir", Prefs.DIR_EN_HU); put("src", Prefs.LANG_EN); put("tgt", Prefs.LANG_HU)
                put("style", Prefs.STYLE_NETFLIX); put("eco", false); put("delay", 0); put("tts", false)
            },
            p(R.string.profile_conference, TranslateService.MODE_MIC) {
                put("dir", Prefs.DIR_EN_HU); put("src", Prefs.LANG_EN); put("tgt", Prefs.LANG_HU)
                put("style", Prefs.STYLE_YOUTUBE); put("eco", false); put("delay", 0); put("tts", false)
            },
            p(R.string.profile_conversation, TranslateService.MODE_MIC) {
                put("dir", Prefs.DIR_CONVERSATION); put("src", Prefs.LANG_EN); put("tgt", Prefs.LANG_HU)
                put("style", Prefs.STYLE_YOUTUBE); put("eco", false); put("delay", 0); put("tts", true)
            },
            p(R.string.profile_concert, TranslateService.MODE_MIC) {
                put("dir", Prefs.DIR_EN_HU); put("src", Prefs.LANG_EN); put("tgt", Prefs.LANG_HU)
                put("style", Prefs.STYLE_CONCERT); put("eco", false); put("delay", 0); put("tts", false)
            }
        )
    }

    private fun custom(prefs: Prefs): List<Profile> {
        val raw = prefs.profilesJson
        if (raw.isBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name")
                if (name.isBlank()) null
                else Profile(name, o.optString("mode", TranslateService.MODE_MIC), o.optJSONObject("values") ?: JSONObject(), false)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun all(ctx: Context, prefs: Prefs): List<Profile> = built(ctx) + custom(prefs)

    fun find(ctx: Context, prefs: Prefs, name: String): Profile? =
        all(ctx, prefs).firstOrNull { it.name == name }

    /** Az aktuális beállítások mentése megadott néven (azonos nevűt felülír). */
    fun saveCurrent(prefs: Prefs, name: String, mode: String) {
        val v = JSONObject()
        v.put("dir", prefs.translateDir)
        v.put("src", prefs.sourceLang)
        v.put("tgt", prefs.targetLang)
        v.put("model", prefs.modelId)
        v.put("size", prefs.textSizeSp)
        v.put("bg", prefs.bgAlphaPct)
        v.put("color", prefs.textColorIdx)
        v.put("hc", prefs.highContrast)
        v.put("showEn", prefs.showEnglish)
        v.put("eco", prefs.eco)
        v.put("delay", prefs.delayMs)
        v.put("tts", prefs.ttsEnabled)
        v.put("lines", prefs.maxOverlayLines)
        val list = custom(prefs).filter { it.name != name }.toMutableList()
        list.add(Profile(name, mode, v, false))
        write(prefs, list)
    }

    fun delete(prefs: Prefs, name: String) {
        write(prefs, custom(prefs).filter { it.name != name })
    }

    private fun write(prefs: Prefs, list: List<Profile>) {
        val arr = JSONArray()
        for (p in list) {
            arr.put(JSONObject().put("name", p.name).put("mode", p.mode).put("values", p.values))
        }
        prefs.profilesJson = arr.toString()
    }

    /** Profil alkalmazása; a visszatérési érték az indítási mód. */
    fun apply(prefs: Prefs, p: Profile): String {
        if (prefs.meetingMode) MeetingMode.clear(prefs)
        val v = p.values
        if (v.has("style")) prefs.applyStylePreset(v.optInt("style"))
        if (v.has("dir")) prefs.translateDir = v.optString("dir")
        if (v.has("src")) prefs.sourceLang = v.optString("src")
        if (v.has("tgt")) prefs.targetLang = v.optString("tgt")
        if (v.has("model")) prefs.modelId = v.optString("model")
        if (v.has("size")) prefs.textSizeSp = v.optInt("size")
        if (v.has("bg")) prefs.bgAlphaPct = v.optInt("bg")
        if (v.has("color")) prefs.textColorIdx = v.optInt("color")
        if (v.has("hc")) prefs.highContrast = v.optBoolean("hc")
        if (v.has("showEn")) prefs.showEnglish = v.optBoolean("showEn")
        if (v.has("eco")) prefs.eco = v.optBoolean("eco")
        if (v.has("delay")) prefs.delayMs = v.optInt("delay")
        if (v.has("tts")) prefs.ttsEnabled = v.optBoolean("tts")
        if (v.has("lines")) prefs.maxOverlayLines = v.optInt("lines")
        prefs.lastProfile = p.name
        return p.mode
    }
}
