# Validációs jegyzőkönyv – 4.2.2

| Ellenőrzés | Eredmény |
|---|---|
| Forrás ZIP CRC ellenőrzés | Sikeres |
| Statikus release/security guardrails | Sikeres |
| XML parse | 38/38 sikeres |
| Gradle/Kotlin build | Nem futott: Gradle 8.9 letöltése DNS-hibán elakadt |
| Android SDK build | Nem futott: SDK nincs telepítve |
| Fizikai eszközteszt | Nem történt |

A sikeres statikus ellenőrzés nem jelent sikeres Android buildet. A GitHub Actions futás és a fizikai eszközteszt a következő kötelező kapu.
