# AngolMagyar Platinum Intelligence 7.0 – teljes fejlesztési program

**Kiindulás:** AngolMagyar 4.2.6 / Platinum Intelligence 6.0 Engineering  
**Cél:** Ultimate Offline Translation & Learning Ecosystem  
**Dokumentumállapot:** megvalósítási specifikáció; nem állítja, hogy a 7.0 funkciói már elkészültek.

## 1. Kötelező invariánsok

1. Meglévő működés, felhasználói adatok, fájlformátumok és frissítési útvonal megőrzése.
2. A jelenlegi `applicationId=hu.angolmagyar.fordito`, `versionName=4.2.6`, `versionCode=40406`, `minSdk=29`, `targetSdk=35`, `compileSdk=35` változatlan a migrációs tesztekig.
3. Offline-first: internet nélkül működő képesség nem függhet hálózati szolgáltatástól. Ha helyi modell hiányzik, a rendszer ezt pontosan jelzi; nem szimulál sikeres feldolgozást.
4. A titkosítási, Keystore-, exported komponens-, FileProvider- és fail-closed határok nem gyengíthetők.
5. Nincs automatikus adatfelülírás: fordítási memória, szótár, AI-javaslat vagy szinkron csak explicit jóváhagyással módosíthat védett adatot.
6. Nincs hamis pontossági százalék. ASR-konfidencia, WER/CER, nyelvi heurisztika és fonetikai mérés külön metrika.
7. Kamera, felhőszinkron, online motor és mikrofon kizárólag engedélyezett, látható, visszavonható felhasználói művelet után használható.
8. „Kész” csak akkor jelölhető, ha forrás, teszt, build és szükséges eszközteszt bizonyítja.

## 2. A tíz fejlesztési terület teljes célképe

### 01 – Smart Translation Command Center
**Cél:** egy központi fordítási munkafelület.
- HU↔EN, szó szerinti, természetes, hivatalos, üzleti stílus.
- Több változat összevetése; eredeti/módosított diff; verzióelőzmény és visszaállítás.
- Nevek, számok, dátumok, pénznemek, tagadás, mértékegységek és terminológia külön ellenőrzése.
- Memória- és szótárjavaslat csak felhasználói jóváhagyással.
- Offline motor marad az offline útvonal; online motor külön opt-in.

**Fő fájlok:** `TranslateService.kt`, `Engines.kt`, `PolyEngine.kt`, `SmartTranslationMemoryStore.kt`, `core/.../SmartTranslationMemory.kt`, `core/.../TranslationQuality.kt`, `ui/design/PremiumTranslationCard.kt`, `MainActivity.kt`.

**Új célkomponensek:** `TranslationWorkspaceState`, `TranslationVariant`, `TranslationRevisionStore`, `TranslationDiff`, `TranslationAuditCoordinator`.

### 02 – Platinum Speech Intelligence
**Cél:** hosszú hangfelvételek megbízható, folytatható átiratfolyamata.
- Hanghullám és szegmensenkénti átirat; kijelölt szó/mondat visszajátszása.
- Bizonytalan szakaszok jelölése csak motor által szolgáltatott konfidencia vagy validált heurisztika alapján.
- Célzott újrafelismerés, checkpoint, megszakítás utáni folytatás.
- HU/EN nyelvprofilok; diarizáció csak akkor, ha a motor ténylegesen támogatja.
- WER/CER csak ember által ellenőrzött referenciával.

**Fő fájlok:** `AudioRecorder.kt`, `AudioHelper.kt`, `LiveWhisperAsr.kt`, `SystemAsr.kt`, `ChunkedTranscriber.kt`, `Checkpoint.kt`, `SpeakerDiarizer.kt`, `TranscriptRepo.kt`, `TranscriptDetailActivity.kt`, `tools/golden_wer.py`.

**Új célkomponensek:** `TranscriptSegmentEditor`, `AudioWaveformTimeline`, `RecognitionConfidenceAdapter`, `AsrBenchmarkRunner`.

### 03 – Dictionary Intelligence Pro
**Cél:** személyes, projekt- és szakterületi terminológia kezelése.
- Globális/projekt szótár; duplikátum- és konfliktusészlelés.
- Példamondat, jelentés, nyelvpár, címke és szakterület.
- Jóváhagyott változatok védelme; hatásvizsgálat, hogy egy terminus hol változtatná meg a fordítást.
- Import/export, verziózás, visszavonás és titkosított helyi mentés.
- Offline keresés és ismétlés.

**Fő fájlok:** `GlossaryStore.kt`, `PhrasebookStore.kt`, `PhrasebookActivity.kt`, `Projects.kt`, `SmartTranslationMemoryStore.kt`, `BackupHelper.kt`.

**Új célkomponensek:** `TerminologyConflictResolver`, `GlossaryRevisionStore`, `TerminologyImpactPreview`.

### 04 – Pronunciation Master Studio
**Cél:** kiejtésgyakorlás és visszajelzés mérhető korlátokkal.
- US/UK hangváltozat; szó- és mondatgyakorlás.
- Saját felvétel, visszajátszás, tempó és szünet elemzése.
- Fonéma-/hangkapcsolat-értékelés kizárólag fonetikai modelltől vagy validált referenciaértékelőtől.
- ASR-átirat-egyezés nem nevezhető kiejtési pontosságnak.
- Offline TTS/ASR csak telepített és támogatott nyelvi modellel.

**Fő fájlok:** `AudioRecorder.kt`, `RecognitionHelper.kt`, `LiveWhisperAsr.kt`, `SystemAsr.kt`, `SpeakerDiarizer.kt`, `ModelsActivity.kt`, `AsrPrefs.kt`, `PronunciationAssessment.kt`.

**Új célkomponensek:** `PronunciationSession`, `PronunciationMetric`, `PhonemeFeedbackProvider` (provider nélkül a fonetikai értékelés UNAVAILABLE).

### 05 – Platinum Subtitle Studio
**Cél:** megbízható, két nyelvű feliratszerkesztés.
- SRT/VTT import-export round-trip; ASS csak támogatott parserrel.
- Idővonal, hullámforma, soronkénti szerkesztés, undo/redo.
- Olvasási sebesség, sorhossz, időátfedés, hibás timestamp ellenőrzése.
- Autosave/checkpoint; nagy fájlok memóriahatékony kezelése.
- Eredeti fájl csak explicit exporttal cserélhető.

**Fő fájlok:** `FileSubtitleActivity.kt`, `SrtEditorActivity.kt`, `SrtJob.kt`, `SrtJobService.kt`, `SrtBuild.kt`, `OutputWriter.kt`, `ChunkMapView.kt`, `ReviewSheet.kt`, `core/.../SubFormat.kt`, `SrtExporter.kt`, `SrtQualityCheck.kt`.

**Új célkomponensek:** `SubtitleUndoStack`, `SubtitleTimelineModel`, `SubtitleAutosaveCoordinator`.

### 06 – Offline AI Model Manager
**Cél:** a helyi modellek biztonságos és átlátható életciklusa.
- ASR/TTS/fordítási modellek listája, nyelv, verzió, méret, tárhelyigény.
- Letöltés folytatása, integritásellenőrzés, atomikus telepítés és visszaállítás.
- Sérült/inkompatibilis modell kizárása a használatból.
- Offline readiness: mely képesség működik internet nélkül.
- Licenc és modellforrás metaadat megőrzése.

**Fő fájlok:** `ModelManager.kt`, `ModelsActivity.kt`, `ModelDownloadService.kt`, `MlKitModels.kt`, `LiveWhisperAsr.kt`, `AsrPrefs.kt`, `FeatureReadiness.kt`.

**Új célkomponensek:** `ModelManifest`, `ModelIntegrityVerifier`, `ModelInstallTransaction`, `OfflineCapabilityMatrix`.

### 07 – Intelligent Workflow Automation
**Cél:** felhasználó által indított, megszakítható, újrafuttatható folyamatok.
- Hang → átirat → fordítás → terminológiaellenőrzés → felirat → export sablon.
- Minden lépés bemeneti/kimeneti metaadatot és állapotot tárol.
- Idempotens lépések, checkpoint, cancel/resume, részleges hibakezelés.
- Nincs rejtett hálózati lépés; online komponens külön engedélyezett.

**Fő fájlok:** `SrtJob.kt`, `SrtJobService.kt`, `Checkpoint.kt`, `MeetingWorkflow.kt`, `TranscriptFlow.kt`, `OutputWriter.kt`, `FeatureReadiness.kt`.

**Új célkomponensek:** `WorkflowDefinition`, `WorkflowRun`, `WorkflowStep`, `WorkflowCheckpointStore`.

### 08 – Platinum Privacy Center
**Cél:** adatkezelés átlátható, helyi és ellenőrizhető központja.
- Keystore/AES-GCM státusz és helyi tárhelydiagnosztika.
- Hálózati útvonalak, online opt-in, adatküldési előnézet.
- Naplóredakció; titok és teljes privát átirat nem kerülhet logba.
- Helyi adatok exportja/törlése; titkosított backup/restore.
- Felhasználói döntés és visszavonás naplózása érzékeny tartalom nélkül.

**Fő fájlok:** `SecretStore.kt`, `SmartTranslationMemoryStore.kt`, `PrivacyActivity.kt`, `AuditLog.kt`, `DiagLog.kt`, `BackupHelper.kt`, `AndroidManifest.xml`, `tools/verify_release_security.py`.

**Kötelező invariáns:** a jelenlegi fail-closed titokkezelés és titkosított memória adatmegőrzése nem gyengíthető.

### 09 – Adaptive Learning Intelligence
**Cél:** valódi eseményekből számolt személyes tanulási terv.
- Spaced repetition, napi/heti cél, nehézség adaptálása.
- Gyakori hibák és tanulókártyák csak jóváhagyott eseményekből.
- Aktív/passzív szókincs külön; kimaradt nap nem jár automatikus büntetéssel.
- Offline tanulás, statisztikák forrása visszakövethető.

**Fő fájlok:** `PhrasebookStore.kt`, `PhrasebookActivity.kt`, `ReviewCoachActivity.kt`, `LearningAnalyticsStore.kt`, `LearningAnalyticsActivity.kt`, `SpacedRepetition.kt`, `ReviewIntelligence.kt`, `UltimateLearningEngine.kt`.

**Új célkomponensek:** `LearningEventLedger`, `AdaptiveSchedulePolicy`, `LearningGoalStore`.

### 10 – Platinum Design System 2.0
**Cél:** egységes, akadálymentes, gyors és prémium UI.
- Forest Graphite / Platinum paletta; világos, sötét, OLED.
- Egységes spacing, tipográfia, ikonok, gombok, állapotjelzések.
- Tablet/nagy kijelző; rendszerbetűméret 1.3–1.6; TalkBack.
- reduceMotion, energiatakarékos mód; animáció nem lehet funkciófeltétel.
- Fő képernyők fokozatos migrációja, screenshot és UI regresszióteszt.

**Fő fájlok:** `ui/design/PremiumDesignSystem.kt`, `PremiumTokens.kt`, `LuxuryPalettes.kt`, `ui/theme/Theme.kt`, `Color.kt`, `Type.kt`, `NightMode.kt`, `Anim.kt`, `Haptics.kt`, `NavTransitions.kt`, fő Activity/Compose képernyők.

## 3. Függőségi sorrend és kapuk

| Hullám | Tartalom | Kilépési kapu |
|---|---|---|
| W0 | P0 build, forrásaudit, biztonság | CI build + unit tesztek + security PASS |
| W1 | Privacy Center + Model Manager | titkosítási, sérült modell, rollback és repülőgép-mód tesztek |
| W2 | Translation Center + Dictionary | HU↔EN regresszió, terminológiaütközés, kézi jóváhagyás, offline teszt |
| W3 | Speech + Pronunciation | lifecycle, benchmark, engedély és valós hangteszt |
| W4 | Subtitle Studio + Workflow | round-trip, nagy fájl, cancel/resume, adatvesztés nélküli export |
| W5 | Adaptive Learning | eseményalapú analitika és determinisztikus ütemezési teszt |
| W6 | Design System migráció | képernyőnkénti UI, TalkBack, nagy betűméret, reduceMotion |
| W7 | integrációs és kiadási keményítés | frissítés, teljes eszközmátrix, aláírt release és SHA-256 |

Camera/OCR és Cloud Sync nem része a tíz kötelező célterületnek ebben a hullámban, mert a 4.2.6 állapotjelentése szerint a Capture UNAVAILABLE és Sync NoOp. Ezek csak külön backend/parser/permission döntés és teljes teszt után kapcsolhatók be.

## 4. Verziózási stratégia

A jelenlegi ZIP verziója `4.2.6 / 40406`. A 7.0 itt termék- és fejlesztési célverzió. A `versionName` és `versionCode` emelése csak akkor történjen, amikor a W0–W7 implementációk legalább release candidate szinten buildelnek, az adatbázis/preference migráció tesztelt, és a frissítés a 4.2.x adatokkal bizonyított.

## 5. Megvalósítási előrehaladás – 2026-10-31

- **Megvalósított core alap:** `PlatinumWorkflow.kt` – validált workflow gráf, offline/network/capability kapuk, start/complete/fail/retry/cancel állapotátmenetek; tesztforrás és 6 ellenőrzési eset.
- **Megvalósított core alap:** `TranslationWorkspace.kt` – változatok, append-only revízió, visszaállítás új revízióként, korlátozott token diff és meglévő minőség-audit bekötése; tesztforrás és 5 ellenőrzési eset.
- A Compose képernyők, tartós Room/Keystore tárolóadapterek, tényleges pipeline végrehajtó adapterek és teljes Android build még hátravannak. A két core komponens nem állítja, hogy a teljes 7.0 funkciókészlet elkészült.

## 6. Kiadási definíció

- **Engineering:** forrás és specifikáció rendelkezésre áll; build lehet nem igazolt.
- **Alpha:** minden bekapcsolt funkció implementált, tesztek futnak; ismert korlátok jelölve.
- **Beta:** teljes regresszió és eszközteszt folyamatban; nincs kritikus adatvesztési/biztonsági hiba.
- **RC:** minden blokkoló teszt PASS, release signing konfiguráció ellenőrzött.
- **Stable:** RC + legalább 48 óra smoke teszt, frissítés és valós készülékes ellenőrzés.

**Jelen csomag besorolása:** 7.0 Engineering + részlegesen implementált core alap + 4.2.6 Android baseline. Nem teljes 7.0 alkalmazás, nem APK és nem stable release.
