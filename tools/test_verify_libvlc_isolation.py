#!/usr/bin/env python3
"""Offline regression tests for ELF dynamic dependency parsing."""
import importlib.util
import pathlib
import unittest

path = pathlib.Path(__file__).with_name("verify_libvlc_isolation.py")
spec = importlib.util.spec_from_file_location("verify_libvlc_isolation", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class ElfDynamicParsingTests(unittest.TestCase):
    def test_soname_and_needed_are_extracted(self):
        sample = """ 0x000000000000000e (SONAME) Library soname: [libvlc.so]
 0x0000000000000001 (NEEDED) Shared library: [libvlcjni.so]
 0x0000000000000001 (NEEDED) Shared library: [libc.so]
"""
        self.assertEqual(module.parse_dynamic_dependencies(sample), (["libvlc.so"], ["libvlcjni.so", "libc.so"]))

    def test_duplicate_soname_across_different_paths_rejected(self):
        inventories = {
            "3.5.1": {"native": {"jni/arm64-v8a/liba.so": {"soname": "libshared.so"}}},
            "3.7.7": {"native": {"jni/arm64-v8a/libb.so": {"soname": "libshared.so"}}},
        }
        self.assertTrue(any('duplicate ELF SONAME' in error for error in module.native_collision_errors(inventories)))

    def test_same_soname_on_different_abis_not_collision(self):
        inventories = {
            "3.5.1": {"native": {"jni/arm64-v8a/liba.so": {"soname": "libshared.so"}}},
            "3.7.7": {"native": {"jni/x86/libb.so": {"soname": "libshared.so"}}},
        }
        self.assertEqual(module.native_collision_errors(inventories), [])
    def test_missing_soname_fails_closed(self):
        native = {"jni/arm64-v8a/libcustom.so": {"soname": None, "needed": []}}
        self.assertTrue(any("missing ELF SONAME" in error for error in module.native_metadata_errors("3.5.1", native)))

    def test_duplicate_soname_within_version_and_abi_rejected(self):
        native = {
            "jni/arm64-v8a/liba.so": {"soname": "libshared.so"},
            "jni/arm64-v8a/libb.so": {"soname": "libshared.so"},
        }
        self.assertTrue(any("duplicate ELF SONAME" in error for error in module.native_metadata_errors("3.5.1", native)))

    def test_same_soname_in_distinct_abis_allowed(self):
        native = {
            "jni/arm64-v8a/liba.so": {"soname": "libshared.so"},
            "jni/x86/liba.so": {"soname": "libshared.so"},
        }
        self.assertEqual(module.native_metadata_errors("3.5.1", native), [])

    def test_relocated_native_dependency_must_exist_in_same_abi(self):
        native = {
            "jni/arm64-v8a/libplayer.so": {
                "soname": "libplayer.so", "needed": ["libvlc_377.so", "libc.so"]
            },
            "jni/armeabi-v7a/libvlc_377.so": {
                "soname": "libvlc_377.so", "needed": []
            },
        }
        self.assertTrue(any("unresolved relocated dependency" in error
                            for error in module.unresolved_bundled_dependencies("3.7.7", native)))
        native["jni/arm64-v8a/libvlc_377.so"] = {"soname": "libvlc_377.so", "needed": []}
        self.assertEqual(module.unresolved_bundled_dependencies("3.7.7", native), [])


    def test_relocated_libvlccore_dependency_requires_matching_abi(self):
        native = {
            "jni/arm64-v8a/libvlcjni_377.so": {
                "soname": "libvlcjni_377.so", "needed": ["libvlccore_377.so"]
            },
            "jni/x86/libvlccore_377.so": {
                "soname": "libvlccore_377.so", "needed": []
            },
        }
        self.assertTrue(any("libvlccore_377.so" in error for error in
                            module.unresolved_bundled_dependencies("3.7.7", native)))
        native["jni/arm64-v8a/libvlccore_377.so"] = {
            "soname": "libvlccore_377.so", "needed": []
        }
        self.assertEqual(module.unresolved_bundled_dependencies("3.7.7", native), [])

    def test_non_system_native_dependencies_require_same_abi(self):
        native = {
            "jni/arm64-v8a/libvlc_377.so": {
                "soname": "libvlc_377.so",
                "needed": ["libavcodec_377.so", "libc.so", "liblog.so"]},
            "jni/x86/libavcodec_377.so": {
                "soname": "libavcodec_377.so", "needed": []},
        }
        errors = module.native_dependency_closure_errors("3.7.7", native)
        self.assertTrue(any("libavcodec_377.so" in error for error in errors))
        self.assertFalse(any("libc.so" in error or "liblog.so" in error for error in errors))
        native["jni/arm64-v8a/libavcodec_377.so"] = {
            "soname": "libavcodec_377.so", "needed": []}
        self.assertEqual(module.native_dependency_closure_errors("3.7.7", native), [])

    def test_unrecognized_system_dependency_fails_closed(self):
        native = {"jni/arm64-v8a/libvlc_351.so": {
            "soname": "libvlc_351.so", "needed": ["libprivate_platform.so"]}}
        self.assertTrue(module.native_dependency_closure_errors("3.5.1", native))

    def test_embedded_jni_class_paths_rejected(self):
        errors = module.embedded_jni_reference_errors(
            "3.7.7", "jni/arm64-v8a/libcustom.so",
            b"prefix org/videolan/libvlc/MediaPlayer suffix")
        self.assertTrue(any("unrelocated JNI class" in error for error in errors))

    def test_embedded_native_loader_names_rejected(self):
        errors = module.embedded_jni_reference_errors(
            "3.5.1", "jni/arm64-v8a/libcustom.so", b"load libvlcjni.so")
        self.assertTrue(any("unrelocated native loader" in error for error in errors))

    def test_relocated_binary_strings_accepted(self):
        self.assertEqual(module.embedded_jni_reference_errors(
            "3.5.1", "jni/arm64-v8a/libcustom.so",
            b"com/softppj/isolatedvlc/v3_5_1/MediaPlayer libvlc_351.so"), [])

    def test_medialibrary_class_relocation_is_required(self):
        # The verifier must reject legacy Java class paths even if LibVLC classes
        # themselves were relocated. This is a separate native JNI namespace.
        import io
        import tempfile
        import zipfile
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as tmp:
            aar = pathlib.Path(tmp) / "partial.aar"
            jar_buffer = io.BytesIO()
            with zipfile.ZipFile(jar_buffer, "w") as jar:
                jar.writestr("com/softppj/isolatedvlc/v3_5_1/MediaPlayer.class", b"")
                jar.writestr("org/videolan/medialibrary/MediaLibrary.class", b"")
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr("classes.jar", jar_buffer.getvalue())
            errors = module.check({"3.5.1": aar})
            self.assertTrue(any("unrelocated Videolan classes" in e for e in errors))


    def test_original_exported_jni_namespace_is_rejected(self):
        symbols = """Symbol table '.dynsym' contains 2 entries:
   Num:    Value          Size Type    Bind   Vis      Ndx Name
     1: 0000000000001000    12 FUNC    GLOBAL DEFAULT   12 Java_org_videolan_libvlc_MediaPlayer_nativePlay
     2: 0000000000001010    12 FUNC    GLOBAL DEFAULT   12 Java_org_videolan_medialibrary_MediaLibrary_nativeInit
"""
        errors = module.exported_jni_namespace_errors("3.5.1", "jni/arm64-v8a/libcustom.so", symbols)
        self.assertEqual(len(errors), 2)

    def test_relocated_exported_jni_namespace_is_allowed(self):
        symbols = """     1: 0000000000001000    12 FUNC    GLOBAL DEFAULT   12 Java_com_softppj_isolatedvlc_v3_5_1_MediaPlayer_nativePlay
     2: 0000000000001010    12 FUNC    GLOBAL HIDDEN   12 Java_org_videolan_libvlc_MediaPlayer_nativePlay
     3: 0000000000001020    12 FUNC    GLOBAL DEFAULT  UND Java_org_videolan_libvlc_MediaPlayer_nativeStop
"""
        self.assertEqual(module.exported_jni_namespace_errors("3.5.1", "jni/arm64-v8a/libcustom.so", symbols), [])


    def test_legacy_core_and_compat_names_are_rejected(self):
        import io
        import tempfile
        import zipfile
        from unittest.mock import patch
        with tempfile.TemporaryDirectory() as tmp:
            aar = pathlib.Path(tmp) / "legacy-core.aar"
            jar_buffer = io.BytesIO()
            with zipfile.ZipFile(jar_buffer, "w") as jar:
                jar.writestr("com/softppj/isolatedvlc/v3_5_1/MediaPlayer.class", b"")
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr("classes.jar", jar_buffer.getvalue())
                archive.writestr("jni/arm64-v8a/libvlccore.so", b"ELF")
                archive.writestr("jni/arm64-v8a/libcompatvlc.so", b"ELF")
            dynamic = """ 0x000000000000000e (SONAME) Library soname: [libvlccore.so]
 0x0000000000000001 (NEEDED) Shared library: [libcompatvlc.so]
"""
            from subprocess import CompletedProcess
            with patch.object(module.subprocess, "run", return_value=CompletedProcess([], 0, stdout=dynamic)):
                errors = module.check({"3.5.1": aar})
            self.assertTrue(any("original native filename" in e for e in errors))
            self.assertTrue(any("original LibVLC SONAME/NEEDED" in e for e in errors))


    def test_native_elf_architecture_matches_abi(self):
        for abi, machine in module.ELF_MACHINES.items():
            binary = bytearray(20)
            binary[:4] = b"\x7fELF"
            binary[4] = module.ELF_CLASSES[abi]
            binary[5] = 1
            binary[6] = 1
            binary[16:18] = (3).to_bytes(2, "little")
            binary[18:20] = machine.to_bytes(2, "little")
            path = f"jni/{abi}/libvlc_versioned.so"
            self.assertEqual(module.native_abi_errors("3.5.1", path, binary), [])
            binary[18:20] = (machine + 1).to_bytes(2, "little")
            self.assertTrue(module.native_abi_errors("3.5.1", path, binary))
            self.assertTrue(module.native_abi_errors("3.5.1", path, b"not ELF"))


    def test_native_elf_rejects_executable_and_big_endian(self):
        binary = bytearray(20)
        binary[:4] = b"\x7fELF"
        binary[4] = 2
        binary[5] = 1
        binary[6] = 1
        binary[16:18] = (3).to_bytes(2, "little")
        binary[18:20] = (183).to_bytes(2, "little")
        path = "jni/arm64-v8a/libvlc_versioned.so"
        self.assertEqual(module.native_abi_errors("3.5.1", path, binary), [])
        binary[16:18] = (2).to_bytes(2, "little")
        self.assertTrue(any("ET_DYN" in error for error in module.native_abi_errors("3.5.1", path, binary)))
        binary[16:18] = (3).to_bytes(2, "little")
        binary[5] = 2
        self.assertTrue(any("little-endian" in error for error in module.native_abi_errors("3.5.1", path, binary)))


    def test_native_elf_os_abi_identity(self):
        binary = bytearray(20)
        binary[:4] = bytes((127, 69, 76, 70))
        path = "jni/arm64-v8a/libvlc_versioned.so"
        self.assertEqual(module.native_elf_identity_errors("3.5.1", path, binary), [])
        binary[7] = 3  # Linux ELF OS ABI is permitted.
        self.assertEqual(module.native_elf_identity_errors("3.5.1", path, binary), [])
        binary[7] = 9  # FreeBSD is not Android.
        self.assertTrue(module.native_elf_identity_errors("3.5.1", path, binary))
        binary[7] = 0
        binary[8] = 1
        self.assertTrue(module.native_elf_identity_errors("3.5.1", path, binary))


    def test_duplicate_and_unsafe_aar_members_rejected(self):
        import io
        import tempfile
        import warnings
        import zipfile
        with tempfile.TemporaryDirectory() as tmp:
            aar = pathlib.Path(tmp) / "unsafe.aar"
            jar_buffer = io.BytesIO()
            with zipfile.ZipFile(jar_buffer, "w") as jar:
                jar.writestr("com/softppj/isolatedvlc/v3_5_1/Player.class", b"")
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(aar, "w") as archive:
                    archive.writestr("classes.jar", jar_buffer.getvalue())
                    archive.writestr("classes.jar", jar_buffer.getvalue())
                    archive.writestr("../escape.txt", b"bad")
            errors = module.check({"3.5.1": aar})
            self.assertTrue(any("duplicate AAR archive members" in error for error in errors))
            self.assertTrue(any("unsafe AAR archive path" in error for error in errors))


    def test_embedded_legacy_core_loader_names_rejected(self):
        for name in (b"libvlccore.so", b"libcompatvlc.so"):
            errors = module.embedded_jni_reference_errors(
                "3.7.7", "jni/arm64-v8a/libvlcjni_377.so", b"prefix\\x00" + name + b"\\x00")
            self.assertTrue(any(name.decode() in error for error in errors))


    def test_duplicate_and_unsafe_classes_jar_members_rejected(self):
        import io
        import tempfile
        import warnings
        import zipfile
        with tempfile.TemporaryDirectory() as tmp:
            aar = pathlib.Path(tmp) / "unsafe-classes.aar"
            jar_buffer = io.BytesIO()
            with warnings.catch_warnings():
                warnings.simplefilter("ignore", UserWarning)
                with zipfile.ZipFile(jar_buffer, "w") as jar:
                    jar.writestr("com/softppj/isolatedvlc/v3_5_1/Player.class", b"")
                    jar.writestr("com/softppj/isolatedvlc/v3_5_1/Player.class", b"")
                    jar.writestr("../outside.class", b"")
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr("classes.jar", jar_buffer.getvalue())
            errors = module.check({"3.5.1": aar})
            self.assertTrue(any("duplicate classes.jar archive members" in error for error in errors))
            self.assertTrue(any("unsafe classes.jar archive path" in error for error in errors))


    def test_relocated_class_path_with_legacy_bytecode_references_rejected(self):
        import io
        import tempfile
        import zipfile
        with tempfile.TemporaryDirectory() as tmp:
            aar = pathlib.Path(tmp) / "stale-bytecode.aar"
            jar_buffer = io.BytesIO()
            with zipfile.ZipFile(jar_buffer, "w") as jar:
                jar.writestr("com/softppj/isolatedvlc/v3_5_1/Player.class",
                             b"descriptor:org/videolan/libvlc/MediaPlayer;loader:libvlcjni.so")
            with zipfile.ZipFile(aar, "w") as archive:
                archive.writestr("classes.jar", jar_buffer.getvalue())
            errors = module.check({"3.5.1": aar})
            self.assertTrue(any("unrelocated bytecode reference" in error for error in errors))
            self.assertTrue(any("unrelocated Java loader reference" in error for error in errors))

    def test_empty_dynamic_section(self):
        self.assertEqual(module.parse_dynamic_dependencies("no dynamic section"), ([], []))

if __name__ == "__main__":
    unittest.main()
