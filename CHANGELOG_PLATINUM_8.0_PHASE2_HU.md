# Platinum Intelligence 8.0 – Phase 2 változásnapló

## Hozzáadva
- Stream-alapú SHA-256 és méretellenőrző modellintegritás-kapu.
- Offline képességmátrix a telepített és ellenőrzött helyi komponensek megkülönböztetésére.
- 9 új core unit teszteset.
- Fájlszintű specifikáció és validációs korlátok.

## Megőrizve
- Android 4.2.6 / versionCode 40406, applicationId és SDK-beállítások.
- `:app`, `:core`, `:wear` modulok.
- Offline/online útvonalak és meglévő adatkezelési, titkosítási és permission határok.

## Státusz
Core forrás implementálva. Gradle/JUnit, Android build, modellletöltési adapter és fizikai eszközteszt ebben a környezetben nem igazolt.
