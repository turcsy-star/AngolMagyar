# PLATINUM INFINITY SOVEREIGN AI – v2.59.0

## Memory Coach – adaptív szókártya-ismétlés

A v2.58.0-ban létrejött saját Room-szótár mostantól ismétlési munkafolyamatot is kapott. Az Intelligens ismétlő a saját szótár elemeiből hoz létre helyi kártyákat, megmutatja a magyar jelentést, majd négy értékelési lehetőséggel ütemezi a következő ismétlést.

### Ütemezési szabályok
- Újra (0–2): ismétlésszám nullázása, következő ismétlés 1 nap múlva.
- Nehéz (3): sikeres ismétlés, konzervatív intervallum.
- Jó (4) és Könnyű (5): SM-2 ihletésű intervallumnövelés.
- Az ease factor 1,3 alá nem csökken.

### Adattárolás
- Room schema 3.
- Migráció 2 → 3 kizárólag új `review_schedule` táblát és indexet ad hozzá.
- A fordítási memória és a saját szótár rekordjai változatlanul megmaradnak.

### Korlátok
- Az ütemező nem generatív AI, hanem átlátható, determinisztikus algoritmus.
- A szavak az alkalmazás saját szótárából kerülnek a gyakorlóba.
- Az alkalmazás nem küld tanulási adatokat külső szolgáltatásnak.
- APK-build és fizikai eszközteszt csak sikeres Android buildkörnyezetben igazolható.
