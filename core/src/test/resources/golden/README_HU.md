# Arany-referencia WER-teszt

Minden mintához három fájl tartozik ebben a mappában:

- `ID.ref.txt` – kézzel ellenőrzött referenciaátirat (az igazság),
- `ID.hyp.txt` vagy `ID.hyp.srt` – a pipeline kimenete ugyanarra a hangra,
- a `manifest.csv` sora: `ID,maxWer` (pl. `ugyfel_01,0.08`).

A `GoldenWerTest` (core) minden CI-futáskor kiszámolja a szó-hibaarányt, és elbukik, ha valamelyik minta a határ fölé romlik.

**Új minta hozzáadása (Termux/PC):**
1. Vegyél fel egy rövid (30–120 mp) hangrészletet, gépeld be pontosan a szövegét → `ID.ref.txt`.
2. Futtasd az alkalmazást a hangon, mentsd az SRT-t → `ID.hyp.srt`.
3. Mérd meg: `python3 tools/golden_wer.py src/test/resources/golden/ID.ref.txt src/test/resources/golden/ID.hyp.srt`
4. A kapott WER-nél kicsit magasabb értéket írj a `manifest.csv`-be.

Figyelem: a mellékelt három minta szintetikus (a tesztkeretet mutatja be), valódi hangfájlt nem tartalmaz. Valódi mintákat a saját felvételeidből kell hozzáadni; személyes vagy bizalmas hangot ne tegyél a tárolóba.
