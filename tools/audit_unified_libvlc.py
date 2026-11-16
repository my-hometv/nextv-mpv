#!/usr/bin/env python3
"""Read-only audit of the source prerequisites for a real unified LibVLC APK.

This does not rewrite Java bytecode, native symbols or ELF dependencies.
It intentionally reports blockers instead of producing a nonworking APK.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path

VERSIONS = {
    "3.5.1": ("libvlc-351.aar", "LibVlc351Adapter.kt"),
    "3.7.7": ("libvlc-377.aar", "LibVlc377Adapter.kt"),
    "4.0.0-eap29": ("libvlc-400eap29.aar", "LibVlc400eap29Adapter.kt"),
}


def audit(app: Path) -> dict:
    checks = []
    for version, (artifact, adapter) in VERSIONS.items():
        aar = app / "native-runtimes" / artifact
        source = app / "src" / "unified" / "java" / "com" / "softppj" / "nextv" / "nativevlc" / adapter
        checks.append({"version": version, "artifact": str(aar),
                       "artifact_exists": aar.is_file(),
                       "adapter": str(source), "adapter_exists": source.is_file()})
    return {"ready_for_native_verification": all(
        item["artifact_exists"] and item["adapter_exists"] for item in checks
    ), "checks": checks,
    "warning": "File presence is not proof of relocated JNI/ELF correctness or working playback."}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--app", type=Path, default=Path("app"))
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    report = audit(args.app)
    payload = json.dumps(report, indent=2)
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(payload + "\n", encoding="utf-8")
    print(payload)
    return 0 if report["ready_for_native_verification"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
