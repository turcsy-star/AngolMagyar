# Platinum Intelligence 8.0 – Phase 5: Offline Terminology & Memory

## Cél
A Phase 5 két determinisztikus, teljesen offline használható alapmotort ad a projekthez. Nem kapcsol hálózati szolgáltatáshoz, nem írja át automatikusan a fordításokat, és nem módosít meglévő jogosultságokat vagy adatbázis-sémát.

## Új komponensek

### `core/.../OfflineGlossaryAudit.kt`
- Kétnyelvű terminológiai bejegyzések ellenőrzése a forrás- és célszövegben.
- Hiányzó kötelező célszavak jelzése.
- Ütköző szótárdefiníciók jelzése.
- Unicode betű-/számhatárok, kis-/nagybetű-független céltalálat.
- Bemeneti hosszkorlátok a hibás vagy túlméretes szótáradatok ellen.
- Csak figyelmeztet; nem módosítja a szöveget.

### `core/.../OfflineMemoryRanker.kt`
- Helyben tárolt fordítási memória elemek determinisztikus rangsorolása.
- Jaccard + lekérdezési lefedettség alapú lexikai hasonlóság.
- Opcionális domain-egyezési bónusz.
- Stabil rendezés azonos pontszám esetén.
- Nem állít szemantikai/neurális pontosságot.

## Integrációs elv
A komponensek tiszta Kotlin API-k. A meglévő Room/DAO és UI rétegekhez csak külön, célzott integrációs lépésben kapcsolandók; ebben a fázisban nincs adatbázis-migráció és nincs meglévő működés felülírása.

## Tesztelés
Új JUnit tesztek: 5 terminológiai és 3 rangsorolási eset. A production Kotlin források külön fordítása sikeres, és 5 futó smoke assertion sikeres. A teljes Android Gradle build, JUnit futtatás és eszközteszt külön környezetben futtatandó; ezek sikeressége ebből a forráscsomagból önmagában nem állítható.
