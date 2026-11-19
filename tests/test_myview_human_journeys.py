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
        self.assertIn("previewPictureChoice(activeIndex,c,mode)",dialog)
        self.assertIn('.setNeutralButton("Preview",null)',dialog)
        self.assertNotIn("previewPictureChoice(activeIndex,c,modes[position])",dialog)
        self.assertIn("if(!pictureApplied)clearPicturePreview(activeIndex,c)",dialog)
        self.assertIn("handler.postDelayed(task,650L)",MAIN)
        self.assertIn("clearPicturePreview(index,c,restart=false)",MAIN)
    def test_brand_and_app_untouched(self):
        self.assertIn('reset("MyView  •  HOME")',MAIN)
        self.assertIn("private fun signedLicenseCache(",MAIN)
        self.assertIn("private fun playIndex(",MAIN)

    def test_home_settings_back_matches_myview_title(self):
        reset=between("private fun reset(title:String)", "private fun playlists():")
        self.assertIn('title=="MyView  •  HOME"',reset)
        self.assertNotIn('title.startsWith("My Home TV',reset)
    def test_media_headings_show_myview(self):
        self.assertNotIn('reset("My Home TV',MAIN)
        self.assertIn('reset("MyView  •  Series")',MAIN)
        self.assertIn('reset("MyView  •  Music")',MAIN)
    def test_epg_budget_and_failing_feed_backoff(self):
        loader=between("private fun loadEpg(", "private fun showChannelEpg(")
        self.assertIn("EpgLoadBudget.remainingMs(",loader)
        self.assertIn("EpgLoadBudget.connectionTimeoutMs(remaining)",loader)
        self.assertIn("EpgLoadBudget.readTimeoutMs(remaining)",loader)
        self.assertIn("temporarily skipped after recent failure",loader)
        self.assertIn("showProgress:Boolean=false",loader)
        self.assertIn("epgFailedUntil.clear()",MAIN)

if __name__=="__main__":unittest.main()
