# AngolMagyar Platinum Intelligence 6.0 – fájlszintű megvalósítási terv

## 0. Verziózási és minőségi döntés

A „6.0” fejlesztési célverzió, nem az alkalmazás jelenlegi Android-verziója. A kiinduló alkalmazás `versionName=4.2.6`, `versionCode=40406`; ezek, az `applicationId`, a meglévő adatbázisok, a fájlformátumok és a debug/release aláírási szabályok a 6.0 fejlesztési ág első stabil mérföldkövéig változatlanok. A verziókód emelése csak külön migrációs és frissítési teszt után történhet.

**Nem cél egyetlen lépésben minden funkciót bekapcsolni.** A 6.0 egy minőségkapukkal felépített program: minden fázis külön buildelhető, tesztelhető és visszagörgethető. Külső szolgáltató, backend vagy kamera/dokumentum parser nélkül az adott funkció nem jelenhet meg működőként.

## 1. Alapelvek és architekturális korlátok

1. Offline-first: a fordítás, szótár, tanulás, feliratfeldolgozás, helyi előzmények és mentés internet nélkül használható marad. Hálózati funkció opcionális, explicit online állapotot és felhasználói jóváhagyást igényel.
2. Nincs csendes adatvesztés: hibás/ideiglenesen hozzáférhetetlen titkosított tár nem értelmezhető üres tárként. Írási művelet olvasási hiba után megszakad.
3. Nincs titok plaintext fallbackkel: API-kulcs csak Android Keystore AES-GCM útvonalon tárolható.
4. Nincs automatikus felülírás: jóváhagyott fordítás, kézi szerkesztés és projektterminológia védett; memória csak javaslat.
5. Mérőszámok forrása látható: becsült minőség, ASR-konfidencia, WER/CER és fonetikai pontosság nem keverhető.
6. Nincs késznek jelölés build, teszt és valós készülékes ellenőrzés nélkül.
7. Kifelé irányuló adatküldés, mikrofon, kamera és felhőszinkron csak explicit, visszavonható felhasználói művelettel történhet.

## 2. Fázisok

### P0 – Forrás- és kiadási stabilizálás (első kötelező fázis)

**Cél:** reprodukálható forrásállapot, verziókonzisztencia, regresszióvédelem.

| Fájl | Változtatás |
|---|---|
| `README.md`, `CHANGELOG_V4.2.6_HU.md`, `BUILD_STATUS_4.2.6_HU.md`, `VALIDATION_REPORT_4.2.6_HU.md` | Tényleges `4.2.6 / 40406` verzió rögzítése; build és készülékteszt státuszának pontos megjelölése. |
| `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`, `core/build.gradle.kts`, `wear/build.gradle.kts` | Plugin/dependency verziók és SDK/JDK/NDK mátrix auditja; verziófrissítés csak kompatibilitási teszt után. |
| `.github/workflows/build.yml` | Core teszt, app teszt, debug app, Wear debug és unsigned release build; tesztartefaktumok megőrzése. |
| `tools/verify_release_security.py` | Exported komponensek, FileProvider, backup tiltás, cleartext, release signing és titkosított memória invariánsok ellenőrzése. |
| `.gitignore` | Keystore, lokális SDK és titkos konfiguráció kizárása. |
| `gradle/wrapper/gradle-wrapper.properties`, `gradle/wrapper/gradle-wrapper.jar` | Wrapper-disztribúció és wrapper JAR integritásának rögzítése; Gradle 8.9 checksum ellenőrzés CI-ben. |

**Elfogadás:** tiszta CI-ben teljes build és teszt; az unsigned release nem kap debug kulcsot; a forrásban nincs kulcs/jelszó; a verzióadatok konzisztensen egyeznek.

### P1 – Privacy Vault és helyi adattárolás

| Fájl | Változtatás |
|---|---|
| `app/.../SecretStore.kt` | AES-GCM, migráció, kulcs-invalidation és hibakezelés; plaintext fallback tilos. |
| `app/.../SmartTranslationMemoryStore.kt` | Titkosított blob megőrzése dekódolási hiba esetén; módosító művelet megszakítása, nincs „üres tár” felülírás. |
| `app/.../AuditLog.kt`, `DiagLog.kt`, `PrivacyActivity.kt` | Naplóredakció, törlési/megőrzési felület, diagnosztika személyes szöveg és titok nélkül. |
| `app/.../BackupHelper.kt`, `intelligence/SecureBackupArchive.kt`, `intelligence/BackupArchive.kt`, `ui/BackupCenterActivity.kt` | Mentés-visszaállítás verziózott manifesttel, méretlimittel, integritásellenőrzéssel és titkosítási hibánál fail-closed működéssel. |
| `app/src/main/res/xml/file_paths.xml`, `AndroidManifest.xml` | FileProvider csak szükséges könyvtárakat osszon meg; exportált komponensek allowlistje változatlanul explicit. |

**Elfogadás:** hibás jelszó, sérült blob, hiányzó/invalidált Keystore-kulcs, részleges írás és visszaállítás esetén nincs plaintext fallback, nincs csendes adatvesztés.

### P2 – Context AI és Translation Memory integráció

| Fájl | Változtatás |
|---|---|
| `core/.../SmartContextTranslation.kt` | Közös `ContextRequest`: előző/következő mondat, projekt, beszélő, stílus, jóváhagyott terminológia. |
| `core/.../SmartTranslationMemory.kt`, `app/.../SmartTranslationMemoryStore.kt` | Pontos nyelvpár és pontos normalizált szegmens; projektmemória elsőbbsége; csak APPROVED javaslat; kétértelmű egyezés jelzése. |
| `core/.../TranslationStyle.kt`, `TranslationQuality.kt`, `TranslationCheck.kt`, `TranslationQualityAudit.kt` | Stílus- és minőségi jelzések közös adatmodellje, eredet/súlyosság mezőkkel. |
| `app/.../TranslateService.kt`, `Engines.kt`, `PolyEngine.kt`, `OnlineSettingsDialog.kt` | Pipeline adapter: offline motor elsődleges; online/generatív motor külön opt-in. |
| `app/.../MainActivity.kt`, `ui/design/PremiumTranslationCard.kt` | Memóriajavaslat, eredet, bizonytalanság, jóváhagyás/elutasítás és kézi szerkesztés UI. |
| `core/src/test/.../SmartContextTranslationTest.kt`, `SmartTranslationMemoryTest.kt` és app tesztek | Kontextus, nyelvpár, projektütközés, jóváhagyás, kézi felülírás és offline fallback tesztje. |

**Elfogadás:** az offline fordítás nem függhet hálózattól; a memória nem cserélheti le automatikusan a fordítást; a kézzel jóváhagyott változat megmarad.

### P3 – Translation Quality Intelligence

| Fájl | Változtatás |
|---|---|
| `core/.../TranslationQuality.kt`, `TranslationCheck.kt`, `TranslationQualityAudit.kt`, `LineQuality.kt`, `Hallucination.kt` | Számok, nevek, tagadás, kihagyás/duplikáció, terminológia, nyelvtan és írásjelek ellenőrzése. |
| `core/.../GoldenWer.kt`, `WordErrorRate.kt`, `TextMetrics.kt` | Csak ellenőrzött referenciaátirattal számolt WER/CER; becsült pontszám külön. |
| `app/.../SrtBuild.kt`, `SrtEditorActivity.kt`, `ReviewSheet.kt` | Soronkénti jelzés, forrás-eredet, hangrészlet-visszajátszás és jóváhagyott javítás. |
| `app/src/test/.../HallucinationTest.kt`, `TranslationCheckTest.kt`, `LineQualityTest.kt` | Negáció, számok, nevek, rövid sorok, üres kimenet és hiányzó mondat regressziós mátrix. |

**Elfogadás:** a felület nem nevez heurisztikát „AI-bizonyosságnak”; a mérőszám forrása megjelenik; hibás ellenőrzés nem módosítja a forrást.

### P4 – Personal Learning Intelligence és offline Coach

| Fájl | Változtatás |
|---|---|
| `core/.../review/ReviewIntelligence.kt`, `intelligence/SpacedRepetition.kt`, `ultimate/UltimateLearningEngine.kt` | Egységes, determinisztikus tanulási esemény- és ismétlési szabályok. |
| `app/.../VocabLearner.kt`, `PhrasebookStore.kt`, `GlossaryStore.kt` | Fordítási hibából tanulókártya csak felhasználói megerősítéssel; aktív/passzív szókincs elkülönítése. |
| `app/.../intelligence/LearningAnalyticsStore.kt`, `LearningStats.kt`, `ui/LearningAnalyticsActivity.kt`, `ui/ReviewCoachActivity.kt` | Napi/heti célok, tényleges eseményekből számolt grafikonok, kihagyott napok büntetésének elkerülése. |
| Új `core/.../OfflineCoach.kt` és tesztje | Helyi szabályalapú feladatgenerálás és előre definiált szerepjátékok; internet és generatív modell nélkül is működőképes. |
| `app/.../MainActivity.kt`, `ui/PlatinumIntelligenceActivity.kt` | Coach felület és tanulási terv bekötése. |

**Elfogadás:** minden statisztika visszavezethető mentett eseményre; az offline Coach nem állít generatív AI képességet; ütemezés stabil és tesztelt.

### P5 – Voice & Pronunciation Intelligence

| Fájl | Változtatás |
|---|---|
| `app/.../AudioRecorder.kt`, `AudioHelper.kt`, `RecognitionHelper.kt`, `LiveWhisperAsr.kt`, `SystemAsr.kt` | Lifecycle, megszakítás, permission-visszavonás, audio erőforrás-felszabadítás és offline modellállapot. |
| `app/.../intelligence/PronunciationAssessment.kt`, `SpeakerDiarizer.kt`, `SpeakerTracker.kt` | ASR egyezés, beszédtempó, szünet és fonetikai metrika külön; csak validált metrika jelenhet meg pontszámként. |
| `app/.../TtsHelper.kt`, `AsrPrefs.kt`, `ModelsActivity.kt` | Telepített US/UK hangadat ellenőrzése, hiányzó nyelvi adat világos jelzése. |
| `app/src/test/.../PronunciationAssessmentTest.kt`, új audio tesztek | Referenciahangos benchmark, csend, zaj, rövid beszéd, megszakítás és permission teszt. |

**Elfogadás:** mikrofon csak explicit indításkor; háttérben nincs rejtett rögzítés; fonetikai pontosságot nem helyettesíthet egyszerű átirat-egyezés.

### P6 – Subtitle Studio Platinum

| Fájl | Változtatás |
|---|---|
| `app/.../FileSubtitleActivity.kt`, `SrtEditorActivity.kt`, `SrtJob.kt`, `SrtJobService.kt` | Szerkesztési munkafolyamat, állapotmentés, undo/redo, checkpoint és folytatás. |
| `core/.../SubFormat.kt`, `SrtExporter.kt`, `SrtQualityCheck.kt`, `SubtitlePostProcessor.kt`, `WordTiming.kt` | SRT/VTT/ASS round-trip, időzítés és olvashatóság validálás. |
| `app/.../WaveView.kt`, `ChunkMapView.kt`, `OutputWriter.kt` | Hanghullám/idővonal és biztonságos fájlkiírás. |
| `app/src/test/.../SubFormatTest.kt`, `SrtExporterTest.kt`, `SrtQualityCheckTest.kt` | Hibás bemenet, időátfedés, nagy fájl, export/import round-trip és megszakítás tesztje. |

**Elfogadás:** eredeti fájl soha nem íródik felül előnézet nélkül; export előtt blokkoló hibák megjelennek; checkpointból folytatás adatvesztés nélkül.

### P7 – Live Interpreter és Meeting Assistant

| Fájl | Változtatás |
|---|---|
| `app/.../LiveTranslateActivity.kt`, `LiveFullscreenActivity.kt`, `LivePanels.kt`, `MeetingMode.kt`, `MeetingSummary.kt` | Kétirányú munkamenetállapot, push-to-talk, beszélőváltás, megszakítás és átirat→összefoglaló folyamat. |
| `app/.../TranslateService.kt`, `service/OverlayService.kt`, `AndroidManifest.xml` | Előtérszolgáltatás és mikrofon-engedély életciklusának auditja; háttérben automatikus felvétel tilos. |
| `wear/.../WearMainActivity.kt`, `WearBridge.kt` | Óra companion parancsok és állapot-szinkron biztonságos tesztje. |

**Elfogadás:** a munkamenet egyértelműen látható, leállítható; offline ASR útvonal működik; online fordítás kikapcsolva nem küld adatot.

### P8 – Camera és Document Intelligence (külön opcionális modulok)

| Fájl / új komponens | Változtatás |
|---|---|
| Új `CameraTranslateActivity`, `CameraOcrCoordinator`, CameraX dependency | OCR élő kamera vagy képimport alapján; csak explicit kameraengedély; kiválasztott szöveg fordítása. |
| Új `DocumentImportCoordinator`, `PdfTextExtractor`, `DocxImportAdapter` | PDF/DOCX/TXT import; szkennelt PDF külön OCR állapot; formázásmegőrzés és hibás dokumentum visszautasítása. |
| `AndroidManifest.xml`, `app/build.gradle.kts`, `res/strings.xml` | Kameraengedély, dependency, hozzáférhetőségi szövegek és hibajelzések csak a funkció elkészültekor. |

**Elfogadás:** kamera és parser nincs „kész” státuszban, amíg a permission, OCR, adatvédelem, fájlméret-korlát, sérült fájl és valós eszköz teszt nincs kész.

### P9 – Cloud Sync (backend döntésig csak specifikáció)

| Fájl / új komponens | Változtatás |
|---|---|
| Új `SyncContract`, `SyncState`, `ConflictResolver` | Platformfüggetlen interfész és konfliktusmodell, backend nélkül. |
| Új `EncryptedSyncEnvelope` | Kliensoldali titkosítási protokoll specifikáció; kulcskezelés és helyreállítás tervezése. |
| `app/.../PrivacyActivity.kt`, `SettingsActivity.kt` | Felhasználói opt-in, eszközlista, visszavonás és adatkezelési tájékoztatás. |

**Elfogadás:** backend, auth, adatkezelő, kulcs-helyreállítás és fenyegetésmodell nélkül nincs hálózati sync implementáció vagy bekapcsolható UI.

### P10 – Egységes Platinum UI, accessibility és kiadás

| Fájl | Változtatás |
|---|---|
| `ui/design/PremiumDesignSystem.kt`, `PremiumTokens.kt`, `LuxuryPalettes.kt`, `PremiumColors.kt`, `PlatinumColors.kt` | Egyetlen tokenrendszer; Forest Graphite, Platinum, OLED; kontraszt és állapotpaletta. |
| `ui/theme/Theme.kt`, `Color.kt`, `Type.kt`, `NightMode.kt` | Rendszertéma, explicit világos/sötét/OLED beállítás és betűméret. |
| `ui/MainActivity.kt`, `SettingsActivity.kt`, `TaskSetupActivity.kt`, `TranscriptActivity.kt`, `TranscriptDetailActivity.kt`, `ModelsActivity.kt`, `PhrasebookActivity.kt`, `FileSubtitleActivity.kt`, `SrtEditorActivity.kt` | Képernyőnkénti migráció; egyszerre egy fő képernyő, screenshot baseline és regressziós ellenőrzés. |
| `ui/processing/*`, `Anim.kt`, `Haptics.kt`, `NavTransitions.kt` | Csökkentett mozgás, energiatakarékos mód, animáció és haptika kapcsoló. |
| `app/src/test/.../PremiumDesignScreenshotTest.kt`, `PremiumTokensTest.kt` | Világos/sötét/OLED, kompakt/nagy kijelző, nagy betűméret, kontraszt és fókusz. |

**Elfogadás:** minden fő képernyő ugyanazt a design rendszert használja; nincs levágott szöveg; TalkBack/fókusz és nagy betűméret tesztelt.

## 3. Javasolt kiadási mérföldkövek

- **6.0-alpha1:** P0–P1; forrásstabilizálás és adatbiztonság.
- **6.0-alpha2:** P2–P4; fordítási memória, minőség és offline tanulás.
- **6.0-beta1:** P5–P7; hang, felirat és meeting.
- **6.0-beta2:** P8 csak ha a CameraX/OCR és dokumentum parser ténylegesen implementálva és eszközön tesztelve; P9 backenddöntés után.
- **6.0-rc:** P10, teljes regresszió, frissítési teszt, aláírt release.
- **6.0 stable:** csak minden kiadási kapu teljesülése után.

## 4. Kockázatok és visszaállítás

- Adatbázis vagy preferences séma változtatása előtt verziózott migráció és mentés.
- Új funkció feature flag mögött, kikapcsolható módon.
- Új dependency csak licenc-, méret-, offline és biztonsági ellenőrzés után.
- A `minSdk`, `targetSdk`, `versionCode`, `applicationId`, FileProvider authority és aláírási kulcs változtatása külön jóváhagyást igényel.
- Bármelyik fázis visszavonható úgy, hogy a meglévő felhasználói adatok és exportált fájlok olvashatók maradjanak.
