# AngolMagyar – Platinum Ultra Extreme v16: Infinity Luxury

## Ebben a csomagban ténylegesen módosult
- Az alkalmazás verziója 2.45.0-ról 2.46.0-ra, a versionCode 4500-ról 4600-ra frissült.
- A Platinum Ultra Console felületén V16 jelzés szerepel.
- A megjelenített hangszint és feladat-előrehaladás 0–100% tartományra korlátozott; a hangszint 0.0–1.0 közötti tartományban kerül a vizualizációba. Ez megakadályozza, hogy átmeneti vagy hibás bemeneti értékek túlcsorduló százalékot vagy széteső progress bart okozzanak.
- A 40 sávos spektrum továbbra is a mért hangszintből vagy a feldolgozás előrehaladásából rajzolódik, nem állít elő független, hamis mérési adatokat.
- A meglévő alkalmazásfunkciók és a Platinum/Graphite/Champagne felületi rendszer megmaradtak.

## Ellenőrzés
- A ZIP bemeneti csomag megnyitható volt, 390 bejegyzéssel.
- Forrásmódosítás és verziójelzés frissítve.
- Android build nem igazolt: a `bash gradlew :app:assembleDebug --no-daemon` a Gradle 8.9 letöltésekor `UnknownHostException: services.gradle.org` hibával leállt, mert a buildkörnyezet nem éri el a letöltési kiszolgálót. Ezért ehhez a csomaghoz nem állítom, hogy ellenőrzött APK készült.

## Fordítás
Linux/macOS: `bash gradlew :app:assembleDebug`
Windows: `gradlew.bat :app:assembleDebug`
