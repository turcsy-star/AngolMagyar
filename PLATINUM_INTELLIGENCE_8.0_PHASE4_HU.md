# Platinum Intelligence 8.0 – Phase 4: Ultimate Intelligence Core

## Cél
A Phase 3 biztonságos modelltelepítési alapjaira építve két platformfüggetlen, offline használható minőségi képesség hozzáadása. A modulok konzervatív jelzéseket adnak; nem írják át automatikusan a fordítást, nem küldenek adatot hálózatra, és nem helyettesítik a felhasználói döntést.

## Új fájlok

### `core/src/main/java/hu/angolmagyar/fordito/TranslationInvariantAudit.kt`
- Számok és százalékértékek forrás/cél összevetése.
- Gyakori angol és magyar tagadó elemek jelenlétének összevetése.
- Figyelmeztetést ad, nem javít és nem állít teljes szemantikai megfelelést.
- Üres szöveget figyelmen kívül hagy.
- Teljesen lokális, tiszta Kotlin; nincs Android- vagy hálózati függőség.

### `core/src/main/java/hu/angolmagyar/fordito/AdaptiveReviewScheduler.kt`
- SM-2 ihletésű, determinisztikus ismétlési ütemezés 0–5 minősítéssel.
- Sikertelen felidézésnél újraindítja az ismétlési sorozatot, egy nap múlva újrapróbálást javasol.
- Sikeres felidézésnél 1, 6, majd növekvő időközöket számol.
- Korlátozza az ease faktort és a maximális időközt; védi az időbélyeg túlcsordulását.
- Nem tárol adatot: a perzisztencia és az esetleges titkosítás az alkalmazás adattároló adapterének felelőssége.

## Tesztek
- `TranslationInvariantAuditTest.kt`: eltérő szám, hiányzó tagadás, egyező jelentéshordozó tokenek, üres bemenet.
- `AdaptiveReviewSchedulerTest.kt`: sikertelen felidézés, 1/6 napos ütemezés, határértékek, esedékesség.

## Integrációs megjegyzések
A két új motor a `:core` modulban érhető el. A jelen csomag nem köti automatikusan UI-hoz vagy adatbázishoz, mert a meglévő fordítási és tanulási folyamatok konkrét hívási pontjának ellenőrzése nélkül ez regressziót okozhatna. Következő integrációs lépés: fordítási eredmény megjelenítésekor az audit figyelmeztetéseinek felületi megjelenítése; tanulókártya válaszának értékelésekor a scheduler eredményének meglévő helyi tárolóba mentése.

## Biztonság és offline működés
- Nincs új engedély, hálózati kliens, külső SDK vagy új adatgyűjtés.
- Nincs automatikus fordításmódosítás.
- A scheduler csak bemeneti értékekből számol, nem végez fájl- vagy adatbázis-műveletet.
- A korábbi Phase 2/3 fájlok és biztonsági kapuk változatlanul megmaradtak.

## Validáció
- A teljes `:core:test` Gradle futtatását megkíséreltük. A wrapper a Gradle 8.9 disztribúciót igényelte, de a környezet DNS/hálózati korlátozása miatt a `services.gradle.org` nem volt elérhető; ezért a JUnit tesztek nem futottak.
- Android build, APK és eszközteszt nem futott.
- Production-ready minősítés nem állítható. A csomag forráskód-bővítés és dokumentált validációs állapot.
