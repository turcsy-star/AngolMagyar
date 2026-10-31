# CHANGELOG – AngolMagyar 4.2.6 + Platinum Intelligence 6.0 Engineering

## 4.2.6 / Platinum Intelligence 6.0 Engineering (forrásszintű javítócsomag)

### Biztonság (P1)
- `SmartTranslationMemoryStore`: dekriptálási / Keystore hiba esetén a titkosított blob megmarad; `readEntries()` null-t ad; mentés és törlés megszakad. Nincs csendes adatvesztés, nincs plaintext fallback.
- `SecretStore`: fail-closed AES-GCM; legacy plaintext csak sikeres migráció után; titkosítási hiba esetén nem ír vissza plaintextet.

### Stabilizálás (P0)
- Projektmappa, README, BUILD_STATUS, VALIDATION_REPORT és forrás-audit a valós `versionName=4.2.6` / `versionCode=40406` értékhez igazítva.
- CI workflow: static security guardrail → core teszt → app unit teszt → debug APK → Wear debug → unsigned release.
- `tools/verify_release_security.py` – PASS.

### Dokumentáció és terv
- `PLATINUM_INTELLIGENCE_6.0_IMPLEMENTATION_PLAN_HU.md` – fázisonkénti, fájlszintű terv.
- `PLATINUM_INTELLIGENCE_6.0_STATUS_HU.md` – tényleges vs. tervezett státusz.
- `PLATINUM_INTELLIGENCE_6.0_TEST_PLAN_HU.md` (ha jelen van) + meglévő FEATURE_MATRIX és IMPLEMENTATION_AND_TEST_PLAN.

### Domain logika (már meglévő, megerősített)
- Offline Smart Context Translation, Smart Translation Memory (APPROVED-only, scope, ambiguity).
- ReviewIntelligence, TranslationCheck, minőség- és tanulási komponensek.

### Nem része ennek a csomagnak
- Új APK / aláírt release.
- Teljes P5–P10 implementáció (hang/meeting/kamera/cloud/teljes UI-migráció).
- Fizikai eszközteszt és golden screenshot baseline.

### Verziószabály
Az alkalmazás `versionName` és `versionCode` változatlan (4.2.6 / 40406) a frissítési kompatibilitás miatt. A „6.0” fejlesztési célverzió és minőségkapu-program.


## P2–P3 folytatás (ugyanebben a csomagban)

### Új
- `app/.../TranslationIntelligence.kt` – offline koordinátor: SmartTranslationMemory + SmartContextTranslation + TranslationCheck.
- `TranslationIntelligenceTest.kt` – negáció/szám/név flag-ek, pending entry scope, store nélküli analyze.

### Módosított
- `ui/design/PremiumTranslationCard.kt` – opcionális memóriajavaslat, minőség-chippek, kontextus-megjegyzés, „Javaslat elfogadása”.
  A régi hívások továbbra is fordulnak (új paraméterek default értékkel).

### Elvek
- Memória és kontextus csak javaslat; automatikus felülírás tilos.
- Minőségjelzés forrása látható (determinisztikus offline check, nem „AI certainty”).
- Keystore-hiba esetén memória-javaslat nem jelenik meg.


## P4 folytatás (ugyanebben a csomagban)

### Módosított
- `PhrasebookStore.kt`: SpacedRepetition (SM-2) ütemezés; `repetitions` + `easeFactor` mezők; `due()` lekérdezés; `review(id, grade)`.
- `PhrasebookActivity.kt`: ismétlési mód (jelentés felfedése, Újra/Nehéz/Jó/Könnyű), due számláló.
- `PlatinumIntelligenceActivity.kt`: hub státuszok P2–P4 szerint frissítve.

### Elvek
- Tanulási ütemezés determinisztikus, offline, helyi tárolás.
- Phrasebook és ReviewCoach ugyanazt a SpacedRepetition motort használja.
- Nincs hálózati feltöltés a szókincstárból.


## P5–P7 folytatás

### Új
- `MeetingWorkflow.kt` – offline meeting csomag: összefoglaló + minőség (ReviewIntelligence) + phrase candidates (nem auto-save)
- `LiveTranslationHints.kt` – élő párokhoz rövid UI-hint (memória / minőség / kontextus)
- `MeetingWorkflowTest.kt`

### Módosított
- `TranscriptDetailActivity.showSummary`: MeetingWorkflow minőségpontszám és jelzések
- PlatinumIntelligence hub: Subtitle + Meeting Workflow státusz

### Elvek
- Minőségjelzés nem módosítja az átiratot
- Tanulási jelöltek csak explicit felhasználói mentés után kerülhetnek szótárba
- MeetingMode prefs snapshot/restore változatlan


## P8 / P9 / P10 folytatás

### Új
- `ui/design/AccessibilityKit.kt` – kontraszt, touch target, snapshot, checklist
- `sync/SyncContract.kt` – NoOp sync, envelope, registry (P9)
- `capture/CaptureContracts.kt` – camera/document engines default UNAVAILABLE (P8)
- Unit tesztek: AccessibilityKitTest, SyncContractTest, CaptureContractsTest

### Módosított
- `DiagnosticsActivity` – akadálymentesség checklist kártya
- PlatinumIntelligence hub – P8/P10 státuszszövegek

### Elvek
- P8: nincs „kész” OCR/kamera, amíg readiness != READY
- P9: nincs bekapcsolható felhőszinkron backend nélkül
- P10: practical a11y helpers, nem WCAG tanúsítvány-állítás

