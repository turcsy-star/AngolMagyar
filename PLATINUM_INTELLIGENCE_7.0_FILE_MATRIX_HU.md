# Platinum Intelligence 7.0 – fájlszintű módosítási mátrix

A „meglévő” a fájl vagy képesség forrásbeli jelenlétét jelenti, nem minden eszközön igazolt működést. Új fájl csak a hozzá tartozó teszttel együtt fogadható el.

| Terület | Meglévő belépési pontok | Célzott módosítás / új fájl | Állapot a 4.2.6 ZIP-ben |
|---|---|---|---|
| Fordítási központ | `TranslateService.kt`, `Engines.kt`, `PolyEngine.kt`, `PremiumTranslationCard.kt` | `TranslationWorkspace.kt` (elkészült core változatkezelés/diff/audit); később tartós RevisionStore és UI integráció | Core workspace alap megvalósítva; UI/persistence adapter még hiányzik |
| Speech Intelligence | `AudioRecorder.kt`, `LiveWhisperAsr.kt`, `SystemAsr.kt`, `ChunkedTranscriber.kt`, `TranscriptDetailActivity.kt` | `TranscriptSegmentEditor.kt`, `AudioWaveformTimeline.kt`, `RecognitionConfidenceAdapter.kt` | ASR/átirat alapok meglévők; teljes hullámforma-szerkesztő nincs igazolva |
| Dictionary Pro | `GlossaryStore.kt`, `PhrasebookStore.kt`, `Projects.kt`, `SmartTranslationMemoryStore.kt` | `TerminologyConflictResolver.kt`, `GlossaryRevisionStore.kt`, `TerminologyImpactPreview.kt` | Alap szótárak meglévők; konfliktus- és hatásvizsgálat bővítendő |
| Pronunciation Studio | `PronunciationAssessment.kt`, `RecognitionHelper.kt`, `SpeakerDiarizer.kt`, `ModelsActivity.kt` | `PronunciationSession.kt`, `PronunciationMetric.kt`, `PhonemeFeedbackProvider.kt` | ASR/értékelési alap van; fonetikai mérés csak validált providerrel |
| Subtitle Studio | `SrtEditorActivity.kt`, `FileSubtitleActivity.kt`, `SrtJob.kt`, `SrtBuild.kt`, `OutputWriter.kt` | `SubtitleUndoStack.kt`, `SubtitleTimelineModel.kt`, `SubtitleAutosaveCoordinator.kt` | Szerkesztés és job alap meglévő; professzionális timeline/undo bővítendő |
| Model Manager | `ModelManager.kt`, `ModelsActivity.kt`, `ModelDownloadService.kt`, `FeatureReadiness.kt` | `ModelManifest.kt`, `ModelIntegrityVerifier.kt`, `ModelInstallTransaction.kt`, `OfflineCapabilityMatrix.kt` | Modellkezelés meglévő; integritás/atomikus telepítés auditálandó |
| Workflow Automation | `SrtJob.kt`, `SrtJobService.kt`, `Checkpoint.kt`, `MeetingWorkflow.kt`, `TranscriptFlow.kt` | `PlatinumWorkflow.kt` (elkészült core állapotgép); később `WorkflowCheckpointStore` és UI adapter | Determinisztikus engine megvalósítva; tartós tároló/UI még hiányzik |
| Privacy Center | `SecretStore.kt`, `SmartTranslationMemoryStore.kt`, `PrivacyActivity.kt`, `AuditLog.kt`, `BackupHelper.kt` | Privacy dashboard és redakciós tesztek; `PrivacyDiagnostics.kt` | Biztonsági alap meglévő; teljes központ bővítendő |
| Adaptive Learning | `PhrasebookStore.kt`, `SpacedRepetition.kt`, `ReviewIntelligence.kt`, `LearningAnalyticsStore.kt` | `LearningEventLedger.kt`, `AdaptiveSchedulePolicy.kt`, `LearningGoalStore.kt` | Ismétlés/analitika alapok meglévők; eseményalapú adaptáció bővítendő |
| Design System 2.0 | `PremiumDesignSystem.kt`, `PremiumTokens.kt`, `Theme.kt`, `NightMode.kt` | token audit, komponensmigráció, screenshot/accessibility tesztek | Részleges design rendszer; teljes képernyőmigráció nincs igazolva |

## Megőrzendő konfigurációs fájlok

- `settings.gradle.kts`, gyökér `build.gradle.kts`, `app/build.gradle.kts`, `core/build.gradle.kts`, `wear/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`, `app/src/main/res/xml/*`, `proguard-rules.pro`
- `.github/workflows/build.yml`, `.gitignore`, `gradle/wrapper/*`

Ezek módosítása csak dependency-, permission-, manifest- vagy CI-szükséglet esetén történhet. Új exported komponens, permission, endpoint vagy dependency security review nélkül nem fogadható el.


## Platinum Intelligence 8.0 Phase 2 kiegészítés

| Terület | Új fájl | Állapot |
|---|---|---|
| Offline Model Manager trust gate | `core/.../ModelIntegrityVerifier.kt` | Core implementálva; stream SHA-256/méretellenőrzés |
| Offline readiness | `core/.../OfflineCapabilityMatrix.kt` | Core implementálva; verified component szükséges |
| Model integrity tesztek | `core/src/test/.../ModelIntegrityVerifierTest.kt` | 5 JUnit tesztforrás |
| Capability matrix tesztek | `core/src/test/.../OfflineCapabilityMatrixTest.kt` | 4 JUnit tesztforrás |

A modellkezelő UI és letöltési/telepítési tranzakció integrációja még nem kész.
