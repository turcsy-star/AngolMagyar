# 4.2.6 + Platinum Intelligence 6.0 Engineering – ellenőrzési jelentés

## Verzió
- `versionName=4.2.6`, `versionCode=40406`
- applicationId: `hu.angolmagyar.fordito`
- minSdk 29 / targetSdk 35 / compileSdk 35

## Statikus kapuk (ebben a környezetben)

| Ellenőrzés | Eredmény |
|---|---|
| Forrás ZIP / csomag integritás | OK |
| `python tools/verify_release_security.py` | **PASS** |
| `python -m compileall -q tools` | PASS (korábbi futások) |
| Android version/SDK invariánsok | PASS |
| SmartTranslationMemoryStore fail-closed | Forrásban igazolt |
| SecretStore plaintext fallback tiltás | Forrásban igazolt |
| Gradle `:core:test` / `:app:test` | **Nem futott** (nincs offline Gradle cache / SDK) |
| APK assemble | **Nem készült** |
| Fizikai eszköz / offline / mikrofon | **Nem futott** |

## Forrásszinten bekerült 6.0 mérnöki tételek

### P0–P1
- Verzió- és dokumentumkonzisztencia
- Titkosított memória: dekriptálási hiba → blob megmarad, mutáció megszakad
- API-kulcs: fail-closed AES-GCM

### P2–P3
- `TranslationIntelligence` koordinátor (memória + kontextus + minőség)
- `PremiumTranslationCard` opcionális javaslat / minőség-chippek
- `TranslationIntelligenceTest`

### P4
- `PhrasebookStore` + `PhrasebookActivity` SpacedRepetition (SM-2) ismétlés
- ReviewCoach változatlanul elérhető

### P5–P7
- `MeetingWorkflow` (összefoglaló + minőség + tanulási jelöltek, nem auto-save)
- `LiveTranslationHints`
- `TranscriptDetailActivity` összefoglaló minőségpontszám
- `MeetingWorkflowTest`

### P8–P9 (specifikáció, nem éles)
- `capture/CaptureContracts` – camera/document **UNAVAILABLE**
- `sync/SyncContract` – **NoOp**, nem bekapcsolható

### P10 (részben)
- `AccessibilityKit` + Diagnosztika checklist
- PremiumMotion reduceMotion, OLED, design tokenek (meglévő + bővítés)

## Blokkoló kapuk a stable / RC előtt

1. `./gradlew :core:test` és `:app:testDebugUnitTest` – minden PASS
2. `./gradlew :app:assembleDebug :wear:assembleDebug`
3. Legalább egy valós eszköz: telepítés, frissítés 4.2.x-ről, offline mód
4. Keystore hiba szimuláció: memória nem veszik el; API-kulcs nem kerül plaintextbe
5. Phrasebook ismétlés + ReviewCoach egy-egy ciklus
6. Meeting összefoglaló + SRT export egy mintafájlon
7. TalkBack / nagy betűméret / reduceMotion smoke
8. Aláírt release saját keystore-ral (vagy tudatos unsigned belső build)
9. P8/P9 **nem** kerülhet „kész” státuszba READY nélkül

## Kiadási osztályozás

**FORRÁSSZINTŰ MÉRNÖKI CSOMAG – NEM STABLE RELEASE, NEM RC**

A csomag a 6.0 terv P0–P7 forrásszintű megvalósítását, P8/P9 biztonságos vázát és P10 a11y alapot tartalmazza. Production kiadás csak a fenti blokkoló kapuk után.

