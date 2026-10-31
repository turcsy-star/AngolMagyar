# AngolMagyar Premium Intelligence 5.0 – teljes fejlesztési specifikáció

## Termékcél

Az AngolMagyar egyetlen koherens, kétirányú angol–magyar nyelvi munkakörnyezetté váljon: fordítás, beszéd, tanulás, felirat, dokumentum és minőségbiztosítás közös projekttel, terminológiával, előzményekkel és adatvédelmi vezérléssel.

A v5.0 célverzió nem tekinthető késznek pusztán attól, hogy egy API vagy képernyő létezik. Minden képességnek végig kell érnie a teljes felhasználói útvonalon: felület → domain logika → tárolás → hibaállapot → teszt → dokumentáció.

## 1. Infinity Context AI – kontextusérzékeny fordítás

### Elvárt működés
- A fordítás kapja meg az előző és következő mondatot, a beszélőazonosítót, a projekt témáját és a projekthez tartozó jóváhagyott terminológiát.
- Hangnemek: természetes, szó szerinti, hivatalos, társalgási, üzleti, műszaki és feliratbarát.
- Legfeljebb három alternatíva jelenjen meg, rövid indoklással és egyértelmű eredetjelöléssel.
- A bizonytalanság legyen külön jelölve; heurisztikus jelzés nem nevezhető AI-bizonyosságnak.
- Kézzel jóváhagyott fordítás automatikusan nem írható felül.

### Integráció
A meglévő `SmartContextTranslation`, `OnlineTranslator`, `TranslationStyle`, `TranslationMemory` és `GlossaryStore` közös ContextRequest/TranslationCandidate adatfolyamot használjon. Az offline heurisztika és a külső generatív szolgáltató külön motor maradjon; hálózat nélküli módban a generatív opció ne jelenjen meg elérhetőként.

## 2. Translation Quality Intelligence

- Külön ellenőrzés: számok, dátumok, nevek, tagadás, hiányzó/duplikált sorok, terminológia, nyelvtani és írásjelezési hibák.
- Súlyossági szintek: információ, figyelmeztetés, blokkoló hiba.
- Soronkénti eredet: szabályalapú, ASR-konfidencia, referenciaadat vagy külső modell.
- Mért WER/CER csak kézzel ellenőrzött referenciaátirattal számolható.
- A becsült minőség, szabálytalálat és tényleges pontosság külön kijelzésű.
- Egyérintéses hangrészlet-visszajátszás és célzott újrafelismerés.

## 3. Voice Intelligence Studio

- Élő és fájlból történő angol–magyar beszédmód.
- US/UK TTS-hangválasztás, a telepített Android hangadatok ellenőrzésével.
- Mondatonkénti gyakorlás, rögzítés, visszajátszás és lassítás.
- Kiejtési értékelés csak fonetikai/ASR-alapú, referenciahanggal validált metrikából készülhet.
- Beszédtempó, szünet, érthetőség és szövegfelismerési egyezés külön mérőszám.
- Beszélőnkénti szakaszolás és feliratkapcsolat.

## 4. Personal Learning Intelligence

- A meglévő SM-2 ismétlés és tanulási analitika közös tanulási profilt használjon.
- Gyenge szavak és hibásan fordított kifejezések automatikus gyakorlólistája.
- Aktív és passzív szókincs külön mérése.
- Napi/ heti / havi célok, témakörök, tanulási sorozat és teljesítményjelvények.
- A felhasználó módosíthassa az ütemezést; a rendszer ne büntesse a kihagyott napokat.
- Minden statisztika legyen visszavezethető tényleges tanulási eseményre.

## 5. Subtitle Studio Pro

- Eredeti és fordított felirat párhuzamos nézete.
- Hanghullám, idővonal, soronkénti hang-visszajátszás.
- SRT/VTT/ASS/TXT import/export, hibás fájlok biztonságos visszautasítása.
- Szó- és mondatszintű időzítés, olvasási sebesség, átfedés, sortörés és üres blokkok ellenőrzése.
- Visszavonás/ismétlés, automatikus mentés, verzió-összehasonlítás.
- Hosszú feldolgozás checkpointtal és megszakítás utáni folytatással.

## 6. Offline AI & Privacy Vault

- Offline/online állapot egyértelmű jelzése minden feldolgozásnál.
- API-kulcs kizárólag Android Keystore AES-GCM titkosítással; titkosítási hiba esetén nincs plaintext fallback.
- Titkosított, jelszóval védett biztonsági mentés; import előtt integritás- és méretellenőrzés.
- Biometrikus alkalmazászár opcionális, helyreállítási útmutatóval.
- Ideiglenes hang- és szövegfájlok megőrzési ideje állítható.
- Külső AI szolgáltató használata előtt egyértelmű adatküldési hozzájárulás.
- Adat-export, projekt- és előzménytörlés, naplóanonimizálás.

## 7. Smart Translation Memory

- Globális és projektszintű terminológia.
- Állapotok: PENDING, APPROVED, DISMISSED, RETIRED.
- Jóváhagyott projektterminus elsőbbséget kap a globális javaslattal szemben.
- Ütközés, duplikáció és eltérő fordítás észlelése.
- Import előnézet, hibás sorok listája, TSV/CSV export.
- Szótármódosítás előtti hatásvizsgálat; pontos egyezésű érintett sorok listája.
- Újrafordítás csak explicit jóváhagyással; kézzel jóváhagyott fordítás védett.

## 8. Diamond Experience

- Forest Graphite, Platinum, Obsidian, Gold, Arctic és Sapphire paletták.
- Világos, sötét és OLED sötét mód.
- Egységes komponens-tokenek, tipográfia, térköz, kártyák és állapotjelzések.
- Kompakt/normál/kényelmes sűrűség; nagy betűméret és akadálymentes kontraszt.
- Csökkentett mozgás, animációk visszafogása energiatakarékos módban.
- Kezdőlap: új fordítás, élő tolmács, feliratprojekt, tanulás, legutóbbi projektek és folyamatban lévő feladatok.
- Klasszikus View képernyők fokozatos átemelése a közös design rendszerbe.

## 9. Kiegészítő prémium funkciók

### AI Conversation Partner
Beszédgyakorló mód szerepjátékokkal, szinthez igazított kérdésekkel, javítási magyarázattal és beszédértékeléssel. Generatív párbeszédhez szolgáltatói kulcs és internet szükséges; offline változat csak előre definiált gyakorlatokat használhat.

### Live Interpreter
Kétirányú élő beszélgetés, beszélőváltás, push-to-talk, felirat és TTS. A mikrofonengedélyt csak a felhasználó által indított munkamenet alatt kérheti; háttérben nem rögzíthet rejtetten.

### Smart Camera Translate
Kameraképből OCR, kiválasztott szöveg fordítása, eredeti/fordított szöveg és adatvédelmi figyelmeztetés. Külön CameraX és ML Kit Text Recognition integráció, kameraengedély és valós készülékes teszt szükséges.

### Document Intelligence
PDF/DOCX/TXT import, szerkezetmegőrző fordítás, táblázat- és bekezdéskezelés, export és dokumentum-összehasonlítás. A beolvasott PDF OCR-t igényelhet; ezt külön kell jelezni.

### Meeting Assistant
Megbeszélés-átirat, beszélőcímkék, fordítás, döntések és teendők összefoglalója. A meglévő MeetingMode/MeetingSummary alapokra épüljön; összefoglaló generálása előtt a forrás és az adatküldés legyen látható.

### Cloud Sync
Végponttól végpontig titkosított szinkron, konfliktusfeloldás, eszközlista és visszavonható hozzáférés. Szolgáltató, autentikáció, adatkezelési feltételek és backend még nincs rögzítve; ezek nélkül felhőszinkron nem aktiválható és nem állítható késznek.

### Smart Export
DOCX/SRT/VTT/ASS/TXT export, fájlnevek és metaadatok védelme, sablonok, export előnézet és hibajelentés.

## 10. Adat- és hibakezelési alapelvek

- A forrás, automatikus átirat, kézi javítás és végleges fordítás külön változat.
- Minden módosítás visszavezethető legyen forrásra, időpontra, motorra és felhasználói jóváhagyásra.
- Sikertelen hálózati kérés ne törölje a forrásszöveget.
- Hosszú feladatnál megszakítás és folytatás; duplikált feladat ne fusson véletlenül kétszer.
- Külső adatküldés előtt világos jelzés; titkok soha nem kerülhetnek naplóba.

## 11. v5.0 kiadási kapu

A v5.0 csak akkor nevezhető teljes kiadásnak, ha:
1. Android és Wear build sikeres.
2. Minden unit és instrumented teszt sikeres.
3. Valós, engedéllyel használható referenciahangok WER/CER benchmarkja dokumentált.
4. Kamera, dokumentum, élő beszéd, felirat és tanulás fizikai eszközön tesztelt.
5. Offline mód hálózat nélkül is ellenőrzött.
6. Titkosítás, mentés-visszaállítás, export és adat-törlés tesztelt.
7. Accessibility, világos/sötét/OLED és kis/nagy kijelző screenshot ellenőrzés megtörtént.
8. Saját release-keystore-ral aláírt APK/AAB készült, és a frissítési útvonal ellenőrzött.
