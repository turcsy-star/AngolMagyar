# AngolMagyar – dokumentáció (4.2.10)

A korábbi, verziónkénti állapot- és changelog-fájlok egyetlen fájlba lettek összevonva; a régi előzmények az eredeti csomagban (AngolMagyar-fix-4_2_9-next-wave.zip) megtalálhatók.

## 4.2.10 – Diamond egységesítés, animációmentes gombok, kék-fehér alaptéma

- **Gombanimáció megszüntetve**: a View-gombok/kártyák (Ui.kt), a Compose kártyák (MainActivity, PlatinumGlass) és az overlay chipek nem méretváltozással reagálnak; marad a hullámeffekt és a haptika. A kattintás azonnal fut (nincs 80 ms késleltetés).
- **Alaptéma**: az alapértelmezett luxus paletta `sapphire` (Midnight Sapphire, kék-fehér) a korábbi `forest` helyett. Aki már kézzel választott palettát, annak a választása megmarad.
- **Egységes diamond felület**: a pezsgőarany kiemelés (`Champagne`) kék-fehérre (`DiamondWhite`, #D6E9FF) cserélve a feldolgozó panelekben, a spektrumban, a hullámformában és a főképernyőn; a képernyők címkéi egységesen `DIAMOND • …` formátumúak.
- **Fejlesztési hullám**: a Motion / Global UI / Akadálymentesség kártya valódi tartalmat kapott (mozgásprofil-állapot, kontraszt-audit minden palettán `DesignAudit`, migrációs checklist). A hub állapotai pontosítva; a Document/Camera (P8) továbbra is specifikáció, ezért a gombja le van tiltva („Hamarosan”).
- Új teszt: `DesignAuditTest`.
- Nyitott (eszközteszt vagy külső függőség kell): teljes View→Compose migráció, TalkBack/fókuszsorrend teszt, CameraX/OCR.
- A főképernyő nagy gombkártyáján (HeroCard) végigfutó fénycsík is megszűnt.

## Nyitott tételek – elkészült részek
- **Kártyák keretfény-animációja megszűnt** (`PremiumCard`: nincs mozgó keretfény és belső fénycsík; a keret statikus).
- **P8 Dokumentum / Kamera**: új `DocumentCameraActivity`, `MlKitCameraEngine`, `OcrTextCleaner` (+ teszt), új függőség: `com.google.mlkit:text-recognition:16.0.1`. Állapot: kísérleti, eszköztesztre vár (`P8_DOCUMENT_CAMERA_HU.md`). PDF-import nincs.
- **TalkBack**: címsor-szemantika, 48 dp célok, összevont leírások; kézi tesztlista: `TALKBACK_TESZT_HU.md`.
- **View→Compose**: az Adatvédelem képernyő Compose-ra átírva. A többi klasszikus képernyő (Beállítások, Feliratfeldolgozás 2000+ sor, SRT-szerkesztő, Élő fordítás, Modellek, Átiratok…) még View-alapú, ezt nem jelöltem késznek.


---

## P8 – Dokumentum / Kamera (4.2.10)

Működés: `DocumentCameraActivity` – fotó (rendszerkamera, `TakePicture`, nincs CAMERA-engedély) vagy kép/szövegfájl kiválasztása → `MlKitCameraEngine` (ML Kit text-recognition, eszközön) → `OcrTextCleaner` → szerkeszthető szöveg → ML Kit angol→magyar fordítás.

- Támogatott: képek (latin betűs szöveg), .txt (max. 2 MB). **PDF-import nincs**; az oldalról fotó/képernyőkép készíthető.
- Adatvédelem: a kép nem hagyja el a telefont; a fordítómodell első használatkor letöltődhet.
- Állapot: `CaptureReadiness.EXPERIMENTAL`. READY csak eszköztesztek után:
  1. éles és homályos fotó, gyenge fény; 2. 12 MB feletti kép elutasítása; 3. offline fordítás letöltött modellel; 4. kamera nélküli eszköz / megszakított fotó; 5. nagy betűméret.
- A `CaptureRegistry` alapértelmezése szándékosan `UNAVAILABLE` maradt (a meglévő szerződésteszt védi).
- Tesztek: `OcrTextCleanerTest` (tiszta logika). Az OCR és a kamera csak eszközön tesztelhető.


---

## TalkBack / fókuszsorrend kézi teszt (4.2.10)

Kódszinten kész: címsor-szemantika (`heading()`), legalább 48 dp érintési célok, a hub gombjainak összevont leírása.
Eszközön ellenőrizendő (Beállítások → Kisegítő lehetőségek → TalkBack):

1. Intelligence Suite: a „Következő” gesztus szakaszcímről szakaszcímre ugrik (címsor-navigáció), a kártyák címe → leírása → gombja sorrendben szólal meg.
2. Minden „Megnyitás” gomb hangosan „<kártya címe> – megnyitás” formában hangzik el; a tiltott gomb „hamarosan”.
3. Adatvédelem: a kapcsoló szövege és állapota együtt hangzik; a törlés megerősítő párbeszéd fókusza a párbeszédre kerül, bezárás után a „Minden átirat törlése” gombra tér vissza.
4. Dokumentum / Kamera: fotó után az állapotsor szövege elhangzik; a szerkeszthető mező felirata „Szerkeszthető szöveg”.
5. Betűméret 200 %-on egyik szöveg sem vágódik le, görgetéssel minden elérhető.

Eredmény rögzítése: eszköz, Android-verzió, megfelelt / hibás (+ rövid leírás). Ha minden pont megfelelt, a `InfinityGlobalUi` checklist utolsó sora `true`-ra állítható.
