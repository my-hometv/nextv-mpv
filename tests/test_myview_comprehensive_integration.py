"""Source-level integration guards for MyView interactive search and playlist transitions.

These are wiring assertions, not emulator/physical-remote tests. Kotlin unit tests
cover the underlying identity-resolution policy separately.
"""
import pathlib
import unittest

ROOT=pathlib.Path(__file__).resolve().parents[1]
MAIN=(ROOT/"app/src/main/java/com/softppj/nextv/MainActivity.kt").read_text(encoding="utf-8")

def section(start,end):
    return MAIN.split(start,1)[1].split(end,1)[0]

class CrossFeatureSearchIntegrationTest(unittest.TestCase):
    def test_all_due_epg_reminders_are_presented_without_loss(self):
        body=section("private fun checkEpgTune(){","private fun showEpgSchedules(){")
        self.assertIn("EpgDuePolicy.partition(due)",body)
        self.assertIn("val reminders=batch.reminders.mapNotNull",body)
        self.assertIn('setItems(reminders.map{(_,name,title)->',body)
        self.assertIn("val chosen=batch.automatic",body)
        self.assertNotIn("val entry=due.lastOrNull()",body)
    def test_media_controls_validate_current_generation_and_player(self):
        body=section("private fun mediaPlaybackControls(){","private fun legacyMediaKey(")
        self.assertIn("owningGeneration=playbackGeneration",body)
        self.assertIn("controlsOwnActivePlayer()",body)
        self.assertIn("player===exo",body)
        self.assertIn("vlcPlayer===vlc",body)
        self.assertGreaterEqual(body.count("withCurrentPlayer{"),2)
    def test_ambiguous_legacy_progress_is_never_migrated_after_deletion(self):
        body=section("private fun legacyMediaAmbiguousKey(","private fun saveCurrentMediaProgress(")
        self.assertIn("putBoolean(legacyMediaAmbiguousKey(c),true)",body)
        self.assertIn("prefs.getBoolean(legacyMediaAmbiguousKey(c),false)",body)
        self.assertIn("MediaProgressPolicy.canMigrateLegacy(channels,c)",body)
    def test_pin_callbacks_resolve_original_identity(self):
        body=section("private fun resumeAuthorizedChannel(","private fun playIndex(")
        self.assertIn("resolveListedChannel(identity,false,hint)",body)
        self.assertIn("playIndex(current)",body)
        player=section("private fun playIndex(","private fun playIndexResolved(") if "private fun playIndexResolved(" in MAIN.split("private fun playIndex(",1)[1] else MAIN.split("private fun playIndex(",1)[1]
        self.assertIn("resumeAuthorizedChannel(requestedIdentity,index)",player)
        self.assertIn("resumeAuthorizedChannel(requestedIdentity,index,true)",player)
        self.assertNotIn("openLockedPlaylist(c.source){playIndex(index)}",player)
    def test_media_buttons_re_resolve_saved_identity_before_display(self):
        series=section("private fun mediaSeries(","private fun mediaMusic(")
        music=section("private fun mediaMusic(","private fun mediaPlaybackControls(")
        library=section("private fun mediaLibrary(","private fun mediaSearch(")
        for body in (series,music,library):
            self.assertIn("currentMediaSelection(",body)
            self.assertIn("map{ id(channels[it]) }",body)
    def test_progress_migration_is_identity_specific(self):
        body=section("private fun legacyMediaKey(","private fun saveCurrentMediaProgress(")
        self.assertIn("MediaProgressPolicy.identityHash(c)",body)
        self.assertIn("MediaProgressPolicy.canMigrateLegacy(channels,c)",body)
        self.assertIn(".remove(oldKey)",body)
    def test_details_buttons_revalidate_identity_after_playlist_mutation(self):
        body=section("private fun mediaDetails(","// V6 cinematic Android TV components")
        self.assertIn("val identity=id(c)",body)
        self.assertIn("withAuthorizedMedia",body)
        self.assertIn("val current=resolveListedChannel(identity,true,index)",body)
        self.assertIn("playIndex(current)",body)
        self.assertNotIn("playIndex(index)",body)
    def test_player_drawer_uses_stable_identity_for_selection(self):
        body=section("private fun showOverlayList(","private fun showLeftNavigation(")
        self.assertIn("val identities=available.map{ id(channels[it]) }",body)
        self.assertIn("fun currentIndex(position:Int)",body)
        self.assertIn("val selected=currentIndex(position)",body)
        self.assertIn("channels.getOrNull(currentIndex(position))",body)
        self.assertNotIn("showChannelListQuickActions(available[position]",body)
    def test_search_uses_a_single_authorization_snapshot(self):
        body=section("private data class SearchAccess(","private fun youtubeLibrary(")
        self.assertIn("private fun searchAccessSnapshot()",body)
        self.assertIn("val access=searchAccessSnapshot()",body)
        self.assertIn("searchEligible(channels[index],access)",body)
        self.assertIn("temporaryFailureHidden",body)
    def test_universal_search_restores_epg_and_search_context(self):
        body=section("private fun showUniversalSearchResults(","private fun mediaRows(")
        self.assertIn("programmeList(c).any{it.title.contains(query,true)}",body)
        self.assertIn("showUniversalSearchResults(query)",body)
    def test_hardware_back_uses_same_search_return_destination(self):
        actions=section("override fun onBackPressed(){","private fun stopPlayback(){")
        self.assertIn("val destination=searchResultBack",actions)
        self.assertIn("destination()",actions)
        rows=section("private fun mediaRows(","private fun youtubeThumbnail(")
        self.assertIn("if(search)searchResultBack=onBack",rows)
        collection=section("private fun channelCollection(","private val bufferProfiles")
        self.assertIn("if(search)searchResultBack=onBack",collection)
    def test_epg_schedules_do_not_fall_back_across_playlist_by_url(self):
        schedule=section("private fun checkEpgTune(){","private fun showEpgSchedules(){")
        self.assertIn('entry.optString("channel_identity")',schedule)
        self.assertIn('identity.isNotBlank()',schedule)
        self.assertIn('key.isNotBlank()',schedule)
        self.assertNotIn('channels.indexOfFirst{it.url==url}',schedule)
        self.assertIn("canAutoRestoreChannel(selected)",schedule)
        self.assertIn("resolveListedChannel(targetIdentity,true,index)",schedule)
    def test_search_categories_do_not_bypass_enabled_and_lock_gates(self):
        body=section("private fun searchEligible(","private fun mediaMatches(")
        self.assertIn("source in access.enabled",body)
        self.assertIn("source !in access.lockedSources",body)
        self.assertIn("c.group !in access.lockedGroups",body)
        self.assertIn("!isManuallyHidden(c)",body)
