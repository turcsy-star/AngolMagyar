# Gyémánt Edition – Update 8

## Változás
Az export előtti minőségellenőrzés a korábban támogatott SRT és WebVTT mellett most a Markdown, TXT, DOCX és PDF export indítása előtt is lefut.

## Működés
- A feliratokat az export aktuális pillanatképéből ellenőrzi.
- A problémákat jelzi, de nem javítja automatikusan a szöveget vagy az időbélyegeket.
- A felhasználó dönthet a folytatásról vagy a megszakításról a meglévő ellenőrző párbeszédben.

## Ellenőrzés
A módosított forráskód bekerült a projektbe. Ebben a körben a teljes Android Gradle-buildet és az eszközön végzett exporttesztet nem futtattuk, ezért a működés teljes körű igazolása még hátravan.
