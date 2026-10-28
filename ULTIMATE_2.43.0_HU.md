# AngolMagyar – Silver & Gold Ultimate 2.43.0

## Cél

A korábbi prémium Compose-felületet tényleges, használható nyelvi funkciókkal bővíti: online LLM-fordítási stílusokkal, helyi mentett kifejezésekkel, a fordítás felolvasásával és célzott tesztekkel.

## Ebben a csomagban megvalósított módosítások

### 1. Online fordítási stílusok
- Új `TranslationStyle` modell: Természetes, Pontos, Hivatalos, Közvetlen.
- A kiválasztás a meglévő privát beállításokban tárolódik.
- Az `OnlineTranslator` rendszerutasítása tartalmazza a kiválasztott stílus instrukcióját.
- A vezérlőpult csak a támogatott Anthropic/OpenAI-kompatibilis online LLM-fordítóknál mutatja a stílusválasztót. A DeepL és az offline ML Kit működését nem állítja át.

### 2. Helyi saját szókincstár
- Új `PhrasebookStore` és `SavedPhrase` adatmodell.
- A felhasználó kifejezett Mentés művelete rögzíti a forrás és fordítás párosát.
- Helyi SharedPreferences-tárolás; a szókincstár nem küld adatot hálózatra és nem naplózza a mentett szövegeket.
- Ismétlődő párok deduplikálása, keresés, másolás, egyedi törlés és megerősítést kérő teljes törlés.
- Legfeljebb 2000 elem tárolható.
- Új `PhrasebookActivity`, elérhető a kezdőképernyő gyorsműveleteiből.

### 3. Felolvasás és hozzáférhetőség
- Az élő fordítási kártya felolvasás művelete Android TextToSpeech szolgáltatást használ magyar nyelvi beállítással.
- A szolgáltatás Compose-életciklus végén leáll és felszabadul.
- Hiányzó magyar beszédhang esetén a felület tájékoztatja a felhasználót.
- A kártya műveleti sora kis képernyőn vízszintesen görgethető.

### 4. Tesztek
- Core: a fordítási stílus biztonságos alapértéke és a rendszerutasításba kerülő stílusinstrukció.
- App: szókincstár mentés, deduplikálás, törlés és üres bemenet elutasítása.
- Compose: a Mentés gomb megjelenése és engedélyezett állapota.
- A core modul JUnit-függősége explicit módon rögzítve.

### 5. Build/CI
A meglévő CI feladat továbbra is futtatni próbálja a `:core:test`, `:app:testDebugUnitTest` és `:app:assembleDebug` feladatokat, és feltölti az APK-t, a tesztjelentéseket és a screenshot kimeneteket.

## Ismert korlátok és következő feladatok

- **A build és a tesztek ebben a környezetben nem igazoltak.** A Gradle Wrapper nem tudta letölteni a Gradle 8.9 disztribúciót, mert a `services.gradle.org` névfeloldása hálózati hibával leállt. Ez nem sikeres build.
- A helyi szókincstár nem szinkronizál eszközök között, és jelenleg nem tartalmaz időzített ismétléses tanulóalgoritmust.
- A stílusválasztó az online LLM-fordítási útvonalat befolyásolja; az offline ML Kit fordítás minőségét vagy hangnemét nem módosítja.
- A kontextusmagyarázat, a brit/amerikai kiejtésválasztás, a kiejtés pontozása, az automatikus szókártyagenerálás és a teljes képernyőkön átívelő Compose-migráció további fejlesztést igényel; ezeket nem jelöljük elkészültnek.
- A Roborazzi teszt jelenleg képernyőkép-kimenetet ellenőrző/smoke jellegű teszt. Referencia-képek rögzítése és pixel-eltéréses regressziókapu külön feladat.

## Ellenőrzési parancsok

```bash
./gradlew :core:test --stacktrace
./gradlew :app:testDebugUnitTest --stacktrace
./gradlew :app:assembleDebug --stacktrace
```

A csomag akkor tekinthető kiadásra igazoltnak, ha ezek a parancsok sikeresen lefutnak, az APK települ, és a mikrofonos felolvasási/mentési munkafolyamatot tényleges Android-eszközön is ellenőrizték.
