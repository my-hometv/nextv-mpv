#!/usr/bin/env python3
"""Reproducible, fail-closed orchestration of three *source-based* LibVLC builds.

Input manifest JSON schema:
{"versions":{"3.5.1":{"source_dir":"/checkout","git_commit":"40-hex-sha",
"build_command":["./buildsystem/compile.sh","-a","arm64"],"output_aar":"/path/output.aar"},...}}
Build commands are supplied explicitly because upstream release build flags and toolchains
must be audited for each version. No downloads, dependency substitutions or APK packaging.
"""
import argparse
import hashlib
import json
import pathlib
import re
import subprocess
import sys
import os
import tempfile

VERSIONS = ("3.5.1", "3.7.7", "4.0.0-eap29")


def run(args, cwd):
    return subprocess.run(args, cwd=cwd, text=True, capture_output=True, check=True)


def pinned_submodules(source):
    """Require recursive Git submodules to match the source commit's gitlinks."""
    status = run(["git", "submodule", "status", "--recursive"], source).stdout
    entries = []
    for line in status.splitlines():
        if not line:
            continue
        if line[0] != " ":
            raise ValueError(f"Submodule is uninitialized, conflicted or at wrong commit: {line}")
        parts = line[1:].split()
        if len(parts) < 2 or not re.fullmatch(r"[0-9a-fA-F]{40}", parts[0]):
            raise ValueError(f"Malformed submodule status: {line}")
        entries.append({"commit": parts[0].lower(), "path": parts[1]})
    return entries


def build(manifest, output, verify_relocation=False):
    versions = manifest.get("versions", {})
    if set(versions) != set(VERSIONS):
        raise ValueError("Manifest must specify exactly the three supported versions")
    if output.exists():
        raise ValueError(f"Provenance already exists: {output}")
    records = {}
    prepared = {}
    output_paths = set()
    source_paths = set()
    provenance_path = output.resolve()
    # Validate all checkout boundaries before inspecting Git status. A nested
    # checkout appears as untracked content in its parent and would otherwise
    # mask the more fundamental overlapping-source configuration error.
    for version in VERSIONS:
        source = pathlib.Path(versions[version]["source_dir"]).resolve(strict=True)
        if any(source == other or source in other.parents or other in source.parents
               for other in source_paths):
            raise ValueError(f"{version}: source_dir overlaps another version checkout: {source}")
        source_paths.add(source)
    # Preflight every version before invoking any build command.
    for version in VERSIONS:
        item = versions[version]
        source = pathlib.Path(item["source_dir"]).resolve(strict=True)
        expected = item["git_commit"].lower()
        if not re.fullmatch(r"[0-9a-f]{40}", expected):
            raise ValueError(f"{version}: git_commit must be a full 40-character SHA")
        actual = run(["git", "rev-parse", "HEAD"], source).stdout.strip().lower()
        if actual != expected:
            raise ValueError(f"{version}: source commit mismatch ({actual} != {expected})")
        submodules = pinned_submodules(source)
        dirty = run(["git", "status", "--porcelain", "--untracked-files=all"], source).stdout
        if dirty.strip():
            raise ValueError(f"{version}: source tree is dirty")
        command = item["build_command"]
        if not isinstance(command, list) or not command or not all(isinstance(x, str) and x for x in command):
            raise ValueError(f"{version}: build_command must be a nonempty argument list")
        aar = pathlib.Path(item["output_aar"]).resolve()
        if aar.exists():
            raise ValueError(f"{version}: output already exists; remove it before rebuilding: {aar}")
        if aar == provenance_path:
            raise ValueError(f"{version}: output_aar cannot equal provenance path: {aar}")
        if aar in output_paths:
            raise ValueError(f"{version}: output_aar path is shared by multiple versions: {aar}")
        if aar == source or aar.is_dir():
            raise ValueError(f"{version}: output_aar must be a new file path")
        output_paths.add(aar)
        prepared[version] = (source, actual, command, aar, submodules)
    for version in VERSIONS:
        source, actual, command, aar, submodules = prepared[version]
        print(f"Building {version} at {actual}", flush=True)
        subprocess.run(command, cwd=source, check=True)
        # Build tooling may create ignored/untracked outputs, but must not
        # silently rewrite the pinned source revision or tracked inputs.
        post_build_head = run(["git", "rev-parse", "HEAD"], source).stdout.strip().lower()
        if post_build_head != actual:
            raise ValueError(f"{version}: source HEAD changed during build")
        if pinned_submodules(source) != submodules:
            raise ValueError(f"{version}: recursive submodule revisions changed during build")
        # Check newly staged paths first: git diff HEAD includes staged additions,
        # otherwise the more general tracked-change error hides their cause.
        staged_changes = run(["git", "diff", "--cached", "--name-only", "--diff-filter=A"], source).stdout
        if staged_changes.strip():
            raise ValueError(f"{version}: build staged new source files: {staged_changes.strip()}")
        tracked_changes = run(["git", "diff", "--name-only", "HEAD", "--"], source).stdout
        if tracked_changes.strip():
            raise ValueError(f"{version}: tracked source files changed during build: {tracked_changes.strip()}")
        if not aar.is_file():
            raise ValueError(f"{version}: build did not produce declared AAR {aar}")
        digest = hashlib.sha256(aar.read_bytes()).hexdigest()
        records[version] = {"source_commit": actual, "output_aar": str(aar), "sha256": digest,
                            "build_command": command, "submodules": submodules}
    # A later version's build command can mutate an earlier checkout or artifact.
    # Recheck the complete build set before publishing any provenance.
    for version in VERSIONS:
        source, actual, command, aar, submodules = prepared[version]
        if run(["git", "rev-parse", "HEAD"], source).stdout.strip().lower() != actual:
            raise ValueError(f"{version}: source HEAD changed after build")
        if pinned_submodules(source) != submodules:
            raise ValueError(f"{version}: submodule revisions changed after build")
        staged = run(["git", "diff", "--cached", "--name-only", "--diff-filter=A"], source).stdout
        if staged.strip():
            raise ValueError(f"{version}: source staged new files after build")
        tracked = run(["git", "diff", "--name-only", "HEAD", "--"], source).stdout
        if tracked.strip():
            raise ValueError(f"{version}: tracked source files changed after build")
        if not aar.is_file() or hashlib.sha256(aar.read_bytes()).hexdigest() != records[version]["sha256"]:
            raise ValueError(f"{version}: output artifact changed after build")
    if verify_relocation:
        verifier = pathlib.Path(__file__).with_name("verify_libvlc_isolation.py")
        subprocess.run([sys.executable, str(verifier)] +
                       [f"{v}={records[v]['output_aar']}" for v in VERSIONS], check=True)
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        raise ValueError(f"Provenance already exists: {output}")
    fd, temporary = tempfile.mkstemp(prefix=".libvlc-provenance-", dir=output.parent)
    try:
        with os.fdopen(fd, "w") as stream:
            json.dump({"versions": records, "relocation_verified": verify_relocation}, stream, indent=2)
            stream.write("\n")
        os.replace(temporary, output)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)
    print(f"Provenance saved to {output}")
    return records


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=pathlib.Path)
    parser.add_argument("--provenance", type=pathlib.Path, required=True)
    parser.add_argument("--verify-relocation", action="store_true",
                        help="Run the strict relocated-AAR validator after source builds")
    args = parser.parse_args()
    build(json.loads(args.manifest.read_text()), args.provenance,
          verify_relocation=args.verify_relocation)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, KeyError, subprocess.CalledProcessError) as exc:
        sys.exit(f"Source build failed closed: {exc}")
