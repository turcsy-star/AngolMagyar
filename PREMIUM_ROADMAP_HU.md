# AngolMagyar Gyémánt Edition – prémium fejlesztési specifikáció

Ez a dokumentum a teljes javaslatcsomag célállapotát és az integrációs sorrendet rögzíti. A tervben szereplő funkciók nem mind kész funkciók; a státuszt az alábbi jelölések különítik el.

- **Elkészült ebben a csomagban:** konkrét forráskód vagy teszt került be.
- **Meglévő alap / ellenőrizendő:** a projektben már van kapcsolódó komponens, de teljes körű működése nincs ebben a csomagban igazolva.
- **Következő integráció:** szükséges a teljes felhasználói munkafolyamathoz.

## 1. Diamond Signature UI

Cél: elegáns, csendes luxus; nem túlzsúfolt, gyors és akadálymentes felület.

- Grafit, OLED-fekete, világos, Gyémánt és Platina színpaletta.
- Egységes tipográfiai skála, ikonméret, lekerekítés, térköz és komponensállapot.
- Kártyák és fő műveletek egyértelmű hierarchiája; a csillogás és animáció nem vonhatja el a figyelmet.
- Kompakt / normál / kényelmes sűrűség, dinamikus betűméret és megfelelő kontraszt.
- Animációk visszafogása rendszerbeállítás szerint, csökkentett mozgás lehetősége.
- Kezdőlap: új feladat, folytatás, legutóbbi projektek, mentett sablonok és feldolgozási sor.

**Státusz:** a meglévő témarendszer bővült Platina palettával; teljes képernyőszintű design-system átalakítás és vizuális regressziós teszt még szükséges.

## 2. Pontos átirat és biztonságos minőségellenőrzés

- A forrás-átirat, a kézzel javított átirat és a fordítás külön változatként maradjon meg.
- Jelölje a bizonytalan szavakat, számokat, dátumokat, neveket, tagadásokat, hiányzó vagy ismétlődő szakaszokat.
- A jelzés prioritást mutasson, ne állítsa bizonyíték nélkül, hogy egy adott szó hibás.
- Egyérintéses hangrészlet-visszajátszás és célzott újrafelismerés.
- Referenciahangon és kézzel ellenőrzött referenciaátiraton alapuló benchmark; WER és feldolgozási idő külön mérve.
- Ne jelenjen meg tényleges felismerési pontosságként pusztán heurisztikából számított pontszám.

**Státusz:** meglévő ellenőrzési és célzott felülvizsgálati alapok találhatók a projektben; teljes pipeline-integrációt és készülékes ellenőrzést még el kell végezni.

## 3. Kontextusérzékeny fordítás

- Előző/következő mondat, beszélő, projekt és szakterület kontextusának figyelembevétele.
- Stílusprofilok: természetes, szó szerinti, üzleti, műszaki, társalgási és feliratbarát.
- Szám-, dátum-, tagadás- és entitáskonzisztencia ellenőrzése.
- A gépi fordítás, szótárszabály és kézi jóváhagyás eredetének látható jelölése.
- Az automatikus javítás ne írja felül csendben a kézzel jóváhagyott szöveget.

**Státusz:** meglévő fordítási memória és fordításellenőrző komponensek; egységes eredet- és verziókezelés integrálandó.

## 4. Szótár és terminológia

- Globális és projektszintű szótár.
- PENDING / APPROVED / DISMISSED / RETIRED állapotok.
- Konfliktuskeresés, duplikátum- és eltérő fordításjelzés.
- Projekt-szótár elsőbbsége a globális szótárral szemben, csak jóváhagyott bejegyzés esetén.
- Példamondat, megjegyzés, forrás és módosítási előzmény.
- TSV import/export, hibás sorok jelentése, import előnézete.
- Változatmentés, visszaállítás és archiválás végleges törlés helyett.
- Tanulási javaslatok; globális automatikus elfogadás csak explicit beállítással és világos visszavonási lehetőséggel.

**Státusz:** ebben a csomagban elkészült a `GlossaryGovernance` API és tesztjei; a meglévő tanuló és javítólistákhoz, tároláshoz és UI-hoz integrálni kell.

## 5. Újrafordítás és hatásvizsgálat

- Szótárváltoztatás előtt mutassa meg az érintett projektek, mondatok és feliratblokkok számát.
- Pontos kifejezés-egyezésből induljon ki; fuzzy találat csak külön, felülvizsgálható javaslat legyen.
- Mutassa egymás mellett az előző és az új fordítást, eltérésekkel.
- Műveletek: elfogadás, elutasítás, csak a kiválasztott sor, kijelölt sorok vagy projekt újrafeldolgozása.
- A kézzel jóváhagyott szöveg megőrzése alapértelmezés legyen.
- Minden művelethez visszavonás, naplóbejegyzés és forrás-/modell-/szótárverzió.

**Státusz:** a csomagban a terminusok pontos találatainak hatás-előnézete elkészült külön API-ként; a fordítási pipeline-hoz kötés még szükséges.

## 6. Szerkesztő és feliratstúdió

- Eredeti szöveg és fordítás egymás mellett; szinkronizált időbélyeg és hanghullám.
- Mondatra vagy sorra koppintva lejátszás a megfelelő időponttól.
- Visszavonás/ismétlés, keresés, többszörös kijelölés és biztonságos automatikus mentés.
- SRT / VTT / TXT export, sortörés- és olvashatósági ellenőrzés.
- Felirat-hossz, olvasási sebesség, üres blokk, átfedő időbélyeg és sorrend ellenőrzése.
- Eltérő verziók vizuális összehasonlítása.

**Státusz:** meglévő felirat- és átiratszerkesztők vannak; teljes szinkronizálás és UX-ellenőrzés készülékes próbát igényel.

## 7. Megbízható feldolgozás és adatvédelem

- Hosszú feladatok folytathatósága, checkpoint, feladat-sor, megszakítás utáni helyreállítás.
- Valós, mért feldolgozási idő és egyértelmű hibaállapotok.
- Helyi és felhőfeldolgozás világos megkülönböztetése.
- API-kulcsok biztonságos tárolása; titkok nem kerülhetnek naplóba vagy exportba.
- Projektadatok, átiratok és naplók törlésének/megőrzésének érthető szabályai.
- Importált fájlok és mentések validálása, sérült fájl esetén biztonságos visszautasítás.

**Státusz:** meglévő kapcsolódó komponensek és korábbi ellenőrzőlisták vannak; kiadás előtti biztonsági és eszköztesztek szükségesek.

## 8. Javasolt megvalósítási sorrend

1. Reprodukálható Android build és teljes tesztfuttatás.
2. Szótár-adatmodell, jóváhagyási folyamat, migráció és visszaállítás.
3. Szótár-hatásvizsgálat és célzott újrafordítás a pipeline-ban.
4. Forrás/fordítás változatkezelése, auditnapló és összehasonlító nézet.
5. Hangrészlet-visszajátszás és bizonytalan szakaszok ellenőrzése.
6. Minőségmérés referenciaadatokon; a pontossági állítások validálása.
7. Egységes Diamond / Platinum felület, akadálymentesség és vizuális regressziós ellenőrzés.
8. Hosszú fájlok, megszakítás, offline mód, export és adatvédelmi tesztek valódi Android-eszközön.

## Kiadási kapu

A csomag csak akkor nevezhető teljesen kész Android-kiadásnak, ha a Gradle build, a JUnit tesztek, az APK összeállítása, az alap munkafolyamat és a valós készülékes tesztek sikeresek. A jelen csomag forráskódot tartalmaz, nem igazoltan kész APK-t.
