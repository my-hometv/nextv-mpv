"""Source-level regression guard for the channel-settings hide path."""
import pathlib
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main/java/com/softppj/nextv/MainActivity.kt"


class ChannelSettingsHideContractTest(unittest.TestCase):
    def test_more_settings_hide_uses_directional_handoff(self):
        source = MAIN.read_text(encoding="utf-8")
        start = source.index("private fun showChannelOptions()")
        end = source.index("private fun sideAction(", start)
        settings = source[start:end]
        self.assertIn("val previousSequence=surfingIndices()", settings)
        self.assertIn("val hiding=!isManuallyHidden(c)", settings)
        self.assertIn("moveAfterFailedChannelHidden(oldIndex,screen,previousSequence)", settings)
        self.assertIn("if(hiding && screen!=null", settings)


if __name__ == "__main__":
    unittest.main()
