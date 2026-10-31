# Platinum Intelligence 8.0 – Phase 6: Ultimate Integration

## Cél
A Phase 6 közös offline belépési ponttal kapcsolja össze a korábbi fázisok minőségellenőrzési, terminológiai, fordítási memória- és ismétlési komponenseit. A Room-adapter a már meglévő adatbázistáblákat használja.

## Új fájlok

- `core/src/main/java/hu/angolmagyar/fordito/OfflineIntelligencePipeline.kt`
  - Egyesített auditjelentés (jelentésinvariánsok + terminológia).
  - Offline memória-rangsorolás és ismétlési ütemezés egységes, mellékhatásmentes API-n.
- `app/src/main/java/hu/angolmagyar/fordito/core/OfflineIntelligenceRepository.kt`
  - Room adapter a szótárhoz, fordítási memóriához és ismétlési táblához.
  - IO dispatcher használata.
  - Korlátozott rangsorolási és ismétlési lekérdezési limitek.
  - Meglévő Room entitások és DAO-k használata.
- `core/src/test/java/hu/angolmagyar/fordito/OfflineIntelligencePipelineTest.kt`
  - Kombinált audit, üres figyelmeztetés, stabil memória-rangsorolás és ismétlési ütemezés tesztek.

## Adatbázis és kompatibilitás
- Room adatbázis verzió változatlanul 3.
- Nincs új tábla, oszlop vagy migráció.
- A `translation_memory`, `dictionary_entries` és `review_schedule` meglévő táblák kerülnek felhasználásra.
- Nincs jogosultság-, hálózati vagy fordítási kimenetet automatikusan módosító változtatás.
- A fordítási memória rangsorolása legfeljebb 2000 legutóbbi rekordot vizsgál; a visszaadott elemszám legfeljebb 20.
- Az esedékes ismétlések lekérdezése legfeljebb 200 rekordot ad vissza.

## API áttekintés
`OfflineIntelligenceRepository`:
- `auditTranslation(source, target)` – szótár és invariánsok együttes ellenőrzése.
- `rankSimilarTranslations(query, limit)` – hasonló helyi fordítások lexikai rangsorolása.
- `recordReview(englishKey, quality, nowEpochMs)` – ismétlés rögzítése és következő időpont kiszámítása.
- `dueReviews(nowEpochMs, limit)` – esedékes elemek lekérése.
- `dueReviewCount(nowEpochMs)` – esedékes elemek száma.

## Korlátok
- A memória-rangsorolás lexikai, nem neurális vagy szemantikus modell.
- Az audit figyelmeztetés, nem fordítási minőségi garancia.
- A Phase 6 repository API-t ad; automatikus UI bekötést és fordítási folyamatba történő kötelező beavatkozást nem végez.
- Az új funkciók teljes alkalmazásbeli használatához a képernyőknek / ViewModel-eknek explicit módon kell meghívniuk a repository-t.

## Validálás
Lásd `PHASE6_VALIDATION_HU.md`. A teljes Android Gradle build külső Gradle disztribúciót igényel; a build megkísérlése ebben a környezetben hálózati DNS-hiba miatt nem jutott el a fordításig.
