#!/usr/bin/env python3
"""Strict verification of user-approved MyView branding assets.

Run from repository root: python tools/verify_myview_branding.py
This gate does not download images or build an APK.
"""
from pathlib import Path
from hashlib import sha256
import struct
import sys

ROOT = Path(__file__).resolve().parents[1]
EXPECTED = {
    "app/src/main/res/drawable/tv_banner.png": {
        "sha256": "aa26291bf879dca3d7cf6619557d17429c29b710974efb27ca48f0e6ed22d6e1",
        "size": (1671, 941),
    },
    "app/src/main/res/drawable/nextv_launcher.png": {
        "sha256": "42adbea165166c7d78b49573f8cab9b9c7aa96d9cb220669d28205610bc52dae",
        "size": (512, 512),
    },
}

def inspect_png(path: Path) -> tuple[str, tuple[int, int]]:
    data = path.read_bytes()
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        raise ValueError("not a valid PNG header")
    size = struct.unpack(">II", data[16:24])
    return sha256(data).hexdigest(), size

def verify(root: Path = ROOT) -> list[str]:
    failures = []
    for name, expected in EXPECTED.items():
        path = root / name
        try:
            digest, size = inspect_png(path)
        except (OSError, ValueError) as exc:
            failures.append(f"{name}: cannot inspect PNG: {exc}")
            continue
        if digest != expected["sha256"]:
            failures.append(f"{name}: SHA-256 mismatch (actual {digest})")
        if size != expected["size"]:
            failures.append(f"{name}: dimensions {size} != {expected['size']}")
    return failures

def main() -> int:
    failures = verify()
    if failures:
        for issue in failures:
            print("FAIL", issue)
        print("Approved image replacement NOT complete. Do not release APK.")
        return 1
    print("PASS: both assets exactly match the approved original PNGs.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
