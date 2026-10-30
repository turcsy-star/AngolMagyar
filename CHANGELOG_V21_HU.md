# v21 / 2.51.0 — Infinity Theme Live Refresh

- A fő Compose-felület a Beállításokból visszatérve automatikusan újraalkalmazza a módosított Luxury palettát, Platinum kapcsolót és OLED fekete módot.
- A téma-összehasonlítási állapotot az újraindítás előtt frissíti, elkerülve az ismételt `recreate()` hívásokat.
- A Beállítások témaleírása pontosítva: a paletta a Compose Material 3 színsémát vezérli; a klasszikus View-képernyők külön témázása nem változik.
- Verzióemelés: 2.51.0 / versionCode 5100.

## Ellenőrzés

A forráscsomag módosítva és ZIP-ként ellenőrizve. Android Gradle buildet csak olyan környezetben lehet véglegesíteni, ahol a Gradle wrapper és a szükséges Android függőségek elérhetők.
