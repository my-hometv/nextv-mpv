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

    def test_disabled_playlist_browse_and_surfing_filters(self):
        self.assertIn("enabledChannelIndices()",between("private fun showOverlayList(", "private fun showLeftNavigation("))
        self.assertIn("enabledChannelIndices()",between("private fun showLeftNavigation(", "private fun showLeftActions("))
        self.assertIn("enabledChannelIndices()",between("private fun showGroupDrawerContents(", "private fun channelVolumeKey("))
        self.assertIn("enabledChannelIndices() else playlistIndices()",MAIN)
        visible=between("private fun visibleIndices()", "private fun channelLabel(")
        self.assertIn("channel.source in enabledSources",visible)
        self.assertIn("channel.source.isBlank()",visible)
        self.assertIn("val enabledSources=playlists().filter{it.enabled}",visible)
        self.assertIn("invalidateSurfingCache()",between("private fun savePlaylists(", "private fun mediaItems("))
    def test_manual_picture_custom_transition_avoids_uncommitted_retune(self):
        body=between("private fun configureChannelPicture(", "private fun showChannelOptions(")
        self.assertIn("clearPicturePreview(activeIndex,c,restart=false)",body)
    def test_epg_partial_guide_retry_and_deadline_socket_close(self):
        loader=between("private fun loadEpg(", "private fun showChannelEpg(")
        self.assertIn("nextIncompleteRetry",loader)
        self.assertIn("epgIncompleteRetryAt=if(incomplete)",loader)
        self.assertIn('java.util.Timer("MyView-EPG-deadline",true)',loader)
        self.assertIn("cancellation.cancel()",loader)
        self.assertIn("deadlineTimer.cancel()",loader)
        self.assertIn("reachedDeadline.get()",loader)
        self.assertIn("replacement!=null",loader)

    def test_playlist_disable_and_deletion_switch_immediately(self):
        manager=between("private fun managePlaylists()", "private fun startAddedPlaylist(")
        drawer=between("private fun showPlaylistDrawer()", "private fun showGroupDrawer(")
        self.assertIn("disablePlaylistSource(p.name)",manager)
        self.assertIn("deletePlaylistSource(p.name)",manager)
        self.assertIn("disablePlaylistSource(source)",drawer)
        self.assertIn("switchAfterPlaylistRemoved(name,previous,wasPlaying,wasSelected)",MAIN)
        self.assertIn('prefs.getString("last_working_url_"+source,null)',MAIN)
    def test_last_channel_and_startup_reject_disabled_sources(self):
        last=between("private fun returnToPreviousChannel()", "private fun routeHttpChannel(")
        self.assertIn("candidate.source in enabled",last)
        self.assertIn('showBanner("Previous channel is unavailable")',last)
        self.assertIn('showBanner("This playlist is disabled or deleted",true)',MAIN)
        startup=between("private fun resumeLast()", "private fun liveSettings(")
        self.assertIn("it.enabled && it.name==source",startup)
    def test_pin_failure_delete_uses_same_next_playlist_handoff(self):
        deletion=between("private fun deletePlaylistAfterPinFailures(", "private fun deleteGroupAfterPinFailures(")
        self.assertIn("removePlaylistSource(name,true)",deletion)

    def test_delete_stops_correct_vod_before_channel_indices_change(self):
        block=between("private fun removePlaylistSource(", "private fun clearPlaylistLocalHistory(")
        self.assertLess(block.index("if(wasPlaying)stopPlayback()"),block.index("channels=channels.filter"))
        self.assertLess(block.index("clearPlaylistLocalHistory(name)"),block.index("channels=channels.filter"))
    def test_unrelated_playlist_disable_preserves_active_decoder_and_index(self):
        transition=between("private fun switchAfterPlaylistRemoved(", "private fun disablePlaylistSource(")
        self.assertIn("if(keepWatching)",transition)
        self.assertIn("return",transition)
        delete=between("private fun removePlaylistSource(", "private fun clearPlaylistLocalHistory(")
        self.assertIn("if(active !in channels.indices)",delete)
    def test_skip_entirely_hidden_locked_replacements(self):
        select=between("private fun nextAvailablePlaylistChannel(", "private fun clearRemovedPreviousChannel(")
        self.assertIn("PlaylistTransitionPolicy.nextEligiblePlaylist(",select)
        self.assertIn("eligible(name).isNotEmpty()",select)
        self.assertIn("!isManuallyHidden(c)",select)
        self.assertIn("!isLocked(c)",select)
    def test_confirmed_last_channel_is_preferred_over_failed_attempt(self):
        select=between("private fun nextAvailablePlaylistChannel(", "private fun clearRemovedPreviousChannel(")
        self.assertLess(select.index("channels[it].url==confirmed"),select.index("channels[it].url==attempted"))
        self.assertIn('putString("last_working_url_"+c.source,c.url)',MAIN)
    def test_removed_previous_channel_is_cleared_by_identity(self):
        clear=between("private fun clearRemovedPreviousChannel(", "private fun switchAfterPlaylistRemoved(")
        self.assertIn("identity==null || id(c)==identity",clear)
        self.assertIn("c.source in enabled",clear)

if __name__=="__main__":unittest.main()
