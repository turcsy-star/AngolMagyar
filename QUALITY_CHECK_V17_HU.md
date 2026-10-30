# V17 minőségellenőrzési jegyzőkönyv

## Elvégzett ellenőrzések
- A projekt ZIP tartalma kibontásra került.
- A haladássáv kódjában a megjelenített százalék, a progress animáció és a képernyőolvasó-leírás ugyanazt a 0–100 közé szorított értéket használja.
- Az ismeretlen haladás (`pct < 0`) továbbra sem jelenít meg kitalált százalékot.
- Egységtesztek kerültek a határértékek és a normál értékek ellenőrzésére.

## Még ellenőrizendő
- `:app:testDebugUnitTest`
- `:app:assembleDebug`
- Valódi Android-eszközön: mikrofonengedély, háttérbe lépés, hangszintmérő, fordítás, feliratimport/export.

APK csak sikeres fordítás és telepítési próba után tekinthető ellenőrzöttnek.
