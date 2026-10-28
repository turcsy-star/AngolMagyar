# AngolMagyar Silver & Gold Ultimate Edition 2.33.0

## Újdonságok
- Új **Platina Gyémánt** kiemelőtéma: platina tónusok kristályos cián és indigó fényekkel.
- `TranslationQualityAudit`: külön, nem romboló EN→HU fordítási ellenőrző a hiányzó fordítás, számeltérés, tulajdonnév/tagadás gyanúja és szélsőséges szöveghossz jelzésére.
- Egységtesztek az ellenőrző fő eseteire.
- Verzió: 2.33.0 (versionCode 3300).

## Pontossági megjegyzés
Az ellenőrző figyelmeztetéseket ad, nem bizonyítja a fordítás helyességét és nem generál „pontossági százalékot”. A természetes nyelvi fordítás minőségéhez továbbra is szükség lehet a forrás és a célszöveg emberi ellenőrzésére. A meglévő fordítómotor viselkedését ez a modul nem módosítja automatikusan.

## Ellenőrzés
A projekt forrása és az új egységtesztek bekerültek a csomagba. Android/Gradle buildet ebben a környezetben nem igazoltunk; a kiadás előtt futtasd az `./gradlew :core:test` és az `./gradlew assembleDebug` parancsokat Android SDK-val és elérhető Gradle-függőségekkel.
