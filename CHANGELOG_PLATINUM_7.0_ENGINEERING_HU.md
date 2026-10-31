# Változásnapló – Platinum Intelligence 7.0 Engineering csomag

## Hozzáadva
- `PLATINUM_INTELLIGENCE_7.0_MASTER_PLAN_HU.md` – mind a tíz prémium fejlesztési terület részletes célja, működési szabályai és fő fájljai.
- `PLATINUM_INTELLIGENCE_7.0_FILE_MATRIX_HU.md` – meglévő belépési pontok és célzott új fájlok.
- `PLATINUM_INTELLIGENCE_7.0_TEST_PLAN_HU.md` – funkcionális, offline, adatvédelmi, UI és release tesztmátrix.
- `PLATINUM_INTELLIGENCE_7.0_STATUS_HU.md` – pontos átadási státusz és nem igazolt tételek.

## Megőrizve
- Az eredeti 4.2.6 app/core/wear forrás és konfiguráció.
- `applicationId`, `versionName`, `versionCode`, SDK és meglévő adatkezelési határok.
- Az eredeti 6.0 biztonsági ellenőrző és release checklist.

## Fontos
Ez Engineering tervcsomag, nem teljesen implementált 7.0 alkalmazás. A build, tesztek és eszköztesztek nem állíthatók PASS-nak, ha nem futottak le.

## W1/W2 folytatás – tényleges core implementáció
- `core/.../PlatinumWorkflow.kt`: offline-first workflow állapotgép, definíció-validáció, DAG ciklusellenőrzés, capability/network gate, start/complete/fail/retry/cancel.
- `core/.../TranslationWorkspace.kt`: stílusváltozatok, append-only revíziótár, visszaállítás új revízióként, token diff és minőség-audit adapter.
- `PlatinumWorkflowTest.kt` és `TranslationWorkspaceTest.kt`: 11 új unit teszteset.
- Core Kotlin compile és smoke harness: PASS. A Gradle/JUnit/Android build környezeti korlát miatt továbbra sem igazolt.
