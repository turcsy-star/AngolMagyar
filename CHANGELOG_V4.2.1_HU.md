# v4.2.1 – Biztonsági hardening és Premium Intelligence 5.0 előkészítés

## Javítások
- API-kulcs titkosítási hiba esetén nincs plaintext fallback.
- Régi plaintext API-kulcs csak sikeres AES-GCM migráció után marad meg.
- Online beállítási felület visszajelzi a biztonságos kulcsmentés hibáját.
- Egyedi távoli fordítási végpontnál HTTPS kötelező; HTTP csak loopback/privát LAN végponton engedélyezett.
- Release APK többé nem használhatja a debug aláíró kulcsot.
- Saját release kulcs hiányában unsigned release készül; részleges konfiguráció buildhibát okoz.

## Prémium UI
- Forest Graphite színpaletta.
- Compose prémium mód világos és sötét színsémát is használ a rendszer beállítása szerint.

## Dokumentáció
- Premium Intelligence 5.0 cél-specifikáció.
- Funkcióállapot-mátrix, fájlszintű megvalósítási és tesztelési terv.
- Biztonsági hardening és 4.2.0 → 4.2.1 migrációs útmutató.

## Ellenőrzés
A Gradle 8.9 letöltése hálózati DNS-hibán meghiúsult, ezért a 4.2.1 Android build és tesztfuttatás ebben a környezetben nem igazolt. Lásd `BUILD_STATUS_4.2.1_HU.md`.
