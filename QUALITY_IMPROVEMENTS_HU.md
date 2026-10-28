# AngolMagyar v2.28.7 – minőségi fejlesztések és ellenőrzési terv

## Ebben a verzióban megvalósítva

### Ellenőrzési prioritás (v2.28.7)
- A „Legrosszabb 10 sor” nézet a jelzések súlyozott súlyossága szerint rendez, nem időrendben.
- A gyenge szókonfidencia nagyobb prioritást kap; a beszéd/csend gyanúja, a töredék és a szokatlan hosszarány közepes súlyú.
- Ez a rendezés csak az ellenőrzési sorrendet módosítja; nem töröl és nem ír át szöveget.

### Tartalmi hűség
- A `TextPolish.cleanWhisperOutput()` és a nyelvenkénti utófeldolgozás alapból nem távolít el töltelékszavakat.
- A töltelékszó-eltávolítás külön, explicit `removeFillers = true` paraméterrel kérhető.
- A feliratvégi hallucinációs minták csak önálló sorra illeszkednek; a mondat közepén álló kifejezések nem törlődnek.
- A nagyon alacsony logprob önmagában nem elég ok egy sor törlésére.
- A hosszú csendben álló rövid sor megmarad és ellenőrzendőként jelenik meg a minőségellenőrzésben.
- Az ismétlődő szövegek szűrése 30 másodperces ablakra korlátozott, így a távol egymástól elhangzó, valóban ismételt mondatokat nem dobja el.

### Szókonfidencia és célzott ellenőrzés (v2.28.6)
- A Whisper meglévő tokenenkénti szöveg-, valószínűség- és időadatából szó-szintű, konzervatív jelzés készül.
- Az SRT-szerkesztő a gyenge tokenekhez tartozó forrásszavakat narancssárga aláhúzással mutatja; a sor lejátszásával a megfelelő hangszakasz visszahallgatható.
- A szegmens átlagos logprobja külön marad a szókonfidenciától.
- A no-speech becslés csak figyelmeztet, nem töröl automatikusan.
- Az ellenőrzőpont új formátuma menti a pontszámokat is, de a korábbi, ötoszlopos checkpointok továbbra is betölthetők.

### Minőségjelzés és mérés
- `QualityRules` közös küszöböket biztosít a minőségjelzéshez és a hallucináció-ellenőrzéshez.
- A fájlfelismerés a meglévő egyéni szótár kanonikus alakjait is átadja a rövid, nyelvhelyes Whisper-promptnak; a hibás bemeneti alakokat nem erősíti meg.
- `WordErrorRate` kiszámítja a szubsztitúciók, törlések, beszúrások számát és a WER-t.
- A WER a kis-/nagybetűt és írásjeleket normalizálja, de a magyar ékezeteket nem tünteti el.
- Új tesztek fedik a tartalommegőrzést, az önálló hallucinációs sorokat, a távoli ismétléseket és a WER-t.

### Meglévő modulok megtartása
- Megmarad a meglévő Compose Material 3 kezdőlap, animált hullámforma, SRT-szerkesztő, fordítási memória, VAD, Whisper-prompt és online/offline fordítás.
- Nem készült párhuzamos felismerőmotor vagy második UI-architektúra.

## Minőségtesztelési javaslat

1. Készíts 20–50 rövid, kézzel ellenőrzött referenciát különböző hangminőségekkel.
2. Futtasd ugyanazokat a fájlokat a korábbi és az új verzión.
3. Hasonlítsd össze a WER-t és kézzel ellenőrizd a számok, nevek, tagadások, rövid mondatok és mondathatárok hibáit.
4. Külön mérd a feldolgozási időt; az automatikus második felismerési körök számát ne növeld általánosan.
5. Ne tekintsd a WER-t fordításminőségi mutatónak: az csak a referenciához viszonyított felismerési hibát méri.

## Build-ellenőrzés

A `:core` Kotlin-forrásai a kiadás előkészítésekor lefordultak `kotlinc` segítségével. A smoke teszt ellenőrizte, hogy a „hát” és „izé” megmarad, az önálló „Subtitles by …” sor eltávolítható, a valódi mondatban szereplő „thanks for watching” megmarad, az alacsony logprobú sor pedig nem törlődik.

A teljes Android APK-fordítás és a JUnit tesztcsomag futtatása ebben a környezetben nem történt meg. Az eredeti ZIP-ben hiányzik a `gradle/wrapper/gradle-wrapper.jar`, és nincs telepített Android SDK. Android Studio vagy megfelelő SDK-val rendelkező gép esetén futtasd:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :wear:assembleDebug
```

Ha a wrapper JAR hiányzik, előbb állítsd helyre a projekt Gradle Wrapperét, majd futtasd a parancsokat.
