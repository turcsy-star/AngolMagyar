# AngolMagyar – összes fejlesztési javaslat megvalósítási terve

## v2.28.8-dev frissítés

- A `ReviewIntelligence` már az `SrtEditorActivity` eszközei között is megjelenik.
- Az „Ellenőrzési javaslatok” megnyitja a felismert jelzések listáját, súlyossággal és indoklással.
- Egy listaelem kiválasztásával a szerkesztő az adott feliratblokk oldalára navigál.
- A jelzések nem végeznek automatikus tartalmi javítást.
- Az Android APK-build és az eszközteszt továbbra sincs igazolva.

## Ebben a fejlesztési csomagban elkészült

### 1. Offline ellenőrzési motor (új, tiszta Kotlin API)
- `core/review/ReviewIntelligence.kt`: nem módosítja az átiratot, kizárólag ellenőrzési jelzéseket ad.
- Külön jelzi az alacsony confidence-ot, számokat, tagadást/kizárást, dátumokat/időpontokat, rövid töredékeket, ismétlődő szöveget és üres sorokat.
- Súlyosság alapján rangsorol: a tagadások kritikus jelzést kapnak; számok, dátumok és nagyon alacsony confidence magas prioritásúak.
- A célzott ellenőrzési terv betartja a sorszám- és időkeretet; nem indít felismerést, csak sorindexeket javasol.
- SHA-256 fingerprint azonosítja azonos bemenetek ismételt feldolgozását; a részek hosszát is kódolja, így a `("ab", "c")` és `("a", "bc")` nem ütközik.
- `ReviewAuditEntry` tárolható modellt ad a visszavonható javítások naplózásához.
- `RecognitionBenchmark` a WER-től külön kezeli a kritikus hibaszámot és a feldolgozási időt.

### 2. Biztonsági elv
- A program sem számot, sem nevet, sem tagadást nem javít át automatikusan e modul alapján.
- A „kritikus” jelzés ellenőrzési prioritás, nem annak állítása, hogy a felismert szó biztosan hibás.
- A WER nem tekinthető fordításminőségnek vagy jogi hitelességi mutatónak.

## Teljes megvalósítási lista és státusz

| Javaslat | Státusz ebben a csomagban |
|---|---|
| Célzott újrafelismerés idő-/darabkerettel | A prioritás- és kerettervező API elkészült; az SRT-szerkesztő és az audio-vágó motor összekötése még szükséges. |
| Kritikus szavak ellenőrzése | Az offline jelzőmotor elkészült; a soronkénti UI-integráció még szükséges. |
| Referenciaalapú benchmark | Benchmark adatmodell elkészült; a 20–50 kézzel címkézett hangminta és a futtató UI/projekt még szükséges. |
| Kontextusérzékeny, konzervatív javítás | A kritikus jelzések és a nem automatikus működés alapja elkészült; a szótár-javaslatok megerősítési munkafolyamata még szükséges. |
| Modern minőségellenőrző felület | A meglévő felületet meg kell tartani; az új motor bekötése és a kritikus szűrők UI-ja még szükséges. |
| Visszavonható auditnapló | Az adatmodell elkészült; a tartós tárolás és a meglévő szerkesztő Undo funkciójának összekötése még szükséges. |
| Idő- és erőforrás-kezelés | Kerettervező és fingerprint elkészült; feldolgozási pipeline-integráció és eszközfüggő mérés még szükséges. |
| Kiadási és adatbiztonsági ellenőrzés | Ez a tervdokumentum és a tesztek bekerültek; teljes Android-buildet ebben a környezetben nem igazoltunk. |
| Hibatípus-alapú felismerés | A típusozott jelzések alapja elkészült; a felismerési stratégiákkal való összekötés még szükséges. |
| Bizonytalansági térkép | A soronkénti jelzések és a forrásidő mezői rendelkezésre állnak; hullámforma/UI-integráció még szükséges. |
| Javítás eredményességének visszamérése | Benchmark modell elkészült; az újrafelismerési eredmény automatikus összehasonlítása még szükséges. |

## Tesztelés

A `ReviewIntelligence.kt` fájl önálló Kotlin-fordítása sikeres volt. A hozzáadott JUnit tesztek a kritikus jelzéseket, a kereteket, a fingerprintet és a benchmark számítását fedik le. A teljes JUnit csomag és az Android APK nem tekinthető igazoltnak: ehhez helyreállított Gradle Wrapper, Android SDK és teljes build szükséges.

Javasolt ellenőrzés Android Studio-ban:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

A teljes kiadást csak akkor szabad késznek tekinteni, ha a tesztek és az APK-build is sikeresek, valamint a célzott hangrészlet-visszajátszást tényleges Android-eszközön kipróbálták.
