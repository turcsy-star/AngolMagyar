# Build állapot – v2.62.0

- [x] WorkManager napi periódikus feladat.
- [x] WorkManager heti periódikus feladat.
- [x] Ütemezés be/ki kapcsolása a Backup Center felületén.
- [x] Helyi AES-256-GCM titkosítás Android Keystore kulccsal.
- [x] Automatikus mentés rotáció (14 legfrissebb fájl).
- [x] Konzisztens adatbázis-pillanatkép Room-tranzakcióval.
- [x] Atomi fájlírás ideiglenes fájllal és fsync művelettel.
- [x] Titkosítási fejléc hitelesítése AES-GCM AAD használatával.
- [x] Titkosított puffer törlése a feldolgozás után.
- [x] Utolsó sikeres futás / hiba állapotának tárolása.
- [x] Verzióemelés 6200 / 2.62.0.
- [ ] Gradle Kotlin fordítás és KSP ellenőrzés.
- [ ] Unit tesztek és Android-eszköz teszt.

A Gradle ellenőrzés megkísérelve: a wrapper a Gradle 8.9 disztribúciót próbálta letölteni, de a környezet DNS-hibája miatt (`UnknownHostException: services.gradle.org`) nem volt elérhető. A build ezért nem minősíthető sikeresnek. A csomag ZIP-integritásellenőrzése sikeres; Kotlin-fordítás, tesztfuttatás és fizikai Android-eszközön végzett ellenőrzés nem történt.
