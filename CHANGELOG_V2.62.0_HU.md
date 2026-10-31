# v2.62.0 – Automatic Diamond Backup

- Napi automatikus biztonsági mentés WorkManager ütemezéssel.
- Heti automatikus biztonsági mentés WorkManager ütemezéssel.
- Eszközön belüli AES-256-GCM titkosítás, nem exportálható Android Keystore kulccsal.
- Offline működés; akkumulátorkímélő feltétel.
- Egyedi WorkManager feladatok, bekapcsolás/kikapcsolás és állapotjelzés a Backup Centerben.
- Automatikus mentési rotáció: legfeljebb 14 fájl marad meg.
- Verziófrissítés: versionCode 6200 / versionName 2.62.0.

## Biztonsági megjegyzés
Az automatikus mentés kulcsa az adott Android-eszköz Keystore-ához kötött. A fájl másik eszközön önmagában nem állítható vissza. Eszközváltáshoz továbbra is a jelszóval védett, hordozható `.pibackup` exportot használd.

## Javítási kör – teljes csomag
- Az automatikus mentés adatbázis-olvasása egyetlen Room-tranzakcióban történik.
- A mentés fájlba írása atomi: előbb ideiglenes fájl készül, majd fsync és végleges átnevezés következik.
- Az AES-GCM fejléc AAD-ként is hitelesített.
- A titkosítatlan és titkosított byte pufferek a feldolgozás után törlésre kerülnek.
- A rotáció kizárólag a kész automatikus mentésfájlokat érinti.
- A mentés csak megfelelő akkumulátor- és tárhelyállapot mellett indul.
