# Fejlesztési javaslatok – státusz 2.43.0

Ez a dokumentum elkülöníti a ténylegesen bekerült fejlesztéseket a további munkát igénylő ötletektől.

| Terület | Státusz | Megjegyzés |
|---|---|---|
| Platinum/Gold központi Compose-tokenek | Részben megvalósítva | A központi tokenek és komponensek megvannak; a teljes View-képernyőmigráció nincs kész. |
| Prémium fordítási kártya | Megvalósítva a Compose élő kártyában | Forrás/fordítás, másolás, felolvasás, mentés. |
| Online LLM stílusválasztás | Megvalósítva | Természetes/Pontos/Hivatalos/Közvetlen; offline/DeepL nem módosul. |
| Saját helyi szókincstár | Megvalósítva alapfunkciókkal | Mentés, keresés, másolás, törlés; nincs felhőszinkron. |
| Magyar felolvasás | Megvalósítva alapfunkcióval | Android TTS; elérhetőség a telepített beszédhangtól függ. |
| Beszédállapotok és kétirányú élő fordítás | Meglévő alapok, további validáció szükséges | Valódi eszközteszt és megszakítási/életciklus-tesztek szükségesek. |
| Kontextusmagyarázat és többértelműség jelzése | További fejlesztés | A meglévő online háttér/hint nem teljes kontextusmagyarázó funkció. |
| Ismétléses tanulóalgoritmus | További fejlesztés | A mentett kifejezések még nem ütemeznek ismétlést. |
| Kiejtéslabor, brit/amerikai hang és kiejtési pontszám | További fejlesztés | Pontozáshoz valódi fonetikai/ASR-alapú mérés szükséges. |
| Adatvédelem | Részben megvalósítva | A szókincstár helyi és csak manuálisan ment; a hálózati szolgáltatók adatkezelését külön ellenőrizni kell. |
| Performance és akkumulátor | Részben megvalósítva | Üresjárati frissítések csökkentése megvan; mért benchmark nincs. |
| Build/CI | Automatizálás konfigurálva, eredmény nem igazolt | Gradle 8.9 letöltése hálózati hibán elbukott. |
| Vizuális regresszióteszt | Részben megvalósítva | Screenshot smoke teszt van; jóváhagyott golden-baseline összehasonlítás hiányzik. |

**Kiadási állapot:** forráscsomag elkészítve, de build- és tesztigazolás nélkül nem tekinthető bizonyítottan buildelhető kiadásnak.


## 2.44.0 kiegészítés

- Elkészült: helyi szókincsismétlés, fordítás felfedése, „Tudtam” / „Nem tudtam” értékelés és fokozatos időzítés. Részletek: `ULTIMATE_2.44.0_HU.md`.
- Továbbra is hátralévő tételek: kontextusmagyarázat, kiejtéslabor, teljes Compose-migráció, valódi golden screenshot összehasonlítás és fizikai eszközös ellenőrzés.
