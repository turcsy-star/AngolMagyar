# Angol–Magyar élő fordító – v2.9

Offline élő fordítás: Vosk + ML Kit + ONNX speaker + Wear.

## Modulok
- `:app` – telefon
- `:wear` – Wear OS companion

```bash
./gradlew :app:assembleDebug
./gradlew :wear:assembleDebug
```

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
