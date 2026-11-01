#!/usr/bin/env python3
"""Guard against the DiamondLiveView Compose lambda-capture regression."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "app/src/main/java/hu/angolmagyar/fordito/ui/processing/DiamondLiveTheater.kt"
source = SOURCE.read_text(encoding="utf-8")

checks = {
    "Compose content resolves reduced motion from LocalContext inside composition":
        "DiamondLiveTheater(reduced = PremiumMotion.reduced(LocalContext.current))" in source,
    "DiamondLiveView does not capture a constructor-local reduced Boolean":
        "val reduced = PremiumMotion.reduced(ctx)" not in source,
    "ComposeView content remains attached through setContent":
        "setContent {" in source,
    "reduced-motion preference provider remains in use":
        "PremiumMotion.reduced(LocalContext.current)" in source,
    "effect and draw lambdas read a stable Compose State":
        source.count("rememberUpdatedState(reduced)") >= 2
        and "delay(if (reducedMotion.value)" in source
        and "startAngle = -90f + if (reducedMotion.value)" in source,
}

failed = [label for label, passed in checks.items() if not passed]
for label, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {label}")
if failed:
    sys.exit(1)
print("PASS: Compose compiler regression guard")
