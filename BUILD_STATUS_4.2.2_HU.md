# Build státusz – 4.2.2

## Elkészült
- Verzió: `4.2.2` (`versionCode=40402`).
- EndpointPolicy RFC1918 határérték- és URL-ambiguitás tesztek bővítve.
- Statikus kiadási biztonsági ellenőrzés: `tools/verify_release_security.py`.
- GitHub Actions: statikus ellenőrzés, core/app unit tesztek, app debug, Wear OS debug és unsigned app release build.
- CI artifactok: app debug/release, Wear OS debug APK és tesztjelentések.

## Ebben a munkamenetben ténylegesen ellenőrizve
- ZIP CRC/integritás: sikeres.
- Statikus kiadási biztonsági ellenőrzés: sikeres.
- XML fájlok: 38/38 sikeresen parse-olva.
- Verzióadatok: `versionName=4.2.2`, `versionCode=40402` ellenőrizve.

## Nem igazolt
- Gradle/Kotlin fordítás és unit tesztek.
- APK-k létrehozása.
- Fizikai Android és Wear OS eszközteszt.

A helyi környezetben nincs Android SDK, és nincs gyorsítótárazott Gradle 8.9. A wrapper letöltése `UnknownHostException: services.gradle.org` hibával meghiúsult. Ezért a forráscsomag nem tekinthető kiadásra kész APK-nak.

## GitHub Actions parancsai
```bash
python tools/verify_release_security.py
./gradlew :core:test :app:testDebugUnitTest
./gradlew :app:assembleDebug :wear:assembleDebug
./gradlew :app:assembleRelease
```

A release kulcs nélküli release APK szándékosan aláíratlan. Terjesztéshez saját release keystore és sikeres készülékes ellenőrzés szükséges.
