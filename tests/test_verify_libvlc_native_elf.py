import importlib.util
import pathlib
import tempfile
import unittest
import zipfile
from unittest.mock import patch
from types import SimpleNamespace

ROOT = pathlib.Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location("elf_audit", ROOT / "tools/verify_libvlc_native_elf.py")
AUDIT = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(AUDIT)

class NativeElfAuditTest(unittest.TestCase):
    def test_valid_elf_and_jni_symbols_are_parsed(self):
        with tempfile.TemporaryDirectory() as temp:
            path = pathlib.Path(temp) / "sample.aar"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("jni/arm64-v8a/libexample.so", bytes((127,69,76,70)) + b"payload")
            output = """
 0x0000000000000001 (NEEDED) Shared library: [libc.so]
 0x000000000000000e (SONAME) Library soname: [libexample.so]
  14: 0000  0 FUNC GLOBAL DEFAULT  11 Java_example_Player_start
"""
            with patch.object(AUDIT.subprocess, "run", return_value=SimpleNamespace(returncode=0,stdout=output,stderr="")):
                libs, needed, exports, errors = AUDIT.inspect(str(path), "llvm-readelf")
            self.assertEqual([], errors)
            self.assertEqual(["libexample.so"], libs["arm64-v8a"])
            self.assertIn("libc.so", needed["arm64-v8a"])
            self.assertIn("Java_example_Player_start", exports)

    def test_non_elf_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            path = pathlib.Path(temp) / "bad.aar"
            with zipfile.ZipFile(path, "w") as archive:
                archive.writestr("jni/arm64-v8a/libfake.so", b"not an elf")
            _, _, _, errors = AUDIT.inspect(str(path), "llvm-readelf")
            self.assertTrue(any("not ELF" in item for item in errors))

if __name__ == "__main__":
    unittest.main()
