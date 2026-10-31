# Platinum Ultra Extreme v21 — Infinity Sovereign Theme Refresh

## Újdonság

A kiválasztott globális Luxury téma mostantól nemcsak mentésre kerül, hanem a fő Compose-felületre való visszatéréskor automatikusan érvényesül. Ugyanez igaz a Platinum téma és az OLED fekete mód beállításaira.

## Működés

- A főképernyő rögzíti az alkalmazott témabeállítások egyszerű aláírását.
- `onResume()` összehasonlítja a mentett beállításokat az utoljára alkalmazott értékekkel.
- Csak valódi változás esetén történik `recreate()`, és az összehasonlítási állapot előre frissül.
- A Blue Cinematic / rendszerfüggő mód továbbra is elérhető a Platinum téma kikapcsolásával.

## Korlátok

Ez a frissítés a Compose Material 3 felületre vonatkozik. A klasszikus Android View képernyők külön színrendszerének teljes egységesítése további munkát igényel. A csomag Android buildje itt nem igazolt; telepíthető APK-nak csak sikeres helyi/CI build után tekinthető.
