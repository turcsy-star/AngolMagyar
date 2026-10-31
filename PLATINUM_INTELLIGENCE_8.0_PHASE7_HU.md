# Platinum Intelligence 8.0 – Phase 7: Ultimate Product Experience

## Cél
A Phase 5–6 offline minőségellenőrző és tanulási komponenseinek első, felhasználó által elérhető képernyő-integrációja.

## Új funkció
Az **Offline Intelligence Center** képernyő elérhető a Platinum Intelligence Suite központból. A képernyő:
- megmutatja a helyi szótár, fordítási memória és esedékes ismétlések darabszámát;
- lehetővé teszi angol forrásszöveg és magyar fordítás offline ellenőrzését;
- megjeleníti a szám-/tagadás- és szótári terminológiai figyelmeztetéseket;
- lexikai rangsorolással hasonló, korábban tárolt fordításokat keres;
- megjeleníti a következő esedékes ismétléseket.

## Biztonság és adatkezelés
- A képernyő csak helyi Room-adatokat olvas; az audit és a memória-rangsorolás nem küld adatot hálózatra.
- A képernyő nem írja át a fordításokat és nem módosítja a szótárat.
- Az ismétlési állapot ezen a képernyőn csak olvasható; a pontozás továbbra is a meglévő ismétlő képernyőben történik.
- A Room adatbázis verziója változatlan (3); nincs új migráció és nincs adatvesztéssel járó művelet.
- A szótári audit legfeljebb 5000 friss bejegyzést, a memória-keresés legfeljebb 2000 friss fordítást vizsgál.
- Az új Activity `exported=false` beállítású, belső alkalmazásképernyő.

## Módosított fájlok
- `app/src/main/java/hu/angolmagyar/fordito/ui/OfflineIntelligenceCenterActivity.kt` – új Compose képernyő.
- `app/src/main/java/hu/angolmagyar/fordito/ui/PlatinumIntelligenceActivity.kt` – navigációs belépési pont.
- `app/src/main/AndroidManifest.xml` – nem exportált Activity regisztráció.
- `app/src/main/java/hu/angolmagyar/fordito/core/OfflineIntelligenceRepository.kt` – helyi darabszámok és korlátozott szótárbeolvasás.
- `app/src/main/java/hu/angolmagyar/fordito/core/db/DictionaryDao.kt` – limitált, friss szótári lekérdezés.

## Korlátok
A memória-rangsorolás lexikai, nem embedding- vagy LLM-alapú szemantikus keresés. A figyelmeztető audit nem helyettesít emberi nyelvi ellenőrzést. A képernyő nem biztosít még szótárbejegyzés-szerkesztést vagy export/import műveletet; ezek külön, tranzakció- és validációközpontú fejlesztési hullámba tartoznak.
