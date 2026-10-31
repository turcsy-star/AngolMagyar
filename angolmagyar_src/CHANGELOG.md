# V2.48.0 – akadálymentes folyamatjelző és tesztelhetőség

- A platina folyamatjelző Compose-szemantikája most valódi `ProgressBar` szerepet és 0–100 közötti tartományt ad a képernyőolvasóknak.
- Ismeretlen haladásnál indeterminált állapotot jelent, nem ad meg kitalált százalékot.
- A százalékot korlátozó segédfüggvény tiszta Kotlin-függvény lett (nem Composable), így a meglévő JUnit tesztek közvetlenül hívhatják.
- Verzió: 2.48.0 (`versionCode` 4800).
- Ellenőrzés: a forrásmódosítás és a ZIP-csomag készítése elvégezhető; Android/Gradle tesztfuttatás nem igazolt, mert a `services.gradle.org` DNS-feloldása meghiúsult a környezetben.

# Platinum Ultra Extreme UI v12

- Új, részletes Platinum Ultra állapotközpont a kezdőképernyőn.
- Valós hangszint-, munkamenet- és feladat-előrehaladás megjelenítése.
- A meglévő funkcionális folyamatok változatlanok.

## Diamond Blue UI v11 – Diamond Experience
- Új, vektorosan rajzolt zafírmedál a kezdőképernyőn, forgó fénygyűrűvel és finom pulzáló aurával.
- Valós aktív/készenléti állapotjelzés; csökkentett mozgás és energiatakarékos mód támogatása.
- Részletek: DIAMOND_BLUE_V11_HU.md

## Platinum Luxury v7 (nem fordított forrásállapot)
- Platina hangszintmérő az Élő fordításban, platina letöltési haladássáv a Modellkezelőben.
- Részletek: PREMIUM_UI_PLATINUM_V7_HU.md

## Platinum Luxury v6 (nem fordított forrásállapot)
- Interaktív, összekapcsolt folyamatábra animált adatáramlással, érintéses csomópont-részletek, üvegkártyák, haladás-grafikon.
- Részletek: PREMIUM_UI_PLATINUM_V6_HU.md

## Platinum Luxury v5 (nem fordított forrásállapot)
- Platina műszer (60 osztás, szegmensgyűrű), ékköves folyamatvonal lépésenkénti idővel, gravírozott statisztikák, befejezési pecsét; téma-kapcsoló a beállításokban.
- Részletek: PREMIUM_UI_PLATINUM_V5_HU.md

## Blue Cinematic v4 (nem fordított forrásállapot)
- Új cinematic feldolgozó kártya (ProcessingPanel) a SrtJob valós adataiból; kapcsolható és csökkentett animációs móddal.
- Részletek: PREMIUM_UI_BLUE_CINEMATIC_V4_HU.md

# Változásnapló

## v2.45.0 – élő folyamat-kijelzés, tisztább átirat és fordítás
- **Folyamat-hullámforma**: a kész rész zöld, a hátralévő szürke, az éppen feldolgozott ~30 mp-es ablak piros és fehér között pulzál.
- **Élő pozíció és szöveg**: a hullám alatt „Éppen: tól – ig” (és a szakasz határai), alatta az éppen hallott mondat időpontjával és az utoljára lefordított mondat (offline fordításnál soronként élőben). A folyamat-ikon fölötti kitalált példapár helyett a valódi szöveg látszik.
- **Hallucináció-szűrő**: sűrű, lehetetlenül gyors (35 karakter/mp fölötti) blokk-sorozat (≥4 egymás után) kiszűrése – az összeomlott időbélyegű Whisper-ismétlések ellen. Két új teszttel.
- **Ismétlési hurok védelem**: ha az előző darab szövege hurokra utal (ugyanaz a háromszavas sorozat ≥3-szor), nem kerül kontextusként a következő darab Whisper-promptjába.
- **Második menet**: az elfogadási küszöb 0,12-ről 0,05-re csökkent (azonos a célzott ellenőrzésével), és a napló kiírja a szakaszonkénti régi és új lp értéket a diagnosztikához.
- **Fordítás szétosztása**: a lefordított mondat darabolása már nem az arányos helyen vág vakon, hanem a közeli mondatvégi írásjelet keresi (±4 szó), kötőszó/névelő után nem vág szívesen, és minden résznek jut szó.
- **Időzítés**: ha a következő blokk közel kezdődik, az előző nem lóg bele (legalább 200 ms látszik) – nincs több átfedés.
- **Export**: ASS (Advanced SubStation) a jegyzőkönyv exportmenüjében; kétnyelvű módban a forrássor dőlt.
- **Minőségmérés**: `TextMetrics.chrF` (karakter n-gram F-érték) a regressziós tesztekhez a WER mellé.
- A NaN naplóvalószínűség „bizonytalan felismerés” jelölést kap; a régi, elavult `MainActivity.kt` törölve; a tesztek fordítási hibái javítva.
- **Nem készült** (fordító, eszköz vagy fiók nélkül itt nem ellenőrizhető, vagy beállítás kell): online fordítás beállítása (DeepL/Claude kulcs), visszafordításos ellenőrzés, a whisper.cpp dekódolási paramétereinek (no_context, hőmérséklet) módosítása, turbo + large-v3 kétmenetes mód, szószintű időbélyeg-igazítás, szereplő-kötőjelezés, szószintű kiemelés lejátszás közben, termikus ütemezés finomítása.

## v2.29.0 – célzott ellenőrzés, napló, benchmark, biztonság, prémium ellenőrző lap
- **Prémium minőségellenőrző lap** (alulról felcsúszó): animált pontgyűrű, súlyosság-szűrők, színes kártyák, koppintásra sorra ugrás; a szerkesztőben minőség-kártya jelzi a pontot.
- **Célzott újrafelismerés**: csak a kritikus/magas jelzésű sorok hangszakasza fut újra nagyobb nyalábbal (large-v3-mal, ha van); a javaslatokat soronként választod ki, semmi sem íródik át magától.
- **Módosítási napló** (`AuditLog`): kézi szerkesztés, újrafordítás, célzott újrafelismerés rögzítve előtte/utána értékkel; a napló bejegyzése visszaállítható.
- **Benchmark**: „Mentés referenciának” gomb; újrafuttatás után WER, kritikus (szám/tagadás) eltérés és előzmények verziónként.
- **Eredményújrahasznosítás**: a kész fájl ellenőrzőpontja 7 napig megmarad; ugyanazon fájl és beállítás (szótár/szabályok is az ujjlenyomatban) újrafuttatása a kész darabokat nem számolja újra.
- **API-kulcs titkosítva** (Android Keystore AES-GCM); a régi egyszerű érték első olvasáskor áttelepül.
- **Fordítási memória**: a Room-kettősírás és a szálankénti `runBlocking` megszűnt; atomikus fájlírás.
- **Hő-alapú szálszám**: a Whisper melegedéskor (mérsékelt/súlyos/kritikus) folyamatosan kevesebb szálon fut.
- **Éjszakai ütemezés** („🌙 Éjszakára ütemezés”): töltőn (és Wi-Fin, ha online fordítás kell) értesítés, koppintásra indul.
- **Mentés**: a `MANAGE_EXTERNAL_STORAGE` csak a debug manifestben; a release a mappa-engedélyt (SAF) használja.
- **Release**: saját keystore (`keystore.properties` / környezeti változók), R8 opcionálisan (`-Pminify`).
- A régi élő-fordító képernyő `MainActivity` → `LiveTranslateActivity` (nincs több azonos nevű osztály).
- 85 üres `catch` helyett `DiagLog.swallow` (azonos hiba 5 mp-en belül egyszer), így a Hibajelentés használhatóbb.
- Nem készült: a `FileSubtitleActivity` ViewModel-re bontása (nagy, fordító nélkül nem ellenőrizhető átszervezés).

## v2.28.9 – javítások a 2.28.8-dev átnézése után
- Javítva: az „Ellenőrzési javaslatok” ablakban a `setMessage` miatt nem jelent meg a sorlista; a fejléc most a címben van.
- A szerkesztő átadja a valós bizonytalanságot (`lp`, szó-szintű konfidencia) az ellenőrző motornak; az `LOW_CONFIDENCE` jelzés így ténylegesen működik.
- Eltérés-alapú jelzés: forrás+fordítás esetén szám/tagadás/név csak tényleges eltérésnél jelez (a `TranslationCheck` logikájával). Ismert, magas bizonyosságú egynyelvű sort nem jelöl; ismeretlen bizonyosságnál csökkentett súly.
- Új `BAD_TIMING` típus a fordított időbélyegre; az évszám már nem jelenik meg számként és dátumként is.
- `QualityReport`: a `szám eltér`, `tagadás eltér` (5), `név hiányzik` (4) jelzés nagy súlyt kap, így a „Legrosszabb 10 sor” elejére kerül.
- Időkorlát (Android 15, ~6 óra): a feldolgozás nem vész el; a félbehagyott feladat és az ellenőrzőpont megmarad, értesítés jelzi, és a Fájl képernyő felajánlja a folytatást (újraindított képernyőn is).
- Pótolva: `gradle/wrapper/gradle-wrapper.jar` + `.properties` (Gradle 8.9), `gradlew.bat`.
- CI: a unit tesztek külön, kötelező jobban futnak (hiba esetén piros), az APK-build ettől függetlenül elkészül.
- Új tesztek: eltérés-alapú jelzés, ismeretlen bizonyosság, BAD_TIMING, súlyozási sorrend.

## v2.28.8-dev — 2026-10-27
- A minőségellenőrző motor bekötése az SRT-szerkesztő eszközei közé.
- Új „Ellenőrzési javaslatok” lista: kritikus tagadások, számok, dátumok/időpontok, rövid töredékek, ismétlődő és üres sorok áttekintése.
- A jelzés kiválasztásával a szerkesztő közvetlenül az érintett blokkhoz navigál.
- A modul kizárólag jelez, nem írja át automatikusan a szöveget; a jelzés nem bizonyít hibát.
- APK/telefonos build még nem igazolt ebben a környezetben.


## 2.28.7 – prioritásos minőségellenőrzés
- Javítva a „Legrosszabb 10 sor” rendezése: a legsúlyosabb problémák jelennek meg először, nem időrendi sorrendben.
- A gyenge szókonfidencia külön súlyt kap a minőségjelentésben; a beszéd/csend gyanúja, a töredék és a hosszarány-eltérés közepes súlyú jelzés.
- Új regressziós teszt ellenőrzi a súlyossági sorrendet.
- A változás nem írja át vagy törli az átirat szövegét.
- Ellenőrzés: a teljes `:core` Kotlin-forrás lefordult `kotlinc`-cel, és a prioritásos sorrend smoke tesztje sikeres. Teljes Android-build és JUnit futtatás SDK/Gradle Wrapper hiányában nem igazolt.

## 2.28.5 – pontosság, tartalmi hűség és minőségmérés
- A `TextPolish` alapértelmezésben megőrzi a hezitálásnak tűnő magyar szavakat (pl. „hát”, „na”, „izé”); eltávolítás csak explicit `removeFillers = true` beállítással történik.
- A tipikus feliratvégi zajszövegek csak önálló sorban törlődnek; valódi mondat közepén szereplő „thanks for watching” és hasonló kifejezés megmarad.
- A hallucináció-ellenőrző a nagyon alacsony logprobú és a hosszú csendben magányos rövid sorokat megőrzi, és ellenőrzendőként jelöli. Csak a rövid időablakon belüli, harmadik és további azonos hosszú ismétlés kerül automatikusan kiszűrésre.
- Közös `QualityRules` küszöbök: az SRT szerkesztő és a hallucináció-ellenőrző azonos logprob- és magányos-szegmens küszöböt használ.
- A fájlfelismerés a meglévő egyéni szótár kanonikus alakjait is átadja a Whisper-promptnak, karakterkorláttal és duplikátumszűréssel.
- Új `WordErrorRate` mérőmodul a felismerési változatok objektív összehasonlításához; a kis-/nagybetű és az írásjelek nem növelik a hibaarányt, a magyar ékezetek számítanak.
- Új regressziós tesztek a tartalommegőrzéshez, hallucinációszűréshez és WER-számításhoz.
- A meglévő Compose Material 3 felületet, animációkat, VAD-ot, promptot, fordítási memóriát és szerkesztőt megtartja; nem készülnek párhuzamos modulok.

## 2.28.1
- Az önjavító szótár a végén újrafordítja azokat a sorokat, amelyek forrása megváltozott.
- Szerkesztő: „Szótár újraalkalmazása + javított sorok újrafordítása” – a régi felirat is megkapja az azóta tanult szabályokat.

## 2.28.0
- Teljesen automatikus, önjavító szótár: a felvételen belül ritkán, hibásan leírt neveket (pl. Nino / Nina) a gyakori alakra javítja az angol és a magyar szövegben is, és az új szabályt megjegyzi a következő fájlokhoz.
- Önjavítás: ha egy automatikus szabály eredményét visszaírod az eredeti alakra, a szabály magától törlődik.
- A szótár ablakban az automatikus szabályok külön, szerkeszthető listában látszanak.

## 2.27.0
- Szótár, ami magától fejlődik: a háromszor ugyanúgy javított szó automatikusan szabály lesz (kapcsolható); a szótár ablak mutatja az utoljára tanult elemeket.
- Szótár exportálása / importálása fájlba (javítások, nevek, beszélők) – új telefonra költözéshez, másik sorozathoz.
- Sorozat-mód: a felismert szereplőnevek bekerülnek a projekt szereplői közé, a következő rész már ismeri őket.
- Szerkesztő: lejátszás közben az éppen szóló blokk kiemelődik és a lista követi; a szövegmezőre koppintva a lejátszás megáll.
- Szerkesztő: „Beszélők nevei…” (S1 → Nina az egész feliratban, és megjegyzi); „Szakasz újrafordítása egyben (±4 sor)”.
- Egyszerűbb főképernyő: a projekt, a profil és a teljesítmény a „Haladó beállítások” alá került.
- Hangminőség-előszűrés a felismerés előtt (zúgás szűrése, halk felvétel felhangosítása; kapcsolható).

## 2.26.0
- Fordítómotor jelzése: a feldolgozó képernyőn és az eredményben látszik, hány sort fordított a DeepL/Claude és hányat az offline ML Kit.
- Szereplőnevek automatikus felismerése az átiratból (többször előforduló nagybetűs szavak); háttérként megy az online fordítónak, és kiírja a képernyő.
- A második menet a töredék sorokat is újrafuttatja, legfeljebb 12 szakaszt.
- Szerkesztő: „Fordítások összehasonlítása” – jelenlegi / online / offline változat egymás mellett, a választott bekerül a sorba és a fordítási memóriába.
- Olvasási sebesség: a 20 karakter/mp fölötti feliratok végét a következő blokk előtti szabad helyig kitolja.
- Félbehagyott feldolgozás folytatása: ha az app közben leállt, a Fájl képernyő megnyitásakor felajánlja a folytatást (a kész darabok megvannak).

## 2.25.0
- Alapértelmezett a Whisper large-v3: az első indításnál (Wi-Fin) ez települ, és ez dolgozik; a régi telepítések is letöltik egyszer.
- Egyetlen profil maradt: „Pontos” (large-v3, pontosabb keresés). A többi (vázlat, kiegyensúlyozott, éjszakai) megszűnt; alapból ez él.
- DeepL egyszerűen: alapértelmezett szolgáltató; csak az API-kulcs kell (a modell- és végpontmező elrejtve), és a mentéskor az online fordítás magától bekapcsol offline tartalékkal.
- Javítva: az online fordítás alapból ki volt kapcsolva (Ki mód), ezért a kulcs megadása után is az offline fordító dolgozott.

## 2.24.2
- Fordítás: a Whisper darabhatárokon hozzáadott hamis pontokat ("what are you." | "Doing later?") a mondatképzés áthidalja, így a félbevágott mondatok egyben kerülnek fordításra.
- Az online fordítás (Claude / OpenAI / DeepL) is mondatokba fogva dolgozik, a lefordított mondatot a rendszer visszaosztja az időzített blokkokra.

## 2.24.1
- Hullám: zöld csak az a ~30 mp-es ablak, amelyen a felismerés éppen dolgozik, és az nagyon lassan, finoman lüktet; ami még nincs kész, az szürke.
- Folyamat-gyöngysor: lassabb, finomabb mozgás – üstökös-szerű áramlás a lépések között, lágy fény és két eltolt hullám az aktív lépés körül, elhalványuló forgó ív, a haladás-kitöltés simán követi az értéket.

## 2.24.0
- Élő átirat-folyam a hullámforma alatt: a felismert sorok beszélőszínnel; a legfrissebb sor betűnként gépelődik, majd áttűnéssel megjelenik alatta a fordítás; a bizonytalan sorok sárgásan derengenek.

## 2.23.1
- DeepL mint harmadik online szolgáltató (ingyenes `:fx` kulcs is): a szomszédos sorok kontextusként mennek vele, tegező forma, 456-os keretkimerülés kezelése.
- Élő mód: az online fordítás megkapja az előző 4 sort (eredeti + fordítás) kontextusként.

## v2.23.0
- Hibajavítás: a fordítómodell-letöltés megakadt, ha egyszerre több helyről indult (Modellek képernyő, háttér-letöltő, feliratkészítés). Közös letöltő (MlKitModels): ha a modell megvan, nem tölt; egyszerre egy letöltés fut; időtúllépésnél érthető hiba.
- Egységes megjelenés: a Compose kezdőlap és a régi képernyők ugyanazokat a színeket használják; téma-választó (5 kiemelőszín, rendszer/világos/sötét/OLED) a kezdőlapon.
- Élő nézet: chat-szerű beszélő-buborékok (színes, váltakozó oldal, zászló + nyelv, 🔀 nyelvváltás jelzés, betűnkénti fordítás), átkapcsolható a két panelre.
- Folyamat-sor mini-animációkkal (pergő hang, megtelő négyzetek, angol→magyar szóváltás, író dokumentum) és kipattanó "kész" jelzéssel.
- Hullám: kurzor a feldolgozás pontos helyén (kész = fehér, aktuális szakasz = zöld, hátra = szürke); koppintásra a hang megszólal azon a ponton.
- Illusztrált üres/hiba állapotok a kezdőlapon (nincs modell, nincs internet, hiba) egy gombos megoldással.

## v2.22.8
- Minőség-ellenőrzés: a kész képernyőn "⚠ N gyanús sor átnézése (meghallgatással)" gomb, a szerkesztő csak a gyanús sorokat mutatja.
- Kétmenetes felismerés: alapból be; a bizonytalan szakaszokat a telepített large-v3 modellel futtatja újra (max. 10 szakasz).
- Beszélők az SRT-ben: a név csak váltáskor / szünet után jelenik meg (teszttel).
- Teljes képernyős élő mód: nagy betűs két panel, csípéssel és A-/A+ gombbal méretezhető, a képernyő nem alszik el.
- Előzetes időbecslés indítás előtt modellenként (mért sebesség alapján, TimeEstimate + teszt).

## v2.22.7
Animált folyamat-sor a feliratkészítésnél (Előkészítés → Hang → Betöltés → Felismerés + fordítás → Mentés): kész lépés pipa, aktív lépés hullámzik, felé áramló fénypontok. Élő nézet: hangszint-oszlopok, beúszó fordítás. Kezdőlap: letöltés-sáv a háttérben futó modell-letöltéshez, "Indítás" gomb. Pontosabb modell ajánlása, ha csak kicsi Whisper van. Ritka nyelvekhez online fordítás tartalék (ha be van állítva).

## v2.22.6
Első indításkor automatikus modell-letöltés (Whisper large-v3 (~1,1 GB) + offline fordító, csak Wi-Fin, egyszer; ha large-v3 vagy turbo már van, nem tölt). Az online fordítás próbája pontosan megmondja, mi hiányzik (API-kulcs, modellnév, tudomásulvétel).

## v2.22.5
Feliratkészítés képernyő: a körgyűrű megszűnt (marad a Teljes folyamat csík); a hullám alatt kiírja, melyik hangszakaszt dolgozza éppen; a Darabok lista a 4. lépés alá került, a darab-kockák megszűntek; a hullám nem lüktet: kész = fehér, éppen dolgozik = zöld, hátralévő = szürke.

## v2.22.4
Élő fordító: többnyelvű mód (alapértelmezett). Két panel: Eredeti és Fordítás; minden sor előtt színes beszélőjelölés (hangmagasság alapján, max. 6 beszélő) és a felismert nyelv; nyelvváltásnál "másik nyelven szól" jelzés; alul célnyelv-választó (50 nyelv). Felismerés: Whisper automatikus nyelvfelismeréssel (99 nyelv), fordítás ML Kit offline-nal vagy a beállított online modellel. Az élő Whisper már nem dobja el a gyors egymásutáni szakaszokat (3 szakaszos sor).
Új tiszta modulok tesztekkel: LiveLine/LiveLog, LangCatalog, SpeakerTracker. Verzió: 2224 / 2.22.4.

## v2.22.3
Javítások: TextPolish, Vad, Anim, CheckView újraírva; törött teszt javítva; MainActivity manifest-összeomlás; WhisperPrompt nyelvhű; OLED a Compose témában; :core valódi modul; duplikátumok és elavult fájlok törölve.
Újdonságok: Compose kezdőlap animációkkal; egy érintéses felirat-készítés; TranslationCheck (szám/név/tagadás); feladat-folyamatsáv a kezdőlapon.
Verzió: versionCode 2223 / 2.22.3 (app + wear).

## v2.22.2 és korábbiak
Lásd az előző csomagok README-it (a v2.22.2 nem fordult le, ezért nem javasolt).


## 2.28.6 – szókonfidencia és célzott ellenőrzés
- A meglévő tokenenkénti valószínűség- és időbélyeg-JNI-hozzáférést használja; nem hoz létre párhuzamos tokenmodult.
- Narancssárga aláhúzás az SRT-szerkesztőben a gyenge szavaknál; a sor lejátszógombjával célzottan meghallgatható a hozzá tartozó hangszakasz.
- A Whisper JNI szó-szintű konfidenciát ad vissza a tokenek valószínűségeiből képzett geometriai átlaggal; ez csak ellenőrzési jelzés, nem kalibrált helyességi valószínűség.
- Az SRT-szerkesztő a gyenge szókonfidenciájú forrásszavakat narancssárga aláhúzással jelzi; a sor lejátszás gombjával a kapcsolódó hangszakasz célzottan meghallgatható.
- A sorok minőségjelzése egy helyen használja a TokenQuality küszöbét.
- A magas Whisper no-speech érték önmagában nem töröl sort; a szerkesztő ellenőrzési jelzést mutat.
- A szószintű pontszámok csak akkor maradnak meg a szövegjavítás után, ha a forrásszavak száma változatlan, hogy elkerüljük a félrevezető kiemelést.
- A forrás és a pontszámok nem írják át automatikusan a felismert szöveget.
- Repeated segment suppression now requires low log-probability for all three consecutive duplicates.

## v2.28.8-dev – Review intelligence alapok
- Új offline `ReviewIntelligence` motor: kritikus tagadás-, szám-, dátum-, confidence-, töredék-, ismétlődés- és üres sor jelzések.
- Célzott ellenőrzési terv sorszám- és időkerettel; nem indít automatikusan új felismerést.
- SHA-256 fingerprint az ismételt feldolgozás elkerülésének támogatására.
- Auditnapló- és benchmark-adatmodellek a visszavonható javításokhoz és mérhető összehasonlításhoz.
- Hozzáadott egységtesztek és teljes megvalósítási/státusz dokumentum.
- Ez fejlesztési alap, nem teljesen integrált kiadás: az Android UI/pipeline összekötése és a teljes APK-build még igazolandó.

## v2.30.0 – Gyémánt Edition Update 6
- Added deterministic pre-export SRT quality checks for invalid timestamps, empty source/translation fields, out-of-order cues, and overlapping cues.
- Validation reports distinguish blocking errors from warnings and informational notes; checks never mutate subtitle text.
- Added focused unit tests for invalid durations, empty translations, overlaps, and valid cues.

## v2.30.0 Gyémánt Edition – Update 8
- Az export előtti felirat-minőségellenőrzés most már a Markdown, TXT, DOCX és PDF exportokra is kiterjed.
- Az ellenőrzés figyelmeztetéseket mutat, és nem javítja át automatikusan a feliratokat.
- A minőségkapu közös segédfüggvényt használ a dokumentumexportoknál.


## v2.30.0 – Gyémánt Edition Update 9
- Az export előtti minőségellenőrző párbeszédablak új „Első érintett sor” műveletet kapott.
- A művelet a jelentés első problémás feliratsorához görget; a szöveget és az időbélyegeket nem módosítja automatikusan.
- Az exportot a felhasználó külön folytathatja vagy megszakíthatja.

## v2.30.0 – Gyémánt Edition Update 10
- A minőségellenőrzési ablak teljes hibajegyzéket nyit, nem csak az első nyolc jelzést mutatja.
- A jegyzék bármely elemére koppintva a megfelelő feliratsorhoz ugrik.
- Ha az aktív keresési szűrő elrejti a hibás sort, a nézet törli a szűrőt, majd a célzott sorra navigál.
- A hibajegyzékből történő navigáció nem módosítja a felirat tartalmát.


## v2.30.0 – Update 11 (2026-10-28)
- Új platformfüggetlen terminológia-governance API globális/projekt hatókörrel, jóváhagyási állapotokkal, konfliktusvizsgálattal, hatás-előnézettel és TSV import/exporttal.
- Új unit tesztek a szótárkezelés kritikus szabályaihoz.
- Új Platina kiemelőszín-téma.
- Új `PREMIUM_ROADMAP_HU.md` integrációs és kiadási specifikáció.
- A teljes Android-build és a UI/pipeline-integráció nincs igazolva; lásd `UPDATE11_HU.md`.

## Update 12 – Premium glossary integration
- Connected the governed glossary API to Android preferences via `GlossaryStore`.
- Added a Diamond Glossary Center entry point to Settings, including duplicate-key validation and activation into the existing translation vocabulary pipeline.
- Preserved the legacy `source=target` vocabulary format and documented the limits of this integration.
- Kotlin glossary smoke tests passed. Full Gradle/Android build remains unverified because Gradle distribution download was blocked by network access.

## Update 13 – Premium safe retranslation
- Added `RetranslationPlanner`: explicit preview, manual-translation protection, selected-only apply, revision guard and diff output.
- Added JUnit tests for safe retranslation planning.

## Platinum Ultra Extreme UI v13
- Platinum Spectrum: 32 oszlopos, valós állapothoz kötött vizualizáció.
- Pulzáló aktív állapotjelző, animált progresszió és képernyőolvasó-leírás.


## Platinum Ultra Extreme UI v14 – javított teljes csomag
- A v13 teljes forrásfája alapján újracsomagolt, integritásellenőrzött kiadás.
- A kezelőfelület verziójelzése V14-re frissítve.
- A Platinum Spectrum vizualizáció és a korábbi alkalmazásfunkciók megőrizve.
- A csomagolás ellenőrizhető; az Android/Gradle build külön környezeti függőség, sikeressége csak tényleges fordítással igazolható.


## Platinum Ultra Extreme UI v15 – Infinity Edition
- Frissített Platinum Infinity fejléc, állapotközpont-cím és lábléc.
- 40 csatornás, magasabb és sűrűbb Platinum Spectrum vizualizáció.
- A meglévő, tényleges hangszint- és feldolgozási adatokhoz kötött működés megőrizve.
- Teljes forráscsomag újracsomagolva; Android build sikeressége nincs igazolva.


## 2.46.0 – Platinum Ultra Extreme v16
- A Platinum Ultra Console megjelenített mérési és feladatértékei tartománykorlátozást kaptak.
- V16 felületi verziójelzés és 2.46.0 alkalmazásverzió.
- A buildkörnyezet hálózati korlátozása miatt az APK összeállítása nem volt igazolható.

## 2.47.0 / V17 – Infinity Luxury Plus
- A letöltési folyamat százalékértéke minden UI-felületen egységesen 0–100 közé korlátozott.
- Az ismeretlen haladás továbbra is százalék nélküli animált jelzést használ.
- Új egységtesztek a haladássáv százalékos határértékeihez.

## v2.62.0 – Automatic Diamond Backup
- Napi és heti WorkManager mentés, Android Keystore-alapú AES-256-GCM titkosítással.
- Ütemezésvezérlés és állapotjelzés a Backup Centerben; 14 fájlos automatikus rotáció.

## v2.63.0 – Learning Analytics Diamond
- Napi/heti tanulási aktivitás, 7 napos trend és 30 napos összesítések.
- Napi célkövetés, pontossági arány és helyi, adatminimalizált számlálók.
- Szókincstár és intelligens ismétlő eseményeinek integrációja.
