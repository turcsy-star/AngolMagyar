# Platinum Ultra Extreme v19 — Infinity Sovereign Edition

## Ebben a csomagban

A v18 Infinity Luxury Plus forrására épülő, kompatibilitást szem előtt tartó fejlesztési csomag. A meglévő fordítási, beszédfelismerési, felirat-, dokumentum- és diagnosztikai funkciók megőrzése volt az elsődleges szempont.

### Újonnan beépített
- **Globális Infinity Luxury palettaválasztó** a Beállításokban: Platinum Silver, Obsidian Black, Royal Gold, Arctic Diamond és Midnight Sapphire.
- **Közös LuxuryPalettes tokenrendszer** a Compose prémium kártyák felületeihez, fényeihez és kereteihez.
- **Tartós témaválasztás** SharedPreferences-alapon, alkalmazás-újraindítás után is.
- **Verzióemelés** 2.49.0 / versionCode 4900.
- **Megőrzött csökkentett animációs beállítás**: a palettaváltás nem kapcsolja ki vagy írja felül ezt a beállítást.

### A v18-ból megtartott képességek
- Material 3 / Compose kezdőképernyő és prémium kártyarendszer.
- Animált felület és csökkentett mozgás opció.
- Élő hangfordítás, hangfelvétel és hanghullám-vizualizáció.
- Dokumentum-/feliratfordítás, SRT-munkafolyamatok és folyamatjelzők.
- Fordítási előzmények, szótár-/kifejezéskönyv és kedvencek.
- Diagnosztika, adatvédelmi képernyő, offline/online szolgáltatások beállításai.

## A 12 fejlesztési javaslat státusza

1. **Infinity Glass Luxury Design** — továbbépítve a meglévő PremiumCard felületén; a kiválasztott paletta meghatározza a kártyák színét és fénykeretét.
2. **Cinematic Micro-Animations** — a meglévő mozgásrendszer és csökkentett animációs mód megtartva.
3. **Smart Language Intelligence** — meglévő fordítási, nyelvazonosítási, szótár- és memóriafunkciók megtartva; új külső AI-szolgáltatás nincs automatikusan bekötve.
4. **Platinum Command Center** — meglévő Compose kezdőlap és gyorsműveleti kártyák megtartva.
5. **Adaptive Luxury Themes** — öt paletta választása és mentése hozzáadva. A globális paletta jelenleg a Compose prémium kártyákra vonatkozik; a teljes régi View-alapú képernyők egységesítése külön migrációs feladat.
6. **Translation Cards 2.0** — a meglévő prémium fordítási kártya, másolási és kapcsolódó műveletek megtartva.
7. **Voice Studio Ultra** — a meglévő élő fordítási/hangfelületek és hullámnézet megtartva.
8. **Document Translation Pro** — a meglévő dokumentum-/feliratfeldolgozási folyamatok megtartva.
9. **Ultra Premium Navigation** — a meglévő kezdőlap és képernyőnavigáció megtartva.
10. **Trust & Transparency Center** — meglévő adatvédelmi és diagnosztikai képernyők megtartva.
11. **Performance Engine** — meglévő teljesítmény- és csökkentett animációs megoldások megtartva; külön eszközprofilos benchmark futtatása javasolt.
12. **Signature Infinity Experience** — egységes paletta-tokenekkel továbbfejlesztve; a teljes alkalmazás minden régi képernyőjének egységesítése fokozatosan végezhető el.

## Fontos műszaki megjegyzés

A témaválasztás jelenleg a Compose `PremiumCard` elemek színvilágát állítja. A régi Android View-alapú képernyők külön témákat/komponenseket használhatnak. Nem állítjuk, hogy az összes funkció Androidon le lett fordítva vagy eszközön tesztelve: a build ellenőrzéséhez Android SDK-val rendelkező környezet szükséges.

## Build és ellenőrzés

A projekt gyökeréből:

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
./gradlew :app:testDebugUnitTest
```

Ha a build hibát jelez, először az Android SDK / Gradle / JDK verzióit, majd a konkrét fordítási hibát kell ellenőrizni. Telepítés előtt érdemes mentést készíteni a korábbi APK-ról és alkalmazásadatokról.
