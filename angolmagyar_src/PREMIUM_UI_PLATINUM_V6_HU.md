# AngolMagyar 2.45.0 – Platinum Luxury v6 (interaktív folyamatábra)

**Állapot:** forrásszintű módosítás, lefordítva és eszközön kipróbálva NEM lett (nincs Kotlin fordító / Gradle a környezetben). Első lépés: `./gradlew :app:assembleDebug`.

A képernyőképeken kért irány (platina–fekete–pezsgőarany, többrétegű üveghatás, összetett összekapcsolt folyamatábra animált vonalakkal és adatáramlással, interaktív csomópontok, dinamikus grafikon, mikrointerakciók) ebben a körben a feldolgozó kártyára került.

## Újdonságok
| Elem | Fájl | Leírás |
|---|---|---|
| **Összekapcsolt folyamatábra** | `PlatinumFlowGraph.kt` | ívelt (Bézier) összekötők zegzugos elrendezésben; a kitöltés a valós haladás; a futó szakaszon pezsgőarany **adatáramlás-fénypontok** futnak az éren; fordításnál a Felismerés után külön **Fordítás** csomópont jelenik meg (lefordított sorok számával) |
| **Interaktív csomópontok** | `PlatinumFlowGraph.kt`, `PlatinumPanel.kt` | érintésre a csomópont kijelölődik (arany gyűrű, haptikus visszajelzés), alatta üvegkártyán részletek: állapot, idő, haladás, szegmens, megjegyzés, fordítómotor; újabb érintés bezárja |
| **Üveg-felület** | `PlatinumGlass.kt` | áttetsző fehér réteg, ferde fénykiemelés, átmenetes perem |
| **Mikrointerakció** | `PlatinumGlass.kt` (`pressScale`) | az üvegkártya érintésre finoman összenyomódik, a görgetést nem zavarja |
| **Dinamikus grafikon** | `PlatinumSparkline.kt` | a teljes haladás időbeli menete; a mintákat a panel a valós %-értékekből gyűjti, új feldolgozásnál nullázódik |

## Őszinteség-elv
- Az adatáramlás-pontok csak akkor mozognak, ha a forráslépés ténylegesen fut.
- A Fordítás csomópont nem külön mért lépés: a Felismerés + fordítás egyetlen `SrtJob`-lépés, ezért a csomópont a felismerés állapotát és haladását követi, a sorok száma viszont valós (`engOnline + engOffline`).
- A grafikon üres, amíg nincs legalább két valós minta.
- Csökkentett animáció módban a pontok, csillanások és a nyomás-animáció kimarad.

## Hátravan (a képernyőképek alapján)
- Platina téma a **kezdőképernyőre** (prémium fejléc, hangforrás-választó, utolsó feldolgozások), a **modellkezelőre** (telepített modellek, méret, letöltési sebesség és %), az átiratokra, diagnosztikára, élő fordításra.
- Élő mikrofon-hullám (32–64 oszlop, valós RMS, mikrofon / belső hang megkülönböztetése) – az `AudioRecorder` hangerő-kimenetét ki kell vezetni.
- Szünet/folytatás a feldolgozásban, ha a motor támogatja.
- Build, eszközteszt, képkocka-idő és akku mérése; unit/snapshot tesztek (Robolectric kell a `SrtJob` miatt).
- Opcionális: 3D hatású (perspektivikus) rétegek, parallax.
