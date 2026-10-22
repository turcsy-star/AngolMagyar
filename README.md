# Angol–Magyar élő fordító – v2.12

## v2.12.0 (Whisper, élő teljesítmény-csúszka, pontosabb állapotjelzés)
- **Whisper (whisper.cpp) offline felismerő** – a PC-s változat motorja a telefonon: magyarul is pontos, nincs szükség Google csomagra. A modellt a Modellek képernyőn töltöd le (ajánlott: large-v3-turbo q5_0 ≈ 574 MB, gyengébb telefonra small ≈ 181 MB). Automatikus választás: ha van Whisper modell, az dolgozik; felülbírálható.
- **Teljesítmény-csúszka (1–5)** futás közben is: CPU-szálak száma (Whisper), szálprioritás, szünetek, melegedési küszöb; Vosk-nál csak a legfelső szinteken fut több chunk párhuzamosan. Csak CPU, GPU-t nem használ.
- **ETA és folyamat** másodpercenként frissül (az utolsó percek mért tempója alapján).
- **Chunk-végi tevékenység kiírva**: beszélők, felismerés %, javítás, fordítás i/n, összeállítás; chunk-térkép, hűtés-szalag, élő előnézet.
- Gyorsabb fordítás (egyszerre beadott blokkok), a Vosk modell nem töltődik újra chunkonként.
- **Mentés javítva**: az Android ICU reguláris kifejezés-hiba (ExceptionInInitializerError) megszűnt; a hibaüzenet most az okot is kiírja.
- Mentés a videó mellé / alapértelmezett mappába (Minden fájl elérése vagy mappa-engedély), kész rész mentése megszakításnál.
- Natív build: a CI-nek most C++-t is fordítania kell (NDK/CMake 3.22.1), az első build hosszabb.

Offline élő fordítás: Vosk + ML Kit + ONNX speaker + Wear.

## Modulok
- `:app` – telefon
- `:wear` – Wear OS companion

```bash
./gradlew :app:assembleDebug
./gradlew :wear:assembleDebug
```

## v2.9.4 (egyszerűbb indulás és letöltés)
- Első indítás: egy képernyő = egy engedély (Engedélyezés / Tovább), görgetés nélkül; végén egykoppintásos ajánlott modell-letöltés.
- A főképernyő csempéi mindig látszanak; modellek nélkül szürkék, koppintásra a letöltéshez visznek, fölöttük „Első lépés: modellek" kártya.
- Letöltés: szünet / folytatás (HTTP Range, a félbehagyott rész megmarad), Mégse hibaüzenet nélkül, várható idő (ETA) és sebesség, több modell egymás után, kapcsolatmegszakadásnál 3 automatikus újrapróbálás.
- Modellek képernyő: nincs készülékleírás, a nagy angol modell csak jelölt ajánlás; a többi nyelv összecsukva.
- „Felirat videóhoz": lépésről lépésre leírás és négy kész választás (angol videó → magyar felirat stb.).

## v2.9.3 (3. kör: használhatóság, adatvédelem, tesztek)
- Önellenőrzés / „Előkészítés ellenőrzése" képernyő (modellek, magyar felismerő, fordító, overlay, mikrofon, értesítés, akkumulátor, eszköz) gombokkal; első indításkor automatikusan megnyílik.
- Adatvédelem és mentés: online felismerés kapcsoló, beállítások + átiratok exportja/importja zipben, összes átirat törlése.
- Beállítások három fülön (Megjelenés / Felismerés / Haladó).
- Átiratok szűrő: Mind / Élő / Fájlból.
- A futó kártyán állapotsor az aktív felismerő motorokkal.
- Egységtesztek (Vad, SrtExporter, Fixes, TextPolish); a GitHub Actions lefuttatja, de hiba esetén is elkészül az APK.

## v2.9 (javítás: a Beállítások és más képernyők kártyái láthatatlanok voltak – Ui.card alpha=0)

**Fontos:** ezt a csomagot nem sikerült valódi Gradle-lel buildelni (nem volt SDK/Maven-hozzáférés); csak Kotlin-fordítási ellenőrzés futott android-33 jar + stubok ellen. Az első build Android Studióban/`./gradlew` paranccsal történjen, és a hibákat jelezd.

Új: témaválasztó (rendszer/világos/sötét), utolsó munkamenet folytatása, hang mentése + lejátszás soronként, tanuló szótár, beszélők átnevezése, összegzés, keresés ékezetfüggetlenül, törlés visszavonással, kedvencek, exportok (TXT/MD/VTT/SRT/DOCX/PDF), tárgyalás mód, érintésátengedő overlay, QuickTile, szint-mérő.
Javítva a v2.8-ban: lefagyó időzítő, `isNoise`, villogás, MeetingMode tartós mentése, overlay túlcsordulás, leállításkor elveszett függő sorok, Wear téma összeomlás, hiányzó `ChunkedTranscriber.trimOverlapHead`, hiányzó `useDiarization` (FileSubtitle kapcsoló), ORT `Result.get` hívás.
Nincs megvalósítva: Compose/Material3/Material You, Wear Tiles/Complications/OngoingActivity.

## v2.8
- ONNX / energia diarizáció kapcsoló (`preferOnnx`)
- ML Kit Language ID ki/be (`useMlLangId`)
- Képernyő ébren tartása futás közben (`keepScreenOn`)
- Futás kártya: session időzítő + utolsó felirat
- FileSubtitle: preferOnnx a Prefs-ből

## v2.7
- ONNX Runtime speaker embedding
- ML Kit Language ID
- Wear OS modul + WearBridge

## v2.6–2.2
Értesítés, shortcuts, beszélőnevek, összefoglaló, TTS, overlay gesztusok…

## ONNX modell
`speaker_encoder.onnx` → Beállításokból telepítés, vagy `files/models/`.
