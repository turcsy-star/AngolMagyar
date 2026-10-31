# Forrásaudit – feltöltött AngolMagyar 4.2.6 ZIP

## Ellenőrzött tények

- A ZIP CRC olvasása és kicsomagolása sikeres.
- 565 fájl szerepel az archívumban.
- A belső gyökérmappa neve 4.2.5, miközben az Android Gradle konfiguráció 4.2.6 / 40406 verziót állít.
- A README 4.2.3 / 40403 forrásverziót említ, tehát több, egymásnak ellentmondó verzióállítás volt.
- A projekt három Gradle modult tartalmaz: app, core és wear.
- A csomagban vannak core és app unit tesztforrások, valamint statikus release/security ellenőrző script.
- A `VALIDATION_REPORT_4.2.6_HU.md` maga is rögzíti, hogy Gradle/Kotlin build és eszközteszt korábban sem volt igazolva.
- A Smart Translation Memory adapter Android Keystore AES-GCM tárolást használ. A korábbi `readEntries()` catch ág eltávolította a titkosított preferences blobot, majd üres listát adott vissza. Ez a későbbi írásoknak adatvesztési kockázatot jelentett.
- A `tools/verify_release_security.py` ellenőrzi a release/debug signing szétválasztást, SecretStore AES-GCM használatot, endpoint policy-t, backup tiltást, exported komponens allowlistet, FileProvider határt és CI bekötést.

## Javítás

A `SmartTranslationMemoryStore.readEntries()` most `null`-t ad vissza visszafejtési/parszolási hibánál, a titkosított blobot megőrzi, és minden módosító művelet megszakad. A `entries()` olvasó API üres listát ad a megjelenítéshez, de nem törli a blobot; a `lookup()` nem ad találatot hiba esetén.

## Környezeti korlát

A futtatókörnyezetben JDK 21 elérhető, de `gradle` parancs nincs telepítve. A `./gradlew --offline :core:test` megkísérlésekor a wrapper a Gradle 8.9 disztribúció letöltését próbálta, majd `UnknownHostException: services.gradle.org` hibával leállt; a helyi cache hiányzik. Az Android SDK/NDK elérhetősége nem igazolt. Emiatt a teljes Gradle build és Kotlin tesztfuttatás nem volt lehetséges. `python3 tools/verify_release_security.py` sikeres; `python3 -m compileall -q tools` sikeres; a 38 XML fájl parse-olása sikeres; az Android verzió/SDK invariánsok ellenőrzése sikeres; a módosított Kotlin memóriaadapter és a core memóriaosztály ideiglenes API-stubokkal végzett szintaxis/típusellenőrzése sikeres (ez nem teljes Android build). A statikus ellenőrzés nem helyettesíti a Kotlin buildet vagy a készüléktesztet.

## Következtetés

A forráscsomag egy részleges, de érdemi biztonsági és verziókonzisztencia-javítást kapott. A teljes 6.0 funkciólista még terv, nem implementált állapot.
