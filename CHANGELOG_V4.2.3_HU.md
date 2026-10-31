# AngolMagyar 4.2.3 – biztonsági ellenőrzés megerősítése

## Változások
- A kiadási biztonsági ellenőrzés pontos, blokk-alapú vizsgálatot végez a release aláírási konfiguráción; a debug kulcs release buildbe kerülését tiltja.
- Ellenőrzi a részleges release-keystore konfiguráció fail-closed kezelését.
- Android-manifeszt ellenőrzések: `allowBackup=false`, globális cleartext forgalom tiltásának megőrzése, engedélyezett exported komponenskészlet, `FileProvider` nem exportált állapota és a Quick Settings Tile kötési jogosultsága.
- Ellenőrzi, hogy a fájlmegosztási belépési pont csak a deklarált megosztási műveletekkel legyen elérhető.
- A `.gitignore` kulcsfájl-védelmi ellenőrzése PKCS#12 fájlokra is kiterjed.

## Fontos korlát
Ez a kiadás a meglévő funkciókat nem bővíti új AI-szolgáltatóval, kamerás OCR-rel vagy felhőszinkronnal. A biztonsági script statikus guardrail; nem helyettesít dinamikus tesztet, függőségvizsgálatot vagy penetrációs tesztet.
