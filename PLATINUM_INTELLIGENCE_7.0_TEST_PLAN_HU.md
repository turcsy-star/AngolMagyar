# Platinum Intelligence 7.0 – teljes tesztelési terv

## 0. Tesztelési szabály

A forrásban létező tesztosztály nem bizonyítja a teszt lefutását. Minden eredményhez rögzíteni kell: commit/forrás hash, JDK, Gradle, Android SDK, eszköz/API szint, parancs, eredmény és napló.

## 1. Build és statikus kapuk

```bash
python tools/verify_release_security.py
python -m compileall -q tools
./gradlew --no-daemon :core:test
./gradlew --no-daemon :app:testDebugUnitTest
./gradlew --no-daemon :app:assembleDebug :wear:assembleDebug
./gradlew --no-daemon :app:assembleRelease
```

Release buildhez saját, helyi keystore szükséges; a titkos adat nem kerülhet ZIP-be vagy Gitbe. Részleges aláírási konfiguráció hibával álljon le.

## 2. Funkcionális tesztmátrix

| Terület | Kötelező esetek | Elvárt eredmény |
|---|---|---|
| Fordítás | HU→EN, EN→HU; üres/hosszú szöveg; nevek, számok, dátum, pénznem, tagadás; stílusváltás | Forrásszöveg megmarad; diff pontos; memória nem ír felül jóváhagyás nélkül |
| Fordítási verziók | módosítás, visszavonás, alkalmazás újraindítása | változatok sorrendje és eredete stabil |
| Terminológia | globális/projekt ütközés, duplikátum, import/export | projektprioritás explicit; nincs csendes felülírás |
| ASR | csend, zaj, több beszélő, hosszú felvétel, process death, engedély visszavonása | erőforrás felszabadul; checkpointból folytatás; nincs rejtett rögzítés |
| Kiejtés | US/UK hangadat, hiányzó modell, fonéma provider nélkül | provider nélkül fonetikai pontszám nem jelenik meg |
| Felirat | SRT/VTT round-trip, Unicode, hibás időbélyeg, átfedés, 2 órás fájl, megszakítás | nincs csendes adatvesztés; hibák export előtt jelölve |
| Model Manager | hiányos letöltés, checksum mismatch, kevés tárhely, megszakítás, rollback | sérült modell nem válik aktívvá; meglévő modell megmarad |
| Workflow | minden lépés sikeres, köztes hiba, cancel, resume, duplikált újraindítás | idempotens; checkpoint konzisztens; nincs kettős export |
| Privacy | hibás kulcs, Keystore invalidation, ciphertext sérülés, export, törlés | fail-closed; eredeti titkos blob megmarad; logban nincs titok |
| Learning | SM-2 határok, időzóna, óraátállítás, kihagyott nap, ismétlés félbeszakítása | determinisztikus; statisztika eseményekből származik |
| UI | világos/sötét/OLED, 1.3–1.6 betűméret, TalkBack, reduceMotion, tablet | nincs levágás; fókusz és címkék érthetők |

## 3. Offline / repülőgép-mód teszt

- Hálózati interfészek tiltása és a kimenő kérések naplózása.
- Telepített helyi modellel ASR/fordítás csak akkor sikeres, ha a modell és motor valóban elérhető.
- Szótár, Phrasebook, Coach, feliratszerkesztés, memória, helyi backup és analitika hálózat nélkül működik.
- Hiányzó modellnél pontos „nem telepített” állapot; nincs online fallback opt-in nélkül.
- Online funkció letiltva semmilyen háttérkérést nem indíthat.

## 4. Biztonsági és adatvédelmi tesztek

- `SecretStore`: AES-GCM; legacy migráció; hibánál plaintext eltávolítási és fallback szabály ellenőrzése.
- `SmartTranslationMemoryStore`: hibás ciphertext olvasása után sem write, sem delete nem törölheti az eredeti blobot.
- Manifest exported allowlist, FileProvider `exported=false`, URI grant visszavonhatóság.
- `allowBackup=false`; cleartext nincs globálisan engedélyezve.
- Log redaction: API kulcs, token, teljes privát átirat és ciphertext nem jelenhet meg.
- Backup: hibás header, rossz jelszó, módosított payload, túlméretes fájl, részleges restore.

## 5. Eszközmátrix

Minimum: Android 10/11 és Android 14/15/16 fizikai vagy hiteles emulátoron; kis és nagy kijelző; legalább egy nagy betűméretű konfiguráció. Wear OS külön, ha támogatott eszköz rendelkezésre áll. Mikrofon/TTS/ASR tesztet fizikai eszközön is el kell végezni.

## 6. Kiadási blokkolók

- Bármely kritikus biztonsági vagy adatvesztési hiba.
- Core/app unit teszt sikertelen vagy nem futott.
- Debug build sikertelen vagy nem futott.
- Offline tesztben nem engedélyezett hálózati kérés.
- Manifest/exported komponens eltérés.
- Nem ellenőrzött release signing.
- Nincs frissítési teszt 4.2.x adatokkal.

Egy blokkoló pont esetén a csomag Engineering/Alpha státuszban marad; Stable megjelölés tilos.


## 7. W1/W2 core implementáció – célzott tesztek

### `PlatinumWorkflowTest`
- Offline sablon lépésenkénti futtatása és képességkapu.
- Hálózatot igénylő lépés elutasítása offline módban.
- Függőség teljesülése előtti indítás elutasítása.
- Hibás lépés korlátozott újrapróbálása.
- Függőségi ciklus elutasítása.
- Befejezett futás terminális állapota.

### `TranslationWorkspaceTest`
- Duplikált azonosító és ismeretlen szülőverzió elutasítása.
- Visszaállítás új revízióként, eredeti rekord megőrzésével.
- Előzménykorlát és legújabb verzió megőrzése.
- Diff mindkét oldalának pontos rekonstrukciója.
- Nagy szöveg diffjének korlátozott memóriaigényű fallbackje.

A két új tesztosztály forrásként bekerült. A teljes core Kotlin forrás fordítása PASS; a 11 tesztmetódus külön ellenőrző harnessben lefutott (11/11 PASS, JUnit API-kompatibilitási stubbal). A Gradle/JUnit hivatalos futtatása továbbra sem történt meg, mert a Gradle disztribúció/cache hiányzik.
