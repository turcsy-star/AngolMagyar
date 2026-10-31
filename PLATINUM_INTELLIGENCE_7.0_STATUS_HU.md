# Platinum Intelligence 7.0 – átadási státusz

## Jelen csomag

- Kiinduló forrás: AngolMagyar 4.2.6 / Platinum Intelligence 6.0 Engineering.
- 7.0: fejlesztési célverzió, nem Android `versionName`.
- Android versionName/versionCode: változatlan `4.2.6 / 40406`.
- applicationId: `hu.angolmagyar.fordito`.
- Modulok: `:app`, `:core`, `:wear`.

## Ebben az átadási csomagban ténylegesen elvégzett munka

- **W1/W2 alapréteg – megvalósítva, UI/persistence integrációra vár:** `core/.../PlatinumWorkflow.kt` determinisztikus, offline-first munkafolyamat-állapotgép; függőség- és ciklusellenőrzéssel, capability gate-tel, hálózati tiltással, hibával, korlátozott retry-val és cancel állapottal.
- **Fordítási munkaterület alapréteg – megvalósítva, UI/persistence integrációra vár:** `core/.../TranslationWorkspace.kt` változat- és előzménykezelés, append-only visszaállítás, korlátozott diff, meglévő fordítási audit bekötése.
- Új unit tesztforrások: `PlatinumWorkflowTest.kt` (6 eset) és `TranslationWorkspaceTest.kt` (5 eset).
- A teljes `core/src/main/java` Kotlin forrásállomány JDK 21 / kotlinc fordítással sikeresen lefordult; csak a meglévő, nem blokkoló figyelmeztetések maradtak.
- A két új tesztosztály 11 tesztmetódusát külön futtató ellenőrző harness: 11/11 PASS (JUnit API-kompatibilitási stubbal; ez nem Gradle/JUnit futtatás).
- Külön smoke harness: workflow és translation workspace PASS.

- A 4.2.6 teljes forrásprojekt változatlan alapként megőrizve.
- 10 fejlesztési területre kiterjedő 7.0 master specifikáció hozzáadva.
- Fájlszintű módosítási mátrix hozzáadva.
- Funkcionális, offline, adatvédelmi és kiadási tesztterv hozzáadva.
- Kiadási kapuk és a „kész” státusz bizonyítási feltételei rögzítve.
- A meglévő statikus biztonsági ellenőrző futtatva: PASS.

## Nem állítható még

- A tíz 7.0 terület teljes funkcionális implementációja.
- A teljes Gradle build és unit tesztek sikere.
- APK/AAB előállítása vagy aláírása.
- Fizikai Android-eszközös, mikrofon-, TTS- és repülőgép-mód teszt.
- 7.0 Stable vagy Production Ready státusz.

## Ellenőrzés

| Ellenőrzés | Eredmény |
|---|---|
| ZIP kibontás / forrásstruktúra | PASS |
| `python tools/verify_release_security.py` | PASS |
| `python -m compileall -q tools` | PASS |
| Gradle wrapper elérhető a környezetben | Nem igazolt (Gradle 8.9 disztribúció/cache hiányzik) |
| `:core:test`, `:app:testDebugUnitTest` | Nem futott (Gradle disztribúció hiányzik) |
| Core Kotlin források `kotlinc` fordítása | PASS |
| Workflow + TranslationWorkspace smoke harness | PASS |
| `assembleDebug`, `assembleRelease`, Wear build | Nem futott |
| Fizikai eszközteszt | Nem futott |

**Besorolás: 7.0 ENGINEERING + IMPLEMENTED CORE FOUNDATION / 4.2.6 ANDROID BASELINE.** A két új core komponens forrása és smoke ellenőrzése kész; Android UI, tartós tároló adapter, teljes Gradle teszt és APK továbbra sincs igazolva.
