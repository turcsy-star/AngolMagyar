# AngolMagyar – dokumentáció (4.2.10)

A korábbi, verziónkénti állapot- és changelog-fájlok egyetlen fájlba lettek összevonva; a régi előzmények az eredeti csomagban (AngolMagyar-fix-4_2_9-next-wave.zip) megtalálhatók.

## 4.2.10 – Diamond egységesítés, animációmentes gombok, kék-fehér alaptéma

- **Gombanimáció megszüntetve**: a View-gombok/kártyák (Ui.kt), a Compose kártyák (MainActivity, PlatinumGlass) és az overlay chipek nem méretváltozással reagálnak; marad a hullámeffekt és a haptika. A kattintás azonnal fut (nincs 80 ms késleltetés).
- **Alaptéma**: az alapértelmezett luxus paletta `sapphire` (Midnight Sapphire, kék-fehér) a korábbi `forest` helyett. Aki már kézzel választott palettát, annak a választása megmarad.
- **Egységes diamond felület**: a pezsgőarany kiemelés (`Champagne`) kék-fehérre (`DiamondWhite`, #D6E9FF) cserélve a feldolgozó panelekben, a spektrumban, a hullámformában és a főképernyőn; a képernyők címkéi egységesen `DIAMOND • …` formátumúak.
- **Fejlesztési hullám**: a Motion / Global UI / Akadálymentesség kártya valódi tartalmat kapott (mozgásprofil-állapot, kontraszt-audit minden palettán `DesignAudit`, migrációs checklist). A hub állapotai pontosítva; a Document/Camera (P8) továbbra is specifikáció, ezért a gombja le van tiltva („Hamarosan”).
- Új teszt: `DesignAuditTest`.
- Nyitott (eszközteszt vagy külső függőség kell): teljes View→Compose migráció, TalkBack/fókuszsorrend teszt, CameraX/OCR.
- A főképernyő nagy gombkártyáján (HeroCard) végigfutó fénycsík is megszűnt.

## Nyitott tételek – elkészült részek
- **Kártyák keretfény-animációja megszűnt** (`PremiumCard`: nincs mozgó keretfény és belső fénycsík; a keret statikus).
- **P8 Dokumentum / Kamera**: új `DocumentCameraActivity`, `MlKitCameraEngine`, `OcrTextCleaner` (+ teszt), új függőség: `com.google.mlkit:text-recognition:16.0.1`. Állapot: kísérleti, eszköztesztre vár (`P8_DOCUMENT_CAMERA_HU.md`). PDF-import nincs.
- **TalkBack**: címsor-szemantika, 48 dp célok, összevont leírások; kézi tesztlista: `TALKBACK_TESZT_HU.md`.
- **View→Compose**: az Adatvédelem képernyő Compose-ra átírva. A többi klasszikus képernyő (Beállítások, Feliratfeldolgozás 2000+ sor, SRT-szerkesztő, Élő fordítás, Modellek, Átiratok…) még View-alapú, ezt nem jelöltem késznek.


---

## P8 – Dokumentum / Kamera (4.2.10)

Működés: `DocumentCameraActivity` – fotó (rendszerkamera, `TakePicture`, nincs CAMERA-engedély) vagy kép/szövegfájl kiválasztása → `MlKitCameraEngine` (ML Kit text-recognition, eszközön) → `OcrTextCleaner` → szerkeszthető szöveg → ML Kit angol→magyar fordítás.

- Támogatott: képek (latin betűs szöveg), .txt (max. 2 MB). **PDF-import nincs**; az oldalról fotó/képernyőkép készíthető.
- Adatvédelem: a kép nem hagyja el a telefont; a fordítómodell első használatkor letöltődhet.
- Állapot: `CaptureReadiness.EXPERIMENTAL`. READY csak eszköztesztek után:
  1. éles és homályos fotó, gyenge fény; 2. 12 MB feletti kép elutasítása; 3. offline fordítás letöltött modellel; 4. kamera nélküli eszköz / megszakított fotó; 5. nagy betűméret.
- A `CaptureRegistry` alapértelmezése szándékosan `UNAVAILABLE` maradt (a meglévő szerződésteszt védi).
- Tesztek: `OcrTextCleanerTest` (tiszta logika). Az OCR és a kamera csak eszközön tesztelhető.


---

## TalkBack / fókuszsorrend kézi teszt (4.2.10)

Kódszinten kész: címsor-szemantika (`heading()`), legalább 48 dp érintési célok, a hub gombjainak összevont leírása.
Eszközön ellenőrizendő (Beállítások → Kisegítő lehetőségek → TalkBack):

1. Intelligence Suite: a „Következő” gesztus szakaszcímről szakaszcímre ugrik (címsor-navigáció), a kártyák címe → leírása → gombja sorrendben szólal meg.
2. Minden „Megnyitás” gomb hangosan „<kártya címe> – megnyitás” formában hangzik el; a tiltott gomb „hamarosan”.
3. Adatvédelem: a kapcsoló szövege és állapota együtt hangzik; a törlés megerősítő párbeszéd fókusza a párbeszédre kerül, bezárás után a „Minden átirat törlése” gombra tér vissza.
4. Dokumentum / Kamera: fotó után az állapotsor szövege elhangzik; a szerkeszthető mező felirata „Szerkeszthető szöveg”.
5. Betűméret 200 %-on egyik szöveg sem vágódik le, görgetéssel minden elérhető.

Eredmény rögzítése: eszköz, Android-verzió, megfelelt / hibás (+ rövid leírás). Ha minden pont megfelelt, a `InfinityGlobalUi` checklist utolsó sora `true`-ra állítható.

## 4.3.0
- Varázsló: nincs modellválasztás; a letöltés (Whisper + fordítómodell) magától indul, a varázsló sávval, százalékkal, sebességgel és hátralévő idővel mutatja, szüneteltethető, hiba esetén újrapróbálható.
- Olvashatatlan menük javítva: a közös kártya (PremiumCard) a tartalmát egymás alá rakja (korábban egymásra rajzolta).
- „Minőség" menü: csak szövegjavítás online szótárral, automatikus szótár, online kapcsolat-ellenőrzés, fordításminőség-ellenőrző. Törölve: Ultimate, Dashboard, Next Wave, Ismétlő, Analytics, Kamera képernyők.
- Online szótáras szöveghiba-javítás (Datamuse, alapból kikapcsolt, csak különálló szavak mennek ki) + javított sorok újrafordítása; feldolgozás közben és a szerkesztő gombjával.
- Automatikus szótár: szavankénti összevetés (eltérő szószámnál is tanul), elírás-szerű csere már a 2. javításnál szabály, 1000 szabályig, mondat eleji nagybetű megmarad.
- Online kapcsolat: érthető hibaüzenetek, hálózatkiesésnél gyors átváltás offline fordításra, hibás sorok újrapróbálása kisebb csomagokban, internet-előellenőrzés.

## 4.4.0 – Prémium vizuális réteg
- Valódi üvegfelület (`glassSurface`, `PlatinumGlassCard`): tónusos ambient bloom, felső specular csík, belső alsó árnyék, tile-olt zajszemcse (csíkozódás ellen), fényes él. A NodeDetail kártya az állapot színét kapja kiemelésnek.
- Üstökös a folyamatábrán (`PlatinumFlowGraph`): fényes fej, halványuló csóva a futó szakaszon; amikor egy fázis KÉSZ lesz, egyszeri, nagyobb üstökös fut át a következő csomóponthoz.
- Hangszint-glow (`AudioGlow.kt`): a valós `AppState.level` simított értéke (gyors felfutás, lassú lecsengés) vezérli a mérő/spektrum háttérfényét és keretét, valamint a magas sávok halóját. Mikrofon / belső hang forrásnál is ugyanaz az érték, nincs szimulált adat.
- Gördülő számjegyek (`RollingDigits.kt`): az ELTELT és HÁTRA érték, valamint a főképernyő munkamenet-ideje számjegyenként gördül; az irány a változásból adódik (növekvő fel, visszaszámláló le). A képernyőolvasó egyben olvassa.
- Mozgásprofil: csökkentett animáció / kikapcsolt animáció mellett nincs glow-ciklus, nincs üstökös, a számjegyek sima szövegként cserélődnek.
- Eszközön ellenőrizendő: a glow és az üstökös simasága hosszú Whisper-futás közben, valamint a főképernyő spektrumának fénye mikrofon- és belső hang módban.

## 4.5.0 – Megbízhatóság és pontosság
- **Hangjavítás felismerés előtt** (`AudioConditioner`): 70 Hz-es magas áteresztő szűrő (egyenfeszültség, dübörgés), a beszédes keretek RMS-e alapján felfelé normalizálás (cél −23 dBFS, max +18 dB, rossz jel/zaj aránynál max +6 dB, a csúcsok korlátozzák), lágy limiter. Zajzár nincs, mert a Whisper pontosságát rontaná. Kapcsoló: Formázás és szűrők → „Hangjavítás felismerés előtt".
- **Magyar helyesírás-javítás** (`HungarianRules`) a fordításon: „a" → „az" magánhangzó előtt (csak ez az irány, mert az „az nem igaz" rendben van), idézőjelek → „…", 1,000,000 → 1 000 000, 3.5 % → 3,5 %, 5kg → 5 kg, hiányzó szóköz , ; ! ? után, hónapok és napok kisbetűje mondat közben (névre hasonlító szónál nem). Kapcsoló: „Magyar helyesírás-javítás".
- **Összeomlás-napló** (`CrashLog`, `CrashReport`): a kezeletlen kivételt a `crash.log` fájlba írja (verzió, telefon, memória, hőállapot, futó feladat), az eredeti kezelőnek továbbadja. Következő indításkor a főképernyő felajánlja az anonimizált jelentés küldését; a „Hibajelentés" is tartalmazza.
- **Memóriavédelem** (`MemoryPlan`): a hangdarab hosszát a Java heap méretéhez korlátozza; ha egy darabnál elfogy a memória (`OutOfMemoryError`), a darabot felezve újrapróbálja, a feladat nem áll le.
- **Ellenőrzőpont-kulcs** kibővítve a hangjavítás és a helyesírás-javítás kapcsolóival, így a folytatás nem keveri a beállításokat.
- **Tesztek**: `:core` JUnit tesztek (hangjavítás, magyar szabályok, memóriaterv, összeomlás-jelentés, SubFormat időzítés/sortörés, OnlineTranslator, WER, szótár-összefésülés, ETA-simítás), a CI-ben a `:core:testDebugUnitTest` lépés.
- **Release**: a CI a release APK-t R8 minify + erőforrás-szűkítéssel építi (`-Pminify`); ha az elbukik, figyelmeztetéssel minify nélkül építi újra. Aláírás csak akkor, ha a `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` titkok léteznek, különben unsigned marad (debug kulcsra soha nem esik vissza). A `mapping.txt` feltöltődik az artifactba, a sorszámok megmaradnak, így az összeomlás-napló visszafejthető.
- **Függőségek**: Compose BOM 2025.02.00, activity-compose 1.10.1, core-ktx 1.15.0, coroutines 1.10.1.
- **Kódszerkezet**: a `FileSubtitleActivity`-ből kiemelve a szótár-összefésülés (`GlossaryMerge`) és az ETA-simítás (`EtaSmoother`) a tesztelhető `core` modulba.
- Eddig is megvolt, és változatlan: VAD alapú darabolás, kontextusos online fordítás (előző 4 / következő 3 sor), hőlépcsőzés, ellenőrzőpontból folytatás, olvashatósági időzítés és sortörés (`SubFormat.polish`).
- Eszközön ellenőrizendő: a hangjavítás hatása halk felvételeken (DiagLog „HANG darab" sorok), a minify-olt release indulása (JNI: Whisper, Vosk, ONNX).
