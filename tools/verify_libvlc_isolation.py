#!/usr/bin/env python3
"""Fail-closed verifier for independently relocated LibVLC Android AARs.

Usage: python3 tools/verify_libvlc_isolation.py VERSION=AAR [VERSION=AAR ...]
Requires readelf and javap from the Android/Linux build environment.
This is a validation gate, NOT an AAR relocator.
"""
import io
import json
import pathlib
import re
import subprocess
import sys
import tempfile
import zipfile

VERSIONS = ("3.5.1", "3.7.7", "4.0.0-eap29")
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


def parse_dynamic_dependencies(dynamic):
    """Extract ELF dynamic SONAME and NEEDED entries from readelf -d output."""
    sonames = re.findall(r"\(SONAME\)[^\n]*\[([^\]]+)\]", dynamic)
    needed = re.findall(r"\(NEEDED\)[^\n]*\[([^\]]+)\]", dynamic)
    return sonames, needed


def native_collision_errors(inventories):
    """Reject cross-version ELF SONAME collisions, including differing AAR paths."""
    errors = []
    for i, left in enumerate(VERSIONS):
        if left not in inventories:
            continue
        for right in VERSIONS[i + 1:]:
            if right not in inventories:
                continue
            for left_path, left_info in inventories[left]['native'].items():
                for right_path, right_info in inventories[right]['native'].items():
                    left_abi = left_path.split('/')[1]
                    right_abi = right_path.split('/')[1]
                    if left_abi != right_abi:
                        continue
                    left_soname = left_info.get('soname')
                    right_soname = right_info.get('soname')
                    if left_soname and left_soname == right_soname:
                        errors.append(f'{left}/{right} {left_abi}: duplicate ELF SONAME {left_soname}')
    return errors

def native_metadata_errors(version, native):
    """Reject missing SONAMEs and ambiguous duplicate identities within an ABI."""
    errors = []
    seen = {}
    for path, info in native.items():
        abi = path.split("/")[1]
        soname = info.get("soname")
        if not soname:
            errors.append(f"{version} {path}: missing ELF SONAME")
            continue
        identity = (abi, soname)
        if identity in seen:
            errors.append(f"{version} {abi}: duplicate ELF SONAME {soname} ({seen[identity]}, {path})")
        else:
            seen[identity] = path
    return errors


# Android public NDK libraries and runtime dependencies normally provided by the OS.
# Any other DT_NEEDED entry must be packaged in the same ABI. This catches
# codec/compatibility libraries accidentally omitted during relocation.
ANDROID_SYSTEM_SONAMES = frozenset({
    "libandroid.so", "libaaudio.so", "libc.so", "libdl.so", "libm.so",
    "liblog.so", "libz.so", "libEGL.so", "libGLESv1_CM.so",
    "libGLESv2.so", "libGLESv3.so", "libOpenSLES.so",
    "libjnigraphics.so", "libmediandk.so", "libnativewindow.so",
    "libvulkan.so", "libstdc++.so", "libatomic.so",
})


def native_dependency_closure_errors(version, native):
    """Require every non-system DT_NEEDED dependency in the same packaged ABI."""
    errors = []
    for path, info in native.items():
        abi = path.split("/")[1]
        bundled = {entry.split("/")[-1] for entry in native
                   if entry.split("/")[1] == abi}
        for needed in info.get("needed", []):
            if needed not in ANDROID_SYSTEM_SONAMES and needed not in bundled:
                errors.append(f"{version} {path}: missing packaged dependency {needed} for {abi}")
    return errors


def unresolved_bundled_dependencies(version, native):
    """A renamed native dependency must resolve to a library in the same ABI.

    Android system libraries are loaded from the OS, not from the AAR. Only
    references to relocated LibVLC-family libraries require local resolution.
    """
    errors = []
    for path, info in native.items():
        abi = path.split("/")[1]
        available = {entry.split("/")[-1] for entry in native if entry.split("/")[1] == abi}
        for dependency in info.get("needed", []):
            if dependency.startswith(("libvlc_", "libvlcjni_", "libcompatvlc_", "libvlccore_")) and dependency not in available:
                errors.append(f"{version} {path}: unresolved relocated dependency {dependency} for {abi}")
    return errors


ELF_MACHINES = {"arm64-v8a": 183, "armeabi-v7a": 40, "x86": 3, "x86_64": 62}
ELF_CLASSES = {"arm64-v8a": 2, "armeabi-v7a": 1, "x86": 1, "x86_64": 2}


def native_abi_errors(version, path, binary):
    """Reject binaries whose ELF architecture does not match the packaged ABI."""
    abi = path.split("/")[1]
    if len(binary) < 20 or binary[:4] != b"\x7fELF":
        return [f"{version} {path}: missing ELF header"]
    elf_class, endian = binary[4], binary[5]
    if endian != 1:
        return [f"{version} {path}: Android ABI requires little-endian ELF"]
    if binary[6] != 1:
        return [f"{version} {path}: unsupported ELF header version"]
    elf_type = int.from_bytes(binary[16:18], "little")
    if elf_type != 3:
        return [f"{version} {path}: native library must be ET_DYN shared object"]
    machine = int.from_bytes(binary[18:20], "little" if endian == 1 else "big")
    if elf_class != ELF_CLASSES[abi] or machine != ELF_MACHINES[abi]:
        return [f"{version} {path}: ELF architecture does not match ABI {abi}"]
    return []


def native_elf_identity_errors(version, path, binary):
    """Reject ELF binaries with a non-Android OS ABI or invalid ABI revision."""
    if len(binary) < 9 or binary[:4] != b"\x7fELF":
        return []  # native_abi_errors reports the invalid ELF header.
    if binary[7] not in (0, 3):
        return [f"{version} {path}: unsupported ELF OS ABI {binary[7]}"]
    if binary[8] != 0:
        return [f"{version} {path}: unsupported ELF ABI version {binary[8]}"]
    return []


def embedded_jni_reference_errors(version, path, binary):
    """Reject legacy JNI class paths and unrelocated native library loader names."""
    errors = []
    for marker in (b"org/videolan/libvlc/", b"org/videolan/medialibrary/"):
        if marker in binary:
            errors.append(f"{version} {path}: embedded unrelocated JNI class reference {marker.decode()}")
    for marker in (b"libvlc.so", b"libvlcjni.so", b"libvlccore.so", b"libcompatvlc.so"):
        if marker in binary:
            errors.append(f"{version} {path}: embedded unrelocated native loader reference {marker.decode()}")
    return errors



def exported_jni_namespace_errors(version, path, symbols):
    """Reject JNI exports bound to the original VideoLAN Java namespaces.

    Class-name strings alone are insufficient: native JNI exports can still
    target org.videolan classes after Java bytecode has been relocated.
    """
    errors = []
    legacy = ("Java_org_videolan_libvlc_", "Java_org_videolan_medialibrary_")
    for line in symbols.splitlines():
        # readelf -Ws: Num Value Size Type Bind Vis Ndx Name
        fields = line.split()
        if len(fields) < 8 or fields[6] in ("UND", "ABS"):
            continue
        name = fields[7].split("@", 1)[0]
        if fields[4] not in ("GLOBAL", "WEAK") or fields[5] != "DEFAULT":
            continue
        if name.startswith(legacy):
            errors.append(f"{version} {path}: unrelocated exported JNI symbol {name}")
    return errors

def check(artifacts):
    errors = []
    inventories = {}
    for version in VERSIONS:
        path = artifacts.get(version)
        if not path or not pathlib.Path(path).is_file():
            errors.append(f"{version}: relocated AAR missing")
            continue
        with zipfile.ZipFile(path) as aar:
            members = aar.namelist()
            names = set(members)
            if len(members) != len(names):
                errors.append(f"{version}: duplicate AAR archive members")
            unsafe = [name for name in members if name.startswith("/") or
                      any(part in ("", ".", "..") for part in name.rstrip("/").split("/"))]
            if unsafe:
                errors.append(f"{version}: unsafe AAR archive path {unsafe[0]}")
            if "classes.jar" not in names:
                errors.append(f"{version}: classes.jar missing")
                continue
            with zipfile.ZipFile(io.BytesIO(aar.read("classes.jar"))) as classes:
                jar_members = classes.namelist()
                if len(jar_members) != len(set(jar_members)):
                    errors.append(f"{version}: duplicate classes.jar archive members")
                unsafe_jar = [name for name in jar_members if name.startswith("/") or
                              any(part in ("", ".", "..") for part in name.rstrip("/").split("/"))]
                if unsafe_jar:
                    errors.append(f"{version}: unsafe classes.jar archive path {unsafe_jar[0]}")
                class_names = {name[:-6] for name in jar_members if name.endswith(".class")}
                # Relocating archive paths alone does not rewrite bytecode
                # descriptors, method references or JNI loader constants.
                for class_file in sorted(name for name in class_names if name + ".class" in jar_members):
                    data = classes.read(class_file + ".class")
                    for marker in (b"org/videolan/libvlc/", b"org/videolan/medialibrary/",
                                   b"org.videolan.libvlc.", b"org.videolan.medialibrary."):
                        if marker in data:
                            errors.append(f"{version} {class_file}: unrelocated bytecode reference {marker.decode()}")
                    for marker in (b"libvlc.so", b"libvlcjni.so", b"libvlccore.so", b"libcompatvlc.so"):
                        if marker in data:
                            errors.append(f"{version} {class_file}: unrelocated Java loader reference {marker.decode()}")
            legacy_prefixes = ("org/videolan/libvlc/", "org/videolan/medialibrary/")
            old_classes = sorted(name for name in class_names if name.startswith(legacy_prefixes))
            if old_classes:
                errors.append(f"{version}: {len(old_classes)} unrelocated Videolan classes ({old_classes[0]})")
            namespace = "com/softppj/isolatedvlc/v" + re.sub(r"[^a-zA-Z0-9]", "_", version) + "/"
            if not any(name.startswith(namespace) for name in class_names):
                errors.append(f"{version}: no classes in expected namespace {namespace}")
            native = {}
            with tempfile.TemporaryDirectory() as temp:
                for abi in ABIS:
                    entries = sorted(name for name in names if name.startswith(f"jni/{abi}/") and name.endswith(".so"))
                    if not entries:
                        errors.append(f"{version}: no native libraries for {abi}")
                    for name in entries:
                        file = pathlib.Path(temp) / pathlib.Path(name).name
                        binary = aar.read(name)
                        errors.extend(native_abi_errors(version, name, binary))
                        errors.extend(native_elf_identity_errors(version, name, binary))
                        errors.extend(embedded_jni_reference_errors(version, name, binary))
                        file.write_bytes(binary)
                        proc = subprocess.run(["readelf", "-d", str(file)], text=True, capture_output=True)
                        if proc.returncode:
                            errors.append(f"{version} {name}: readelf failed: {proc.stderr.strip()}")
                            continue
                        symbols = subprocess.run(["readelf", "-Ws", str(file)], text=True, capture_output=True)
                        if symbols.returncode:
                            errors.append(f"{version} {name}: readelf symbols failed: {symbols.stderr.strip()}")
                        else:
                            errors.extend(exported_jni_namespace_errors(version, name, symbols.stdout))
                        dynamic = proc.stdout
                        sonames, needed = parse_dynamic_dependencies(dynamic)
                        native[name] = {"soname": sonames[0] if sonames else None, "needed": needed}
                        if pathlib.Path(name).name in ("libvlc.so", "libvlcjni.so", "libvlccore.so", "libcompatvlc.so"):
                            errors.append(f"{version} {name}: original native filename still present")
                        if any(x in ("libvlc.so", "libvlcjni.so", "libvlccore.so", "libcompatvlc.so") for x in sonames + needed):
                            errors.append(f"{version} {name}: original LibVLC SONAME/NEEDED reference")
                        if sonames and sonames[0] != pathlib.Path(name).name:
                            errors.append(f"{version} {name}: SONAME differs from packaged filename")
            errors.extend(native_metadata_errors(version, native))
            errors.extend(unresolved_bundled_dependencies(version, native))
            errors.extend(native_dependency_closure_errors(version, native))
            inventories[version] = {"classes": class_names, "native": native}
    for i, left in enumerate(VERSIONS):
        if left not in inventories:
            continue
        for right in VERSIONS[i + 1:]:
            if right not in inventories:
                continue
            shared_classes = inventories[left]["classes"] & inventories[right]["classes"]
            shared_paths = set(inventories[left]["native"]) & set(inventories[right]["native"])
            if shared_classes:
                errors.append(f"{left}/{right}: {len(shared_classes)} duplicate Java class paths")
            if shared_paths:
                errors.append(f"{left}/{right}: {len(shared_paths)} duplicate native library paths")
    errors.extend(native_collision_errors(inventories))
    return errors


def main(argv):
    artifacts = {}
    for argument in argv:
        if "=" not in argument:
            raise SystemExit("Arguments must be VERSION=/path/to/relocated.aar")
        version, path = argument.split("=", 1)
        if version not in VERSIONS or version in artifacts:
            raise SystemExit(f"Invalid or duplicate version: {version}")
        artifacts[version] = path
    errors = check(artifacts)
    print(json.dumps({"isolation_ready": not errors, "blockers": errors}, indent=2))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
