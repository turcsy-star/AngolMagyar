# AngolMagyar 4.2.4 – Premium Intelligence

## Smart Context Translation finomítás
- Néhány gyakori kétértelmű szóhoz lokális, determinisztikus kontextusbecslés készült.
- A `bank`, `light` és `charge` lehetséges jelentéseit közeli kulcsszavak alapján szűkíti.
- Ha a kontextus nem elégséges, az alternatívák megmaradnak, és a rendszer kifejezetten jelzi a bizonytalanságot.
- A `bank` pénzügyi megfelelője most természetes magyar „pénzintézet”.
- Új regressziós tesztek fedik a pénzügyi, földrajzi, műszaki és bizonytalan eseteket.

## Korlát
Ez offline, szabályalapú heurisztika, nem generatív AI és nem garantálja a helyes jelentést. A végső fordítást a felhasználónak kell ellenőriznie.
