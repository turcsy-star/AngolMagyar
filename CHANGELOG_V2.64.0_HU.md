# v2.64.0 – Smart Context + Translation Quality (hátralévő hullám)

## Cél
A „Következő fejlesztési hullám” képernyőn még „Fejlesztendő” státuszú két elem teljes implementációja a core modulban, valamint a részben kész elemek dokumentációs lezárása.

## Új / befejezett elemek

### Smart Context Translation (korábban: Fejlesztendő)
- Új `SmartContextTranslation` objektum a core modulban.
- Kontextus-, kétértelműség- és hangnem-alapú alternatívák (offline, szabályalapú).
- Gyakori kétértelmű angol szavak (bank, light, right, set, run, charge, match, check, order…) felismerése.
- Hangnem-érzékelés: semleges / formális / informális / műszaki / barátságos.
- Soha nem írja felül automatikusan a fordítást – csak javaslatokat ad.
- `hasAmbiguity()` gyors ellenőrzés a minőségmodul számára.

### Translation Quality (korábban: Fejlesztendő)
- Új `TranslationQuality` objektum, amely összefogja:
  - a meglévő `TranslationCheck` (szám, név, tagadás),
  - a `TranslationQualityAudit` hossz-arány logikáját,
  - új heurisztikákat: angol maradványszavak, ékezet-hiány, kérdőjel-struktúra, ambiguitás-jelzés.
- Súlyozott figyelmeztetések (CRITICAL / WARNING / REVIEW / INFO).
- Heurisztikus tájékoztató pontszám (0–100), nem AI-pontszám.
- `summaryLine()` a felületi megjelenítéshez.

### Infinity Global UI & Platinum Motion Engine
- Státusz: részben kész (a korábbi v2.60–v2.63 UI-tokenek és animációs előkészületek megmaradtak).
- A teljes paletta-migráció és egységes Motion Engine a v3.00 Diamond integráció keretében zárul.

## Verzió
- Ajánlott Android versionCode: 6400
- Ajánlott versionName: 2.64.0

## Korlátozások
- A Smart Context lista nem teljes lexikai adatbázis – csak a leggyakoribb kétértelműségeket fedi.
- A Translation Quality nem helyettesít emberi lektorálást és nem ad valószínűségi pontosságot.
- A main app UI (PlatinumIntelligenceActivity / NextWave képernyő) a core-t használja; a felületi bekötés a teljes app-modulban történik.
- Gradle/Kotlin fordítás ebben a környezetben a korábbi DNS-korlát miatt nem futott le.

## Ellenőrzési javaslat
1. `SmartContextTranslation.suggest("Please check the bank balance and the light on the right.")`
2. `TranslationQuality.analyze(source, target)` – üres fordítás, számeltérés, angol maradvány.
3. A Next Wave képernyőn a két elem státuszát „Kész” / „Részben kész” értékre állítani a UI oldalon.
