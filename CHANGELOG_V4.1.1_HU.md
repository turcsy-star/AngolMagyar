# v4.1.1 – Stabilizáló javítócsomag (Memory & Speech Integration)

## Javítások
- Beszédfelismerés: az eredményt csak RESULT_OK esetén fogadja el; megszakított felismerés után nem marad régi átirat a képernyőn.
- Kiejtés/WER: a görbe aposztróf (don’t) azonos a közönséges aposztróffal; üres referencia/átirat nem okoz hibát.
- Dokumentumbeolvasás: legfeljebb 200 000 karaktert olvas be (nem a teljes fájlt a memóriába); csonkolásról státuszüzenet jelzi.
- Szótármentés: az angol kifejezés szóközei normalizáltak, a kulcs Locale.ROOT szerint készül (azonos a CSV-importtal).
- Szituációs gyakorló: a „Hasznos fordulat" egy gombbal a beszédgyakorló referenciamondata lehet.
- Verziófeliratok (hub, képernyőfejléc, manifest) egységesen 4.1.

## Tesztek
- PronunciationAssessmentTest bővítve (csere/hiány/többlet, aposztróf, üres bemenet).
- Új UltimateLearningEngineTest (szintek, olvasási idő, forgatókönyvek).

## Verzió
- versionName 4.1.1, versionCode 40300

## Ellenőrzés
- Ebben a környezetben a Gradle/Android SDK nem érhető el (services.gradle.org és Google Maven tiltott), ezért a fordítás és a tesztek NEM futottak le; a módosítások statikus átnézéssel készültek. A GitHub Actions CI futtatása szükséges.
