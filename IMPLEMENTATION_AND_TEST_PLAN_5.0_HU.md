# AngolMagyar 5.0 – fájlszintű megvalósítási és tesztelési terv

## A. Alap hardening és reprodukálható build

| Fájl / terület | Feladat | Elfogadási feltétel |
|---|---|---|
| `app/build.gradle.kts` | Release aláírás kizárólag saját release kulccsal; debug fallback tiltása | Hiányzó kulcs esetén unsigned release, részleges kulcsnál egyértelmű buildhiba |
| `SecretStore.kt` | AES-GCM titoktárolás fail-closed módban | Keystore-hiba esetén nincs plaintext tárolás; régi kulcs migrációja titkosítva |
| `core/EndpointPolicy.kt`, tesztje | Távoli custom endpoint HTTPS-korlátja | HTTPS és helyi/private HTTP elfogadott; távoli plaintext HTTP elutasított |
| `Presets.kt`, `OnlineSettingsDialog.kt` | Titkosított mentés hibájának kezelése | UI visszajelzést ad, online kapcsolatpróba nem indul kulcsmentési hiba után |
| `LuxuryPalettes.kt`, `Prefs.kt`, `SettingsActivity.kt` | Forest Graphite paletta | Új telepítésen megjelenik; meglévő paletták nem vesznek el |
| `ui/theme/Theme.kt` | Világos/sötét prémium Compose színséma | Rendszertéma-váltásra kontrasztos, olvasható színséma |
| `.gitignore` | Helyi release kulcsfájlok kizárása | `keystore.properties` és release keystore nem kerül a repositoryba |

## B. Kontextus és minőség

- `core/SmartContextTranslation.kt`: ContextRequest bővítése szomszédos mondattal, beszélővel, projekttémával és stílusprofillal.
- `core/OnlineTranslator.kt`: közös context prompt; eredet és szolgáltatói hibák strukturált visszaadása.
- `core/TranslationQualityAudit.kt`, `TranslationCheck.kt`, `ReviewPriority.kt`: súlyosság és jelzés-eredet egységesítése.
- `app/SrtBuild.kt`, `SrtEditorActivity.kt`: ellenőrzési jelzés, hangrészlet lejátszás és jóváhagyás bekötése.

Teszt: többértelmű szó tesztmátrix, név/szám/tagadás regresszió, üres és hiányos válasz, offline fallback, kézi jóváhagyás megőrzése.

## C. Voice és tanulás

- `RecognitionHelper.kt`, `LiveWhisperAsr.kt`, `AudioRecorder.kt`: életciklus, megszakítás, permission és erőforrás felszabadítás.
- `PronunciationAssessment.kt`: csak validált mérőszámokat adjon; átirat-egyezés ne legyen fonetikai pontszámként megjelenítve.
- `UltimateLearningEngine.kt`, `LearningAnalyticsStore.kt`, `LearningAnalyticsActivity.kt`: fordítási hibából tanulókártya, aktív/passzív szókincs.

Teszt: referenciahangos WER, hangfelvétel/újrajátszás, permission visszavonás, TTS locale hiány, SM-2 határértékek, tanulási események tartóssága.

## D. Subtitle Studio

- `FileSubtitleActivity.kt`, `SrtEditorActivity.kt`, `SrtBuild.kt`, `SubtitlePostProcessor.kt`, `SrtJob.kt`, `Checkpoint.kt`: kétpaneles szerkesztő, audio seek, undo/redo, automatikus mentés, checkpoint.

Teszt: SRT/VTT/ASS import-export round-trip, hosszú fájl, megszakítás és folytatás, átfedés, időbélyeg, hibás bemenet, memóriahasználat.

## E. Privacy Vault

- `SecretStore.kt`, `SecureBackupArchive.kt`, `BackupCenterActivity.kt`, `PrivacyActivity.kt`, `AuditLog.kt`, `ReportAnonymizer.kt`.
- Következő bővítés: biometrikus feloldás, adatmegőrzési UI, projektszintű törlés, titkosított sync csak backend döntés után.

Teszt: hibás kulcs, sérült titkosított mentés, jelszóhibák, napló- és export anonimizálás, plaintext keresés tesztadatokon, adat törlés utáni visszaállíthatatlanság.

## F. Kamera, dokumentum, meeting, cloud

- Kamera: új `CameraTranslateActivity`, CameraX, ML Kit Text Recognition; csak explicit kameraengedéllyel.
- Dokumentum: `DocumentImportCoordinator`, PDF/DOCX/TXT parser adapterek; formázásmegőrző export.
- Meeting: meglévő `MeetingMode` és `MeetingSummary` teljes munkafolyamatba kötése.
- Cloud: backend, hitelesítés és titkosítás kiválasztása előtt csak interface/specifikáció; éles szinkron nem kapcsolható be.

Teszt: OCR pontosság különböző fényben, PDF szöveg és scan külön kezelése, DOCX stílusok, meeting beszélőváltás, sync konfliktus és jogosultság.

## G. Kiadási tesztmátrix

1. `./gradlew :core:test`
2. `./gradlew :app:testDebugUnitTest`
3. `./gradlew :app:assembleDebug :wear:assembleDebug`
4. `./gradlew :app:assembleRelease` saját release kulccsal
5. Valós eszköz: telepítés, frissítés, mikrofon, háttérfolyamat, fájlválasztó, TTS, offline fordítás.
6. UI: világos, sötét, OLED, nagy betűméret, kompakt kijelző, csökkentett mozgás.
7. Biztonság: API-kulcs tárolás, hálózati hozzájárulás, mentés-visszaállítás, adat-export és törlés.
8. Kiadási jegyzőkönyv: commit hash, Gradle/JDK/SDK/NDK verzió, teszteredmények, APK SHA-256 és aláírási tanúsítvány ujjlenyomata.

## Kiadási döntés

Egy funkció csak akkor „kész”, ha a forráskód, a felhasználói felület, a tárolás, a hibakezelés, a teszt és a dokumentáció együtt teljesül. A jelen 4.2.1 csomagban az A fejezetben felsorolt hardening változások kerültek be; a B–F fejezet a v5.0 további végrehajtandó specifikációja.

## 4.2.2 automatizált kiadási kapu

A CI a `tools/verify_release_security.py` ellenőrzést futtatja a Gradle tesztek előtt. Ez forrásszintű regresszióvédelmi kapu, nem helyettesíti a Kotlin/Android buildet vagy a készülékes validációt. A pipeline külön lefordítja az app debug, Wear OS debug és unsigned app release variánsokat.
