# AngolMagyar – Silver & Gold Ultimate 2.44.0

## Újdonság: időzített szókincsismétlés

A saját szókincstár mostantól egyszerű, helyi ismétlési ütemezést is támogat:

- **Gyakorlás** nézetben a rendszer csak az esedékes kifejezéseket mutatja.
- Először a forráskifejezés látszik; a fordítást külön gombbal lehet felfedni.
- A **Tudtam** válasz fokozatosan hosszabb időközt állít be: 1, 3, 7, 14 nap, majd legfeljebb 365 nap.
- A **Nem tudtam** válasz másnapra ütemezi újra a kifejezést.
- A szókincstár megőrzi a korábbi mentéseket; az új ismétlési mezők hiányzó értékei alapértéket kapnak.
- A mentett szavak és az ismétlési adatok továbbra is kizárólag a készüléken tárolódnak.

## Korábbi funkciók

A 2.43.0-ban bevezetett fordítási stílusok, kézi szókincstárba mentés, másolás és felolvasás megmaradtak.

## Ellenőrzési státusz

A forráscsomag és a módosított fájlok statikusan ellenőrizhetők. A teljes Gradle buildet és az Android APK-t ebben a környezetben nem lehet igazolni, amíg a Gradle disztribúció letöltése hálózati/DNS-hiba miatt meghiúsul. Ezért ez a csomag nem állítja, hogy a teljes alkalmazás sikeresen lefordult vagy eszközön tesztelve lett.
