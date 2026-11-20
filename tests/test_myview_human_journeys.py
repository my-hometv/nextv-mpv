"""Regression guard for MyView human-style navigation and EPG journeys."""
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[1]
MAIN=(ROOT/"app/src/main/java/com/softppj/nextv/MainActivity.kt").read_text()
EPG=(ROOT/"app/src/main/java/com/softppj/nextv/EpgSourceEditorPolicy.kt").read_text()

def between(start,end):
    return MAIN.split(start,1)[1].split(end,1)[0]

class HumanJourneyTest(unittest.TestCase):
    def test_invalid_numeric_channel_message_auto_dismisses(self):
        numeric=between("private var channelDigits=", "private var previewIndex=")
        banner=between("private fun showBanner(", "private fun styledMenuList()")
        self.assertIn('showBanner("Channel "+number+" is not available",true,2500L)',numeric)
        self.assertIn('dismissAfterMs:Long=if(error)4000L else 2500L',MAIN)
        self.assertIn('if(dismissAfterMs>0L)',banner)
        self.assertIn('handler.postDelayed(task,dismissAfterMs)',banner)
        self.assertIn('if(dismissAfterMs>0L)',banner)
        # All error notifications now dismiss automatically; numeric errors
        # retain their shorter explicit timeout.
        self.assertNotIn('if(!error){',banner)
        self.assertIn('clearNumericOverlay()',numeric)

    def test_vod_resume_survives_engine_fallback_but_not_explicit_restart(self):
        body=between("private var requestedMediaResumeSeconds=", "private fun mediaDetails(")
        tune=between("private fun playIndex(", "private fun routeHttpChannel(") if False else MAIN
        failure=between("private fun handleEngineFailure(", "private fun isLocked(")
        self.assertIn("private fun playRecoveryIndex(index:Int)",body)
        self.assertIn("startupResumeChannelKey==channelKey(channel)",body)
        self.assertIn("requestedMediaResumeSeconds=startupResumeSeconds",body)
        self.assertIn("playRecoveryIndex(next.first)",failure)
        self.assertIn("try{playRecoveryIndex(index)}finally{preservingRecoveryScan=false}",failure)
        self.assertIn("if(resumeSeconds>0L)",MAIN)
        self.assertIn("startupResumeChannelKey=null",MAIN)
        self.assertIn("if(startupResumeChannelKey==channelKey(c))",MAIN)

    def test_web_tv_back_returns_to_library_and_closes_directory_dialog(self):
        back=between("override fun onBackPressed(){", "private fun stopPlayback(){")
        self.assertNotIn("showWebTvDirectory(web!!,active,screen)",back)
        self.assertIn("returnToMediaLibrary()",back)
        self.assertIn('setNegativeButton("Back to Web TV"){_,_->mediaLibrary("web")}',MAIN)
        self.assertIn("webTvDirectoryEntries=emptyList()",MAIN)

    def test_epg_matching_populates_visible_channel_info(self):
        info=between("private fun showChannelInfo(", "private fun clearChannelPreview(")
        self.assertIn("val programmes=if(showEpg)programmeList(channel)",info)
        self.assertIn('programmeColumn("NOW",currentTitle,currentTime)',info)
        self.assertIn('programmeColumn("NEXT",nextTitle,nextTime)',info)
        self.assertIn('tag="myview_channel_info"',info)
        matcher=between("private fun programmeList(", "private fun runNetworkDiagnostics(")
        self.assertIn("HomeTvPolicies.matchEpg(epgGuide,id,name)",matcher)
        self.assertIn("EpgGuidePolicy.matchNames(c.name)",matcher)
        self.assertIn("epg_offset_minutes",matcher)

    def test_channel_info_epg_refreshes_at_programme_boundaries(self):
        info=between("private fun showChannelInfo(", "private fun clearChannelPreview(")
        self.assertIn("fun armProgrammeBoundary(rows:List<EpgProgramme>)", info)
        self.assertIn(".flatMap{sequenceOf(it.start,it.end)}", info)
        self.assertIn(".filter{it>moment}", info)
        self.assertIn("handler.postDelayed(task,(boundary-moment).coerceAtLeast(1L))", info)
        self.assertIn("nowTitleView.text=onNow?.title", info)
        self.assertIn("nextTitleView.text=onNext?.title", info)
        self.assertIn("nowTimeView.text=onNow?.let", info)
        self.assertIn("nextTimeView.text=onNext?.let", info)
        self.assertIn("armProgrammeBoundary(updated)", info)
        self.assertIn("if(banner!==panel || panel.parent!==screen ||", info)
        self.assertIn("cancelChannelInfoEpgBoundaryRefresh()", info)
        self.assertIn("if(!isPreview)handler.postDelayed(task,4000)", info)
        self.assertNotIn("showChannelInfo(index)", info.split("val task=Runnable{",1)[1].split("armProgrammeBoundary(programmes)",1)[0])

    def test_channel_info_epg_timer_cleans_up_on_exit(self):
        self.assertIn("channelInfoEpgBoundaryTask?.let{handler.removeCallbacks(it)}", MAIN)
        for start,end in (
            ("private fun showBanner(", "private fun styledMenuList()"),
            ("private fun clearChannelPreview()", "private fun confirmPreview()"),
            ("private fun stopPlayback()", "override fun onDestroy()"),
            ("override fun onDestroy()", "override fun onBackPressed()")
        ):
            body=MAIN.split(start,1)[1]
            if end in body:
                body=body.split(end,1)[0]
            self.assertIn("cancelChannelInfoEpgBoundaryRefresh()",body)

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
        self.assertIn("EpgLoadBudget.connectionTimeoutMs(",loader)
        self.assertIn("EpgLoadBudget.readTimeoutMs(",loader)
        self.assertIn("EpgLoadBudget.sourceRemainingMs(sourceStart,",loader)
        self.assertIn("feed exceeded 20-second limit",loader)
        self.assertIn("temporarily skipped after recent failure",loader)
        self.assertIn("showProgress:Boolean=false",loader)
        self.assertIn("epgFailedUntil.clear()",MAIN)

    def test_disabled_playlist_browse_and_surfing_filters(self):
        self.assertIn("enabledChannelIndices()",between("private fun showOverlayList(", "private fun showLeftNavigation("))
        self.assertIn("GroupNavigationPolicy.visibleGroups(",between("private fun showLeftNavigation(", "private fun showLeftActions("))
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
        self.assertIn("LastChannelPolicy.findEligibleSavedChannel(",last)
        self.assertIn("::canAutoRestoreChannel",last)
        eligibility=between("private fun canAutoRestoreChannel(", "private fun clearRemovedPreviousChannel(")
        self.assertIn("it.enabled && it.name==source",eligibility)
        self.assertIn("!isManuallyHidden(c)",eligibility)
        self.assertIn('showBanner("Previous channel is unavailable")',last)
        self.assertIn('showBanner("This playlist is disabled or deleted",true)',MAIN)
        startup=between("private fun resumeLast()", "private fun liveSettings(")
        self.assertIn("::canAutoRestoreChannel",startup)
    def test_pin_failure_delete_uses_same_next_playlist_handoff(self):
        deletion=between("private fun deletePlaylistAfterPinFailures(", "private fun deleteGroupAfterPinFailures(")
        self.assertIn("removePlaylistSource(name,true)",deletion)

    def test_delete_stops_correct_vod_before_channel_indices_change(self):
        block=between("private fun removePlaylistSource(", "private fun clearPlaylistLocalHistory(")
        self.assertLess(block.index("if(wasPlaying)stopPlayback()"),block.index("publishChannelsKeepingPlayback("))
        self.assertLess(block.index("clearPlaylistLocalHistory(name)"),block.index("publishChannelsKeepingPlayback("))
    def test_unrelated_playlist_disable_preserves_active_decoder_and_index(self):
        transition=between("private fun switchAfterPlaylistRemoved(", "private fun disablePlaylistSource(")
        self.assertIn("if(keepWatching)",transition)
        self.assertIn("return",transition)
        delete=between("private fun removePlaylistSource(", "private fun clearPlaylistLocalHistory(")
        self.assertIn("publishChannelsKeepingPlayback(channels.filter{it.source!=name}.toMutableList())",delete)
    def test_skip_entirely_hidden_locked_replacements(self):
        select=between("private fun nextAvailablePlaylistChannel(", "private fun clearRemovedPreviousChannel(")
        self.assertIn("PlaylistTransitionPolicy.nextEligiblePlaylist(",select)
        self.assertIn("eligible(name).isNotEmpty()",select)
        self.assertIn("!isManuallyHidden(c)",select)
        self.assertIn("!isLocked(c)",select)
    def test_confirmed_last_channel_is_preferred_over_failed_attempt(self):
        select=between("private fun nextAvailablePlaylistChannel(", "private fun clearRemovedPreviousChannel(")
        self.assertLess(select.index('prefs.getString("last_working_identity_"+source,null)'),
                        select.index('prefs.getString("last_identity_"+source,null)'))
        self.assertIn('putString("last_working_url_"+c.source,c.url)',MAIN)
    def test_removed_previous_channel_is_cleared_by_identity(self):
        clear=between("private fun clearRemovedPreviousChannel(", "private fun switchAfterPlaylistRemoved(")
        self.assertIn("identity==null || id(c)==identity",clear)
        self.assertIn("c.source in enabled",clear)

    def test_reordered_playlist_saves_outgoing_vod_before_swap(self):
        updater=between("private fun publishChannelsKeepingPlayback(", "private var automaticRefreshGeneration:")
        self.assertLess(updater.index("if(needsRetune)stopPlayback()"),
                        updater.index("channels=replacement"))
        self.assertIn("ActiveChannelRefreshPolicy.planTransition(",updater)
        refresh=between("private fun refresh(", "private var backgroundProbeGeneration")
        self.assertIn("publishChannelsKeepingPlayback(nextChannels)",refresh)
        self.assertNotIn("active=relocated",refresh)
    def test_delete_eviction_occurs_unconditionally(self):
        remover=between("private fun removePlaylistSource(", "private fun clearPlaylistLocalHistory(")
        self.assertIn("publishChannelsKeepingPlayback(channels.filter{it.source!=name}",remover)
        self.assertNotIn("if(active !in channels.indices)",remover)
        self.assertIn("if(wasPlaying)stopPlayback()",remover)
    def test_restore_reconciles_disabled_sources_and_downloads_newly_enabled(self):
        method=between("private fun reconcileRestoredPlaylistState(", "private fun restoreSettingsFromText(")
        self.assertIn("if(lostCurrent)stopPlayback()",method)
        self.assertIn("publishChannelsKeepingPlayback(channels.filter",method)
        self.assertIn("clearRemovedPreviousChannel()",method)
        self.assertIn("refresh({},forceRebuild=false)",method)
        restore=between("private fun restoreSettingsFromText(", "private fun showDiagnosticLogs(")
        self.assertIn("val previousPlaylists=playlists().toList()",restore)
        self.assertIn("reconcileRestoredPlaylistState(previousPlaylists)",restore)
    def test_per_playlist_working_history_not_exported(self):
        safety=(ROOT/"app/src/main/java/com/softppj/nextv/BackupSafety.kt").read_text()
        self.assertIn('name.startsWith("last_working_url_")',safety)
        self.assertIn('putString("last_working_url_"+c.source,c.url)',MAIN)

    def test_pin_failure_group_deletion_cannot_rebase_foreground_index_unsafely(self):
        group=between("private fun deleteGroupAfterPinFailures(", "private fun toggleLock(")
        self.assertIn("publishChannelsKeepingPlayback(channels.filter{it.group!=group}",group)
        self.assertNotIn("channels=channels.filter{it.group!=group}",group)
        self.assertLess(group.index("if(wasPlaying)stopPlayback()"),
                        group.index("publishChannelsKeepingPlayback("))
        self.assertIn('c.source in enabled',group)
        self.assertIn('!isManuallyHidden(c)',group)
        self.assertIn('!isLocked(c)',group)

    def test_enabling_playlist_immediately_reloads_channels(self):
        manager=between("private fun managePlaylists()", "private fun startAddedPlaylist(")
        self.assertIn("savePlaylists(list)",manager)
        self.assertIn("refresh({managePlaylists()})",manager)
    def test_standalone_saved_web_survives_cache_and_refresh(self):
        cached=between("private fun cachedChannels()", "private fun publishChannelsKeepingPlayback(")
        self.assertIn('if(sources.isEmpty())return mutableListOf<Channel>().also(::addSavedWebTv)',cached)
        self.assertIn('addSavedWebTv(combined)',cached)
        refresh=between("private fun refresh(", "private var backgroundProbeGeneration")
        self.assertIn("addSavedWebTv(combined)",refresh)
        self.assertIn("publishChannelsKeepingPlayback(standalone)",refresh)
        self.assertIn("addSavedWebTv(standalone)",refresh)
    def test_standalone_web_still_respects_license_gate(self):
        cached=between("private fun cachedChannels()", "private fun publishChannelsKeepingPlayback(")
        self.assertLess(cached.index("if(!licenseReady)return mutableListOf()"),
                        cached.index("if(sources.isEmpty())return"))

    def test_zero_usable_epg_feed_cannot_replace_last_working_guide(self):
        loader=between("private fun loadEpg(", "private fun showChannelEpg(")
        self.assertIn("EpgGuidePolicy.shouldPublishFeed(programmeCount)",loader)
        self.assertIn("empty feed (0 usable programmes); previous guide preserved",loader)
        self.assertLess(loader.index("if(!EpgGuidePolicy.shouldPublishFeed(programmeCount))"),
                        loader.index("sourceProgrammes.forEach{(id,entries)->"))
        self.assertIn('status.contains("empty feed")',loader)

    def test_disabling_active_engine_restarts_after_stop_resets_index(self):
        engine=between("private fun configureEnabledEngines(", "private fun initialEngines(")
        self.assertIn("stopPlayback()",engine)
        self.assertIn("PlaybackEnginePolicy.mayRetuneAfterEngineShutdown(",engine)
        self.assertNotIn("active==channel && channels === playlistSnapshot",engine)
        self.assertIn("channels.getOrNull(channel) === selectedChannel",engine)

    def test_movies_series_music_and_web_do_not_use_live_only_indices(self):
        media=between("private fun visibleMediaIndices(", "private val posterCache")
        self.assertIn("channelRepresentatives",media)
        self.assertIn("MediaLibraryPolicy.allowedRepresentatives(",media)
        self.assertIn("visibleMediaIndices().filter{channels[it].mediaType==type}",media)
        continuing=between("private fun continueMedia(", "private fun mediaLibrary(")
        self.assertIn("visibleMediaIndices()",continuing)
    def test_saved_web_tv_is_not_incorrectly_tagged_live(self):
        saved=between("private fun saveWebTvChannel(", "private fun playIndex(")
        self.assertIn('webChannel="auto",mediaType="web"',saved)
        self.assertNotIn('Channel(name,url,"Web TV",webChannel="auto")',saved)

    def test_all_playlists_locked_group_opens_pin_before_channel_drawer(self):
        nav=between("private fun showLeftNavigation(", "private fun showLeftActions(")
        self.assertIn("GroupNavigationPolicy.visibleGroups(",nav)
        drawer=between("private fun showGroupDrawer(group:String)", "private fun showGroupDrawerContents(")
        self.assertIn("openLockedGroup(group)",drawer)
        self.assertIn("if(browsingAllPlaylists())",drawer)
        self.assertNotIn("if(browsingAllPlaylists()){showGroupDrawerContents(group);return}",drawer)
    def test_saved_web_duplicate_provider_url_does_not_block_independent_copy(self):
        saved=between("private fun saveWebTvChannel(", "private fun playIndex(")
        self.assertIn("SavedWebTvPolicy.isIndependentCopy(it,url)",saved)
        self.assertNotIn("out.none{it.url==url}",saved)
        self.assertNotIn("channels.none{it.url==url}",saved)
    def test_web_tv_append_uses_identity_safe_channel_publication(self):
        saved=between("private fun saveWebTvChannel(", "private fun addSavedWebTv(")
        self.assertIn("publishChannelsKeepingPlayback((channels+added).toMutableList())",saved)
        self.assertNotIn("channels.add(",saved)

if __name__=="__main__":unittest.main()
