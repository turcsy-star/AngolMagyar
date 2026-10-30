# AngolMagyar V18 – Platinum Infinity Luxury Plus

## Változások

- A folyamatjelző akadálymentes szemantikát ad: `ProgressBar` szerep, ismert haladásnál 0–100-as tartomány, ismeretlen haladásnál indeterminált állapot.
- A százalékot biztonságosan korlátozó `safeProgressPercent` tiszta Kotlin-függvény, ezért a unit teszt közvetlenül tesztelheti.
- Az ismeretlen haladás továbbra sem jelenik meg hamis százalékként.
- Verzió: 2.48.0, versionCode 4800.

## Ellenőrzési állapot

A ZIP-et a módosított forrásokból állítjuk elő. A teljes Android-build és a JUnit-tesztek futtatása nem volt lehetséges: a Gradle Wrapper a `services.gradle.org` címet nem tudta feloldani (`UnknownHostException`). Emiatt a build sikeressége nem állítható.
