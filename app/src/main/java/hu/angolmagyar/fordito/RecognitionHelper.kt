package hu.angolmagyar.fordito

import org.json.JSONArray
import org.json.JSONObject

/**
 * Vosk JSON eredmény feldolgozása: szöveg + opcionális confidence.
 * A Vosk result formátum: {"text": "...", "result": [{"word": "...", "conf": 0.95, ...}, ...]}
 */
object RecognitionHelper {

    data class Result(val text: String, val confidence: Float)

    fun parseResult(json: String): Result {
        return try {
            val obj = JSONObject(json)
            val text = obj.optString("text", "").trim()
            val conf = averageConfidence(obj)
            Result(text, conf)
        } catch (_: Exception) {
            Result("", 0f)
        }
    }

    fun parsePartial(json: String): String {
        return try {
            JSONObject(json).optString("partial", "").trim()
        } catch (_: Exception) {
            ""
        }
    }

    /** Átlagos szó-confidence; ha nincs result tömb, 1.0 (elfogadjuk). */
    private fun averageConfidence(obj: JSONObject): Float {
        val arr: JSONArray = obj.optJSONArray("result") ?: return 1f
        if (arr.length() == 0) return 1f
        var sum = 0.0
        var n = 0
        for (i in 0 until arr.length()) {
            val w = arr.optJSONObject(i) ?: continue
            if (w.has("conf")) {
                sum += w.optDouble("conf", 0.0)
                n++
            }
        }
        return if (n == 0) 1f else (sum / n).toFloat()
    }

    /** true, ha a confidence eléri a küszöböt (küszöb 0–100; 0 = minden átmegy). */
    fun passes(confidence: Float, minPercent: Int): Boolean {
        if (minPercent <= 0) return true
        return confidence * 100f >= minPercent
    }
}
