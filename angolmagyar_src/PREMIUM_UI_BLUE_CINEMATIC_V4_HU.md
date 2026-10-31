# Blue Cinematic UI – 4. fejlesztési kör

## Irány
Cinematic Platinum Blue: mély navy, elektromos kék, cyan fények, hideg fehér felületek.

## Közös felületi rendszer
- A Compose PremiumCard egységes, cyan-kék átmenetes peremet, finom árnyékot és 360 ms-os belépést kapott.
- A régi View-alapú Ui.card elemek kékes, többrétegű felületet és visszafogott belépési animációt kaptak; a csökkentett animáció beállítást tiszteletben tartják.
- A régi ScrollView képernyők egységes háttérkitöltést, viewport-viselkedést és visszafogott görgetési túlcsordulást kaptak.
- A hero fejléc minden régi képernyőn fénycsíkos, navy átmenetes megjelenést használ.
- A világos színpaletta hideg, kékes fehér tónusokra frissült; az éjszakai paletta továbbra is mély navy, az OLED mód pedig megmarad.
- A meglévő vezérlők, callbackek, navigáció, fordítási és letöltési logika változatlan.

## Érintett alkalmazásrétegek
- Compose kezdőlap és közös prémium kártyarendszer.
- Régi View-alapú képernyők közös kártya- és fejlécépítői (így a modellek, diagnosztika, beállítások, átiratok, felirat- és feladatképernyők).
- Közös erőforrás-paletta.

## Build és teszt
A forrásmódosítás elkészült. A teljes Android Gradle build és eszközteszt csak Android SDK-val és elérhető Gradle függőségekkel igazolható; sikeres APK buildet ez a csomag nem állít.
