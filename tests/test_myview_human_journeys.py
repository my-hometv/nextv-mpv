"""Regression guard for MyView human-style navigation and EPG journeys."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
MAIN=(ROOT/"app/src/main/java/com/softppj/nextv/MainActivity.kt").read_text()
EPG=(ROOT/"app/src/main/java/com/softppj/nextv/EpgSourceEditorPolicy.kt").read_text()

def between(start,end):
    return MAIN.split(start,1)[1].split(end,1)[0]

class HumanJourneyTest(unittest.TestCase):
    def test_combined_browsing_preserves_surfing_scope(self):
        self.assertIn("PlaylistBrowsePolicy.displayedIndices(",between("private fun showOverlayList(", "private fun showLeftNavigation("))
        self.assertIn('putBoolean("myview_browse_all_playlists",selected==null)',MAIN)
        self.assertIn('putString("selected_playlist_source",c.source)',MAIN)
        self.assertIn("else playlistIndices()",between("private fun surfingIndices()", "private fun toggleChannelSkip("))
    def test_legacy_epg_surplus_not_silently_truncated(self):
        self.assertNotIn("take(MAX_MANUAL_SOURCES)",EPG)
        self.assertIn("Legacy XMLTV sources preserved",MAIN)
        self.assertIn("maxActiveEpgFeeds=32",MAIN)
    def test_epg_matching_coverage_displayed(self):
        self.assertIn("Omitted by ",MAIN)
        self.assertIn("tvg-id first",MAIN)
        self.assertIn("Live channels with current or upcoming programme",MAIN)
    def test_picture_preview_and_cancel(self):
        dialog=between("private fun configureChannelPicture(", "private fun showChannelOptions(")
        self.assertIn("previewPictureChoice(activeIndex,c,modes[position])",dialog)
        self.assertIn("if(!pictureApplied)clearPicturePreview(activeIndex,c)",dialog)
        self.assertIn("handler.postDelayed(task,650L)",MAIN)
    def test_brand_and_app_untouched(self):
        self.assertIn('reset("MyView  •  HOME")',MAIN)
        self.assertIn("private fun signedLicenseCache(",MAIN)
        self.assertIn("private fun playIndex(",MAIN)

if __name__=="__main__":unittest.main()
