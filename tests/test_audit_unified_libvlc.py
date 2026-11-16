import tempfile
import unittest
from pathlib import Path
from tools.audit_unified_libvlc import audit, VERSIONS


class UnifiedLibVlcAuditTests(unittest.TestCase):
    def test_missing_native_inputs_fail_closed(self):
        with tempfile.TemporaryDirectory() as temp:
            result = audit(Path(temp))
            self.assertFalse(result["ready_for_native_verification"])
            self.assertEqual(3, len(result["checks"]))

    def test_only_real_inputs_and_adapters_allow_next_verification_stage(self):
        with tempfile.TemporaryDirectory() as temp:
            app = Path(temp)
            for aar, adapter in VERSIONS.values():
                native = app / "native-runtimes" / aar
                native.parent.mkdir(parents=True, exist_ok=True)
                native.write_bytes(b"placeholder: file-presence test only")
                source = app / "src" / "unified" / "java" / "com" / "softppj" / "nextv" / "nativevlc" / adapter
                source.parent.mkdir(parents=True, exist_ok=True)
                source.write_text("// placeholder", encoding="utf-8")
            result = audit(app)
            self.assertTrue(result["ready_for_native_verification"])


if __name__ == "__main__":
    unittest.main()
