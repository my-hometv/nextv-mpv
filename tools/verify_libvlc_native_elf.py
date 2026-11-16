#!/usr/bin/env python3
"""Inspect relocated LibVLC AAR ELF exports/dependencies without building APKs.

Requires Python 3 and the Android NDK's llvm-readelf on PATH (or READELF).
Only reads supplied AAR files. Does not modify or produce native binaries.
"""
import os
import re
import shutil
import subprocess
import sys
import zipfile
from collections import defaultdict

def inspect(path, readelf):
    names = defaultdict(list)
    exported = set()
    needed = defaultdict(set)
    errors = []
    with zipfile.ZipFile(path) as archive:
        for name in archive.namelist():
            if not name.startswith("jni/") or not name.endswith(".so"):
                continue
            abi, so = name.split("/")[1:3]
            payload = archive.read(name)
            if not payload.startswith(bytes((0x7f, 0x45, 0x4c, 0x46))):
                errors.append(f"{path}: {name} is not ELF")
                continue
            import tempfile
            with tempfile.NamedTemporaryFile(suffix=".so") as tmp:
                tmp.write(payload)
                tmp.flush()
                result = subprocess.run([readelf, "-d", "--dyn-syms", tmp.name],
                                        capture_output=True, text=True)
            if result.returncode:
                errors.append(f"{path}: {name}: readelf failed: {result.stderr.strip()}")
                continue
            output = result.stdout
            names[abi].append(so)
            needed[abi].update(re.findall(r"\(NEEDED\).*?\[([^]]+)\]", output))
            exported.update(re.findall(r"\b(Java_[A-Za-z0-9_]+)\b", output))
            # SONAME may be omitted by upstream binaries; report only actual collisions.
    if not names:
        errors.append(f"{path}: no readable native libraries")
    return names, needed, exported, errors

def main():
    if len(sys.argv) != 2:
        print("Usage: verify_libvlc_native_elf.py aar1|aar2|aar3", file=sys.stderr)
        return 2
    paths = sys.argv[1].split("|")
    if len(paths) != 3 or any(not os.path.isfile(p) for p in paths):
        print("Three existing AAR paths are required", file=sys.stderr)
        return 2
    readelf = os.environ.get("READELF") or shutil.which("llvm-readelf")
    if not readelf:
        print("Android NDK llvm-readelf required (set READELF)", file=sys.stderr)
        return 2
    reports = [inspect(p, readelf) for p in paths]
    problems = [e for _, _, _, errors in reports for e in errors]
    for path, (libs, needed, exports, _) in zip(paths, reports):
        print(f"{os.path.basename(path)}: {sum(map(len, libs.values()))} ELF libraries, "
              f"{len(exports)} JNI exports; ABIs: {', '.join(sorted(libs))}")
        if not exports:
            print(f"REVIEW: {path} has no Java_ exports; verify dynamic RegisterNatives mapping")
        for abi, dependencies in needed.items():
            own = set(libs[abi])
            unresolved = {dep for dep in dependencies
                          if dep.startswith(("libvlc", "libcompat")) and dep not in own}
            if unresolved:
                problems.append(f"{path} {abi}: missing LibVLC dependencies: {sorted(unresolved)}")
    for i in range(3):
        for j in range(i + 1, 3):
            overlap = reports[i][2] & reports[j][2]
            if overlap:
                problems.append(f"Duplicate JNI export symbols in {i+1}/{j+1}: {sorted(overlap)[:8]}")
    for problem in problems:
        print("FAIL:", problem, file=sys.stderr)
    return 1 if problems else 0

if __name__ == "__main__":
    sys.exit(main())
