# AngolMagyar 4.2.5 – Smart Translation Memory

## Újdonságok
- Új, platformfüggetlen `SmartTranslationMemory` motor a `core` modulban.
- Csak jóváhagyott, pontosan egyező szegmens és pontos nyelvpár adhat javaslatot.
- A projektmemória az adott projekten belül elsőbbséget kap a globális memóriával szemben.
- Az azonos hatókörű, eltérő fordítások több jelöltként jelennek meg, `ambiguous=true` jelzéssel.
- A memória soha nem írja át automatikusan a fordítást; a találat mindig felhasználói megerősítést igényel.
- A használatszám csak explicit megerősített használat után növelhető.
- Jóváhagyás és archiválás/törlés helyett visszakereshető visszavonás (`RETIRED`) támogatott.

## Biztonsági és adatvédelmi határok
- A core motor nem végez hálózati műveletet és nem tárol adatot önállóan.
- A perzisztencia, titkosítás, hozzáférés-szabályozás és törlési UI az Android adapter feladata; ez a verzió ezeket nem állítja késznek.
- A projektazonosító szerinti szűrés csak logikai elkülönítés; többfelhasználós környezetben szerveroldali jogosultságellenőrzés is szükséges.

## Verzió
- versionName: 4.2.5
- versionCode: 40405
