package hu.angolmagyar.fordito

/**
 * Smart Context Translation – v2.64.0
 *
 * Kontextus-, kétértelműség- és hangnem-alapú fordítási alternatívák.
 * Teljesen offline, szabályalapú, nem generatív AI.
 * A javaslatok soha nem írják felül automatikusan a fordítást;
 * csak ellenőrzésre és választásra szolgálnak.
 */
object SmartContextTranslation {

    enum class Tone { NEUTRAL, FORMAL, INFORMAL, TECHNICAL, FRIENDLY }
    enum class AmbiguityLevel { NONE, LOW, MEDIUM, HIGH }

    data class Alternative(
        val text: String,
        val tone: Tone,
        val reason: String,
        val confidenceHint: String = "heurisztikus"
    )

    data class ContextSuggestion(
        val source: String,
        val primary: String,
        val alternatives: List<Alternative>,
        val ambiguity: AmbiguityLevel,
        val notes: List<String>
    )

    /** Gyakori kétértelmű angol szavak és lehetséges magyar irányok (rövid lista). */
    private val AMBIGUOUS = mapOf(
        "bank" to listOf(
            Alternative("pénzintézet", Tone.NEUTRAL, "pénzügyi intézmény"),
            Alternative("part", Tone.NEUTRAL, "folyópart / tengerpart")
        ),
        "light" to listOf(
            Alternative("fény", Tone.NEUTRAL, "világosság / lámpa"),
            Alternative("könnyű", Tone.NEUTRAL, "súly / nehézség"),
            Alternative("világos", Tone.NEUTRAL, "szín / világosság")
        ),
        "right" to listOf(
            Alternative("jobb", Tone.NEUTRAL, "irány / helyes"),
            Alternative("jog", Tone.FORMAL, "jogi értelem"),
            Alternative("helyes", Tone.NEUTRAL, "igaz / korrekt")
        ),
        "left" to listOf(
            Alternative("bal", Tone.NEUTRAL, "irány"),
            Alternative("elhagyott", Tone.NEUTRAL, "past tense of leave")
        ),
        "set" to listOf(
            Alternative("készlet", Tone.NEUTRAL, "halmaz / készlet"),
            Alternative("beállít", Tone.TECHNICAL, "konfigurálás"),
            Alternative("kész", Tone.NEUTRAL, "kész / beállított")
        ),
        "run" to listOf(
            Alternative("fut", Tone.NEUTRAL, "mozgás"),
            Alternative("futtat", Tone.TECHNICAL, "program / folyamat"),
            Alternative("üzemel", Tone.TECHNICAL, "gép / szolgáltatás")
        ),
        "charge" to listOf(
            Alternative("tölt", Tone.TECHNICAL, "akkumulátor / energia"),
            Alternative("díj", Tone.FORMAL, "pénzügyi / költség"),
            Alternative("vád", Tone.FORMAL, "jogi")
        ),
        "match" to listOf(
            Alternative("mérkőzés", Tone.NEUTRAL, "sport"),
            Alternative("egyezés", Tone.NEUTRAL, "összehasonlítás"),
            Alternative("gyufa", Tone.NEUTRAL, "tárgy")
        ),
        "check" to listOf(
            Alternative("ellenőriz", Tone.NEUTRAL, "vizsgálat"),
            Alternative("csekk", Tone.NEUTRAL, "pénzügyi"),
            Alternative("jelöl", Tone.TECHNICAL, "pipa / jelölőnégyzet")
        ),
        "order" to listOf(
            Alternative("rendelés", Tone.NEUTRAL, "kereskedelmi"),
            Alternative("rend", Tone.FORMAL, "sorrend / fegyelem"),
            Alternative("parancs", Tone.FORMAL, "utasítás")
        )
    )

    private val FORMAL_MARKERS = listOf(
        "please", "kindly", "would you", "could you", "sir", "madam",
        "regarding", "concerning", "pursuant", "hereby", "therefore"
    )
    private val INFORMAL_MARKERS = listOf(
        "hey", "hi ", "yeah", "ok ", "okay", "cool", "gonna", "wanna",
        "gotta", "lol", "btw", "asap"
    )
    private val TECHNICAL_MARKERS = listOf(
        "api", "sdk", "endpoint", "payload", "latency", "throughput",
        "algorithm", "dataset", "model", "pipeline", "deploy", "runtime",
        "bytecode", "compiler", "debug", "stacktrace"
    )

    /**
     * Forrásszöveg alapján javasol kontextus-érzékeny alternatívákat.
     * @param sourceEnglish a fordítandó angol szöveg
     * @param currentHungarian opcionális jelenlegi fordítás (ha van)
     * @param preferredTone preferált hangnem (null = automatikus érzékelés)
     */
    fun suggest(
        sourceEnglish: String,
        currentHungarian: String? = null,
        preferredTone: Tone? = null
    ): ContextSuggestion {
        val src = sourceEnglish.trim()
        val notes = mutableListOf<String>()
        val alts = mutableListOf<Alternative>()
        var ambiguity = AmbiguityLevel.NONE

        if (src.isEmpty()) {
            return ContextSuggestion(src, currentHungarian.orEmpty(), emptyList(), AmbiguityLevel.NONE,
                listOf("Üres forrásszöveg – nincs javaslat."))
        }

        val detectedTone = preferredTone ?: detectTone(src)
        notes += "Érzékelt hangnem: ${toneLabel(detectedTone)}"

        // Kétértelmű szavak keresése
        val lower = src.lowercase()
        val words = lower.split(Regex("[^\\p{L}]+")).filter { it.length >= 3 }
        var ambCount = 0
        for (index in words.indices) {
            val w = words[index]
            val candidates = AMBIGUOUS[w] ?: continue
            ambCount++
            val sense = inferSense(w, words, index)
            val selected = when {
                sense == "financial" && w == "bank" -> candidates.filter { it.text == "pénzintézet" }
                sense == "geographic" && w == "bank" -> candidates.filter { it.text == "part" }
                sense == "weight" && w == "light" -> candidates.filter { it.text == "könnyű" }
                sense == "illumination" && w == "light" -> candidates.filter { it.text == "fény" || it.text == "világos" }
                sense == "battery" && w == "charge" -> candidates.filter { it.text == "tölt" }
                sense == "financial" && w == "charge" -> candidates.filter { it.text == "díj" }
                sense == "legal" && w == "charge" -> candidates.filter { it.text == "vád" }
                else -> candidates
            }.ifEmpty { candidates }
            val evidence = if (sense == null) "a környező szavak nem döntik el egyértelműen" else "a környező szavak alapján valószínűsített jelentés: $sense"
            selected.forEach { alt ->
                if (alts.none { it.text == alt.text }) {
                    alts += alt.copy(
                        reason = "„$w” szó kétértelmű; $evidence (${alt.reason})"
                    )
                }
            }
        }
        ambiguity = when {
            ambCount >= 3 -> AmbiguityLevel.HIGH
            ambCount == 2 -> AmbiguityLevel.MEDIUM
            ambCount == 1 -> AmbiguityLevel.LOW
            else -> AmbiguityLevel.NONE
        }
        if (ambCount > 0) {
            notes += "$ambCount potenciálisan kétértelmű szó található a forrásban."
        }

        // Hangnem-alapú finomítás
        when (detectedTone) {
            Tone.FORMAL -> {
                alts += Alternative(
                    text = currentHungarian?.let { makeMoreFormal(it) } ?: "",
                    tone = Tone.FORMAL,
                    reason = "Formálisabb megfogalmazás javasolt (magázás / hivatalos stílus)"
                )
                notes += "Formális regiszter észlelve – magázó alakok előnyben."
            }
            Tone.INFORMAL -> {
                notes += "Informális regiszter – tegező, laza stílus elfogadható."
            }
            Tone.TECHNICAL -> {
                notes += "Műszaki szöveg – terminológia konzisztencia fontos."
            }
            Tone.FRIENDLY -> {
                notes += "Barátságos hangnem – meleg, közvetlen fordítás ajánlott."
            }
            Tone.NEUTRAL -> { /* no extra */ }
        }

        // Hossz- és struktúra alapú megjegyzések
        val wordCount = words.size
        if (wordCount > 40) {
            notes += "Hosszú mondat/szöveg – javasolt a tagolás vagy több alternatíva vizsgálata."
        }
        if (src.contains("?") && !src.contains("!")) {
            notes += "Kérdő mondat – a magyar fordításban a szórend és a kérdőszó elhelyezése kritikus."
        }

        val primary = currentHungarian?.trim().orEmpty()
        val filteredAlts = alts
            .filter { it.text.isNotBlank() && it.text != primary }
            .distinctBy { it.text.lowercase() }
            .take(6)

        return ContextSuggestion(
            source = src,
            primary = primary,
            alternatives = filteredAlts,
            ambiguity = ambiguity,
            notes = notes
        )
    }


    /**
     * Óvatos, lokális kontextusbecslés néhány gyakori kétértelmű szóra.
     * A találat nem bizonyítja a jelentést; csak az alternatívák szűkítésére szolgál.
     */
    private fun inferSense(word: String, words: List<String>, index: Int): String? {
        val window = words.subList(maxOf(0, index - 3), minOf(words.size, index + 4)).toSet()
        return when (word) {
            "bank" -> when {
                window.any { it in setOf("account", "money", "loan", "cash", "credit", "deposit", "branch") } -> "financial"
                window.any { it in setOf("river", "shore", "beach", "water", "fishing", "boat") } -> "geographic"
                else -> null
            }
            "light" -> when {
                window.any { it in setOf("weight", "bag", "carry", "heavy", "lighter") } -> "weight"
                window.any { it in setOf("lamp", "bulb", "bright", "dark", "switch", "shine") } -> "illumination"
                else -> null
            }
            "charge" -> when {
                window.any { it in setOf("battery", "phone", "charger", "usb", "power") } -> "battery"
                window.any { it in setOf("fee", "price", "payment", "cost", "service") } -> "financial"
                window.any { it in setOf("court", "police", "crime", "criminal", "accused") } -> "legal"
                else -> null
            }
            else -> null
        }
    }

    /** Egyszerű hangnem-érzékelés marker-szavak alapján. */
    fun detectTone(text: String): Tone {
        val lower = text.lowercase()
        var formal = 0
        var informal = 0
        var technical = 0
        for (m in FORMAL_MARKERS) if (lower.contains(m)) formal++
        for (m in INFORMAL_MARKERS) if (lower.contains(m)) informal++
        for (m in TECHNICAL_MARKERS) if (lower.contains(m)) technical++

        return when {
            technical >= 2 -> Tone.TECHNICAL
            formal >= 2 && formal > informal -> Tone.FORMAL
            informal >= 2 && informal > formal -> Tone.INFORMAL
            formal > 0 && informal == 0 -> Tone.FORMAL
            informal > 0 && formal == 0 -> Tone.INFORMAL
            else -> Tone.NEUTRAL
        }
    }

    private fun toneLabel(t: Tone): String = when (t) {
        Tone.NEUTRAL -> "semleges"
        Tone.FORMAL -> "formális"
        Tone.INFORMAL -> "informális"
        Tone.TECHNICAL -> "műszaki"
        Tone.FRIENDLY -> "barátságos"
    }

    /** Nagyon egyszerű formálisabbá tétel (csak demonstrációs célra). */
    private fun makeMoreFormal(hu: String): String {
        var s = hu
        s = s.replace(Regex("\\bte\\b", RegexOption.IGNORE_CASE), "Ön")
        s = s.replace(Regex("\\btéged\\b", RegexOption.IGNORE_CASE), "Önt")
        s = s.replace(Regex("\\bneked\\b", RegexOption.IGNORE_CASE), "Önnek")
        return s
    }

    /**
     * Gyors ellenőrzés: van-e a szövegben ismert kétértelmű szó.
     */
    fun hasAmbiguity(sourceEnglish: String): Boolean {
        val words = sourceEnglish.lowercase().split(Regex("[^\\p{L}]+"))
        return words.any { it in AMBIGUOUS }
    }
}
