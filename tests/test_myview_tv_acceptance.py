"""Non-device regression tests for MyView evidence collector."""
import importlib.util
import pathlib
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / "tools" / "myview_tv_acceptance.py"
spec = importlib.util.spec_from_file_location("myview_tv_acceptance", MODULE_PATH)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class EvidenceSafetyTests(unittest.TestCase):
    def test_all_scenarios_have_unique_identifiers(self):
        ids = [code for code, _ in module.CASES]
        self.assertEqual(len(ids), 30)
        self.assertEqual(len(ids), len(set(ids)))

    def test_urls_and_credentials_are_redacted(self):
        content = "stream https://tv.example/live/key/secret.m3u8 token=topsecret"
        output = module.redact(content)
        self.assertNotIn("tv.example", output)
        self.assertNotIn("topsecret", output)

    def test_logcat_collection_is_not_limited_to_default_18k(self):
        long_text = "x" * 21000 + "FATAL EXCEPTION" + "y" * 100
        with mock.patch.object(module, "adb", return_value=(long_text, "", 0)):
            result = module.command_output(None, "logcat", max_output=250000)
        self.assertIn("FATAL EXCEPTION", result["output"])
        self.assertFalse(result["truncated"])

    def test_logcat_limit_is_fail_closed(self):
        with mock.patch.object(module, "adb", return_value=("x" * 250000, "", 0)):
            result = module.command_output(None, "logcat", max_output=250000)
        self.assertTrue(result["truncated"])


if __name__ == "__main__":
    unittest.main()
