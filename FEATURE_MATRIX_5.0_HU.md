# AngolMagyar – funkcióállapot-mátrix a v5.0 célverzióhoz

A státuszok forráskód- és dokumentációs áttekintést jelentenek, nem fizikai eszközös működési garanciát.

| Javaslat / képesség | Kiinduló állapot a v4.2.0-ban | 4.2.1 csomag változása | v5.0 előtt hátralévő munka |
|---|---|---|---|
| Infinity Context AI | Részleges: `SmartContextTranslation`, `OnlineTranslator` kontextusablak | Nem duplikálva | Kontextus-adatmodell, beszélő/projekt kontextus és alternatívák egységes integrációja |
| Translation Quality Intelligence | Több szabály- és auditmotor, WER referencia tesztek | Nincs új pontossági állítás | Teljes pipeline/UI összekötés, referenciahangos benchmark és nyelvtani ellenőrzés |
| Voice Intelligence Studio | ASR, VAD, TTS, `PronunciationAssessment` | Nincs új pontozási állítás | Hangfelvétel összehasonlítás, fonetikai validáció, US/UK eszközteszt |
| Personal Learning Intelligence | SM-2 és `LearningAnalyticsActivity` | Nincs duplikált tanulómotor | Fordítási hibákból gyakorlófeladat, aktív/passzív szókincs és teljes tanulási eseménynapló |
| Subtitle Studio Pro | SRT/VTT/ASS feldolgozás, editor, job queue, checkpoint | Nincs duplikált szerkesztő | Hanghullám, szinkronizált kétpaneles editor, undo/redo, verziódiff és UX validáció |
| Offline AI & Privacy Vault | `SecretStore`, `SecureBackupArchive`, `PrivacyActivity`; a SecretStore korábban plaintext fallbacket engedett | **Javítva:** fail-closed AES-GCM titoktárolás; release/debug aláírás szétválasztása | Biometrikus zár, megőrzési szabályok UI-integrációja, biztonsági tesztek |
| Smart Translation Memory | TranslationMemory, GlossaryStore, GlossaryGovernance, RetranslationPlanner | Nincs párhuzamos adattár | UI/pipeline integráció, projekt és globális prioritás, hatásvizsgálat és jóváhagyás |
| Diamond Experience | Compose design tokenek és több paletta; View/Compose eltérő képernyők | **Bővítve:** Forest Graphite paletta és Compose világos/sötét prémium színséma | Teljes képernyőmigráció, OLED/kontraszt/accessibility és screenshot baseline |
| AI Conversation Partner | Nincs igazolt teljes párbeszédpartner | Specifikálva | Generatív szolgáltató, beszédfolyam, tanulási visszajelzés, költség/adatvédelmi kontroll |
| Live Interpreter | Élő fordítás és ASR alapok | Nincs duplikálva | Kétirányú munkamenet, megszakítások, latency és valós készülék teszt |
| Smart Camera Translate | Nem találtunk igazolt kamera-OCR munkafolyamatot | Specifikálva | CameraX + ML Kit OCR, engedélyek, kijelölés, overlay és készülékes teszt |
| Document Intelligence | Dokumentum export infrastruktúra részben jelen van | Specifikálva | PDF/DOCX import, formázásmegőrzés, OCR és export teszt |
| Meeting Assistant | `MeetingMode`, `MeetingSummary` alapok | Nincs duplikálva | Teljes átirat → beszélő → fordítás → összefoglaló munkafolyamat |
| Cloud Sync | Nincs rögzített backend/szolgáltató | Tudatosan nem aktiválva | Backend és auth kiválasztás, E2E titkosítás, konfliktusfeloldás és adatkezelés |
| Smart Export | ExportHelper, felirat export | Nincs duplikálva | Export sablonok, előnézet, dokumentumformátumok és metaadat-védelem |

## Fontos értelmezés

A 4.2.1 egy biztonsági és felületi hardening kiadás, nem a teljes v5.0 funkciókészlet. A hiányzó, szolgáltatófüggő képességeket (generatív társalgás, felhőszinkron) nem lehet hitelesen bekapcsolni szolgáltató, backend, hitelesítés és adatkezelési döntés nélkül. A kamera- és dokumentumfeldolgozás is további natív Android-integrációt igényel.
