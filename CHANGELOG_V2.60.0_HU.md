# Változásnapló – v2.60.0

## Infinity Backup Center
- Verziózott, JSON-alapú biztonsági mentési formátum.
- Saját szótár, fordítási memória és ismétlési ütemezés exportja.
- Android Storage Access Framework: a felhasználó választja ki a mentés helyét és a visszaállítandó fájlt.
- Importálás előtti formátum-, verzió-, méret- és rekordvalidáció.
- Tranzakciós visszaállítás: a szótár, fordítási memória és ismétlési ütemezés együtt cserélődik.
- Új Backup Center képernyő az Intelligence Suite központban.
- Kerekítési és formátumtesztek a backup codec-hez.

## Verzió
- versionCode: 6000
- versionName: 2.60.0

## Korlátok
- A mentés jelenleg a három Room-adatkört tartalmazza; médiafájlokat, letöltött modelleket, API-kulcsokat és alkalmazásbeállításokat nem.
- A visszaállítás felülírja a három mentett adatkört; előtte javasolt aktuális mentést exportálni.
- Android build és eszközteszt csak a megfelelő Gradle/SDK környezetben igazolható.
