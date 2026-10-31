# Silver & Gold Ultimate Edition – 2.32.0 fejlesztési csomag

## Ebben a csomagban
- Silver & Gold prémium kiemelőszín és világos/sötét paletta.
- Új `SubtitlePostProcessor` a végső átirat-utófeldolgozáshoz.
- A felhasználó által megadott angol/magyar javítólisták és szótár biztonságos alkalmazása.
- Opcionális töltelékszó-szűrés és írásjel-normalizálás.
- Ellenőrzési jelzések bizonytalan (`[unclear]`, `[inaudible]`, `TODO`, `FIXME`) részekhez.
- Egységtesztek az üres bemenet, javítás, szótár, jelölések és írásjelek kezelésére.

## Fontos viselkedési garanciák
- A javítások determinisztikusak; nincs külső szolgáltatás-hívás.
- A töltelékszavak törlése alapértelmezésben ki van kapcsolva.
- A bizonytalan részek nem kerülnek automatikusan kitalált szöveggel helyettesítésre.
- A `reviewRecommended` heurisztikus ellenőrzési javaslat, nem garantált pontossági pontszám.

## Ellenőrzés
A forráscsomag egységteszteket tartalmaz, de ebben a környezetben az Android/Gradle build nem futott le. A teljes alkalmazás telepíthetőségét és a készülékes működést még ellenőrizni kell.
