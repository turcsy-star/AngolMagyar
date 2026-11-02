package hu.angolmagyar.fordito

import java.text.Normalizer
import java.util.Locale

/**
 * Offline szótár többjelentésű és „hamis barát” szavakkal, szófajokkal, példamondatokkal és kiejtési / használati tippekkel.
 * Kurált alapkészlet, nem teljes lexikai adatbázis; a felhasználó saját (CSV-ből betöltött) szótára ettől külön él.
 */
object DictionaryPro {

    data class Sense(val pos: String, val meanings: List<String>) {
        fun line(): String = "$pos ${meanings.joinToString(", ")}"
    }

    data class Entry(
        val word: String,
        val senses: List<Sense>,
        val example: String,
        val exampleHu: String,
        val tip: String
    ) {
        /** A szócikk jelentései egy sorban (pl. „fn. cím; ige megszólít, kezel”). */
        fun meaningLine(): String = senses.joinToString("; ") { it.line() }
    }

    enum class MatchKind { EXACT, PREFIX, CONTAINS, MEANING }

    data class Hit(val entry: Entry, val kind: MatchKind)

    // szó | szófaj: jelentés, jelentés; szófaj: jelentés | példa | magyar példa | tipp
    private val RAW = listOf(
        "actually|hsz.:valójában, tulajdonképpen|Actually, I agree.|Tulajdonképpen egyetértek.|Hamis barát: nem „aktuálisan” (az a currently).",
        "address|fn.:cím; ige:megszólít, kezel (problémát)|What is your address?|Mi a címed?|",
        "afford|ige:megengedhet magának, futja rá|I can't afford it.|Nem engedhetem meg magamnak.|Általában can / could mellett áll.",
        "appreciate|ige:értékel, nagyra becsül, hálás érte|I appreciate your help.|Hálás vagyok a segítségedért.|",
        "available|mn.:elérhető, rendelkezésre álló, szabad|Is this seat available?|Szabad ez a hely?|",
        "break|fn.:szünet; ige:eltör, megszeg|Let's take a break.|Tartsunk szünetet.|",
        "charge|fn.:díj; ige:tölt (akkut), megvádol, felszámol|Charge your phone.|Töltsd fel a telefonod.|",
        "fair|mn.:igazságos, tisztességes; fn.:vásár|That's fair.|Ez igazságos.|",
        "get|ige:kap, szerez, megért, válik|I get it.|Értem.|",
        "issue|fn.:probléma, ügy, kiadás; ige:kibocsát|We have an issue.|Van egy problémánk.|",
        "kind|mn.:kedves; fn.:fajta|What kind of music?|Milyen fajta zene?|",
        "leave|ige:elmegy, otthagy; fn.:szabadság|Leave it here.|Hagyd itt.|",
        "matter|ige:számít; fn.:ügy, dolog, anyag|It doesn't matter.|Nem számít.|",
        "mean|ige:jelent, szándékozik; mn.:gonosz, fukar|What do you mean?|Mire gondolsz?|",
        "miss|ige:hiányol, lekésik, elhibáz|I miss you.|Hiányzol.|",
        "notice|ige:észrevesz; fn.:értesítés, felmondás|Did you notice?|Észrevetted?|",
        "point|fn.:pont, lényeg; ige:rámutat|What's the point?|Mi értelme?|",
        "right|mn.:helyes, jobb oldali; hsz.:azonnal; fn.:jog|You're right.|Igazad van.|",
        "run|ige:fut, működtet, vezet; fn.:futás|The app runs well.|Az alkalmazás jól működik.|",
        "save|ige:ment, megspórol, megment|Save the file.|Mentsd el a fájlt.|",
        "since|elölj.:óta; ksz.:mivel|Since Monday.|Hétfő óta.|Időhatározónál present perfect jár mellé.",
        "still|hsz.:még mindig; mn.:mozdulatlan, csendes|I still remember.|Még mindig emlékszem.|",
        "support|ige:támogat, alátámaszt; fn.:támogatás, ügyfélszolgálat|We support the project.|Támogatjuk a projektet.|",
        "turn|ige:fordul, fordít, bekapcsol (turn on); fn.:sor, fordulat|Turn left.|Fordulj balra.|",
        "value|fn.:érték; ige:nagyra becsül|Good value.|Jó ár-érték arány.|",
        "wonder|ige:tűnődik, kíváncsi; fn.:csoda|I wonder why.|Kíváncsi vagyok, miért.|",
        "book|fn.:könyv; ige:foglal (szobát, jegyet)|I booked a table.|Foglaltam egy asztalt.|",
        "light|fn.:fény, lámpa; mn.:világos, könnyű|The bag is light.|A táska könnyű.|",
        "close|ige:bezár; mn.:közeli; hsz.:közel|Close the door.|Csukd be az ajtót.|Az ige /kləʊz/, a melléknév /kləʊs/: a kiejtés más.",
        "date|fn.:dátum, randevú; ige:randizik|What's the date today?|Hányadika van ma?|",
        "check|ige:ellenőriz; fn.:ellenőrzés, csekk, számla (amerikai)|Check the settings.|Ellenőrizd a beállításokat.|",
        "cool|mn.:hűvös, menő, laza|That's cool.|Ez menő.|",
        "fine|mn.:rendben lévő, finom, kiváló; fn.:bírság|I'm fine, thanks.|Jól vagyok, köszönöm.|",
        "hard|mn.:kemény, nehéz; hsz.:keményen|This is hard.|Ez nehéz.|A „hardly” jelentése: alig (nem „keményen”).",
        "play|ige:játszik, lejátszik; fn.:színdarab|Play the video.|Játszd le a videót.|",
        "watch|ige:néz, figyel; fn.:karóra|Watch out!|Vigyázz!|",
        "bank|fn.:bank, folyópart|Meet me at the bank.|Találkozzunk a banknál.|",
        "bear|ige:elvisel, visel, hordoz; fn.:medve|I can't bear it.|Ezt nem bírom elviselni.|",
        "bill|fn.:számla, törvényjavaslat, bankjegy (amerikai)|Can we have the bill?|Kérhetnénk a számlát?|",
        "case|fn.:eset, ügy, tok|In that case, no.|Ebben az esetben nem.|",
        "common|mn.:gyakori, közös|It's a common mistake.|Ez gyakori hiba.|",
        "content|fn.:tartalom; mn.:elégedett|I'm content.|Elégedett vagyok.|Főnévként /ˈkɒntent/, melléknévként /kənˈtent/.",
        "current|mn.:jelenlegi; fn.:áram|The current version.|Az aktuális verzió.|",
        "express|ige:kifejez; mn.:gyors, expressz|Express your opinion.|Fejezd ki a véleményed.|",
        "figure|fn.:szám, alak, ábra; ige:kitalál, rájön|I can't figure it out.|Nem jövök rá.|",
        "firm|fn.:cég; mn.:szilárd, határozott|A law firm.|Ügyvédi iroda.|",
        "hold|ige:tart, megtart, megrendez|Hold on.|Várj egy kicsit.|",
        "lead|ige:vezet; fn.:vezető szerep, ólom|Lead the way.|Mutasd az utat.|Az ólom (lead) kiejtése /led/, az igéé /liːd/.",
        "mind|fn.:elme; ige:zavar, ellenez|Do you mind?|Zavar?|A „never mind” jelentése: nem számít, felejtsd el.",
        "novel|fn.:regény; mn.:újszerű|A novel idea.|Újszerű ötlet.|",
        "object|fn.:tárgy; ige:tiltakozik|I object!|Tiltakozom!|Főnév: /ˈɒbdʒekt/, ige: /əbˈdʒekt/.",
        "order|fn.:sorrend, rendelés, parancs; ige:rendel, megparancsol|I'd like to order.|Szeretnék rendelni.|",
        "party|fn.:buli, párt, fél (jogi)|The party starts at eight.|A buli nyolckor kezdődik.|",
        "pass|ige:elmegy, átad, átmegy (vizsgán); fn.:bérlet, belépő|Pass me the salt.|Add ide a sót.|",
        "present|fn.:ajándék, jelen; ige:bemutat; mn.:jelen lévő|Here is a present.|Itt egy ajándék.|Főnév és melléknév: /ˈprez(ə)nt/, ige: /prɪˈzent/.",
        "press|ige:nyom, megnyom; fn.:sajtó|Press the button.|Nyomd meg a gombot.|",
        "rest|fn.:pihenés, maradék; ige:pihen|Take a rest.|Pihenj egy kicsit.|",
        "season|fn.:évszak, évad|The new season.|Az új évad.|",
        "second|fn.:másodperc; mn.:második|Wait a second.|Várj egy másodpercet.|",
        "stick|ige:ragad, ragaszt; fn.:bot|It sticks.|Beragad.|",
        "store|fn.:bolt (amerikai), raktár; ige:tárol|Store the data.|Tárold az adatokat.|",
        "strike|ige:üt, sztrájkol; fn.:sztrájk|Workers are on strike.|A dolgozók sztrájkolnak.|",
        "term|fn.:kifejezés, időszak, feltétel|Terms and conditions.|Felhasználási feltételek.|",
        "train|fn.:vonat; ige:edz, betanít|I train every day.|Minden nap edzek.|",
        "will|ige:fog (jövő); fn.:akarat, végrendelet|I will help.|Segíteni fogok.|",
        "fit|ige:ráillik, belefér; mn.:fitt|It doesn't fit.|Nem fér bele.|",
        "pretty|mn.:csinos; hsz.:elég (köznyelvi)|It's pretty good.|Egész jó.|",
        "quite|hsz.:eléggé, egészen|It's quite long.|Elég hosszú.|Amerikai angolban gyakran „nagyon”, britben inkább „eléggé”.",
        "rather|hsz.:inkább, meglehetősen|I'd rather stay.|Inkább maradnék.|",
        "sound|ige:hangzik; fn.:hang; mn.:szilárd, megalapozott|That sounds good.|Jól hangzik.|",
        "stand|ige:áll, elvisel|I can't stand it.|Nem bírom elviselni.|",
        "cover|ige:befed, tárgyal, fedez; fn.:fedél, borító|Cover the pot.|Fedd le az edényt.|",
        "draw|ige:rajzol, húz; fn.:döntetlen|Draw a line.|Húzz egy vonalat.|",
        "drop|ige:ejt, leejt, csökken; fn.:csepp|Don't drop it.|Ne ejtsd le.|",
        "spell|ige:betűz; fn.:varázslat, időszak|How do you spell it?|Hogyan betűzöd?|",
        "eventually|hsz.:végül, idővel|Eventually, we agreed.|Végül megegyeztünk.|Hamis barát: nem „esetleg” (az a possibly vagy perhaps).",
        "sensible|mn.:józan, értelmes|That's a sensible choice.|Ez józan döntés.|Hamis barát: nem „érzékeny” (az a sensitive).",
        "sympathetic|mn.:együttérző|She was sympathetic.|Együttérző volt.|Hamis barát: nem „szimpatikus” (az nice vagy likeable).",
        "actual|mn.:tényleges, valódi|The actual cost.|A tényleges költség.|Hamis barát: nem „aktuális” (az current).",
        "gift|fn.:ajándék, adottság|A birthday gift.|Születésnapi ajándék.|Hamis barát: a német „Gift” méreg, angolul ajándék.",
        "fabric|fn.:szövet, anyag|Soft fabric.|Puha szövet.|Nem gyár (az a factory).",
        "library|fn.:könyvtár|I went to the library.|Elmentem a könyvtárba.|Nem könyvesbolt (az a bookstore).",
        "realize|ige:felismer, rájön|I realized my mistake.|Rájöttem a hibámra.|Nem „megvalósít” (az achieve vagy carry out).",
        "give up|kif.:feladja, abbahagyja|Don't give up.|Ne add fel.|",
        "look for|kif.:keres|I'm looking for my keys.|A kulcsomat keresem.|",
        "find out|kif.:kiderít, megtud|Let's find out.|Derítsük ki.|",
        "figure out|kif.:kitalál, megfejt|I figured it out.|Rájöttem.|",
        "carry on|kif.:folytat|Carry on, please.|Kérlek, folytasd.|",
        "get along|kif.:kijön valakivel, boldogul|We get along well.|Jól kijövünk egymással.|",
        "run out|kif.:kifogy|We ran out of time.|Kifutottunk az időből.|",
        "set up|kif.:beállít, felállít, létrehoz|Set up the account.|Hozd létre a fiókot.|",
        "take off|kif.:felszáll, levesz (ruhát)|The plane took off.|A gép felszállt.|",
        "put off|kif.:elhalaszt|Don't put it off.|Ne halogasd.|",
        "pick up|kif.:felvesz, felvesz (autóba), megtanul|I'll pick you up at eight.|Nyolckor felveszlek.|",
        "work out|kif.:kidolgoz, sikerül, edz|It worked out well.|Jól alakult.|",
        "turn on|kif.:bekapcsol|Turn on the light.|Kapcsold fel a lámpát.|",
        "turn off|kif.:kikapcsol|Turn off the TV.|Kapcsold ki a tévét.|",
        "look forward to|kif.:örömmel vár|I look forward to it.|Várom.|Utána -ing alak jön: looking forward to seeing you.",
        "come up with|kif.:kitalál, előáll valamivel|She came up with a plan.|Kitalált egy tervet.|",
        "make sure|kif.:megbizonyosodik|Make sure it's off.|Győződj meg róla, hogy ki van kapcsolva.|",
        "used to|kif.:régen szokott|I used to smoke.|Régen dohányoztam.|A „be used to + -ing” más: hozzászokott."
    )

    val entries: List<Entry> by lazy { RAW.map { parse(it) } }

    private fun parse(line: String): Entry {
        val p = line.split("|")
        val senses = p[1].split(";").map { it.trim() }.filter { it.isNotEmpty() }.map { part ->
            val pos = part.substringBefore(':').trim()
            val meanings = part.substringAfter(':').split(",").map { it.trim() }.filter { it.isNotEmpty() }
            Sense(pos, meanings)
        }
        return Entry(p[0].trim(), senses, p.getOrElse(2) { "" }, p.getOrElse(3) { "" }, p.getOrElse(4) { "" })
    }

    private fun fold(s: String): String =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

    fun lookup(word: String): Entry? = entries.firstOrNull { it.word.equals(word.trim(), ignoreCase = true) }

    /**
     * Keresés angol szóra (pontos → előtag → tartalmazza) és magyar jelentésre (ékezet nélkül is).
     * A találatok a pontosság szerint rendezettek.
     */
    fun search(query: String, limit: Int = 12): List<Hit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val lq = q.lowercase(Locale.ROOT)
        val fq = fold(q)
        val hits = ArrayList<Pair<Int, Hit>>()
        for (e in entries) {
            val w = e.word.lowercase(Locale.ROOT)
            val kind = when {
                w == lq -> MatchKind.EXACT
                w.startsWith(lq) -> MatchKind.PREFIX
                w.contains(lq) -> MatchKind.CONTAINS
                fold(e.senses.joinToString(" ") { it.meanings.joinToString(" ") }).contains(fq) -> MatchKind.MEANING
                else -> null
            }
            if (kind != null) hits.add(kind.ordinal to Hit(e, kind))
        }
        return hits.sortedWith(compareBy({ it.first }, { it.second.entry.word })).map { it.second }.take(limit)
    }
}
