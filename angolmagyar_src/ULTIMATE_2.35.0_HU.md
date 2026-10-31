# AngolMagyar – Silver & Gold Ultimate 2.35.0

## Újdonságok
- Az átirat részletező képernyőjének fejlécében megjelenik a heurisztikus audit összesítése: hány sor igényelhet ellenőrzést.
- Ha a nem üres forrás/fordítás sorok között nincs figyelmeztetés, az alkalmazás „Alapellenőrzés kész” állapotot jelez. Ez nem minőségi garancia és nem jelent bizonyított fordítási pontosságot.
- A soronkénti jelzések változatlanul megmaradnak; a rendszer nem írja át automatikusan a fordítást.
- Verzió: `versionCode 3500`, `versionName 2.35.0`.

## Korlátok és ellenőrzés
Az audit szabályalapú heurisztika, ezért hamis pozitív és hamis negatív jelzések előfordulhatnak. Android buildet és eszközön futtatást ebben a környezetben nem sikerült hitelesen lefuttatni; a ZIP ellenőrzése nem helyettesíti a buildet.
