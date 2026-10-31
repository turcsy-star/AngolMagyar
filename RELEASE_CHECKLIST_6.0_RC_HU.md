# Platinum Intelligence 6.0 – RC / stable kiadási checklist

Használd minden release-jelölt előtt. Minden sor: ☐ todo / ☑ kész / ✖ nem alkalmazható.

## A. Forrás és biztonság
- [ ] `python tools/verify_release_security.py` → PASS
- [ ] Nincs új, nem engedélyezett exported komponens a manifestben
- [ ] Nincs API-kulcs / titok a logokban és a forrásban
- [ ] `SecretStore` és `SmartTranslationMemoryStore` regresszió: hiba esetén nincs plaintext, memória blob nem törlődik
- [ ] Release keystore: mind a 4 érték megvan, vagy tudatos unsigned (részleges konfig = hiba)

## B. Automatizált tesztek
- [ ] `./gradlew :core:test`
- [ ] `./gradlew :app:testDebugUnitTest` (TranslationIntelligence, MeetingWorkflow, AccessibilityKit, Sync, Capture, PremiumTranslationCard)
- [ ] `./gradlew :app:assembleDebug`
- [ ] `./gradlew :wear:assembleDebug`
- [ ] (opcionális) `./gradlew :app:assembleRelease` saját kulccsal

## C. Offline funkciók (repülőgép-mód)
- [ ] Élő fordítás / felismerés offline modellel
- [ ] Szókincstár mentés + ismétlés (Phrasebook)
- [ ] ReviewCoach kártyák
- [ ] SRT szerkesztő + ReviewSheet jelzések
- [ ] Meeting összefoglaló minőségpontszámmal
- [ ] Helyi backup / visszaállítás

## D. Adatvédelem
- [ ] Online motor csak explicit opt-in után
- [ ] Sync UI **nem** kínál éles felhőszinkront (NoOp)
- [ ] Kamera/OCR **nem** jelenik meg READY-ként (CaptureRegistry UNAVAILABLE)
- [ ] Exportált fájlok FileProvideren keresztül, visszavonható grant

## E. UI / accessibility
- [ ] Világos / sötét / OLED
- [ ] Nagy betűméret (rendszer skála ≥ 1.3)
- [ ] reduceMotion bekapcsolva: nincs zavaró hurokanimáció
- [ ] TalkBack: fő képernyők címkéi és gombok
- [ ] Diagnosztika → akadálymentesség checklist olvasható

## F. Eszközmátrix (minimum)
- [ ] 1× Android 10/11
- [ ] 1× Android 14/15
- [ ] (ha van) Wear OS debug APK telepítés

## G. Dokumentáció a kiadáshoz
- [ ] `CHANGELOG` / release notes frissítve
- [ ] `BUILD_STATUS` tényleges Gradle eredménnyel
- [ ] `VALIDATION_REPORT` eszközteszt sorokkal kiegészítve
- [ ] APK SHA-256 és aláírási ujjlenyomat rögzítve

## H. Döntés
- [ ] **RC**: A–G kötelező tételek zöldek, ismert korlátok dokumentálva
- [ ] **Stable**: RC + legalább 48 óra smoke / visszajelzés, nincs kritikus regresszió
- [ ] **Nem release**: bármely A/B/C/D blokkoló hiányzik

