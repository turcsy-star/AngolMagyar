# AngolMagyar 4.2.6 – Compose IR javítás és nyitott integrációk

## Azonosított buildhiba

A GitHub Actions `:app:compileDebugKotlin` lépése Kotlin 2.0.21 Compose IR backend hibával állt le:

`No mapping for symbol: VAR name: reduced type: kotlin.Boolean`

A hiba oka az volt, hogy több Compose effect-, transition- és Canvas-lambda közvetlenül zárta körül a `reduced` Boolean paramétert. A javítás az érintett komponensekben `rememberUpdatedState(reduced)` állapotot használ, a View→Compose adapterek pedig a mozgásbeállítást a kompozíció kontextusából olvassák.

## Érintett komponensek

- Diamond élő színház és orb
- Diamond folyamat-színház
- Platinum hangszintmérő
- Platinum és általános folyamat-idővonalak
- Platinum folyamatábra és haladássáv

A regressziós ellenőrzés külön GitHub Actions lépésként fut.

## Ellenőrzési állapot

- Statikus release/security guard: PASS.
- Compose compiler regression guard: PASS.
- Core unit tesztek: a korábbi CI-futásokban PASS.
- A javítás utáni teljes App/Compose teszt, debug APK, Wear APK és unsigned release APK státuszát kizárólag a PR-hoz tartozó GitHub Actions futás igazolhatja.
- APK vagy eszközteszt sikerét nem állítjuk addig, amíg az adott ellenőrzés nem zárult sikeresen.

## Korábban részlegesnek jelölt funkciók újraellenőrzése

- A szótár CSV import/export funkciója már létezik a `NextWaveToolsActivity` képernyőn a `DictionaryCsv` parserrel és exporttal; nem hozunk létre párhuzamos implementációt.
- A szótár, fordítási memória és ismétlési adatok biztonsági mentése/visszaállítása a meglévő `BackupCenterActivity` és `BackupArchive` útvonalon elérhető.
- A dokumentum-/kamerarögzítés jelenlegi `UNAVAILABLE` állapota szándékos adapterhatár, nem kész funkció. Aktiválásához tényleges CameraX/SAF munkafolyamat, jogosultságkezelés és adatmegőrzési tesztek szükségesek.
- A szinkronizáció jelenleg `NoOpSyncProvider` mögött marad. Valós szolgáltató, hitelesítés, titkosítás, konfliktuskezelés, felhasználói opt-in és szerveroldali adatvédelmi szabályok nélkül nem kapcsoljuk be, mert ez érzékeny átiratok hálózati továbbítását jelentené.

## Kiadási döntés

A forrásjavítás nem jelent automatikus stabil kiadási minősítést. A PR CI-jének teljes zöld eredménye, majd valós Android-eszközön mikrofon-, offline-, fordítási és frissítési ellenőrzés szükséges.