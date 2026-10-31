# Platinum Intelligence 6.0 – teljes tesztterv

## 1. Tesztkörnyezet rögzítése

A tesztjegyzőkönyv minden futásnál tartalmazza: Git commit, ZIP SHA-256, JDK, Gradle wrapper, Android Gradle Plugin, Kotlin, compile/target/min SDK, NDK, CMake, készülékmodell, Android verzió, nyelv, hálózati állapot és tesztparancs.

## 2. Automatizált kapuk

| Kapu | Parancs / módszer | Elvárt eredmény |
|---|---|---|
| Forrásbiztonság | `python tools/verify_release_security.py` | PASS |
| Python segédeszközök szintaxisa | `python -m compileall -q tools` | PASS |
| Core unit tesztek | `./gradlew :core:test` | Minden teszt PASS |
| App unit tesztek | `./gradlew :app:testDebugUnitTest` | Minden teszt PASS |
| App debug | `./gradlew :app:assembleDebug` | APK létrejön |
| Wear debug | `./gradlew :wear:assembleDebug` | APK létrejön |
| Release compile | `./gradlew :app:assembleRelease` | Unsigned release vagy saját kulccsal aláírt release |
| R8 | `./gradlew :app:assembleRelease -Pminify` | Shrinkerrel is buildel |
| Manifest | `verify_release_security.py` + merged manifest ellenőrzés | Nincs nem várt exported komponens |
| Licenc/dependency | Dependency report és licenclista | Új dependency jóváhagyva |

## 3. Offline tesztmátrix

A hálózati adaptert repülőgép-módban vagy kontrollált DNS blokkolással kell tesztelni. Az offline teszt alatt semmilyen külső kérés nem indulhat el.

- Offline fordítás telepített modelllel.
- Offline ASR telepített Vosk/Whisper modellel.
- Szótár, kifejezésgyűjtemény, tanulás és analitika.
- Fordítási memória lookup, jóváhagyás és visszavonás.
- Felirat import, szerkesztés, checkpoint, folytatás és export.
- Helyi titkosított mentés és visszaállítás.
- Online funkciók kikapcsolt állapotban nem próbálnak hálózatot használni.
- Hiányzó modell esetén egyértelmű „offline modell nincs telepítve” állapot, nem hamis siker.

## 4. Adatvédelem és biztonság

- Keystore kulcs létrehozás, titkosítás, visszafejtés és kulcsinvalidáció.
- Régi plaintext API-kulcs migrációja; sikertelen migráció esetén plaintext törlése.
- Titkosított fordítási memória blob sérülése: nincs plaintext fallback, nincs automatikus törlés vagy felülírás.
- Olvasási hiba után save/delete művelet `false` eredménnyel megszakad.
- FileProvider csak szükséges útvonalat oszt meg, URI grant visszavonható.
- Exportált Activity/Service/Receiver allowlist.
- Külső endpoint HTTPS-korlátja; localhost/private endpoint szabályainak tesztje.
- Logokban nincs API-kulcs, teljes személyes átirat, token vagy titkosított payload.
- Backup méretkorlát, hibás header, hibás jelszó, módosított ciphertext és részleges fájl.
- Release aláírás: hiányos konfiguráció hibával leáll; debug kulcs release-ben nem használható.

## 5. Funkcionális regresszió

### Fordítás
- HU→EN és EN→HU, üres szöveg, hosszú szöveg, írásjelek.
- Nevek, dátumok, számok, pénznemek, tagadás és mértékegységek.
- Hálózati timeout és szolgáltatói hiba után forrás megmarad.
- Jóváhagyott memóriajavaslat csak explicit felhasználói megerősítés után alkalmazható.
- Projektmemória elsőbbsége globális memória felett; eltérő célfordítás esetén kétértelmű jelzés.

### Tanulás
- SM-2 határértékek, időzóna és óraátállítás.
- Aktív/passzív szókincs külön számolása.
- Ismétlés megszakítása és újraindítása.
- Nincs büntetés kimaradt nap miatt.
- Statisztikák mentett eseményből származnak.

### Hang
- Mikrofon engedély elfogadás/elutasítás/visszavonás.
- Hívás, háttérbe lépés, képernyőzár és folyamatmegszakítás.
- Csend, zaj, több beszélő, nyelvváltás és hosszú felvétel.
- TTS nyelvi adat hiánya és hangválasztás.
- Fonetikai pontszám csak validált referencia-benchmark esetén jelenhet meg.

### Felirat
- SRT/VTT/ASS import-export round-trip.
- Hibás timestamp, átfedés, üres blokk, Unicode, sortörés.
- 2 órás és nagy méretű fájl; memóriahasználat.
- Pause/resume, process death és checkpoint.
- Export célhely jogosultságvesztése.

## 6. Eszköz- és UI-teszt

Legalább egy Android 10/11 eszköz, egy Android 14/15/16 eszköz és egy támogatott Wear OS eszköz szükséges. Képernyőméretek: kis telefon, normál telefon, nagy betűméret és tablet/összehajtható, ha támogatott.

- Világos, sötét és OLED mód.
- 100%, 130%, 160% betűméret.
- TalkBack, billentyűzetes fókusz, kontraszt és érintési célméret.
- Csökkentett mozgás és akkumulátorkímélő mód.
- Forgatás, folyamat újraindítás, rendszer téma váltás.
- Nincs levágott vagy egymásra lógó szöveg.

## 7. Valós hangbenchmark

A WER/CER teszthez jogtisztán használható, ember által ellenőrzött referenciaátirat szükséges. Külön angol US/UK és magyar mintakészlet, zajos és tiszta felvétel, külön beszélők. A benchmark jelentésben rögzítsd a modellverziót, minták számát, WER/CER értékeket és hibamintákat. Kiejtési értékelés nem származtatható kizárólag WER-ből.

## 8. Kiadási checklist

- [ ] Minden P0–P10 scope-hoz státusz és elfogadási bizonyíték.
- [ ] Core/app/Wear tesztek sikeresek.
- [ ] Debug és release build sikeres.
- [ ] Offline repülőgép-mód teszt sikeres.
- [ ] Biztonsági és mentés-visszaállítási teszt sikeres.
- [ ] UI/accessibility eszköztesztek sikeresek.
- [ ] Frissítés a támogatott korábbi verzióról és adatmegőrzés tesztelve.
- [ ] Saját release kulccsal aláírt APK/AAB; SHA-256 és tanúsítvány ujjlenyomat rögzítve.
- [ ] Végső kiadási jegyzőkönyv és ismert hibák listája publikálva.

Egyetlen ki nem pipált blokkoló pont esetén a kiadás nem nevezhető stable-nek.
