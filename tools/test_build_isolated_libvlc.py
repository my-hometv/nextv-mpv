#!/usr/bin/env python3
"""Offline tests for the pinned-source LibVLC build orchestrator."""
import importlib.util
import json
import pathlib
import subprocess
import tempfile
import unittest
from unittest import mock

MODULE = pathlib.Path(__file__).with_name("build_isolated_libvlc.py")
spec = importlib.util.spec_from_file_location("build_isolated_libvlc", MODULE)
build_module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(build_module)


class BuildGateTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = pathlib.Path(self.temp.name)
        self.entries = {}
        for version in build_module.VERSIONS:
            src = self.root / ("source_" + version.replace(".", "_").replace("-", "_"))
            src.mkdir()
            subprocess.run(["git", "init", "-q", str(src)], check=True)
            subprocess.run(["git", "-C", str(src), "-c", "user.name=CI",
                            "-c", "user.email=ci@example.invalid", "commit",
                            "--allow-empty", "-qm", "initial"], check=True)
            sha = subprocess.check_output(["git", "-C", str(src), "rev-parse", "HEAD"], text=True).strip()
            self.entries[version] = {
                "source_dir": str(src), "git_commit": sha,
                "build_command": ["python3", "-c", "from pathlib import Path; Path('out.aar').write_bytes(b'aar')"],
                "output_aar": str(src / "out.aar"),
            }
        self.manifest = {"versions": self.entries}
        self.provenance = self.root / "provenance.json"

    def test_submodule_status_rejects_uninitialized_or_mismatched(self):
        source = pathlib.Path(self.entries["3.5.1"]["source_dir"])
        real_run = build_module.run
        for marker in ("-", "+", "U"):
            def fake_run(args, cwd):
                if args[:3] == ["git", "submodule", "status"]:
                    return mock.Mock(stdout=marker + "a" * 40 + " vendor/vlc\\n")
                return real_run(args, cwd)
            with self.subTest(marker=marker), mock.patch.object(build_module, "run", side_effect=fake_run):
                with self.assertRaisesRegex(ValueError, "Submodule"):
                    build_module.pinned_submodules(source)

    def test_submodule_revisions_recorded_in_provenance(self):
        records = build_module.build(self.manifest, self.provenance)
        self.assertEqual(records["3.5.1"]["submodules"], [])
        saved = json.loads(self.provenance.read_text())["versions"]
        self.assertEqual(saved["3.5.1"]["submodules"], [])

    def test_builds_and_records_hashes(self):
        records = build_module.build(self.manifest, self.provenance)
        self.assertEqual(set(records), set(build_module.VERSIONS))
        saved = json.loads(self.provenance.read_text())["versions"]
        self.assertEqual(set(saved), set(build_module.VERSIONS))
        for version in build_module.VERSIONS:
            self.assertEqual(len(saved[version]["sha256"]), 64)

    def test_verifier_failure_does_not_publish_provenance(self):
        real_run = subprocess.run
        def intercept(command, *args, **kwargs):
            if any("verify_libvlc_isolation.py" in str(part) for part in command):
                raise subprocess.CalledProcessError(1, command)
            return real_run(command, *args, **kwargs)
        with mock.patch.object(build_module.subprocess, "run", side_effect=intercept):
            with self.assertRaises(subprocess.CalledProcessError):
                build_module.build(self.manifest, self.provenance, verify_relocation=True)
        self.assertFalse(self.provenance.exists())

    def test_existing_provenance_is_not_overwritten(self):
        self.provenance.write_text("previous")
        with self.assertRaisesRegex(ValueError, "Provenance already exists"):
            build_module.build(self.manifest, self.provenance)
        self.assertEqual(self.provenance.read_text(), "previous")

    def test_duplicate_source_checkout_fails_before_any_build(self):
        self.entries["3.7.7"]["source_dir"] = self.entries["3.5.1"]["source_dir"]
        self.entries["3.7.7"]["git_commit"] = self.entries["3.5.1"]["git_commit"]
        with self.assertRaisesRegex(ValueError, "source_dir overlaps"):
            build_module.build(self.manifest, self.provenance)
        for version in build_module.VERSIONS:
            self.assertFalse(pathlib.Path(self.entries[version]["output_aar"]).exists())
        self.assertFalse(self.provenance.exists())

    def test_nested_source_checkout_fails_before_any_build(self):
        parent = pathlib.Path(self.entries["3.5.1"]["source_dir"])
        nested = parent / "nested"
        nested.mkdir()
        subprocess.run(["git", "init", "-q", str(nested)], check=True)
        subprocess.run(["git", "-C", str(nested), "-c", "user.name=CI",
                        "-c", "user.email=ci@example.invalid", "commit",
                        "--allow-empty", "-qm", "nested"], check=True)
        self.entries["3.7.7"]["source_dir"] = str(nested)
        self.entries["3.7.7"]["git_commit"] = subprocess.check_output(
            ["git", "-C", str(nested), "rev-parse", "HEAD"], text=True).strip()
        with self.assertRaisesRegex(ValueError, "source_dir overlaps"):
            build_module.build(self.manifest, self.provenance)
        self.assertFalse(self.provenance.exists())
        self.assertFalse(pathlib.Path(self.entries["3.5.1"]["output_aar"]).exists())

    def test_output_directory_is_rejected_before_build(self):
        self.entries["3.7.7"]["output_aar"] = str(self.root)
        with self.assertRaisesRegex(ValueError, "output already exists"):
            build_module.build(self.manifest, self.provenance)
        self.assertFalse(pathlib.Path(self.entries["3.5.1"]["output_aar"]).exists())

    def test_duplicate_output_path_fails_before_any_build(self):
        self.entries["3.7.7"]["output_aar"] = self.entries["3.5.1"]["output_aar"]
        with self.assertRaisesRegex(ValueError, "shared by multiple versions"):
            build_module.build(self.manifest, self.provenance)
        self.assertFalse(pathlib.Path(self.entries["3.5.1"]["output_aar"]).exists())

    def test_output_cannot_alias_provenance_before_build(self):
        self.entries["4.0.0-eap29"]["output_aar"] = str(self.provenance)
        with self.assertRaisesRegex(ValueError, "cannot equal provenance path"):
            build_module.build(self.manifest, self.provenance)
        for version in build_module.VERSIONS:
            self.assertFalse(pathlib.Path(self.entries[version]["output_aar"]).exists())

    def test_existing_provenance_fails_before_any_build(self):
        self.provenance.write_text("previous")
        with self.assertRaisesRegex(ValueError, "Provenance already exists"):
            build_module.build(self.manifest, self.provenance)
        self.assertFalse(pathlib.Path(self.entries["3.5.1"]["output_aar"]).exists())

    def test_unverified_build_is_marked_unverified(self):
        build_module.build(self.manifest, self.provenance)
        self.assertFalse(json.loads(self.provenance.read_text())["relocation_verified"])
    def test_missing_version_fails(self):
        del self.entries["3.5.1"]
        with self.assertRaises(ValueError):
            build_module.build(self.manifest, self.provenance)

    def test_commit_mismatch_fails(self):
        self.entries["3.5.1"]["git_commit"] = "0" * 40
        with self.assertRaisesRegex(ValueError, "commit mismatch"):
            build_module.build(self.manifest, self.provenance)

    def test_dirty_tree_fails(self):
        pathlib.Path(self.entries["3.5.1"]["source_dir"], "dirty.txt").write_text("dirty")
        with self.assertRaisesRegex(ValueError, "dirty"):
            build_module.build(self.manifest, self.provenance)

    def test_existing_output_fails(self):
        self.entries["3.5.1"]["output_aar"] = str(self.root / "stale.aar")
        pathlib.Path(self.entries["3.5.1"]["output_aar"]).write_bytes(b"stale")
        with self.assertRaisesRegex(ValueError, "already exists"):
            build_module.build(self.manifest, self.provenance)

    def test_late_invalid_version_does_not_start_earlier_builds(self):
        self.entries["4.0.0-eap29"]["git_commit"] = "0" * 40
        with self.assertRaisesRegex(ValueError, "commit mismatch"):
            build_module.build(self.manifest, self.provenance)
        for version in build_module.VERSIONS:
            self.assertFalse(pathlib.Path(self.entries[version]["output_aar"]).exists())
        self.assertFalse(self.provenance.exists())

    def test_late_stale_output_does_not_start_earlier_builds(self):
        stale = self.root / "late_stale.aar"
        stale.write_bytes(b"stale")
        self.entries["4.0.0-eap29"]["output_aar"] = str(stale)
        with self.assertRaisesRegex(ValueError, "already exists"):
            build_module.build(self.manifest, self.provenance)
        for version in build_module.VERSIONS[:2]:
            self.assertFalse(pathlib.Path(self.entries[version]["output_aar"]).exists())
        self.assertFalse(self.provenance.exists())


    def test_build_cannot_modify_tracked_source(self):
        source = pathlib.Path(self.entries["3.5.1"]["source_dir"])
        tracked = source / "tracked.txt"
        tracked.write_text("original")
        subprocess.run(["git", "-C", str(source), "add", "tracked.txt"], check=True)
        subprocess.run(["git", "-C", str(source), "-c", "user.name=CI",
                        "-c", "user.email=ci@example.invalid", "commit", "-qm", "add tracked file"], check=True)
        self.entries["3.5.1"]["git_commit"] = subprocess.check_output(
            ["git", "-C", str(source), "rev-parse", "HEAD"], text=True).strip()
        self.entries["3.5.1"]["build_command"] = [
            "python3", "-c", "from pathlib import Path; Path('tracked.txt').write_text('modified'); Path('out.aar').write_bytes(b'aar')"]
        with self.assertRaisesRegex(ValueError, "tracked source files changed"):
            build_module.build(self.manifest, self.provenance)
        self.assertFalse(self.provenance.exists())

    def test_build_cannot_stage_new_source_files(self):
        self.entries["3.5.1"]["build_command"] = [
            "python3", "-c",
            "from pathlib import Path; import subprocess; "
            "Path('new_source.txt').write_text('unexpected'); "
            "subprocess.run(['git', 'add', 'new_source.txt'], check=True); "
            "Path('out.aar').write_bytes(b'aar')"]
        with self.assertRaisesRegex(ValueError, "build staged new source files"):
            build_module.build(self.manifest, self.provenance)
        self.assertFalse(self.provenance.exists())

    def test_build_cannot_move_pinned_head(self):
        self.entries["3.5.1"]["build_command"] = [
            "git", "-c", "user.name=CI", "-c", "user.email=ci@example.invalid",
            "commit", "--allow-empty", "-qm", "unexpected build commit"]
        with self.assertRaisesRegex(ValueError, "source HEAD changed during build"):
            build_module.build(self.manifest, self.provenance)
        self.assertFalse(self.provenance.exists())

    def test_later_build_cannot_rewrite_earlier_artifact(self):
        earlier = pathlib.Path(self.entries["3.5.1"]["output_aar"])
        self.entries["4.0.0-eap29"]["build_command"] = [
            "python3", "-c",
            "from pathlib import Path; "
            f"Path({str(earlier)!r}).write_bytes(b'tampered'); "
            "Path('out.aar').write_bytes(b'aar')"]
        with self.assertRaisesRegex(ValueError, "output artifact changed after build"):
            build_module.build(self.manifest, self.provenance)
        self.assertFalse(self.provenance.exists())

    def test_later_build_cannot_modify_earlier_source(self):
        earlier_source = pathlib.Path(self.entries["3.5.1"]["source_dir"])
        tracked = earlier_source / "tracked.txt"
        tracked.write_text("original")
        subprocess.run(["git", "-C", str(earlier_source), "add", "tracked.txt"], check=True)
        subprocess.run(["git", "-C", str(earlier_source), "-c", "user.name=CI",
                        "-c", "user.email=ci@example.invalid", "commit", "-qm", "tracked"], check=True)
        self.entries["3.5.1"]["git_commit"] = subprocess.check_output(
            ["git", "-C", str(earlier_source), "rev-parse", "HEAD"], text=True).strip()
        self.entries["4.0.0-eap29"]["build_command"] = [
            "python3", "-c", "from pathlib import Path; "
            f"Path({str(tracked)!r}).write_text('tampered'); "
            "Path('out.aar').write_bytes(b'aar')"]
        with self.assertRaisesRegex(ValueError, "tracked source files changed after build"):
            build_module.build(self.manifest, self.provenance)
        self.assertFalse(self.provenance.exists())

    def test_failed_command_fails(self):
        self.entries["3.5.1"]["build_command"] = ["python3", "-c", "raise SystemExit(7)"]
        with self.assertRaises(subprocess.CalledProcessError):
            build_module.build(self.manifest, self.provenance)


if __name__ == "__main__":
    unittest.main()
