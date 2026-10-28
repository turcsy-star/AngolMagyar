# Export előtti minőségellenőrzés – Gyémánt Edition

Az új `SrtQualityCheck` a felirat exportálása előtt futtatható, tisztán ellenőrző komponens. Nem módosítja a feliratokat.

- **Hiba (ERROR):** negatív kezdési idő, nem pozitív időtartam vagy nem időrendi bejegyzés. Ezek blokkolhatják az exportot.
- **Figyelmeztetés (WARNING):** üres fordítás, mindkét mező üres, illetve időbeli átfedés. Ezek nem tiltják automatikusan az exportot, mert lehetnek szándékosak.
- **Információ (INFO):** hiányzó forrásszöveg `BOTH` módban, ha van fordítás.

Példa:

```kotlin
val report = SrtQualityCheck.inspect(cues, SrtExporter.Mode.BOTH)
if (report.canExport) {
    // Az export engedélyezhető; a figyelmeztetéseket ettől még érdemes megmutatni.
}
```

A jelenlegi frissítés az ellenőrző komponenst és a teszteket adja hozzá. A teljes Android UI-ba történő bekötés és az eszközteszt külön következő lépés. A Gradle-tesztfuttatás ebben a környezetben nem indult el, mert a Gradle 8.9 disztribúció nem tölthető le hálózati korlátozás miatt.
