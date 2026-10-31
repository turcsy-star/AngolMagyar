package hu.angolmagyar.fordito

/** Helyzethez igazított tippek: a hibaüzenetből / az eredményből következő teendők magyarul. */
object Tips {
    fun forError(msg: String): List<String> {
        val m = msg.lowercase()
        val out = ArrayList<String>()
        if ("memória" in m || "outofmemory" in m || "oom" in m)
            out.add("Kevés a memória: zárd be a többi appot, vagy válaszd a „Kímélő” teljesítményt. Nagyon hosszú videót érdemes részletekben feldolgozni.")
        if ("modell" in m && ("nem tölthető" in m || "letölt" in m || "hiányz" in m))
            out.add("A modell hiányzik vagy sérült: töltsd le újra a Modellek képernyőn (Wi-Fi-n).")
        if ("whisper" in m && "betölt" in m)
            out.add("A Whisper motor nem indult el. Indítsd újra az appot; ha marad, küldd el a hibajelentést.")
        if ("api-kulcs" in m || "http 401" in m || "http 403" in m)
            out.add("Az online szolgáltató elutasította a kulcsot: ellenőrizd az API-kulcsot és a modellnevet az Online szolgáltatás beállításánál.")
        if ("hálózati" in m || "timeout" in m || "unable to resolve" in m)
            out.add("Nincs elérhető internet (vagy a helyi szerver): „Online + offline tartalék” módban a fordítás offline is elkészül.")
        if ("dekód" in m || "decode" in m || "codec" in m)
            out.add("A fájl hangja nem olvasható: próbáld másik formátumba (pl. MP4/AAC) alakítva, vagy másold át a telefon tárhelyére.")
        if ("mentés" in m && "hely" in m)
            out.add("Nincs hova menteni: a „Mentés helye” kártyán válassz mappát, vagy használd a „SRT mentése a telefonra…” gombot.")
        if (out.isEmpty()) out.add("Ha ismétlődik, a „Hibajelentés / napló küldése…” gombbal elküldheted a naplót.")
        return out
    }

    /** A kész felirat alapján: üres kimenet, túl kevés beszéd, sok jelölt sor. */
    fun forResult(blocks: Int, flagged: Int, lang: String): List<String> {
        val out = ArrayList<String>()
        if (blocks == 0) {
            out.add("Üres lett a felirat. Ellenőrizd, hogy a hang nyelve ($lang) jó-e, és hogy van-e beszéd a fájlban; a hallucináció-szűrő is ki van-e kapcsolva szükség esetén.")
        } else if (blocks >= 20 && flagged * 100 / blocks >= 25) {
            out.add("Sok a jelölt sor ($flagged / $blocks): próbáld a pontosító második menetet, vagy add meg a szereplők neveit a Projekt kártyán.")
        }
        return out
    }
}
