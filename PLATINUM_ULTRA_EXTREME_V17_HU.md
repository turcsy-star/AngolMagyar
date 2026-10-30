# AngolMagyar – Platinum Ultra Extreme v17: Infinity Luxury Plus

## V17 módosítások
- Verzió: 2.47.0, versionCode 4700.
- A prémium letöltési/haladási sáv egységesen a 0–100 tartományba korlátozott százalékot mutatja a feliraton, a kisegítő lehetőségek leírásában és a grafikus kitöltésben is.
- A negatív érték továbbra is az ismeretlen haladást jelöli; ilyenkor animált folyamatjelző jelenik meg százalék kitalálása nélkül.
- Új egységtesztek ellenőrzik az alsó és felső határt, valamint az érvényes százalékok megőrzését.
- A V16 Platinum/Graphite/Champagne megjelenés és a meglévő funkciók megmaradnak.

## Ellenőrzési állapot
- A ZIP csomag ellenőrzése és a forrásmódosítások megtörténtek.
- A Gradle/Android buildet ebben a környezetben külön kell futtatni; sikeres APK-fordítás nincs igazolva.

## Fordítás
Linux/macOS: `bash gradlew :app:testDebugUnitTest :app:assembleDebug`
Windows: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug`
