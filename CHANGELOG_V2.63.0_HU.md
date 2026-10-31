# v2.63.0 – Learning Analytics Diamond

## Új funkciók
- Új, Platinum Intelligence Suite-ból megnyitható Learning Analytics Diamond áttekintő.
- Napi aktivitási cél, állítható 1–500 közötti célszámmal.
- 7 napos aktivitási trend és 30 napos összesített mutatók.
- Gyakorlási pontosság, aktív napok, keresések, gyakorlások és ismétlések számlálása.
- Helyi, adatvédelmi szempontból minimalizált tárolás: csak dátumonkénti számlálók, nincs begépelt szó vagy mondat eltárolva.
- A szókincstár és az Intelligens ismétlő gyakorlási eredményei automatikusan frissítik az analitikát.

## Verzió
- Android versionCode: 6300
- Android versionName: 2.63.0

## Korlátozások és ellenőrzés
- A számlálók az új analitika bekapcsolásától gyűlnek; korábbi aktivitás nem rekonstruálható.
- A fordítási keresések minden belépési pontjáról történő teljes eseménybekötés további iterációban bővíthető.
- A Gradle/Kotlin fordítás ebben a környezetben nem futott le: a Gradle 8.9 disztribúció letöltéséhez szükséges DNS-feloldás sikertelen volt.
- ZIP-integritás ellenőrizhető; Android-eszközös UI- és adatmegmaradási teszt kiadás előtt szükséges.
