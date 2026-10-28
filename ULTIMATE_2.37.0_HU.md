# AngolMagyar Silver & Gold Ultimate 2.37.0

## CI és közös Compose design rendszer

- GitHub Actions minden push és pull request esetén futtatja a `:core:test`, `:app:testDebugUnitTest` és `:app:assembleDebug` feladatokat.
- Az APK és a tesztjelentések egyetlen letölthető artifactba kerülnek.
- Roborazzi-alapú Compose képernyőkép-smoke-teszt rendereli és rögzíti a prémium komponens képét. A vizuális baseline-összehasonlítást a sikeres első CI-futás után a referenciafájl felülvizsgálatával és verziókezelésbe vételével kell véglegesíteni.
- Új `ui.design` csomag: `PremiumSpacing`, `PremiumMotion`, `LocalSpacing`, `LocalMotion`, `PremiumCard`, `StatusPill`, `PrimaryAction`.
- Verzió: 2.37.0 (`versionCode 3700`).

## Ellenőrzési státusz

A forrás és a workflow frissült. A teljes buildet csak sikeres GitHub Actions-futás és az ott létrejött APK-artifact igazolja; ezt a jelenlegi munkakörnyezetből nem lehet előre bizonyítottnak állítani.
