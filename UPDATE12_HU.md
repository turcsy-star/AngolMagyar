# Angol–Magyar Gyémánt Edition – Update 12 Premium Integration

## Ebben a frissítésben ténylegesen elkészült

- A `GlossaryStore` Androidos tárolóadapter összeköti a `GlossaryGovernance` modult a meglévő `SharedPreferences`-beállításokkal.
- A régi személyes szótár első megnyitáskor átvehető a felügyelt szótárba; a már használt `source=target` formátum továbbra is kompatibilis.
- A Beállítások → Haladó → Javítólisták és szótár részhez bekerült a **Gyémánt szótárközpont**.
- A szótárközpontban a globális, jóváhagyott terminusok szerkeszthetők, menthetők és aktiválhatók a meglévő fordítási pipeline számára.
- Mentés előtt ellenőrzi az ismétlődő forráskifejezéseket, és nem engedi a mentést duplikált kulcsokkal vagy alapvetően hibás bejegyzésekkel.
- A governance modul támogatja a globális/projekt hatókört, a függő/jóváhagyott/elvetett/archivált állapotot, az ütközéskeresést, a pontos kifejezésalapú hatás-előnézetet és a TSV import/exportot.
- A szótár változásai nem írják át automatikusan a korábbi átiratokat. A felhasználó által jóváhagyott globális szótár a jövőbeli fordításokhoz kerül szinkronizálásra a meglévő `customVocab` mezővel.

## Ellenőrzés

- A `GlossaryGovernance.kt` önálló Kotlin-fordítása sikeres.
- A kézi smoke teszt sikeres: jóváhagyott projektterminus prioritása, ütközésészlelés, hatás-előnézet és TSV körút.
- A teljes Gradle/JUnit és Android APK buildet nem sikerült lefuttatni, mert a környezet nem tudta letölteni a Gradle 8.9 disztribúciót (`services.gradle.org` hálózati elérés hiánya). Ezért az Android UI teljes fordíthatósága és eszközös működése még nincs igazolva.

## Következő fejlesztési feladatok

1. Projektazonosító és projekt-szintű szótár kiválasztása a felületen.
2. Korábbi átiratok hatás-előnézete fájlból/projektből, diff nézettel és kézi jóváhagyással.
3. A terminológia alkalmazása mondatszintű újrafordításkor, a kézzel jóváhagyott fordítások védelmével.
4. Szótár-verzióelőzmény, visszaállítás és import előtti soronkénti hibaösszegzés.
5. Teljes Gradle build, JUnit, Android készülékes UI-teszt és APK ellenőrzés olyan környezetben, ahol a buildfüggőségek elérhetők.
