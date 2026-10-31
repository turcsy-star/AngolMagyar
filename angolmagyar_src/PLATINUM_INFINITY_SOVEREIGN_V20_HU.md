# Platinum Ultra Extreme v20 — Infinity Sovereign Global Theme

## Újdonságok a v19-hez képest

- **Valódi globális Compose témázás:** az öt kiválasztott Infinity paletta mostantól a Material 3 `primary`, `secondary`, `background`, `surface`, `outline` és szövegszíneit is vezérli.
- **Platinum kapcsoló tiszteletben tartása:** ha a Platinum Luxury téma ki van kapcsolva, a korábbi Blue Cinematic / rendszerfüggő téma marad érvényben.
- **OLED mód:** a fekete háttér beállítás továbbra is feketévé teszi a fő felületi hátteret.
- **Csökkentett mozgás javítása:** a prémium kártyák belépő animációja is kikapcsol, ha a csökkentett animáció aktív.
- **Verzió:** 2.50.0 / versionCode 5000.

## Ellenőrzési állapot

A forrásmódosítások elkészültek. A teljes Android buildet és eszköztesztet külön, Android SDK-val rendelkező környezetben kell lefuttatni.

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
./gradlew :app:testDebugUnitTest
```

## Megjegyzés

A klasszikus Android View-alapú képernyők saját témakezelést is használhatnak. Ez a kiadás a Compose Material 3 témát teszi valóban palettafüggővé; a teljes régi képernyők migrációja további, képernyőnkénti feladat.
