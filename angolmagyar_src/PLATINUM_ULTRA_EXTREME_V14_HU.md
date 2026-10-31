# AngolMagyar – Platinum Ultra Extreme UI v14

## A javított csomagról
Ez a kiadás a teljes v13 forráscsomagból készült újracsomagolással, nem a korábbi hibás/hiányos v14 archívumból. A forrásfában a kezelőfelület verziójelzése V14-re frissült.

## Megőrzött funkciók
- A meglévő Android-projekt, Gradle modulok és wrapper.
- Platinum/Graphite felületi komponensek és Platinum Spectrum vizualizáció.
- A tényleges alkalmazásállapothoz kötött folyamat- és hangszint-visszajelzés.
- A meglévő fordítási, fájlkezelési és feldolgozási kód.

## Ellenőrzés
- ZIP-archívum: integritásellenőrzés elvégzendő a végleges csomagon.
- Fordítás: csak akkor tekinthető sikeresnek, ha a Gradle build a célkörnyezetben lefut.
- Az APK nem része ennek a csomagnak, mert itt nem igazolt a sikeres Android-build.

## Fordítás
Linux/macOS: `./gradlew assembleDebug`
Windows: `gradlew.bat assembleDebug`

Ha a Gradle disztribúció vagy a függőségek nem érhetők el, először hálózati hozzáférést és a Gradle wrapper letöltését kell biztosítani.
