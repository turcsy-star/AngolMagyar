# Biztonsági hardening – 4.2.1

## Javítások

### 1. Release aláírási határ

Korábban saját release kulcs hiányában a build a debug kulcsot használta release APK aláírásához. Ez kiadási és kulcskezelési szempontból hibás gyakorlat.

Most:
- Saját release keystore csak `keystore.properties` vagy a négy `KEYSTORE_*` környezeti változó megadásával használható.
- Részleges konfiguráció esetén a Gradle hibával leáll.
- Ha nincs release kulcs, a release APK unsigned marad.
- A debug aláírás továbbra is a külön, csak tesztelési célú `app/debug.keystore` kulcsot használja; ez nem release kulcs.

### 2. API-kulcsok titkosítása

Korábban az Android Keystore hibája esetén a `SecretStore` visszaesett egyszerű szöveges SharedPreferences tárolásra.

Most:
- AES/GCM/NoPadding, 256 bites Android Keystore kulcs.
- Titkosítási hiba esetén a titok nem kerül plaintextként eltárolásra.
- A régi plaintext érték csak sikeres titkosított migráció után marad meg; sikertelen migráció esetén a régi plaintext bejegyzés eltávolításra kerül.
- Sérült titkosított érték esetén a rendszer üres értéket ad vissza, nem próbálja csendben a plaintextet használni.
- A beállító felület jelzi, ha a biztonságos mentés nem sikerült.

Megjegyzés: ha az Android Keystore hibás vagy a kulcs elveszett, az API-kulcs újbóli megadása szükséges lehet.

### 3. Egyedi online végpontok

- Távoli egyedi végpontoknál HTTPS kötelező.
- HTTP csak localhost, loopback vagy RFC1918 privát IPv4 címen engedélyezett (például helyi Ollama szerver).
- A végpont ellenőrzése nem végez DNS-feloldást; hibás vagy nem biztonságos URL esetén az online konfiguráció nem készül el.

### 4. UI és téma

- Forest Graphite prémium paletta került be.
- A Compose prémium színséma a `darkTheme` értéket figyelembe veszi; a prémium téma nem kényszeríti mindig sötét módra a felületet.

## Kiadás előtt kötelező

- Saját release kulcs létrehozása és biztonságos, offline mentése.
- `keystore.properties` kizárása a Gitből.
- Aláírt release build és tanúsítvány-ujjlenyomat rögzítése.
- API-kulcs migráció tesztelése frissítésből.
- Mentés-visszaállítás tesztelése.
- Android 10+ fizikai eszközteszt.

## Nem állítjuk

Ez a hardening nem helyettesíti a teljes penetrációs tesztet, a hálózati forgalom auditját vagy a felhőszinkron végpontok közötti titkosítását. A felhőszinkron a szolgáltató és backend kiválasztásáig nincs aktiválva.
