# Build státusz – 4.2.6 (Platinum Intelligence 6.0 Engineering)

## Alkalmazásverzió
- versionName: **4.2.6**
- versionCode: **40406**
- applicationId: hu.angolmagyar.fordito
- minSdk 29 / targetSdk 35 / compileSdk 35
- NDK: 27.2.12479018

## Modulok
- `:app` – fő alkalmazás
- `:core` – platformfüggetlen logika (memória, minőség, kontextus, WER stb.)
- `:wear` – Wear OS

## Elvégzett ellenőrzések ebben a csomagban
| Ellenőrzés | Eredmény |
|---|---|
| `python tools/verify_release_security.py` | **PASS** |
| Forrásstruktúra / ZIP integritás | OK |
| Gradle wrapper jelenléte | OK |
| Android SDK / NDK / teljes Gradle build | Ebben a környezetben **nem futtatható** (nincs offline Gradle cache / SDK) |
| Unit tesztek futtatása | Forrásban megvannak; futtatás nem igazolt |
| APK előállítás | Nincs |
| Fizikai eszköz / offline / mikrofon / TTS | Nincs |

## Kiadási osztályozás
**FORRÁSSZINTŰ JAVÍTOTT MÉRNÖKI CSOMAG – NEM RELEASE**

A csomag buildelhetőnek tekinthető, amint egy teljes Android fejlesztői környezetben a CI workflow sikeresen lefut. Production release csak a teljes tesztterv és aláírt APK után.

## Következő kötelező lépések (helyi környezetben)
1. `./gradlew :core:test`
2. `./gradlew :app:testDebugUnitTest`
3. `./gradlew :app:assembleDebug :wear:assembleDebug`
4. (opcionális) aláírt release keystore-ral: `./gradlew :app:assembleRelease`
5. Valós eszköz: telepítés, frissítés 4.2.x-ről, offline mód, memória megőrzés Keystore-hiba szimulációval.

