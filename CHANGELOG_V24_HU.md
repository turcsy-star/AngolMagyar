# Platinum Ultra Extreme v24 – CI fordítási hibajavítás

## Javítások
- `PlatinumDial.kt`: hozzáadva a Compose `getValue` delegált állapotolvasáshoz, így az `animateFloatAsState` eredménye helyesen használható `by` szintaxissal.
- `PlatinumProgressBar.kt`: a `PlatinumProgressBar` függvény `@Composable` annotációt kapott.
- Eltávolítva a nem támogatott `Role.ProgressBar` hivatkozás; az akadálymentesítési állapotot a `ProgressBarRangeInfo` és a `contentDescription` továbbra is közli.
- Verzió: 2.54.0, versionCode 5400.

## Ellenőrzési megjegyzés
A javítás a megadott GitHub Actions fordítási napló konkrét Kotlin-hibáira készült. Teljes Gradle build és unit teszt csak a GitHub Actions futtatásával igazolható; a csomag önmagában nem tekinthető sikeresen lefordított APK-nak.
