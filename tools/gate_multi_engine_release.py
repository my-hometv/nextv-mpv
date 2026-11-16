#!/usr/bin/env python3
"""Fail-closed gate for source-built, independently relocated LibVLC AARs.

This gate does not create relocated binaries or prove Android runtime loading.
"""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys

VERSIONS = ("3.5.1", "3.7.7", "4.0.0-eap29")


def verified_artifacts(provenance):
    if provenance.get("relocation_verified") is not True:
        raise ValueError("Relocation verification is required for release")
    records = provenance.get("versions")
    if not isinstance(records, dict) or set(records) != set(VERSIONS):
        raise ValueError("Provenance must contain exactly three LibVLC versions")
    artifacts = {}
    for version in VERSIONS:
        record = records[version]
        if not isinstance(record, dict):
            raise ValueError(f"{version}: invalid provenance record")
        commit = record.get("source_commit")
        digest = record.get("sha256")
        name = record.get("output_aar")
        if not isinstance(commit, str) or not re.fullmatch(r"[0-9a-fA-F]{40}", commit):
            raise ValueError(f"{version}: invalid source commit")
        if not isinstance(digest, str) or not re.fullmatch(r"[0-9a-fA-F]{64}", digest):
            raise ValueError(f"{version}: invalid SHA-256")
        if not isinstance(name, str) or not name:
            raise ValueError(f"{version}: missing AAR path")
        path = pathlib.Path(name).resolve(strict=True)
        if not path.is_file() or path.suffix.lower() != ".aar":
            raise ValueError(f"{version}: expected an AAR file")
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        if actual != digest.lower():
            raise ValueError(f"{version}: AAR SHA-256 mismatch")
        artifacts[version] = path
    if len(set(artifacts.values())) != len(VERSIONS):
        raise ValueError("Versions must not share an AAR file")
    return artifacts


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("provenance", type=pathlib.Path)
    args = parser.parse_args(argv)
    artifacts = verified_artifacts(json.loads(args.provenance.read_text()))
    verifier = pathlib.Path(__file__).with_name("verify_libvlc_isolation.py")
    subprocess.run([sys.executable, str(verifier)] +
                   [f"{version}={artifacts[version]}" for version in VERSIONS], check=True)
    print("Relocated artifacts passed provenance and static isolation gates.")
    print("Runtime loading, device playback and signing are still required.")


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, KeyError, subprocess.CalledProcessError) as exc:
        sys.exit(f"Multi-engine release blocked: {exc}")
