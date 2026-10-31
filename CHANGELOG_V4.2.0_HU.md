# v4.2.0 – Pontosság, egységesítés és minőségbiztosítás

## Új (kód + teszt)
- **Szóidő-alapú időzítés** (`WordTiming`): a Whisper-szegmensek határait a tokenidőkhöz szűkíti (csak szűkít, nem okoz átfedést; megbízhatatlan tokenidőnél az eredeti marad). Bekötve: `FileRecognizer`. Blokk-szintű igazítás és globális eltolás-becslés is elérhető az API-ban.
- **Kétmenetes egységesítés** (`TerminologyConsistency`): tulajdonnevek írásváltozatai és az azonos forrásmondatok eltérő fordításai a leggyakoribb alakra egységesítve; döntetlennél nem találgat. Bekötve: `SrtBuild` (kapcsoló: Profil és formázás → „Egységesítés", alapból be).
- **Ellenőrzési rangsor** (`ReviewPriority`): fontosság szerinti sorrend (tagadás, szám, név, bizonytalan felismerés, szókonfidencia…). Bekötve a szerkesztőbe: „Következő ellenőrizendő sor ▶". A szókonfidencia-jelölés csak akkor, ha a számuk egyezik a szavakéval.
- **Párbeszéd gondolatjellel** (`DialogueDash`): gyors beszélőváltásból „- … / - …" blokk. Bekötve: `SrtBuild` (kapcsoló, alapból ki).
- **Modellajánló** (`ModelAdvisor`): RAM, magszám, tárhely, hőállapot alapján; a Diagnosztika képernyőn jelenik meg. A sebesség tájékoztató becslés, nem mért érték.
- **Anonimizált hibajelentés** (`ReportAnonymizer`): a megosztott/másolt jelentésben a fájlnevek, útvonalak, URI-k, e-mailek, IP-k és kulcsok kicserélve (a megjelenített napló változatlan).
- **Arany-referencia WER** (`GoldenWer`, `core/src/test/resources/golden`, `tools/golden_wer.py`): a CI minden futáskor összeveti a mintákat az alapvonallal. A három mellékelt minta szintetikus; valódi mintákat a `golden/README_HU.md` szerint adj hozzá.
- **Manifest-integritás teszt** (`ManifestIntegrityTest`): hiányzó/nem bejegyzett Activity-k eszköz nélkül kiderülnek.

## Már korábban megvolt (nem duplikáltuk)
Sorba állított kötegelt feldolgozás (`SrtJob.queue`, éjszakai mód töltőn), megosztás-intent (SEND), hőmérséklet-függő szálszám (`Perf.thermalScaled`), VTT/ASS export, beszélőszínek, token-konfidencia jelölés a szerkesztőben.

## Javított meglévő hibák (a `core` modul nem fordult)
- `HybridTranslationEngine.OnlineAdapter`: `fun interface` absztrakt tulajdonságokkal → sima `interface`.
- `PerformanceRegressionSuite`: nem létező `summaryLine()` hívások → `TranslationQuality.summaryLine(...)`; a belső ellenőrző példák 1 jegyű számokat használtak, amelyeket a `TranslationCheck` szándékosan figyelmen kívül hagy → 3 jegyű példák.

## Ellenőrzés ebben a környezetben
- A teljes `core` modul (főkód + minden unit teszt, új tesztekkel és a golden mintákkal együtt) lefordult és lefutott Kotlin-fordítóval, JUnit-kompatibilis futtatóval: 75/75 sikeres.
- Az Android-független app-kód tesztjei (kiejtés, szótár, SM-2, szinonima, ManifestIntegrityTest) is lefutottak: 18/18.
- NEM futott le: az Android-függő app-kód fordítása (FileRecognizer, SrtBuild, SrtEditorActivity, DiagnosticsActivity, DiagLog, FileSubtitleActivity módosítások), a Gradle build és az eszközteszt: nincs Android SDK / Gradle elérés. Ezeket a GitHub Actions CI-nek kell igazolnia.

## Verzió
versionName 4.2.0, versionCode 40400
