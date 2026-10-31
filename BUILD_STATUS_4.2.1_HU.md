# Build státusz – 4.2.1

## Elvégzett forrásmódosítások

- Release aláírás: debug kulcs fallback eltávolítva; saját kulcs nélkül unsigned release.
- API-kulcs tárolás: Android Keystore AES-GCM fail-closed viselkedés; plaintext fallback eltávolítva.
- Online beállítófelület: biztonságos mentési hiba visszajelzése.
- Egyedi végpontok: HTTPS vagy lokális/privát HTTP szabály, `EndpointPolicy` unit teszt hozzáadva.
- UI: Forest Graphite paletta; Compose prémium téma világos/sötét rendszerbeállítást követ.
- Verzió: `versionName=4.2.1`, `versionCode=40401`.
- Dokumentáció: teljes v5.0 cél-specifikáció, státuszmátrix, tesztterv, biztonsági és migrációs útmutató.

## Környezeti ellenőrzés

- Java: OpenJDK 21 elérhető.
- Kotlin CLI: 1.9.0 elérhető.
- Gradle wrapper: a projekt Gradle 8.9-et igényel.
- Android SDK/NDK: ebben a végrehajtási környezetben nem volt konfigurálva.
- A `./gradlew --version` megkísérelte a Gradle 8.9 letöltését, de a környezet DNS/hálózati korlátozása miatt `UnknownHostException: services.gradle.org` hibával leállt.

## Nem igazolt

- Gradle Kotlin DSL fordítás.
- `:core:test` és `:app:testDebugUnitTest` futtatása ebben a munkamenetben.
- Android APK és Wear APK build.
- Valós eszközteszt, kamera/dokumentum integráció és kiadás-aláírás.

A v4.2.0 dokumentált korábbi tesztállapota: core 75/75 és Android-független app tesztek 18/18 sikeresek voltak a korábbi ellenőrzési környezetben. Ezek nem tekinthetők a 4.2.1 módosítások újrafuttatott tesztjének.

## Következő kötelező ellenőrzés

```bash
./gradlew :core:test :app:testDebugUnitTest
./gradlew :app:assembleDebug :wear:assembleDebug
./gradlew :app:assembleRelease
```

A release build csak saját release kulccsal tekinthető terjesztésre késznek. A teljes v5.0 kiadási kaput az `IMPLEMENTATION_AND_TEST_PLAN_5.0_HU.md` tartalmazza.
