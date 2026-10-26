# Changelog

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
