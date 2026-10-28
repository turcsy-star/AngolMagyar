# AngolMagyar 2.28.6 – megvalósítási megjegyzések

## Token-szintű bizonytalanság
A JNI-híd a Whisper meglévő API-jából kiolvassa a token szövegét, valószínűségét és időbélyegét. A Kotlin `WhisperNative.Seg.tokens` mezőben ezek elérhetők. A `TokenQuality` a küszöb alatti tokeneket kézi ellenőrzésre jelöli. A `FileRecognizer` a leggyengébb tartalmi token log-valószínűségét a meglévő feliratminőségi jelzésbe is beemeli, a szavankénti pontszámokat pedig továbbadja a szerkesztőnek. Ez nem szóhelyességi garancia, és nem módosítja a felismert szöveget.

## Tartalommegőrzés
A `FileRecognizer` már nem dob el szegmenst pusztán azért, mert a no-speech valószínűsége 0.8 fölötti. Az egymás utáni három azonos szegmens is csak akkor szűrhető, ha mindhárom alacsony átlagos logprob értékű.

## Ellenőrzés
A ZIP forráscsomag. Ebben a környezetben az Android NDK-s build és a teljes Gradle tesztfuttatás nincs igazolva; a telefonos telepítés előtt Android Studio/Gradle build szükséges.


## Update 9 – gyors ugrás az első érintett feliratsorhoz
Az export előtti minőségellenőrzés párbeszédablakában megjelent az „Első érintett sor” gomb. Ez az első jelzett cue pozíciójára görget, és fókuszt kér a látható sorra. Nem írja át a feliratot, az időzítést vagy a fordítást. Az ellenőrzést és az exportot a felhasználó továbbra is külön kezeli.
