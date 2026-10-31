# PLATINUM INFINITY AI MASTER – v2.60.0

## Elkészült: Infinity Backup Center

A felhasználó által kiválasztott JSON-fájlba exportálható, illetve onnan visszaállítható:
- saját szótár (`dictionary_entries`),
- fordítási memória (`translation_memory`),
- adaptív ismétlési ütemezés (`review_schedule`).

### Biztonsági és adatvédelmi működés
- A fájlkezelő helyválasztása Android SAF-en keresztül történik.
- A mentés helyben készül; a funkció nem küld adatot hálózaton keresztül.
- A visszaállítás csak támogatott formátum- és verzióazonosítóval indulhat.
- A JSON méretkorlátja 20 MB, a rekordszám táblánként legfeljebb 100 000.
- A három adattábla visszaállítása egy Room-tranzakcióban történik.
- Hibás mentés esetén a tranzakció visszagörgeti a módosításokat.

### Nem része a mentésnek
Médiafájlok, letöltött beszédfelismerő-modellek, alkalmazásbeállítások, titkos kulcsok és külső szolgáltatói hitelesítő adatok.

### Következő lépések
- Titkosított backup opció felhasználói jelszóval.
- Automatikus, ütemezett helyi mentés.
- Visszaállítás előtti tartalmi előnézet és összevonási mód.
- Tanulási dashboard a Room-adatokból számolt mutatókkal.
