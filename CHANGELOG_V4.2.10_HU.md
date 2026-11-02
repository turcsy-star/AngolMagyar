# 4.2.10 – Diamond egységesítés, animációmentes gombok, kék-fehér alaptéma

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
