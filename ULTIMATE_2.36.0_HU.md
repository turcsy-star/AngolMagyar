# AngolMagyar – Silver & Gold Ultimate 2.36.0

## Főbb változások

- Új, kezdőképernyőről elérhető **Fordítási vezérlőpult**: élő fordítási célnyelv-választás és fordítási mód (offline, online, online + offline tartalék).
- Az online mód beállítás hiányában az online szolgáltatás beállító párbeszédablakát nyitja meg; az online fordítás továbbra is a meglévő adatküldési hozzájáruláshoz kötött.
- Az elérhető célnyelvek között nem ajánlja fel a jelenleg beállított forrásnyelvet, és jelzi a forrásnyelvet.
- Új **Akkukímélő animációk** kapcsoló a megjelenés panelen. Bekapcsolva megszünteti a folyamatos aurora-háttérmozgást, a hero-kártya fénycsíkját és az állapotkártyák lebegő animációját.
- Android rendszer akkukímélő módjában ugyanezek a folyamatos dekorációs animációk automatikusan visszafogottak.
- A korábbi Silver & Gold / Platina / Gyémánt témák, az animált lépcsőzetes belépés és a háttérben futó feladatok kezelése megmarad.

## Fordítási és akkumulátor-korlátok

- Az offline fordítás csak akkor érhető el teljes értékűen, ha a szükséges nyelvi modell telepítve van; az első modellletöltéshez internet kell.
- Az online fordítás API-szolgáltatót és felhasználói hozzájárulást igényel. A felismert szöveg elhagyhatja a készüléket; a hangadatok kezelését a kiválasztott felismerési útvonal külön szabályozza.
- Az akkukímélő kapcsoló a felület folyamatos dekorációs animációit csökkenti. Nem kapcsolja le a felhasználó által elindított felvételt vagy fordítási feladatot.
- A Kotlin/Android buildet nem lehetett ellenőrizni, mert a Gradle disztribúció letöltése hálózati DNS-hiba miatt meghiúsult. Eszközön végzett futtatási teszt még szükséges.
