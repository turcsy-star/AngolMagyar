# Diamond Blue UI v10 – Ultimate Diamond Experience

A v10 frissítés a v9 Diamond Blue vizuális rendszerére épül. A fordítási, beszédfelismerési, fájlkezelési és feldolgozási logikához nem nyúl.

## Vizuális fejlesztések
- Új, vektorosan rajzolt, több metszetű zafír Diamond embléma a kezdőképernyő fejlécében.
- Finom, folyamatos fénycsík az emblémán, a csökkentett animációs beállítás tiszteletben tartásával.
- Egységes Diamond Edge peremfény és lassú fényreflexió a közös Compose PremiumCard komponensen.
- A kártyák meglévő belépési animációja és tartalma változatlan marad; a csillanás a tartalom mögött jelenik meg.
- Az animációk kikapcsolnak az alkalmazás csökkentett mozgás beállításánál és energiatakarékos módban.

## Módosított fájlok
- `app/src/main/java/hu/angolmagyar/fordito/ui/design/DiamondBrandMark.kt` (új)
- `app/src/main/java/hu/angolmagyar/fordito/ui/design/PremiumDesignSystem.kt`
- `app/src/main/java/hu/angolmagyar/fordito/ui/MainActivity.kt`

## Ellenőrzés
A ZIP szerkezeti integritása ellenőrizhető. Az Android/Kotlin fordítás a környezetben nem futott le: a Gradle Wrapper a `services.gradle.org` hostot DNS-hiba miatt nem érte el. Ezért az APK buildje nincs igazolva.
- Kódszerkezeti ütközés javítása: a Compose időtartam-token `PremiumMotionDurations` nevet kapott, így nem ütközik a feldolgozó animációs segéd `PremiumMotion` objektummal.
- A `Prefs.reduceMotion` duplikált deklarációját egyetlen közös beállításra tisztítottam.
