#!/usr/bin/env python3
"""Offline tests for fail-closed LibVLC release provenance."""
import hashlib
import importlib.util
import pathlib
import tempfile
import unittest

path = pathlib.Path(__file__).with_name("gate_multi_engine_release.py")
spec = importlib.util.spec_from_file_location("gate_multi_engine_release", path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class ReleaseGateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.records = {}
        for version in module.VERSIONS:
            aar = pathlib.Path(self.tmp.name) / (version + ".aar")
            aar.write_bytes(version.encode())
            self.records[version] = {
                "source_commit": "a" * 40, "sha256": hashlib.sha256(aar.read_bytes()).hexdigest(),
                "output_aar": str(aar)}
        self.data = {"versions": self.records, "relocation_verified": True}

    def test_unverified_provenance_rejected(self):
        self.data["relocation_verified"] = False
        with self.assertRaisesRegex(ValueError, "Relocation verification"):
            module.verified_artifacts(self.data)

    def test_missing_verification_flag_rejected(self):
        del self.data["relocation_verified"]
        with self.assertRaisesRegex(ValueError, "Relocation verification"):
            module.verified_artifacts(self.data)

    def test_valid_provenance(self):
        self.assertEqual(len(module.verified_artifacts(self.data)), 3)

    def test_missing_version(self):
        del self.records["3.7.7"]
        with self.assertRaisesRegex(ValueError, "exactly three"):
            module.verified_artifacts(self.data)

    def test_modified_binary(self):
        pathlib.Path(self.records["3.7.7"]["output_aar"]).write_bytes(b"changed")
        with self.assertRaisesRegex(ValueError, "mismatch"):
            module.verified_artifacts(self.data)

    def test_invalid_commit(self):
        self.records["3.5.1"]["source_commit"] = "unknown"
        with self.assertRaisesRegex(ValueError, "source commit"):
            module.verified_artifacts(self.data)

    def test_duplicate_artifact(self):
        self.records["3.7.7"] = dict(self.records["3.5.1"])
        with self.assertRaisesRegex(ValueError, "share an AAR"):
            module.verified_artifacts(self.data)

if __name__ == "__main__":
    unittest.main()
