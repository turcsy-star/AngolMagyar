# P8 – Dokumentum / Kamera (4.2.10)

Működés: `DocumentCameraActivity` – fotó (rendszerkamera, `TakePicture`, nincs CAMERA-engedély) vagy kép/szövegfájl kiválasztása → `MlKitCameraEngine` (ML Kit text-recognition, eszközön) → `OcrTextCleaner` → szerkeszthető szöveg → ML Kit angol→magyar fordítás.

- Támogatott: képek (latin betűs szöveg), .txt (max. 2 MB). **PDF-import nincs**; az oldalról fotó/képernyőkép készíthető.
- Adatvédelem: a kép nem hagyja el a telefont; a fordítómodell első használatkor letöltődhet.
- Állapot: `CaptureReadiness.EXPERIMENTAL`. READY csak eszköztesztek után:
  1. éles és homályos fotó, gyenge fény; 2. 12 MB feletti kép elutasítása; 3. offline fordítás letöltött modellel; 4. kamera nélküli eszköz / megszakított fotó; 5. nagy betűméret.
- A `CaptureRegistry` alapértelmezése szándékosan `UNAVAILABLE` maradt (a meglévő szerződésteszt védi).
- Tesztek: `OcrTextCleanerTest` (tiszta logika). Az OCR és a kamera csak eszközön tesztelhető.
