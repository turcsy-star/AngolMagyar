# AngolMagyar – Silver & Gold Ultimate 2.41.0

## Fő fejlesztés: Premium Translation Experience

- Új, újrafelhasználható `PremiumTranslationCard` Compose-komponens az eredeti és a lefordított szöveg egységes megjelenítéséhez.
- A másolási és felolvasási műveleteket a hívó képernyő adja át callbackeken keresztül; a komponens nem tart meg Activity-referenciát és nem kötődik Android-szolgáltatáshoz.
- Üres szövegnél informatív helyőrző látható, és a megfelelő műveleti gombok letiltódnak.
- Képernyőolvasó számára leíró szöveg és konzisztens vizuális hierarchia.
- Új Robolectric Compose tesztek a szövegek megjelenítésére és a műveleti gombok engedélyezésére.

## Verzió

- `versionName`: `2.41.0`
- `versionCode`: `4100`

## Ellenőrzés

A forráscsomag és a statikus fájlellenőrzések elvégezhetők helyben. A Gradle buildet és a teszteket csak olyan környezetben lehet sikeresnek nyilvánítani, ahol a szükséges Gradle-disztribúciók és függőségek elérhetők; ezek sikeres futása ebben a környezetben nincs igazolva.
