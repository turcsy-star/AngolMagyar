# AngolMagyar v2.23.0 – javított, egységesített csomag

Ez a kiadás a v2.22.2 hibáit javítja, és az újdonságokat a tesztekkel együtt hozza.
(A mappa és a zip neve mostantól a verziót követi.)

## Javított hibák (a v2.22.2-ben)
| Hiba | Javítás |
|------|---------|
| `TextPolish.kt` 48 soros csonk volt, 10 hiányzó függvénnyel – a projekt nem fordult | Újraírva a tesztek alapján (tegezés, szótár, zajszűrés, `cleanTranscript`) |
| `Vad.kt` csonk volt, 8 hiányzó függvénnyel (`segments`, `speechChunks`, `speechChunksFromFile`, `splitAtPauses`, `mergeSmallPieces`, `isMostlySilent`, `lastFine`…) | Újraírva, közös küszöbű, fájlból streamelő szakaszolás + neurális (Silero) opció |
| `Anim`, `CheckView` osztály hiányzott (`FileSubtitleActivity` használta) | Újraírva: gépelős előnézet, pipa-animáció konfettivel |
| `TextPolishTest.kt`: két teszt az osztályon kívül (szintaktikai hiba) | Visszatéve az osztályba |
| Összeomlás Wear / gyorscsempe / parancsikon / feladat-beállító indításakor: a `MainActivity` nem volt a manifestben | Regisztrálva; a Compose kezdőlap továbbadja az `auto_start` / `profile` extrát |
| `WhisperPrompt`: 4 teszt bukott, a prompt angol volt magyar felismeréshez | Nyelvhű prompt (magyar → magyar szöveg), zajos előző szöveg kiszűrve |
| OLED beállítás nem érvényesült a Compose főképernyőn | A téma a `prefs.oledBlack`-ot kapja |
| Üres `:core`, duplikált `TextPolish`/`WhisperNative`, kétféle „régi” főképernyő, elavult JNI-fájl | 20 tiszta fájl áttéve a `:core`-ba; duplikátumok és `LegacyMainActivity`, `whisper_jni_v221.cpp` törölve |

## Újdonságok
* **Kezdőlap** (Compose): animált aurora-háttér, lépcsőzetes belépés, nyomásra rugózó kártyák, csillogó „Felirat készítése” kártya, élő feladat-folyamatsáv, élő fordítás kártya hanghullámmal.
* **Egy érintéses felirat-készítés**: a gomb rögtön a fájlválasztót nyitja.
* **Fordítás-ellenőrzés** (`TranslationCheck`): „szám eltér”, „név hiányzik”, „tagadás eltér” jelzések a gyanús sorok közé (szerkesztő, jelentés).
* **Whisper prompt**: a felismert nyelven, névlistával és az előző darab végével.

## Modulok
* `:core` – tiszta Kotlin logika (szöveg, VAD, SRT, ellenőrzés, online fordító); Android-függés nélkül tesztelhető.
* `:app`, `:wear`.

## Ellenőrzés
* A `:core` 21 fájlját és a tesztfájlokat (119 teszt) Kotlin 2.4 fordítóval lefordítottam és lefuttattam: mind sikeres.
* Az Android-specifikus részt (Compose, Activity-k, JNI) ebben a környezetben **nem** lehetett lefordítani (nincs Android SDK). Első lépésben futtasd: `./gradlew :app:assembleDebug :wear:assembleDebug :app:testDebugUnitTest`.

## Build
```bash
./gradlew :app:assembleDebug :wear:assembleDebug
./gradlew :app:testDebugUnitTest
```
