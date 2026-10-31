# Build állapot – v2.59.0

- Forráscsomag és Room 2 → 3 migráció elkészítve.
- Intelligens ismétlő Compose-képernyő és központi navigáció elkészítve.
- Spaced-repetition ütemező egységtesztjei hozzáadva.
- ZIP-integritás ellenőrizhető csomagolás után.
- Gradle tesztparancs megkísérelve: `./gradlew :app:testDebugUnitTest --offline --no-daemon`.
- A Gradle wrapper 8.9 disztribúciója hiányzik a helyi cache-ből; a letöltés DNS-hiba (`UnknownHostException: services.gradle.org`) miatt nem sikerült.
- Kotlin fordítás, Room KSP generálás, unit teszt, APK és eszközteszt ezért nincs igazolva.
