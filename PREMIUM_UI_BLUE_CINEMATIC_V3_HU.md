# Blue Cinematic UI – 3. fejlesztési kör

## Érintett felületek
- Élő fordítás: új, kék cinematic hero fejléc.
- Modellkezelő: AI Model Center fejléc és motor-szekció címke.
- Mentett átiratok: Library fejléc, a meglévő keresés/szűrés megtartásával.
- Diagnosztika: System Check fejléc, a meglévő ellenőrző logika és műveletek megtartásával.
- Beállítások: Control Center fejléc, a meglévő beállítások és callbackek megtartásával.

## Közös vizuális rendszer
- A régi View-alapú képernyők is megkapják a kék/cyan cinematic kártyaperemet.
- Kártyák rövid, visszafogott belépési animációt kapnak.
- Az új hero komponens mély navy átmenetes hátteret, cyan tipográfiai kiemelést és belépési animációt használ.
- A funkcionális elemek (keresés, letöltés, modelválasztás, diagnosztikai javítások, navigáció) változatlan műveleti logikával működnek.

## Ellenőrzés
A forráskód módosítva és csomagolva. A Gradle buildet külön Android/Gradle környezetben kell futtatni; sikeres buildet e csomag nem állít.
