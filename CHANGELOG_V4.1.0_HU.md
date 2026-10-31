# v4.1.0 – Memory & Speech Integration Wave

## Új funkciók
- A Platinum Memory Universe már a közös Room-szótárba ment: angol kifejezés, magyar jelentés és opcionális példamondat.
- Az új szótári elem automatikusan bekerül a meglévő ismétlési ütemezőbe, ha még nincs hozzá ütemezés.
- A képernyő megjeleníti a szótár elemszámát és az esedékes ismétlések számát.
- Az Infinity Speech Intelligence referencia-mondat és a rendszer által felismert átirat között WER-alapú szövegegyezési mutatót számol, valamint megmutatja a cserék, hiányok és többletek számát.

## Pontossági és adatvédelmi pontosítások
- A beszédértékelés átirat-alapú szöveg-összehasonlítás; nem állít fonéma-, akcentus- vagy intonációelemzést.
- A rendszer beszédfelismerő szolgáltatása hálózatot használhat; a szolgáltató adatkezelése érvényes rá.
- A szótár és az ismétlési ütemezés az alkalmazás helyi Room-adatbázisában marad.

## Verzió
- versionName: 4.1.0
- versionCode: 40200

## Ellenőrzés
- ZIP-integritás és forrásellenőrzés elvégzendő.
- Android Gradle build csak a Gradle 8.9 és az Android SDK elérhetőségével futtatható; build-sikert nem állítunk ellenőrzés nélkül.
