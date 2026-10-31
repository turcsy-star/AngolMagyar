# AngolMagyar 2.45.0 – Platinum Luxury v7

**Állapot:** forrásszintű módosítás, lefordítva és eszközön kipróbálva NEM lett (nincs Kotlin fordító / Gradle a környezetben). Első lépés: `./gradlew :app:assembleDebug`.

## Újdonságok (a Platinum téma kapcsolója érvényes rájuk; kikapcsolva a régi elemek jelennek meg)
| Képernyő | Elem | Fájl |
|---|---|---|
| **Élő fordítás** | **Platina hangszintmérő**: 48 oszlop, balra gördülő előzmény; forrás-címke (Mikrofon / Belső hang / Push-to-talk), állapot (Figyel / Csend / Szünet / Készenlét), szint % és lassan lecsengő csúcsérték | `PlatinumLevelMeter.kt`, `LiveTranslateActivity` |
| **Modellkezelő** | **Platina haladássáv** letöltéshez: kefélt fém kitöltés, 20 gravírozott osztás, csillanás; ismeretlen haladásnál csak futó fénycsík (százalék nélkül); a meglévő sebesség-, méret-, ETA-felirat alatta látszik | `PlatinumProgressBar.kt`, `ModelsActivity` |

## Őszinteség-elv
- A hangszintmérő oszlopai az `AppState.level` valós, korábbi mintái. Nincs hozzáadott hullámzás; csendben csak vékony pont látszik. A forrás az `AppState.mode`-ból jön.
- A letöltősáv csak a `post(pct, msg)` valós értékét mutatja; `pct < 0` esetén nem mutat százalékot.
- Csökkentett animáció módban a csillanás kimarad, a mérő ritkábban frissül.

## Hátravan
- Platina téma a kezdőképernyőre (`ui/MainActivity.kt`), az átiratokra, diagnosztikára, beállításokra.
- Modellkezelő kártyák (telepített modellek, méret) platina kinézettel.
- Szünet/folytatás a feldolgozásban.
- Build, eszközteszt, képkocka-idő és akku mérése; unit/snapshot tesztek.
