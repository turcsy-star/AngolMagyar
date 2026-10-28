package hu.angolmagyar.fordito

/**
 * Szöveg-utófeldolgozás (angol és magyar): hallucináció- és töltelékszó-szűrés, nagybetűzés, írásjelek,
 * egyéni szótár, valamint a magázó → tegező átalakítás a magyar feliratokhoz.
 *
 * v2.22.3: a v2.22.0-ban egy csonk felülírta a fájlt, ezért a függvények újraírva, tesztekkel.
 */
object TextPolish {

    // ------------------------------------------------------------------ hallucináció, zaj

    /** Önmagukban zajnak számító töltelékhangok. */
    private val NOISE_WORDS = setOf(
        "uh", "um", "uhm", "er", "erm", "ööö", "öö", "őő", "ehm", "öhm"
    )

    private const val L = "\\p{L}\\p{N}"

    private val FILLERS_HU = Regex(
        "(?<![$L])(?:[öÖőŐ]{2,}|[eE]hm+|[öÖ]hm+|hmm+|mhm+|izé|hát|na hát)(?![$L])\\s*,?",
        RegexOption.IGNORE_CASE
    )
    private val FILLERS_EN = Regex(
        "(?<![$L])(?:u+h+m*|u+m+|e+rm?|h+m+|m+h+m+)(?![$L])\\s*,?",
        RegexOption.IGNORE_CASE
    )

    fun isNoise(text: String): Boolean {
        val t = text.trim()
        if (t.isEmpty()) return true
        if (!t.any { it.isLetter() }) return true
        val words = t.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        if (words.all { it in NOISE_WORDS }) return true
        // kizárólag hallucinált sor (pl. "Thanks for watching!")
        val rest = stripHallucinations(t)
        return rest.none { it.isLetterOrDigit() }
    }

    // ------------------------------------------------------------------ alap tisztítás

    private fun collapse(s: String): String = s
        .replace(Regex("\\s+"), " ")
        .replace(Regex("\\s+([,.;:!?…])"), "$1")
        .replace(Regex("^[,;:\\s]+"), "")
        .replace(Regex(",\\s*([.!?…])"), "$1")
        .trim()

    /**
     * Csak az önálló, tipikus feliratvégi zajsorokat távolítjuk el. Egy ilyen kifejezés
     * valódi mondat része is lehet, ezért a szöveg közepén soha nem töröljük.
     */
    private val STANDALONE_HALLUCINATION = Regex(
        "(?i)^\\s*(?:subtitles? by(?: [\\p{L}\\p{N}_ .'-]{1,80})?|thanks(?: you)? for watching|please like and subscribe|köszönöm a figyelmet|köszönjük a figyelmet|feliratot? készítette(?: [\\p{L}\\p{N}_ .'-]{1,80})?|feliratok? [\\p{L}\\p{N}_ .'-]{1,80} készítette|amara\\.org|\\[(?:music|zene|applause|taps|laughter|nevetés)\\]|\\((?:music|zene|applause|laughter|nevetés)\\)|♪+)[.!?…\\s]*$"
    )

    private fun stripHallucinations(text: String): String = text
        .lines()
        .filterNot { line -> STANDALONE_HALLUCINATION.matches(line.trim()) }
        .joinToString("\n")

    /**
     * Biztonságos tisztítás. Alapértelmezésben megőrzi a hezitálásnak tűnő szavakat is,
     * mert a „hát”, „na”, „izé” valódi tartalmat hordozhat. Eltávolítás csak explicit kérésre.
     */
    fun cleanWhisperOutput(raw: String, removeFillers: Boolean = false): String =
        clean(raw, hu = true, en = true, removeFillers = removeFillers)

    private fun clean(raw: String, hu: Boolean, en: Boolean, removeFillers: Boolean = false): String {
        var t = stripHallucinations(raw.trim())
        if (removeFillers && hu) t = FILLERS_HU.replace(t, " ")
        if (removeFillers && en) t = FILLERS_EN.replace(t, " ")
        t = collapse(t)
        return capitalizeFirst(t)
    }

    private fun capitalizeFirst(s: String): String {
        if (s.isEmpty()) return s
        val i = s.indexOfFirst { it.isLetter() }
        if (i < 0) return s
        return s.substring(0, i) + s[i].uppercaseChar() + s.substring(i + 1)
    }

    /** Mondatkezdő nagybetűk: a szöveg elején és a .!?… után. */
    fun capitalizeSentences(s: String): String {
        if (s.isEmpty()) return s
        val sb = StringBuilder(s.length)
        var up = true
        for (ch in s) {
            if (up && ch.isLetter()) {
                sb.append(ch.uppercaseChar()); up = false
            } else {
                sb.append(ch)
                if (ch in ".!?…") up = true
                else if (ch.isLetterOrDigit()) up = false
            }
        }
        return sb.toString()
    }

    /** Hosszabb (legalább 4 szavas) mondat végére pontot tesz, ha nincs lezáró írásjel. Rövid töredéket nem bánt. */
    fun ensurePeriod(s: String): String {
        val t = s.trim()
        if (t.isEmpty()) return t
        val words = t.split(Regex("\\s+")).size
        if (words < 4) return t
        val last = t.last()
        if (last in ".!?…\"”)»:;") return t
        if (last == ',') return t.dropLast(1) + "."
        return "$t."
    }

    // ------------------------------------------------------------------ egyéni szótár

    /**
     * Soronként "szó" (írásmód rögzítése) vagy "hibás=helyes" (csere). Üres sor és a "pl." súgósor kimarad.
     */
    fun parseVocab(text: String): List<Pair<String, String>> =
        text.lines().mapNotNull { line ->
            val l = line.trim()
            if (l.isEmpty() || l.equals("pl.", ignoreCase = true)) return@mapNotNull null
            val i = l.indexOf('=')
            if (i < 0) l to l
            else {
                val a = l.substring(0, i).trim()
                val b = l.substring(i + 1).trim()
                if (a.isEmpty() || b.isEmpty()) null else a to b
            }
        }

    private fun applyVocab(text: String, vocab: List<Pair<String, String>>): String {
        var r = text
        for ((from, to) in vocab) {
            if (from.isBlank()) continue
            r = Regex("(?<![$L])" + Regex.escape(from) + "(?![$L])", RegexOption.IGNORE_CASE)
                .replace(r, Regex.escapeReplacement(to))
        }
        return r
    }

    // ------------------------------------------------------------------ nyelvenkénti finomítás

    fun polishEnglish(
        text: String,
        vocab: List<Pair<String, String>> = emptyList(),
        removeFillers: Boolean = false
    ): String {
        var t = clean(text, hu = false, en = true, removeFillers = removeFillers)
        if (t.isEmpty()) return t
        t = t.replace(Regex("(?<![$L'])i(?=[ ',.!?])"), "I")
            .replace(Regex("\\bi'(m|ll|ve|d)\\b"), "I'$1")
        t = applyVocab(t, vocab)
        return ensurePeriod(capitalizeSentences(t))
    }

    fun polishHungarian(
        text: String,
        vocab: List<Pair<String, String>> = emptyList(),
        removeFillers: Boolean = false
    ): String {
        var t = clean(text, hu = true, en = false, removeFillers = removeFillers)
        if (t.isEmpty()) return t
        t = applyVocab(t, vocab)
        return ensurePeriod(capitalizeSentences(t))
    }

    /** Átirat-szöveg (TranscriptStore formátum) tisztítása: javítólisták + nyelvi finomítás + zajsorok törlése. */
    fun cleanTranscript(
        text: String,
        enRules: List<Pair<Regex, String>>,
        huRules: List<Pair<Regex, String>>
    ): String {
        val cues = SrtExporter.parseCuesFromTranscript(text)
        val out = ArrayList<SrtExporter.Cue>(cues.size)
        for (c in cues) {
            val srcRaw = Fixes.apply(c.source, enRules)
            if (isNoise(srcRaw) && c.target.isBlank()) continue
            val src = if (c.source.trim() == "…") c.source else polishEnglish(srcRaw)
            val tgt = if (c.target.isBlank() || c.target.trim() == "…") c.target
            else polishHungarian(Fixes.apply(c.target, huRules))
            out.add(c.copy(source = src, target = tgt))
        }
        return SrtExporter.toTranscriptText(out)
    }

    // ------------------------------------------------------------------ magázás → tegezés

    // felszólító 3. személy (magázó) → 2. személy (tegező), tárgyatlan / határozatlan ragozás
    private val IMP_INDEF = mapOf(
        "segítsen" to "segíts", "hozzon" to "hozz", "mondjon" to "mondj", "írjon" to "írj",
        "jöjjön" to "jöjj", "menjen" to "menj", "üljön" to "ülj", "álljon" to "állj", "várjon" to "várj",
        "figyeljen" to "figyelj", "nézzen" to "nézz", "kérjen" to "kérj", "lépjen" to "lépj",
        "vegyen" to "végy", "tegyen" to "tégy", "tartson" to "tarts", "maradjon" to "maradj",
        "higgyen" to "higgy", "hagyjon" to "hagyj", "kezdjen" to "kezdj", "válasszon" to "válassz",
        "keressen" to "keress", "találjon" to "találj", "mutasson" to "mutass", "indítson" to "indíts",
        "vigyázzon" to "vigyázz", "hallgasson" to "hallgass", "bízzon" to "bízz",
        "dolgozzon" to "dolgozz", "gondolkodjon" to "gondolkodj", "hívjon" to "hívj",
        "fizessen" to "fizess", "kapcsoljon" to "kapcsolj", "nyomjon" to "nyomj",
        "válaszoljon" to "válaszolj", "meséljen" to "mesélj", "olvasson" to "olvass",
        "tanuljon" to "tanulj", "szóljon" to "szólj", "ismételjen" to "ismételj", "engedjen" to "engedj",
        "kövessen" to "kövess", "legyen" to "légy", "magyarázzon" to "magyarázz", "jelezzen" to "jelezz",
        "próbáljon" to "próbálj", "szálljon" to "szállj", "érjen" to "érj", "mosolyogjon" to "mosolyogj",
        "gyújtson" to "gyújts", "nyisson" to "nyiss", "csináljon" to "csinálj", "adjon" to "adj",
        "kapjon" to "kapj", "fogadjon" to "fogadj", "döntsön" to "dönts", "pihenjen" to "pihenj"
    )

    // tárgyas / határozott ragozás
    private val IMP_DEF = mapOf(
        "olvassa" to "olvasd", "zárja" to "zárd", "nyissa" to "nyisd", "tegye" to "tedd",
        "vegye" to "vedd", "hagyja" to "hagyd", "küldje" to "küldd", "tartsa" to "tartsd",
        "mondja" to "mondd", "írja" to "írd", "csinálja" to "csináld", "nézze" to "nézd",
        "kérje" to "kérd", "vigye" to "vidd", "adja" to "add", "ossza" to "oszd", "hívja" to "hívd",
        "fizesse" to "fizesd", "szedje" to "szedd", "használja" to "használd", "próbálja" to "próbáld",
        "válassza" to "válaszd", "indítsa" to "indítsd", "keresse" to "keresd", "találja" to "találd",
        "mutassa" to "mutasd", "ismételje" to "ismételd", "kapcsolja" to "kapcsold", "nyomja" to "nyomd",
        "hallgassa" to "hallgasd", "kövesse" to "kövesd", "ellenőrizze" to "ellenőrizd",
        "vizsgálja" to "vizsgáld", "engedje" to "engedd", "ismerje" to "ismerd",
        "tudja" to "tudd", "lássa" to "lásd"
    )

    // kérdés eleji ige: 3. sz. → 2. sz. ("Tud …?" → "Tudsz …?"); a 2. elem: tárgyas-e (a/az után is lehet)
    private val Q_INDEF = mapOf(
        "tud" to "tudsz", "akar" to "akarsz", "szeretne" to "szeretnél", "kér" to "kérsz",
        "lát" to "látsz", "hall" to "hallasz", "ért" to "értesz", "segít" to "segítesz",
        "jön" to "jössz", "megy" to "mész", "él" to "élsz", "lakik" to "laksz",
        "dolgozik" to "dolgozol", "szeret" to "szeretsz", "hisz" to "hiszel", "gondol" to "gondolsz",
        "ismer" to "ismersz", "vár" to "vársz", "csinál" to "csinálsz", "kíván" to "kívánsz"
    )

    private val Q_DEF = mapOf(
        "érti" to "érted", "ismeri" to "ismered", "látja" to "látod", "hallja" to "hallod",
        "tudja" to "tudod", "érzi" to "érzed", "szereti" to "szereted", "akarja" to "akarod",
        "kéri" to "kéred", "megérti" to "megérted", "látta" to "láttad", "olvasta" to "olvastad"
    )

    private val THIRD_PERSON = Regex(
        "(?<![$L])(?:ő|ők|neki|nekik|ennek|annak|őt|őket|ez a|az a|a fiú|a lány|a férfi|a nő)(?![$L])",
        RegexOption.IGNORE_CASE
    )

    private val ART = setOf("a", "az")

    private fun matchCase(orig: String, repl: String): String =
        if (orig.isNotEmpty() && orig[0].isUpperCase()) repl.replaceFirstChar { it.uppercaseChar() } else repl

    private fun firstWordRegex(words: Collection<String>): Regex =
        Regex("^(\\s*)(" + words.sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")(?![$L])",
            RegexOption.IGNORE_CASE)

    private val RE_IMP_INDEF = firstWordRegex(IMP_INDEF.keys)
    private val RE_IMP_DEF = firstWordRegex(IMP_DEF.keys)
    private val RE_Q_INDEF = firstWordRegex(Q_INDEF.keys)
    private val RE_Q_DEF = firstWordRegex(Q_DEF.keys)

    private val RE_KEREM = Regex(
        "^(\\s*)(Kérem)(,?\\s+)(" + (IMP_INDEF.keys + IMP_DEF.keys).joinToString("|") + ")(?![$L])",
        RegexOption.IGNORE_CASE
    )

    private val RE_SENT = Regex("[^.!?…]+[.!?…]*\\s*|[.!?…]+\\s*")

    /** Magázó mondatok tegezővé alakítása. Óvatos: csak a biztos mintákat bántja, a többit érintetlenül hagyja. */
    fun informalHungarian(text: String): String {
        if (text.isBlank()) return text
        val sb = StringBuilder()
        for (m in RE_SENT.findAll(text)) sb.append(informalSentence(m.value))
        return sb.toString()
    }

    private fun informalSentence(sent: String): String {
        val core = sent.trimEnd()
        val trail = sent.substring(core.length)
        var s = core
        val question = s.endsWith("?")
        val third = THIRD_PERSON.containsMatchIn(s)

        // névmások
        s = Regex("(?<![$L])az Ön számára(?![$L])", RegexOption.IGNORE_CASE).replace(s) { matchCase(it.value, "neked") }
        s = Regex("(?<![$L])Ön számára(?![$L])", RegexOption.IGNORE_CASE).replace(s) { matchCase(it.value, "neked") }
        val pron = linkedMapOf(
            "Önöknek" to "Nektek", "Önökkel" to "Veletek", "Önöket" to "Titeket",
            "Önnek" to "Neked", "Önnel" to "Veled", "Öntől" to "Tőled", "Önről" to "Rólad",
            "Önhöz" to "Hozzád", "Önért" to "Érted", "Önben" to "Benned", "Önt" to "Téged"
        )
        for ((from, to) in pron) {
            s = Regex("(?<![$L])" + from + "(?![$L])", RegexOption.IGNORE_CASE).replace(s) { matchCase(it.value, to) }
        }
        // "Ön tud …?" → "Te tudsz …?"
        s = Regex("(?<![$L])Ön\\s+(" + (Q_INDEF.keys + Q_DEF.keys).joinToString("|") + ")(?![$L])", RegexOption.IGNORE_CASE)
            .replace(s) { r ->
                val v = r.groupValues[1].lowercase()
                matchCase(r.value, "te " + (Q_INDEF[v] ?: Q_DEF[v] ?: v))
            }

        // "Kérem, segítsen." → "Kérlek, segíts."
        RE_KEREM.find(s)?.let { m ->
            val verb = m.groupValues[4].lowercase()
            val inf = IMP_INDEF[verb] ?: IMP_DEF[verb] ?: verb
            s = m.groupValues[1] + matchCase(m.groupValues[2], "kérlek") + m.groupValues[3] + inf + s.substring(m.value.length)
        }

        // mondatkezdő felszólító
        s = replaceFirstWord(s, RE_IMP_INDEF, IMP_INDEF)
        s = replaceFirstWord(s, RE_IMP_DEF, IMP_DEF)

        // kérdés eleji igék
        if (question && !third) {
            val nextWord = Regex("^\\s*\\p{L}+\\s+(\\p{L}+)").find(s)?.groupValues?.get(1)?.lowercase()
            if (nextWord == null || nextWord !in ART) s = replaceFirstWord(s, RE_Q_INDEF, Q_INDEF)
            s = replaceFirstWord(s, RE_Q_DEF, Q_DEF)
            s = Regex("(?<![$L])szüksége van(?![$L])", RegexOption.IGNORE_CASE)
                .replace(s) { matchCase(it.value, "szükséged van") }
        }
        if (question && !third) {
            s = Regex("(?<![$L])érzi magát(?![$L])", RegexOption.IGNORE_CASE).replace(s) { matchCase(it.value, "érzed magad") }
        }
        // "Remélem, jobban érzi magát."
        s = Regex("(?i)(remélem,?\\s+(?:hogy\\s+)?[^.!?…]*?)érzi magát").replace(s) { "${it.groupValues[1]}érzed magad" }

        return s + trail
    }

    private fun replaceFirstWord(s: String, re: Regex, map: Map<String, String>): String {
        val m = re.find(s) ?: return s
        val w = m.groupValues[2]
        val repl = map[w.lowercase()] ?: return s
        return m.groupValues[1] + matchCase(w, repl) + s.substring(m.value.length)
    }

    /** Igaz, ha a mondat a tegező átalakítás szerint még magázó alakot tartalmaz. */
    fun looksFormal(hu: String): Boolean = informalHungarian(hu) != hu
}
