# Frissítési útmutató – 4.2.0 → 4.2.1

## Felhasználói adatok

A normál alkalmazásadatok és a meglévő debug aláírás megmarad. A debug kulcs változatlanul a projektben maradt, ezért a 4.2.0 debug telepítés frissíthető.

## API-kulcs migráció

Az első beállításolvasáskor a korábban plaintextként tárolt API-kulcs titkosított Android Keystore tárolóba kerül. Sikeres migráció után a régi plaintext mező törlődik.

Ha a Keystore nem érhető el, a kulcs nem marad plaintext formában; ilyen esetben a felhasználónak újra meg kell adnia az API-kulcsot, miután az eszköz Keystore állapota helyreállt.

## Fejlesztői build

A debug APK továbbra is a projekt debug aláírásával készül. A release APK saját kulcs nélkül unsigned, és nem telepíthető normál frissítésként, amíg nincs aláírva.

Release kulcs beállítása: lásd a README „Saját release aláírás” fejezetét. Soha ne commitold a keystore-t vagy a `keystore.properties` fájlt.

## Ellenőrzés

Frissítés után ellenőrizd:
1. alkalmazás elindul és meglévő projektek megjelennek;
2. online fordítási beállítások betöltődnek;
3. API-kulcs titkosított migrációja sikeres;
4. offline fordítás, TTS, mikrofon és felirat-export működik;
5. világos/sötét prémium téma váltása működik.
