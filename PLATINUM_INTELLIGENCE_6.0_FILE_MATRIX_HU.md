# Platinum Intelligence 6.0 – fájlszintű státuszmátrix

Státuszjelölés: **MEGLÉVŐ** = forrásban megtalálható; **JAVÍTVA** = ebben a csomagban ténylegesen módosítva; **TERVEZETT** = még implementálandó; **FELTÉTELES** = külső szolgáltató/backend vagy eszközfüggő.

| Fájl / terület | Kiinduló szerep | 6.0 feladat | Státusz ebben a csomagban |
|---|---|---|---|
| `app/build.gradle.kts` | Android build, signing, dependencies | Release signing és dependency audit | MEGLÉVŐ, auditálva; build nem futott |
| `AndroidManifest.xml` | Permission, exported komponensek, provider | Exported allowlist és permission regresszió | MEGLÉVŐ, statikus ellenőrző védi |
| `SecretStore.kt` | AES-GCM Android Keystore | Fail-closed titoktárolás | MEGLÉVŐ, statikusan ellenőrizve |
| `SmartTranslationMemoryStore.kt` | Titkosított fordítási memória | Dekódolási hiba esetén blob megőrzése, mutáció megszakítása | **JAVÍTVA** |
| `core/SmartTranslationMemory.kt` | Platformfüggetlen pontos memóriaegyezés | Projektprioritás, jóváhagyás, kétértelműség | MEGLÉVŐ |
| `core/SmartContextTranslation.kt` | Kontextusérzékeny fordítás | Egységes request/pipeline integráció | TERVEZETT |
| `TranslateService.kt`, `Engines.kt`, `PolyEngine.kt` | Fordítási pipeline | Offline/online útvonal, memória és quality integráció | TERVEZETT |
| `TranslationQuality.kt`, `TranslationCheck.kt`, `TranslationQualityAudit.kt` | Fordításminőség ellenőrzés | Súlyosság/eredet egységesítése és UI-integráció | MEGLÉVŐ, integráció TERVEZETT |
| `ReviewIntelligence.kt`, `SpacedRepetition.kt`, `UltimateLearningEngine.kt` | Tanulási ütemezés | Közös tanulási események | MEGLÉVŐ, integráció TERVEZETT |
| `LearningAnalyticsStore.kt`, `LearningAnalyticsActivity.kt` | Tanulási analitika | Tényleges eseményekből számolt célok/statisztika | MEGLÉVŐ, bővítés TERVEZETT |
| `AudioRecorder.kt`, `LiveWhisperAsr.kt`, `SystemAsr.kt` | Helyi beszédfelismerés | Lifecycle/permission/benchmark | MEGLÉVŐ, hardening TERVEZETT |
| `PronunciationAssessment.kt` | Kiejtés/átirat metrikák | Fonetikai és ASR-metrika szétválasztása | MEGLÉVŐ, validáció TERVEZETT |
| `FileSubtitleActivity.kt`, `SrtEditorActivity.kt`, `SrtJobService.kt` | Felirat workflow | Undo/redo, wave timeline, autosave és round-trip | MEGLÉVŐ, bővítés TERVEZETT |
| `MeetingMode.kt`, `MeetingSummary.kt` | Meeting domain | Teljes átirat–összefoglaló folyamat | MEGLÉVŐ, integráció TERVEZETT |
| `CameraTranslateActivity` (új) | Nincs jelen | CameraX + OCR + engedélykezelés | TERVEZETT |
| `DocumentImportCoordinator` (új) | Nincs igazolt import pipeline | PDF/DOCX/TXT import/export | TERVEZETT |
| `SyncContract` (új) | Nincs backend | Backendfüggetlen szerződés, konfliktusmodell | FELTÉTELES |
| `PremiumDesignSystem.kt`, `Theme.kt` | Több design token és Compose theme | Egységes képernyőmigráció | MEGLÉVŐ, migráció TERVEZETT |
| `README.md`, státusz- és tervdokumentumok | Projektleírás | Verzió és készültség pontosítása | **JAVÍTVA** |
| projektgyökér könyvtár | ZIP-ben `4.2.5` nevet viselt | `4.2.6` elnevezés | **JAVÍTVA** |

## Változatlanul megőrzendő elemek

- `applicationId=hu.angolmagyar.fordito`
- `versionName=4.2.6`, `versionCode=40406` (amíg migrációs kiadás nincs)
- `minSdk=29`, `targetSdk=35`, `compileSdk=35`
- `:app`, `:core`, `:wear` modulok
- Offline ASR/fordítási és modellletöltési útvonalak
- Meglévő adatbázisok, export formátumok és alkalmazásfájlok
- Release kulcs hiányában unsigned release; debug kulcs soha nem lehet release kulcs
- `allowBackup=false`, nem exportált belső komponensek és FileProvider jogosultsági határai

## Fontos

A „MEGLÉVŐ” nem azonos azzal, hogy minden eszközön hibátlanul működik. A státusz forráskód jelenlétet jelez. A működéshez a tesztterv és a kiadási jegyzőkönyv szükséges.
