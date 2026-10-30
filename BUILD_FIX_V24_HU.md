# CI build fix – v24

A javított fájlok:
- `app/src/main/java/hu/angolmagyar/fordito/ui/processing/PlatinumDial.kt`
- `app/src/main/java/hu/angolmagyar/fordito/ui/processing/PlatinumProgressBar.kt`

A naplóban látható hibaokok: hiányzó `getValue` import a `by animateFloatAsState(...)` kifejezéshez, hiányzó `@Composable` annotáció, illetve a Compose-verzióban nem elérhető `Role.ProgressBar` szimbólum.
