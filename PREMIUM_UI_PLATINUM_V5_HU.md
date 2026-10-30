# AngolMagyar 2.45.0 – Platinum Luxury v5

**Állapot:** forrásszintű módosítás. Lefordítani és eszközön kipróbálni nem tudtam (nincs Kotlin fordító / Gradle a környezetben), ezért a build még igazolásra vár: `./gradlew :app:assembleDebug`.

## Új vizuális nyelv
Ónix-fekete alap, platina fémfények, pezsgőarany (champagne) kiemelés, ékkő-színű állapotjelzők. Paletta: `ui/design/PlatinumColors.kt`.

## Részlet-gazdag platina folyamatjelzések
| Elem | Fájl | Mit mutat (valós adatból) |
|---|---|---|
| **Platina műszer** | `PlatinumDial.kt` | fémes kávagyűrű lassan körbejáró fénnyel, 60 gravírozott osztás (a lehető legpontosabb %-hoz kigyulladnak), haladásív pezsgőarany ívéggel és fénylő véggel, **szegmensgyűrű** (egy szegmens = egy feldolgozott darab: kész = jégkék, éppen dolgozó = pulzáló arany, hátralévő = sötét), üveg-visszfény |
| **Ékköves folyamatvonal** | `PlatinumTimeline.kt` | 5 csomópont fémes foglalatban; állapot szerint ékkő: vár (sötét), fut (arany, forgó ív, táguló gyűrű), kész (smaragd, rajzolódó pipa), figyelmeztetés (borostyán), hiba (rubin), megszakítva (acél); bevésett sín milestone-jelekkel; a kitöltött szakaszon fényes csillanás fut végig; csomópont alatt **lépésenkénti idő** vagy futó % |
| **Gravírozott statisztikák** | `PlatinumPanel.kt` | eltelt idő (másodpercenként ketyeg), hátralévő idő + az ETA alapja, szegmens x/y, fájlsor, motor |
| **Platina kártya** | `PlatinumPanel.kt` | átmenetes fémes háttér, fényes peremvonal, felső hajszálfény, lépcsőzetes belépés |
| **Befejezési pecsét** | `CompletionBurst.kt` | pezsgőarany / platina / smaragd szikragyűrű, csak élő „kész” átmenetnél |
| Hiba / figyelmeztetés | `PlatinumPanel.kt` | rubin / borostyán üvegsáv a részletekkel (kihagyott részek, fordítói megjegyzés) |

## Őszinteség-elv (nincs hamis jelzés)
- Ha a % nem ismert, a műszer „—”-t mutat, az osztások nem gyulladnak ki.
- Szegmensgyűrű csak ismert darabszámnál (1–72) rajzolódik.
- Hátralévő idő: „számítás…”, amíg a motor nem ad becslést.
- Az animációk csak díszítenek, a haladást nem találják ki.

## Beállítások
- **Platinum Luxury téma** (alapértelmezés: be; kikapcsolva a Blue Cinematic kártya jelenik meg).
- **Prémium feldolgozó kártya** (kikapcsolva a régi `PipelineView`).
- **Csökkentett animáció** (a rendszer animációkapcsolóját is figyeli): a forgás, csillanás, pulzálás, pecsét kimarad, a színek és jelek megmaradnak.

## Hátravan
- Build, eszközteszt, teljesítmény (képkocka-idő, akku) mérése hosszú feldolgozás alatt.
- Platina téma a többi képernyőre (modellkezelő, átiratok, diagnosztika, beállítások, élő fordítás).
- Élő mikrofon-hullám platina stílusban.
- Unit/snapshot tesztek (Robolectric kell a `SrtJob` miatt).
- Hangeffekt / haptika a befejezéshez (opcionális).
