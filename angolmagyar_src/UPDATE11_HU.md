# AngolMagyar – Gyémánt Edition Update 11

## Ebben a csomagban ténylegesen módosult

- Új `GlossaryGovernance` platformfüggetlen Kotlin-modul: terminus-bejegyzések validálása, globális/projekt-hatókör, jóváhagyási állapot, ütközéskeresés, jóváhagyott terminus feloldása, pontos kifejezésalapú hatás-előnézet, TSV import/export és visszavonható archiválást támogató állapotváltás.
- Új `GlossaryGovernanceTest` tesztek: projekt-szótár prioritása, ütközésvizsgálat, hatás-előnézet, TSV körút és hiányzó projektazonosító.
- Új Platina kiemelőszín-téma a meglévő témarendszerben.
- Új részletes magyar fejlesztési és integrációs terv a prémium felülethez, minőségbiztosításhoz, újrafeldolgozáshoz, feliratstúdióhoz és adatvédelemhez.

## Fontos korlátok

A szótárgovernance modul most külön, platformfüggetlen API. Még nincs bekötve az Android felületbe, a meglévő `Prefs`-tárolóba vagy a fordítási pipeline-ba; ezért ebben a kiadásban nem állítható, hogy a teljes automatikus újrafordítási munkafolyamat kész. A modul szándékosan nem ír át szöveget: előnézetet ad, és a tényleges módosításhoz explicit alkalmazás szükséges.

A teljes Android Gradle-build, az alkalmazás- és Wear APK, a JUnit-csomag és a készülékes UI-teszt ebben a környezetben nincs igazolva. A `GlossaryGovernance.kt` önálló Kotlin-fordítása sikeres.
