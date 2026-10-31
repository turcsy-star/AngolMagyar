# v2.65.0 – Hybrid Translation Engine + UI/Motion előrelépés

## Új / továbbfejlesztett elemek

### Hybrid Translation Engine (core) – Kész
- Új `HybridTranslationEngine` objektum.
- Módok: OFFLINE_ONLY, PREFER_OFFLINE, PREFER_ONLINE, ONLINE_ONLY.
- Adatvédelmi kapcsolók: allowNetwork, redactLogs, minimizePayload.
- Egységes `Result` / `SegmentResult` formátum, forrásmotor jelöléssel (OFFLINE / ONLINE / FALLBACK_*).
- Szótár-előtöltés exact match-el; offline és online adapter interfészek.
- A meglévő `OnlineTranslator` adapterként csatlakoztatható.

### Platinum Motion Engine – Részben kész (előrelépés)
- `PremiumMotion` bővítve: Profile (FULL / SUBTLE / REDUCED / NONE), scale(), allowLoops(), statusLabel().
- Automatikus animációcsökkentés app- és rendszerbeállítás alapján.

### Infinity Global UI – Részben kész (előrelépés)
- Új `InfinityGlobalUi`: központi paletták (Platinum Dark, Diamond Blue), migrációs checklist és progressLabel().
- A teljes View→Compose migráció a v3.00-ra marad.

### UI
- `PlatinumIntelligenceActivity`: Hybrid elem felvéve „Kész (core) · v2.65.0”; Motion/Global státuszok frissítve.
- `NextWaveToolsActivity`: Hybrid és Motion/Global kártyák frissítve.

## Verzió
- versionCode: 6500
- versionName: 2.65.0
