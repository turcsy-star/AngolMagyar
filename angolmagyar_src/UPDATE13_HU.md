# Angol–Magyar Gyémánt Edition – Update 13

## Elkészült: biztonságos újrafordítási tervmotor

- Új `RetranslationPlanner` platformfüggetlen Kotlin-modul.
- Újrafordítási előnézetet készít, de nem írja át automatikusan a szöveget.
- Alapértelmezetten kihagyja a kézzel jóváhagyott fordításokat.
- Csak a felhasználó által kijelölt szegmensek alkalmazhatók.
- Fordítási revízióval védi a közben módosított szövegeket az elavult előnézet felülírásától.
- Tömör, soronkénti diff-előállítás.
- Öt JUnit-teszt az előnézetre, a kézi szövegek védelmére, a kijelölt alkalmazásra, az elavult revízióra és az üres javaslatokra.

## Integrációs határ

Ez a frissítés elkészíti a biztonságos újrafordítási logika alapját. A meglévő képernyőkkel, a valódi fordítószolgáltatásokkal és a projekt-/fájltárolással való teljes UI-integráció, valamint a teljes Android APK build külön ellenőrzést igényel. A modul önmagában nem hív hálózati fordítót, és nem módosít fájlokat.

## Következő lépések

1. A fordítási pipeline candidate map-et adjon a plannernek.
2. UI diff nézet, jelölőnégyzetes jóváhagyás és „kézi fordítások kihagyása” vezérlő.
3. Tartós fordításverziók és visszaállítás.
4. Teljes Gradle/JUnit futtatás és Android készülékes teszt.
