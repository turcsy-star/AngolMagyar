# Platinum Infinity – v2.58.0 AI & VOICE MASTER

## Integrációs hullám

### Tartós saját szótár (Room)
- Új `dictionary_entries` tábla angol kulccsal, magyar jelentéssel és példamondattal.
- A korábbi `translation_memory` tábla adatait megőrző, 1 → 2 verziós Room migráció.
- CSV import az Android Storage Access Framework fájlválasztójával.
- CSV export felhasználó által kiválasztott célfájlba, UTF-8 BOM kódolással.
- A saját szótár bejegyzései megjelennek az offline keresési találatok között.
- Azonos normalizált angol kulcs importja frissíti a korábbi bejegyzést.

### Beszédfelismeréses gyakorlás
- Android `RecognizerIntent` indítása angol (en-US) nyelvi beállítással.
- A felismert szöveg visszakerül a szerkeszthető mezőbe.
- A meglévő WER-alapú szövegegyezés-értékelő azonnal használható.
- A beszédfelismerés szolgáltatását a készülék biztosítja; internet/offline támogatás és pontosság készülékfüggő.
- A pontszám szövegátirat-egyezést mér, nem fonetikai vagy akcentusminősítést.

## Verzió
- versionName: 2.58.0
- versionCode: 5800

## Ellenőrzés és korlátok
- Forrásmódosítások elkészültek.
- Gradle build megkísérelve: `./gradlew :app:compileDebugKotlin --offline`.
- A wrapper a Gradle 8.9 disztribúciót próbálta letölteni; a környezet DNS-hibája (`UnknownHostException: services.gradle.org`) miatt a fordítás nem indult el.
- APK-build, unit teszt és fizikai Android-eszközteszt nincs igazolva.
- Valódi generatív AI-fordító nincs aktiválva; ehhez külön szolgáltatóintegráció, kulcskezelés és adatvédelmi hozzájárulás szükséges.

Ez módosított Android-forrásprojekt, nem igazoltan telepíthető APK.
