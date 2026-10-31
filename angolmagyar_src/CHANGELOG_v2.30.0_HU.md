# AngolMagyar v2.30.0 – Gyémánt Edition (első UI-frissítési kör)

## Elkészült a forráskódban
- Új „Gyémánt” kiemelőszín hideg türkiz főszínnel és visszafogott lila fényárnyalattal.
- Új telepítéseknél a Gyémánt téma az alapértelmezett; a már elmentett felhasználói témaválasztást nem írja felül.
- A kezdőképernyő fejlécén megjelenik a „GYÉMÁNT” jelölés és az alkalmazás értékajánlata.
- A meglévő színválasztó automatikusan megjeleníti az új témát, mert az Accent.ALL listából épül fel.

## Tudatosan változatlanul hagyva
- Átiratfelismerési és fordítási logika.
- Minőségpontozás, célzott újrafelismerés, fájlformátumok és export.
- A korábban kiválasztott téma a meglévő telepítéseken.

## Ellenőrzés
Ez a forráskód-módosítás önmagában nem igazolja a fordíthatóságot vagy az APK működését. Futtatandó:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

Ezután kis kijelzős telefonon, világos/sötét módban és OLED módban is ellenőrizni kell a fejlécet, a témaválasztót és a régi (View-alapú) képernyőket.

## Második fejlesztési kör – minőségellenőrzés
- A pontszám felirata „Ellenőrzési index” lett, és a képernyő egyértelműsíti, hogy ez a jelzések súlyozása, nem mért felismerési pontosság.
- A természetes rövid válaszok (pl. „Yes”, „Okay”, „Thanks”) nem kapnak automatikusan töredékjelzést.
- Az üres sor kizárólag üres tartalomként kerül jelzésre, nem kap mellé félrevezető töredékjelölést.
- A nulla hosszúságú idősáv is hibás időzítésként jelenik meg.
- Új egységtesztek kerültek a fenti esetekre.

## Gyémánt Edition – harmadik fejlesztési kör
- A fordítási memória szöveges importja most a kereséssel azonos kulcsnormalizálást használja; a külső TSV-fájlokban szereplő nagybetűk és írásjelek nem akadályozzák a találatot.
- Hibás, üres vagy célfordítás nélküli import-sorok kihagyása; az ismétlődő normalizált kulcsoknál a későbbi érték érvényesül.
- Négy célzott `TranslationMemoryTest` készült az import, export/import kör, hibás sorok és ütköző kulcsok ellenőrzésére.
- Android verziómetaadat frissítve: `versionCode = 3000`, `versionName = 2.30.0`.
- A teljes Android-build és a JUnit-futtatás továbbra is külön környezetben ellenőrizendő.


## Gyémánt Edition – negyedik fejlesztési kör
- Unicode NFC-normalizálás a fordítási memória kulcsainál, hogy az ékezetes szövegek előre összeállított és kombináló karakteres változata is egyezzen.
- Az in-memory és Room-alapú fordítási memória közös kulcsnormalizálást használ, csökkentve az eltérő találati viselkedés kockázatát.
- Új egységteszt a kanonikusan ekvivalens Unicode-kifejezésekre.

## Update 7 – export előtti minőségellenőrzés
- Az SRT és WebVTT export előtt lefut a `SrtQualityCheck` ellenőrzés.
- A felhasználó összesítést kap a hibákról és figyelmeztetésekről, legfeljebb nyolc részletes jelzéssel.
- Az ellenőrzés nem módosítja automatikusan a feliratot; a felhasználó dönthet az export folytatásáról.
- A minőségellenőrzés jelenleg az SRT/VTT exportokra van bekötve; a DOCX/PDF/MD/TXT útvonalak külön integrációt igényelnek.
