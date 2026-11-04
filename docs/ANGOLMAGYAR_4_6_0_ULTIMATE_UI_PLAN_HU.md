# AngolMagyar 4.6.0 – Ultimate Neo Futuristic UI megvalósítási terv

## Jóváhagyott irány

- Vizuális stílus: **Ultimate**
- Átalakítási kör: **All**
- Animáció: **Balanced**
- Prioritás: **Balanced** (vizuális minőség, teljesítmény és stabilitás együtt)

## Alapelv: meglévő funkciók megőrzése

A munka a `release/4.6.0-futuristic-ui` ágon található meglévő alkalmazást fejleszti tovább. Nem cseréli le a fordítási, felismerési, adatkezelési vagy navigációs architektúrát. A UI réteg a meglévő állapotokat és műveleteket használja; a szolgáltatási réteg működését csak akkor szabad módosítani, ha egy UI-integráció ezt feltétlenül megköveteli.

Megőrzendő területek:
- offline és online fordítás, nyelv- és szolgáltatóválasztás;
- Whisper/Vosk beszédfelismerés, élő mód és fájl-/feliratfeldolgozás;
- modellek letöltése és kezelése;
- fordítási előzmények, memória, szótár, kifejezéstár és export;
- beszélő-azonosítás, projektek, értekezlet-/átiratfolyamatok;
- API-beállítások, adatvédelem, diagnosztika, mentés és Wear OS kapcsolat.

## Vizuális rendszer

### 1. Ultimate Neo alapnyelv
- Sötét, mély grafit alapszínek, rétegzett felületek.
- Üveg-hatású kártyák, finom peremfény és kontrollált árnyék.
- Holografikus akcentusok (cián, ibolya, aurora zöld), kizárólag állapot- és hierarchiajelzésre.
- Egységes lekerekítési, térköz-, ikon-, tipográfiai és gombméret-tokenek.
- Olvasható kontraszt, nagyítható szöveg és akadálymentes érintési célok.
- Világos, sötét és OLED-kompatibilis megjelenés.

### 2. Témakészlet
Az elsődleges kilenc téma:
1. Bronze
2. Silver
3. Gold
4. Premium
5. Ultra
6. Diamond
7. Ultimate Neo
8. Aurora
9. Cyber

A korábbi palettaazonosítók kompatibilitási okból megmaradnak, így a felhasználó mentett témabeállításai nem vesznek el. A témaváltás a Compose- és a hagyományos Android View-képernyőkön is ugyanazt a palettát használja.

### 3. Animáció – Balanced
- 160–320 ms közötti rövid, megszakítható átmenetek.
- Kártya-megjelenés, állapotváltás, gomb-visszajelzés és képernyőváltás.
- AI Pulse csak tényleges feldolgozás alatt.
- Audio Spectrum csak aktív hangrögzítés/felismerés alatt.
- Végtelen háttéranimációk alapértelmezésben tiltva vagy ritkítva.
- Rendszeres animációcsökkentés és energiatakarékos mód tiszteletben tartása.

## Képernyőnkénti integrációs sorrend

### A. Fő fordítófelület
- Nyelvpár és fordítási irány jól látható, kompakt fejlécben.
- Szövegbevitel és eredmény elkülönített, egységes kártyákban.
- Másolás, megosztás, felolvasás és kedvencek az eredménykártya műveleti sorában.
- AI-/offline állapot egyértelmű, de nem tolakodó jelzése.
- Üres, folyamatban, sikeres és hibaállapotok közös komponensekből.

### B. Élő beszéd és hang
- Felvétel/felismerés állapotát jelző AI Pulse.
- Audio Spectrum csak aktív bemenetnél.
- Szüneteltetés, folytatás és leállítás jól elkülönített, nagy célfelületeken.
- A meglévő mikrofonengedély-, audiofókusz-, életciklus- és háttérfolyamat-kezelés változatlan marad.

### C. Fájl- és hosszú feladatfolyamatok
- Folyamatlépések és százalékos előrehaladás vizuális hierarchiája.
- Megszakítás/folytatás és részleges eredmény állapotainak megtartása.
- Nagy fájlok és hosszú feldolgozás közben nem indulhat UI-szálon munka.

### D. Előzmények, memória és szótár
- Kereshető, jól olvasható listák stabil kulcsokkal.
- Üres állapot, szűrés és tömeges műveletek egységes kezelése.
- A meglévő adattárolási formátumok és migrációk változatlanok.

### E. Beállítások és diagnosztika
- Kilenc Ultimate téma választása előnézeti színmintával.
- Balanced animáció és csökkentett mozgás beállítása.
- API-kulcsok, szolgáltatóválasztás, modellek és offline opciók meglévő logikájának megőrzése.
- Diagnosztikai és hibanapló képernyők ugyanazt a komponensrendszert használják.

## Teljesítmény- és stabilitási kapuk

- Animált értékek olvasása lehetőség szerint Draw fázisban (`graphicsLayer` vagy lambda-alapú modifier).
- Görgethető listákban stabil `key` és megfelelő `contentType`.
- Drága számítások és objektumok `remember`-rel vagy képernyőn kívüli rétegben kezelve.
- Nem lehet képkockánkénti teljes képernyős újraépítés.
- Animációk leállítása, amikor a komponens eltűnik vagy leválik.
- Nincs új, folyamatos polling vagy felesleges coroutine a puszta vizuális effektekhez.
- Accessibility: kontraszt, TalkBack-leírások, érintési célméret, betűméret és csökkentett mozgás.

## Ellenőrzési terv

1. Gradle debug build és Kotlin fordítás.
2. Főképernyő megnyitása, navigáció, képernyő-visszatérés.
3. Offline fordítás, online fordítás, szolgáltatóhiba és hálózat nélküli fallback.
4. Élő beszédfelismerés, felvétel, szüneteltetés, leállítás.
5. Fájlimport, hosszú feldolgozás, export és feliratfolyamat.
6. Előzmények, memória, szótár, mentés és visszaállítás.
7. Mind a kilenc téma, világos/sötét/OLED mód és alkalmazás-újraindítás.
8. Gyors görgetés és képernyőváltás: villogás, akadás, ismételt adatbetöltés nélkül.
9. Release build R8 optimalizálással és valós eszközös ellenőrzés.

## Jelenlegi integrációs állapot

- [x] A meglévő design rendszer és képernyőstruktúra felmérése.
- [x] Kilenc Ultimate paletta hozzáadása, régi palettaazonosítók megtartásával.
- [x] Ultimate témaválasztó beépítése a Megjelenés & kényelem képernyőre.
- [x] Balanced mozgásprofil alapértelmezése új telepítésnél.
- [x] Animációcsökkentési kapcsoló és rendszerbeállítás tiszteletben tartása.
- [x] A főképernyő témaválasztója pontosan a kilenc Ultimate palettát mutatja.
- [x] A klasszikus és Compose megjelenési beállítások ugyanazt a kilenc palettát használják.
- [x] A Compose megjelenési beállítások Premium Design System kártyákat használnak; a Balanced animáció profil helyes állapotból indul.
- [ ] Minden további képernyő vizuális auditja és egységesítése.
- [ ] Teljes regressziós teszt és sikeres CI build.
- [ ] APK és teljes forráscsomag ellenőrzött kiadása.

A feladat csak akkor tekinthető késznek, ha a fenti ellenőrzési kapuk sikeresek. A build és a funkcionális tesztek eredményét külön kell jelenteni; puszta kódmódosítás nem bizonyítja a működést.
