package hu.angolmagyar.fordito

/**
 * Átirat / felirat utófeldolgozás: írásjelek, nagybetű, locked terms, zaj-szavak szűrése.
 */
object TextPolish {

    /** Gyakori angol zaj / töltelékszavak – alacsony információ, eldobható short finalnél. */
    private val NOISE = setOf(
        "uh", "um", "ah", "eh", "hm", "hmm", "mhm", "mm", "er", "oh", "huh",
        "the", "a", "an", "to", "of", "and"
    )

    /** Rövid, de értelmes szavak: ezeket sosem dobjuk el (igen/nem/oké…). */
    private val SHORT_OK = setOf(
        "no", "ok", "so", "hi", "go", "me", "we", "he", "my", "it", "is", "in", "on", "up",
        "i", "yes", "yeah", "yep", "nope", "now", "why", "who", "how", "what", "stop", "wait",
        "igen", "nem", "jó", "ja", "na", "de", "és", "mi", "ki", "én", "te", "ő", "ez", "az"
    )

    /** Alap domain / locked kifejezések (kisbetűs kulcs → megjelenítendő forma). */
    val DEFAULT_LOCKED = listOf(
        "coldplay" to "Coldplay",
        "netflix" to "Netflix",
        "youtube" to "YouTube",
        "spotify" to "Spotify",
        "iphone" to "iPhone",
        "ipad" to "iPad",
        "android" to "Android",
        "budapest" to "Budapest",
        "hungary" to "Hungary",
        "hungarian" to "Hungarian",
        "english" to "English",
        "chatgpt" to "ChatGPT",
        "openai" to "OpenAI",
        "google" to "Google",
        "microsoft" to "Microsoft",
        "tesla" to "Tesla",
        "elon musk" to "Elon Musk",
        "new york" to "New York",
        "los angeles" to "Los Angeles",
        "san francisco" to "San Francisco",
        "united states" to "United States",
        "united kingdom" to "United Kingdom"
    )

    fun isNoise(text: String): Boolean {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return true
        val words = t.split(Regex("\\s+"))
        if (words.size == 1) {
            val w = words[0]
            if (w in SHORT_OK) return false
            if (w in NOISE) return true
            // egybetűs / kétbetűs maradék: valószínűleg felismerési zaj
            if (w.length <= 2 && w.all { it.isLetter() }) return true
        }
        return false
    }

    /** Locked terms alkalmazása (egész szóra / kifejezésre). */
    fun applyLocked(text: String, extra: List<Pair<String, String>> = emptyList()): String {
        var r = text
        val all = DEFAULT_LOCKED + extra
        // hosszabb kifejezések először
        val sorted = all.sortedByDescending { it.first.length }
        for ((from, to) in sorted) {
            val re = Regex(
                "(?<![\\p{L}\\p{N}])" + Regex.escape(from) + "(?![\\p{L}\\p{N}])",
                RegexOption.IGNORE_CASE
            )
            r = re.replace(r) { to }
        }
        return r
    }

    /** Egyszerű nagybetű mondat elején + i → I. */
    fun capitalizeSentences(text: String): String {
        if (text.isBlank()) return text
        val sb = StringBuilder(text.trim())
        // leading
        if (sb.isNotEmpty() && sb[0].isLowerCase()) {
            sb.setCharAt(0, sb[0].uppercaseChar())
        }
        // after .?!
        var i = 1
        while (i < sb.length - 1) {
            val c = sb[i]
            if (c == '.' || c == '!' || c == '?') {
                var j = i + 1
                while (j < sb.length && sb[j].isWhitespace()) j++
                if (j < sb.length && sb[j].isLowerCase()) {
                    sb.setCharAt(j, sb[j].uppercaseChar())
                }
            }
            i++
        }
        // standalone i
        return Regex("(?<![\\p{L}])i(?![\\p{L}])").replace(sb.toString()) { "I" }
    }

    /**
     * Ha a szöveg nem végződik írásjelre és elég hosszú, opcionálisan pont.
     * Élő feliratnál óvatosan – csak final + hosszú mondat.
     */
    fun ensurePeriod(text: String, minWords: Int = 5): String {
        val t = text.trim()
        if (t.isEmpty()) return t
        if (t.last() in charArrayOf('.', '!', '?', ',', ';', ':')) return t
        val words = t.split(Regex("\\s+")).size
        return if (words >= minWords) "$t." else t
    }

    /** Személyes szótár: soronként „forrás=cél” vagy csak szó (önmagára normalizál). */
    fun parseVocab(text: String): List<Pair<String, String>> =
        text.lines().mapNotNull { line ->
            val t = line.trim()
            if (t.isEmpty() || t.startsWith("#")) return@mapNotNull null
            val i = t.indexOf('=')
            if (i <= 0) {
                t.lowercase() to t
            } else {
                val from = t.substring(0, i).trim()
                val to = t.substring(i + 1).trim()
                if (from.isEmpty()) null else from.lowercase() to to
            }
        }

    fun polishEnglish(text: String, lockedExtra: List<Pair<String, String>> = emptyList()): String {
        var t = text.trim()
        if (t.isEmpty()) return t
        t = applyLocked(t, lockedExtra)
        t = capitalizeSentences(t)
        return t
    }

    fun polishHungarian(text: String, lockedExtra: List<Pair<String, String>> = emptyList()): String {
        var t = text.trim()
        if (t.isEmpty()) return t
        if (lockedExtra.isNotEmpty()) t = applyLocked(t, lockedExtra)
        t = capitalizeSentences(t)
        return t
    }

    /** Teljes átirat szöveg tisztítása (soronként, TranscriptStore formátum). */
    fun cleanTranscript(raw: String, enRules: List<Pair<Regex, String>>, huRules: List<Pair<Regex, String>>): String {
        val re = Regex("""\[(\d{2}:\d{2}:\d{2})\]\s*(.+)\n\s+(.+)""")
        val spRe = Regex("""^\{([^\}]*)\}\s*""")
        val sb = StringBuilder()
        var matched = false
        for (m in re.findAll(raw)) {
            matched = true
            val stamp = m.groupValues[1]
            var en = m.groupValues[2].trim()
            var hu = m.groupValues[3].trim()
            // beszélő-jelölés ({Anna}) megőrzése
            var spk = ""
            spRe.find(en)?.let { sm ->
                spk = sm.value
                en = en.substring(sm.value.length)
            }
            en = Fixes.apply(en, enRules)
            en = polishEnglish(en)
            hu = Fixes.apply(hu, huRules)
            hu = polishHungarian(hu)
            sb.append("[$stamp] $spk$en\n           $hu\n\n")
        }
        if (!matched) {
            // plain lines
            for (line in raw.lines()) {
                if (line.isBlank()) continue
                sb.append(polishEnglish(line)).append('\n')
            }
        }
        return sb.toString().trimEnd() + "\n"
    }
}
