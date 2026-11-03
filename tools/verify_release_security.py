#!/usr/bin/env python3
"""Static release/security guardrails for AngolMagyar.

This is a source/configuration check only. It does not replace Android tests,
dependency scanning, runtime testing, or a penetration test.
"""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = "{http://schemas.android.com/apk/res/android}"
errors: list[str] = []


def read(path: str) -> str:
    p = ROOT / path
    if not p.is_file():
        errors.append(f"MISSING: {path}")
        return ""
    return p.read_text(encoding="utf-8", errors="replace")


def require(path: str, needle: str, description: str) -> None:
    if needle not in read(path):
        errors.append(f"FAILED: {description} ({path})")


def forbid(path: str, needle: str, description: str) -> None:
    if needle in read(path):
        errors.append(f"FAILED: {description} ({path})")


# Release signing: no debug-key fallback, and incomplete signing config fails closed.
gradle_app = read("app/build.gradle.kts")
release_match = re.search(r"(?ms)^        release\s*\{(.*?)^        \}", gradle_app)
if not release_match:
    errors.append("FAILED: release buildType block not found (app/build.gradle.kts)")
else:
    release_block = release_match.group(1)
    if 'signingConfig = signingConfigs.getByName("debugPermanent")' in release_block:
        errors.append("FAILED: debug signing must never be assigned to release")
    if 'signingConfigs.findByName("release")?.let { signingConfig = it }' not in release_block:
        errors.append("FAILED: release must use only an explicitly configured release signing config")
require("app/build.gradle.kts", "releaseValues.all { !it.isNullOrBlank() }",
        "release signing must require all four values")
require("app/build.gradle.kts", "A release aláírás hiányos",
        "partial release signing config must fail closed")

# Secret storage and outbound endpoint policy.
require("app/src/main/java/hu/angolmagyar/fordito/SecretStore.kt", 'Cipher.getInstance("AES/GCM/NoPadding")',
        "API secrets must use AES-GCM")
require("app/src/main/java/hu/angolmagyar/fordito/SecretStore.kt", 'p.edit().remove(name).apply()',
        "legacy plaintext must be removed on failed migration")
forbid("app/src/main/java/hu/angolmagyar/fordito/SecretStore.kt", 'putString(name, trimmed)',
       "API secrets must not be written as plaintext")
require("core/src/main/java/hu/angolmagyar/fordito/EndpointPolicy.kt", 'scheme == "https"',
        "custom endpoints must validate transport scheme")
require("app/src/main/java/hu/angolmagyar/fordito/Presets.kt", 'EndpointPolicy.isAllowed(baseUrl(ctx))',
        "endpoint policy must be enforced when translation config is built")

# Manifest exposure / backup / file-sharing boundaries.
manifest_path = ROOT / "app/src/main/AndroidManifest.xml"
if not manifest_path.is_file():
    errors.append("MISSING: app/src/main/AndroidManifest.xml")
else:
    try:
        root = ET.parse(manifest_path).getroot()
        app = root.find("application")
        if app is None:
            errors.append("FAILED: application element missing from manifest")
        else:
            if app.get(ANDROID + "allowBackup") != "false":
                errors.append("FAILED: application backup must remain disabled")
            if app.get(ANDROID + "usesCleartextTraffic") == "true":
                errors.append("FAILED: global cleartext traffic must not be enabled")
            exported = {
                (node.tag, node.get(ANDROID + "name"))
                for node in app
                if node.get(ANDROID + "exported") == "true"
            }
            expected = {
                ("activity", ".ui.MainActivity"),
                ("activity", ".FileSubtitleActivity"),
                ("service", ".QuickTile"),
            }
            if exported != expected:
                errors.append(f"FAILED: unexpected exported component set: {sorted(exported)}")
            provider = next((n for n in app.findall("provider")
                             if n.get(ANDROID + "name") == "androidx.core.content.FileProvider"), None)
            if provider is None or provider.get(ANDROID + "exported") != "false" or provider.get(ANDROID + "grantUriPermissions") != "true":
                errors.append("FAILED: FileProvider must be non-exported and grant scoped URI permissions")
            tile = next((n for n in app.findall("service")
                         if n.get(ANDROID + "name") == ".QuickTile"), None)
            if tile is None or tile.get(ANDROID + "permission") != "android.permission.BIND_QUICK_SETTINGS_TILE":
                errors.append("FAILED: QuickTile must require BIND_QUICK_SETTINGS_TILE")
            share = next((n for n in app.findall("activity")
                          if n.get(ANDROID + "name") == ".FileSubtitleActivity"), None)
            if share is None or share.get(ANDROID + "exported") != "true":
                errors.append("FAILED: file subtitle share entry point must remain explicitly exported")
            elif not any(f.get(ANDROID + "name") == "android.intent.action.SEND"
                         for filt in share.findall("intent-filter") for f in filt.findall("action")):
                errors.append("FAILED: exported file subtitle activity must only expose declared share actions")
    except ET.ParseError as exc:
        errors.append(f"FAILED: AndroidManifest.xml is invalid XML: {exc}")


# Encrypted translation-memory failures must not be treated as an empty store.
memory_store_path = "app/src/main/java/hu/angolmagyar/fordito/SmartTranslationMemoryStore.kt"
memory_store = read(memory_store_path)
require(memory_store_path, "private fun readEntries(): List<SmartTranslationMemory.Entry>?",
        "translation-memory read must distinguish failure from an empty store")
require(memory_store_path, "val entries = (readEntries() ?: return false).toMutableList()",
        "translation-memory writes must abort after a read/decryption failure")
require(memory_store_path, "val current = readEntries() ?: return false",
        "translation-memory deletes must abort after a read/decryption failure")
require(memory_store_path, "az eredeti blob megőrizve",
        "translation-memory ciphertext must be retained after a read/decryption failure")
read_section = memory_store.split("private fun readEntries()", 1)[-1].split("private fun writeEntries()", 1)[0]
if "remove(DATA_KEY)" in read_section:
    errors.append("FAILED: translation-memory read failure must not silently delete the encrypted blob")

# Local signing material must never enter source control; CI must run these checks.
require(".gitignore", "keystore.properties", "local signing properties must be ignored")
require(".gitignore", "*.jks", "JKS signing keys must be ignored")
require(".gitignore", "*.p12", "PKCS#12 signing keys must be ignored")
require(".github/workflows/build.yml", "python tools/verify_release_security.py",
        "CI must run the static release security guard")

if errors:
    print("\n".join(errors))
    sys.exit(1)
print("PASS: static release/security guardrails")
