# Platinum Intelligence 8.0 – Phase 2: Offline Model Trust & Capability

## Cél
A helyi AI-modellek csak integritásellenőrzés után legyenek használhatók, és az alkalmazás ne jelezzen offline készséget pusztán egy telepített, de nem ellenőrzött komponens alapján.

## Ténylegesen implementált core komponensek

### `core/src/main/java/hu/angolmagyar/fordito/ModelIntegrityVerifier.kt`
- SHA-256 ellenőrzés streamből, teljes fájl memóriába olvasása nélkül.
- Manifest mezők validálása (azonosító, verzió, méret, 64 karakteres hex digest).
- Várható méret ellenőrzése.
- `maxBytes` korlát, túlméretes adat elutasítása.
- Elkülönített állapotok: `VERIFIED`, `INVALID_MANIFEST`, `SIZE_MISMATCH`, `TOO_LARGE`, `HASH_MISMATCH`, `IO_ERROR`.
- A komponens nem tölt le és nem telepít modellt; a streamet a hívó kezeli és zárja le.

### `core/src/main/java/hu/angolmagyar/fordito/OfflineCapabilityMatrix.kt`
- Helyi komponens jelenlétének és ellenőrzött státuszának külön kezelése.
- Offline és online-only képességek elkülönítése.
- Hálózat hiányakor online-only képesség nem jelenhet meg használhatónak.
- Duplikált képességazonosítók elutasítása.
- Nem állítja, hogy egy képesség minősége vagy pontossága megfelelő; kizárólag elérhetőséget mér.

## Tesztek
- `ModelIntegrityVerifierTest.kt`: pontos digest/méret, hash eltérés, túlméretes stream, hibás manifest, I/O hiba.
- `OfflineCapabilityMatrixTest.kt`: offline-ready, hiányzó/nem ellenőrzött modell, online-only és duplikált azonosító.

## Integrációs határ
A Phase 2 core kapu. A `ModelManager`, `ModelDownloadService` és `ModelsActivity` adapterei, atomikus fájlcsere, rollback és Room/persistent státusz külön integrációs fázis. E csomag nem módosít manifestet, jogosultságot, endpointot, aláírást vagy meglévő modellletöltési viselkedést.

## Validációs korlát
A jelen futtatási környezetben nincs elérhető Gradle/Kotlin compiler, ezért a hivatalos JUnit és teljes Android build nem állítható PASS-nak. A források bekerültek, a statikus ellenőrzések külön jelentésben szerepelnek.
