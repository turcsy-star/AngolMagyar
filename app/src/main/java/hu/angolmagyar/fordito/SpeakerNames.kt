package hu.angolmagyar.fordito

/**
 * S1/S2… → emberi nevek (pl. S1=Anna, S2=Péter).
 * Prefs.speakerNames: soronként „S1=Név” vagy „1=Név”.
 */
object SpeakerNames {

    fun parse(text: String): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for (line in text.lines()) {
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) continue
            val i = t.indexOf('=')
            if (i <= 0) continue
            var key = t.substring(0, i).trim().uppercase()
            val name = t.substring(i + 1).trim()
            if (name.isEmpty()) continue
            if (key.matches(Regex("\\d+"))) key = "S$key"
            if (!key.startsWith("S")) key = "S$key"
            map[key] = name
        }
        return map
    }

    /** speakerId 0-based → megjelenített név (Anna) vagy S1. */
    fun label(speakerId: Int, map: Map<String, String>): String {
        val key = "S${speakerId + 1}"
        return map[key] ?: key
    }

    fun label(key: String, map: Map<String, String>): String {
        val k = key.trim().uppercase().let {
            when {
                it.matches(Regex("\\d+")) -> "S$it"
                it.startsWith("S") -> it
                else -> it
            }
        }
        return map[k] ?: key
    }

    fun fromPrefs(prefs: Prefs): Map<String, String> = parse(prefs.speakerNames)
}
