# Diamond Blue UI v11 – Diamond Experience

A v11 frissítés a v10 Diamond Blue felületére épül. A fordítási, beszédfelismerési, fájlkezelési és feldolgozási logika változatlan maradt.

## Új felületi elem
- A kezdőképernyő fejlécét követő, új Diamond Experience panel.
- Saját Compose Canvas-alapú, vektoros zafírmedál: többfokozatú fényudvar, körbefutó gyűrű és metszett gyémánt.
- Finom, lassú orbit animáció és pulzáló fény, külső képfájl vagy hálózati erőforrás nélkül.
- A panel valós alkalmazásállapotot jelez: készenléti vagy aktív feldolgozási állapot.
- A meglévő csökkentett animáció és energiatakarékos mód esetén az animációk statikus megjelenésre váltanak.
- A kezdőképernyő többi elemének belépési sorrendje az új panelhez igazítva.

## Módosított fájlok
- `app/src/main/java/hu/angolmagyar/fordito/ui/MainActivity.kt`
- `DIAMOND_BLUE_V11_HU.md`
- `BUILD_STATUS_DIAMOND_V11_HU.md`
- `CHANGELOG.md`

## Ellenőrzés
- A forrásmódosítások és a ZIP archívum szerkezeti integritása ellenőrizve.
- A teljes Android/Kotlin build nem igazolt: a Gradle Wrapper 8.9 disztribúcióját a környezet nem tudta letölteni (`UnknownHostException: services.gradle.org`).
- Emiatt az APK és a készülékes animációs teljesítmény nem tekinthető teszteltnek.
