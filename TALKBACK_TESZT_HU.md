# TalkBack / fókuszsorrend kézi teszt (4.2.10)

Kódszinten kész: címsor-szemantika (`heading()`), legalább 48 dp érintési célok, a hub gombjainak összevont leírása.
Eszközön ellenőrizendő (Beállítások → Kisegítő lehetőségek → TalkBack):

1. Intelligence Suite: a „Következő” gesztus szakaszcímről szakaszcímre ugrik (címsor-navigáció), a kártyák címe → leírása → gombja sorrendben szólal meg.
2. Minden „Megnyitás” gomb hangosan „<kártya címe> – megnyitás” formában hangzik el; a tiltott gomb „hamarosan”.
3. Adatvédelem: a kapcsoló szövege és állapota együtt hangzik; a törlés megerősítő párbeszéd fókusza a párbeszédre kerül, bezárás után a „Minden átirat törlése” gombra tér vissza.
4. Dokumentum / Kamera: fotó után az állapotsor szövege elhangzik; a szerkeszthető mező felirata „Szerkeszthető szöveg”.
5. Betűméret 200 %-on egyik szöveg sem vágódik le, görgetéssel minden elérhető.

Eredmény rögzítése: eszköz, Android-verzió, megfelelt / hibás (+ rövid leírás). Ha minden pont megfelelt, a `InfinityGlobalUi` checklist utolsó sora `true`-ra állítható.
