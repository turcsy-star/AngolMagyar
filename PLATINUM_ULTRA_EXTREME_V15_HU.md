# AngolMagyar – Platinum Ultra Extreme UI v15: Infinity Edition

## Felületi fejlesztések
- Frissített „Platinum Infinity” fejléc és állapotközpont-cím.
- A spektrum 32 helyett 40 csatornát használ, keskenyebb közökkel és magasabb megjelenítési területtel.
- A spektrum felirata egyértelműen jelzi a 40 csatornás megjelenítést.
- Frissített V15 verziójelzés és lábléc.
- A spektrum továbbra is a meglévő hangszintből vagy a feldolgozás tényleges százalékából számol; nem generál hamis mérési adatot.

## Megőrzött elemek
- A teljes Android-projekt és az összes meglévő forrásfájl.
- Fordítási, fájlkezelési, feldolgozási és modellkezelési funkciók.
- A korábbi Platinum/Graphite komponensek és a hozzáférhetőségi leírás.

## Ellenőrzési állapot
- ZIP CRC/integritás: ellenőrizendő a létrehozott csomagon.
- Forrásellenőrzés: a módosított Kotlin-fájl és a V15 jelölések ellenőrizendők.
- Android build/APK: ebben a környezetben nincs igazolt Gradle build; a csomag nem tartalmaz ellenőrzötten lefordított APK-t.

## Fordítás
Linux/macOS: `./gradlew :app:assembleDebug`
Windows: `gradlew.bat :app:assembleDebug`
