# v4.2.2 – automatizált kiadási ellenőrzések

## Változások
- Verzió: 4.2.2 (`versionCode=40402`).
- Bővített EndpointPolicy határérték- és félrevezető URL-tesztek.
- Új `tools/verify_release_security.py` statikus biztonsági kapu.
- GitHub Actions: statikus ellenőrzés, core/app tesztek, Android debug APK, Wear OS debug APK és unsigned release build.
- CI artifactok közé bekerül az app release és Wear OS debug APK is.

## Korlátok
A statikus ellenőrzés nem helyettesíti a Gradle/Kotlin fordítást, a dinamikus teszteket, a dependency/SBOM vizsgálatot vagy a fizikai eszköztesztet. A release APK kulcs nélkül szándékosan aláíratlan.
