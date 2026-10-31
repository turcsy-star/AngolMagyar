# Platinum Intelligence 8.0 – Phase 3: Safe Model Installation

## Cél
A Phase 2 modell-integritási és offline-képesség kapujának következő gyakorlati védelmi rétege: a ZIP-alapú helyi modelltelepítés ellenállóbbá tétele, a meglévő telepítés megőrzése sikertelen frissítés esetén.

## Implementált változások

### `core/src/main/java/hu/angolmagyar/fordito/ArchiveSafetyPolicy.kt`
- Közös, platformfüggetlen ZIP-archívum szabályzat.
- Alapértelmezett korlát: legfeljebb 50 000 bejegyzés.
- Legfeljebb 2 GiB összes kibontott adat és 512 MiB egy bejegyzésre.
- Legfeljebb 250:1 tömörítési arány.
- Abszolút, meghajtóbetűs, visszafelé perjeles és útvonalbejárást tartalmazó nevek elutasítása.
- ZIP könyvtárbejegyzéseknél a szabályos záró perjel megengedett.

### `app/src/main/java/hu/angolmagyar/fordito/ModelManager.kt`
- A ZIP teljes metaadatlistája kibontás előtt ellenőrzésre kerül.
- A meglévő modell nem törlődik a kibontás kezdetén.
- A modell először ideiglenes staging könyvtárba kerül.
- Az aktiválás ugyanazon fájlrendszeren végzett átnevezéssel történik.
- Sikertelen aktiváláskor a korábbi modell visszaállítása megkísérelt és hiba esetén külön jelzett.
- A korábbi modell csak az új modell alapvető szerkezeti ellenőrzése után kerül eltávolításra.

### Tesztek
`ArchiveSafetyPolicyTest.kt`: normál archívum, útvonalbejárás, abszolút és meghajtóbetűs útvonal, méretkorlát, bejegyzésszám és gyanús tömörítési arány.

## Kompatibilitás és korlátok
- Nem módosítja az alkalmazás URL-jeit, engedélyeit, manifestjét, verziókódját vagy meglévő letöltési protokollját.
- Nem állítja, hogy a letöltött fájl kriptográfiailag hitelesített; ehhez kiadói, hiteles SHA-256 manifest szükséges. A Phase 2 verifier ettől elkülönülő kapu marad.
- A könyvtár-átnevezés ugyanazon fájlrendszeren atomi jellegű, de az Android fájlrendszer/gyártói viselkedés miatt eszközteszt szükséges.

## Validáció
- `ArchiveSafetyPolicy.kt` önálló Kotlin fordítása: PASS.
- Gradle/JUnit: NEM FUTOTT; a wrapper a Gradle 8.9 letöltését igényelte, de a futtatási környezet hálózata nem érte el a services.gradle.org címet.
- Teljes Android build és eszközteszt: NEM FUTOTT.
- Production Ready / Stable minősítés ezért nem állítható.
