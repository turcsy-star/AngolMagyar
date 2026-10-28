# Build- és tesztállapot – AngolMagyar v2.29.0

## Ellenőrizve
- Kódolvasás és kézi átnézés minden módosított fájlon; Gradle wrapper (8.9) a csomagban.

## NEM igazolt (nem volt Kotlin-fordító, Android SDK, Maven-elérés)
- Kotlin-fordítás, JUnit-futtatás, APK build, eszközteszt.
- Telefonon kipróbálandó: minőségellenőrző lap, célzott újrafelismerés (hosszú videón), éjszakai értesítés, időkorlát utáni folytatás, Keystore-os kulcs, mappa-engedélyes mentés release buildben.
- R8 (`-Pminify`) csak telefonos próba után használd (Vosk/JNA/ONNX/Whisper).

## Futtatás
```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug :wear:assembleDebug
```

## Gyémánt Edition – második fejlesztési kör
- Sikeres: a `ReviewIntelligence.kt` és `TranslationCheck.kt` önálló Kotlin-fordítása.
- Sikeres: célzott smoke teszt a rövid természetes válaszokra, üres sorokra és nulla hosszúságú időzítésre.
- Nem futtatható: teljes Gradle/JUnit és APK build, mert a Gradle 8.9 disztribúció nincs helyben, a hálózati letöltés DNS-hibával leállt.
- A képernyőn látható index továbbra is jelzésekből számított ellenőrzési mutató, nem ténylegesen mért beszédfelismerési pontosság.


## Update 11 – prémium terminológiai alapok
- Sikeres: `core/.../GlossaryGovernance.kt` önálló Kotlin-fordítása.
- Sikeres: izolált smoke teszt a globális/projekt-szótár prioritására, pontos hatás-előnézetre és TSV import/export körútra.
- Új JUnit tesztfájl: `GlossaryGovernanceTest.kt` (a teljes JUnit futás ebben a környezetben nincs igazolva).
- Új Platina kiemelőszín-téma és magyar prémium fejlesztési specifikáció.
- NEM igazolt: teljes Gradle build, Android APK, Wear APK, teljes tesztcsomag és készülékes UI teszt. A szótárgovernance API még nincs a meglévő Android UI-/tárolási-/fordítási pipeline-ba bekötve.
