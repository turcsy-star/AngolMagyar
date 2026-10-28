# AngolMagyar – Silver & Gold Ultimate 2.38.0

## Premium Design System 2.0

- Központosított vizuális tokenek (`PremiumTokens.kt`): Platinum, Champagne Gold, Graphite és Pearl paletta, valamint közös kártya-/gomb-/pill-geometria.
- A `PremiumCard` most egységes, visszafogott kontúrt, adaptív Material 3 felületszínt és finom mélységet használ.
- Új `PremiumSectionTitle` komponens a következetes, olvasható szekciócímekhez.
- A kezdőképernyő fordítási vezérlőpultja az új prémium kártyát és szekciócímet használja.
- Verzió: `versionCode = 3800`, `versionName = 2.38.0`.

## Ellenőrzés és korlátok

A forráscsomag statikus ellenőrzése és ZIP-integritásvizsgálata elvégezhető ebben a környezetben. A teljes Gradle buildet és teszteket csak olyan környezetben lehet hitelesen igazolni, amely hozzáfér a Gradle-disztribúcióhoz és a függőségekhez. Az előző környezetben a `services.gradle.org` DNS-feloldása sikertelen volt, ezért itt nem állítjuk, hogy a build sikeres.

Javasolt CI-ellenőrzés:

```bash
./gradlew :core:test --stacktrace
./gradlew :app:testDebugUnitTest --stacktrace
./gradlew :app:assembleDebug --stacktrace
```
