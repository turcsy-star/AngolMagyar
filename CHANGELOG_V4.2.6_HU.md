# AngolMagyar 4.2.6 – forráscsomag-javítási napló

## Megőrzött alapok
- Android alkalmazás `versionName=4.2.6`, `versionCode=40406`.
- Meglévő app/core/Wear modulok, offline ASR/fordítási útvonalak, felirat- és tanulási komponensek.
- Android Keystore AES-GCM titoktárolás és release/debug aláírás szétválasztás.
- Meglévő exported component és FileProvider határok.

## Ebben a mérnöki javítócsomagban
- A belső projektgyökér könyvtárneve `4.2.5` helyett a tényleges `4.2.6` verzióhoz igazítva.
- README és build-státusz dokumentáció verzióeltérései javítva.
- `SmartTranslationMemoryStore`: visszafejtési/Keystore-hiba esetén a titkosított adatblob nem törlődik; a mentés és törlés fail-closed módon megszakad, így nem írhatja felül a megőrzendő adatot üres listával.
- Új fájlszintű Platinum Intelligence 6.0 terv, státusz-, audit- és tesztdokumentáció.
- A kiadás előtti statikus ellenőrzés bővítve a fordítási memória adatmegőrzési invariánsaival.

## Kifejezetten NEM állítjuk késznek
AI társalgópartner, generatív offline AI, élő tolmács újratervezése, kamera OCR, PDF/DOCX import, felhőszinkron, teljes képernyőmigráció, biometrikus zár és fonetikai kiejtésértékelés. Ezekhez külön implementáció, engedélyek/szolgáltatók, tesztek és eszközvalidáció szükséges.
