# AngolMagyar 4.2.6 – Platinum Intelligence 6.0 mérnöki fejlesztési csomag

Ez a forráscsomag az AngolMagyar Android `4.2.6` kódállapotából indul ki. Megőrzi az Android, Wear OS és Kotlin core modulokat, az offline feldolgozási útvonalakat és a meglévő biztonsági határokat. A jelen csomag a fordítási memória adatmegőrzési hibáját javítja, valamint részletes 6.0 fejlesztési tervet és tesztkapukat ad hozzá.

## Fontos státusz

- **Alkalmazásverzió:** 4.2.6 (versionCode: 40406). A csomag mappaneve és dokumentációja ehhez igazítva.
- **Meglévő funkciók:** Whisper/Vosk, VAD, fordítási memória, offline/online fordítási útvonalak, feliratfeldolgozás, tanulási és minőségellenőrző komponensek – részletes státusz: `FEATURE_MATRIX_5.0_HU.md`.
- **Biztonsági javítás:** az API-kulcs titkosítási hibája nem írhat vissza plaintext értéket; a régi plaintext kulcs első olvasáskor csak sikeres AES-GCM migráció után maradhat meg.
- **Release aláírás:** saját release-keystore nélkül a release APK unsigned; a debug kulcs többé nem lehet release fallback.
- **UI:** új Forest Graphite prémium paletta; a Compose prémium téma a világos/sötét rendszerbeállítást is követi.
- **Build státusz:** a ZIP szerkezeti és statikus ellenőrzése futtatható; ebben a környezetben Gradle nincs telepítve és a wrapper disztribúciója nem érhető el offline, ezért Android buildet/APK-t nem állítunk sikeresnek. A statikus ellenőrzés leírása: `tools/verify_release_security.py`; státusz: `BUILD_STATUS_4.2.6_HU.md`; ellenőrzési korlátok: `VALIDATION_REPORT_4.2.6_HU.md`.

## Gyors build

Szükséges: JDK 17, Android SDK 35, NDK 27.2.12479018, CMake 3.22.1.

```bash
chmod +x ./gradlew
./gradlew :core:test :app:testDebugUnitTest
./gradlew :app:assembleDebug :wear:assembleDebug
```

A debug APK helye sikeres build után: `app/build/outputs/apk/debug/app-debug.apk`.

## Saját release aláírás

Hozz létre helyben `keystore.properties` fájlt (ne töltsd fel GitHubra):

```properties
storeFile=/abszolut/utvonal/a/release-key.jks
storePassword=SAJAT_STORE_JELSZO
keyAlias=SAJAT_ALIAS
keyPassword=SAJAT_KULCS_JELSZO
```

Alternatívaként használd a `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` környezeti változókat. A négy érték mindegyike kötelező. Ha egyik sincs beállítva, a release build aláíratlan marad; részleges konfiguráció esetén a Gradle hibával leáll. A debug kulcs kizárólag debug buildhez használható.

```bash
./gradlew :app:assembleRelease
```

## Projektfelépítés

- `:app` – Android alkalmazás, Compose és klasszikus képernyők, audio/ASR/TTS, felirat- és projektmunkafolyamatok.
- `:core` – Android-független fordítási, minőségellenőrzési, terminológiai és feliratlogika.
- `:wear` – Wear OS társalkalmazás.

## Dokumentáció

- `docs/BUILD_FIX_4.2.6_COMPOSE_IR_HU.md` – Compose IR fordítási hiba javítása, regressziós CI-kapu és a még külső integrációt igénylő funkciók státusza.

- `PREMIUM_INTELLIGENCE_5.0_SPEC_HU.md` – teljes célarchitektúra és a korábbi javaslatok specifikációja.
- `FEATURE_MATRIX_5.0_HU.md` – meglévő, részleges és hiányzó funkciók; nincs késznek jelölve, ami nincs integrálva.
- `IMPLEMENTATION_AND_TEST_PLAN_5.0_HU.md` – fájlszintű megvalósítási és tesztelési sorrend.
- `SECURITY_HARDENING_4.2.1_HU.md` – biztonsági változások és kiadási követelmények.
- `MIGRATION_4.2.0_TO_4.2.1_HU.md` – frissítési és kompatibilitási megjegyzések.
- `BUILD_STATUS_4.2.2_HU.md` – ellenőrzési eredmények és korlátozások.
- `CHANGELOG_V4.2.2_HU.md` – az új CI és biztonsági tesztváltozások.
- `tools/verify_release_security.py` – statikus kiadási biztonsági kapu.

## Kiadási szabály

Ez a ZIP teljes forrásprojektet és dokumentációt tartalmaz, nem előre lefordított APK-t. Kiadásra alkalmasnak csak a sikeres CI build, tesztek, APK-aláírás és valós Android-eszközön végrehajtott ellenőrzés után nevezhető.


## 4.2.6 – titkosított fordítási memória
A `SmartTranslationMemoryStore` a Smart Translation Memory bejegyzéseit Android Keystore AES-GCM titkosítással tárolja. A hívásokat háttérszálról (`Dispatchers.IO`) végezd. A memóriajavaslat csak jóváhagyott, pontos nyelvpárra illeszkedő bejegyzéseket ad vissza; a felhasználói megerősítés továbbra is kötelező. Részletek: `CHANGELOG_V4.2.6_HU.md`.


## Platinum Intelligence 6.0 – mérnöki státusz

A 6.0 itt fejlesztési célverzió és fájlszintű terv, nem az Android alkalmazás verziószáma.
A `versionName=4.2.6`, `versionCode=40406`, az applicationId és az aláírási
szabályok változatlanok maradtak a frissítési kompatibilitás megőrzése érdekében.

- `PLATINUM_INTELLIGENCE_6.0_IMPLEMENTATION_PLAN_HU.md` – fázisokra és konkrét fájlokra bontott megvalósítási terv.
- `PLATINUM_INTELLIGENCE_6.0_FILE_MATRIX_HU.md` – fájlonkénti módosítási és státuszmátrix.
- `PLATINUM_INTELLIGENCE_6.0_TEST_PLAN_HU.md` – unit, integrációs, offline, biztonsági és eszköztesztek.
- `PLATINUM_INTELLIGENCE_6.0_STATUS_HU.md` – mi készült el ténylegesen és mi maradt terv.
- `SOURCE_AUDIT_4.2.6_HU.md` – a feltöltött ZIP tényleges forrásának ellenőrzési eredménye.

---

## Javított mérnöki csomag átadás (Platinum Intelligence 6.0 Engineering)

Ez a ZIP a 4.2.6 forrás stabilizált és biztonságilag javított változata, a 6.0 fázistervvel és státuszmátrixszal.

- Részletes státusz: `PLATINUM_INTELLIGENCE_6.0_STATUS_HU.md`
- Fájlszintű terv: `PLATINUM_INTELLIGENCE_6.0_IMPLEMENTATION_PLAN_HU.md`
- Változások: `CHANGELOG_V4.2.6_PLATINUM_6.0_ENGINEERING_HU.md`
- Build státusz: `BUILD_STATUS_4.2.6_HU.md`

A csomag **nem** tartalmaz új APK-t és nem stable release. A következő sorrend a dokumentációban leírt P0 → P1 → P2… fázisok helyi Gradle + eszközteszttel.



## Platinum Intelligence 7.0 – folytatási állapot

A 7.0 mérnöki terv mellett két tényleges, platformfüggetlen core alapkomponens került a forrásba:
- `core/src/main/java/hu/angolmagyar/fordito/PlatinumWorkflow.kt`
- `core/src/main/java/hu/angolmagyar/fordito/TranslationWorkspace.kt`

A hozzájuk tartozó unit tesztforrások: `PlatinumWorkflowTest.kt` és `TranslationWorkspaceTest.kt`. A core Kotlin források `kotlinc` fordítása és a smoke harness sikeres. A Compose UI, Room/titkosított tartós tároló adapter és teljes Gradle/Android build külön integrációs hullám. A termék Android-verziója változatlanul 4.2.6 / 40406; a 7.0 fejlesztési program neve nem Android release-verzió.


## Platinum Intelligence 8.0 – Phase 2

A Phase 2 a 7.0 core alapokra épít:
- `ModelIntegrityVerifier`: stream-alapú SHA-256, méret- és manifestellenőrzés.
- `OfflineCapabilityMatrix`: csak jelenlévő és ellenőrzött helyi komponens esetén jelöl offline-ready állapotot.
- Részletes terv: `PLATINUM_INTELLIGENCE_8.0_PHASE2_HU.md`.
- Változásnapló: `CHANGELOG_PLATINUM_8.0_PHASE2_HU.md`.

Ez a hullám core implementáció, nem teljes modellkezelő UI/telepítő integráció. A Gradle/JUnit és Android build csak a szükséges helyi toolchainnel igazolható.
