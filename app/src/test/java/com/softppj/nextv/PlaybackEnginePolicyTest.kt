package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class PlaybackEnginePolicyTest {
    @Test fun versionedEnginesRequireBothPackagingAndRuntimeVerification() {
        val all=setOf("LibVLC-3.5.1","LibVLC-3.7.7","LibVLC-4.0.0-eap29")
        assertEquals(emptyList<String>(),
            PlaybackEnginePolicy.verifiedVersionEngines(all,emptySet()))
        assertEquals(emptyList<String>(),
            PlaybackEnginePolicy.verifiedVersionEngines(emptySet(),all))
        assertEquals(listOf("LibVLC-3.7.7"),
            PlaybackEnginePolicy.verifiedVersionEngines(
                setOf("LibVLC-3.7.7","untrusted"),
                setOf("LibVLC-3.7.7","untrusted")))
        assertEquals(listOf("LibVLC-3.5.1","LibVLC-3.7.7","LibVLC-4.0.0-eap29"),
            PlaybackEnginePolicy.verifiedVersionEngines(all,all))
    }

    @Test fun verifiedEngineOrderIsStableAndUnknownIdsAreIgnored() {
        val packaged=setOf("unknown","LibVLC-4.0.0-eap29","LibVLC-3.5.1")
        assertEquals(listOf("LibVLC-3.5.1","LibVLC-4.0.0-eap29"),
            PlaybackEnginePolicy.verifiedVersionEngines(packaged,packaged))
    }

    @Test fun packagedVersionsAreRecognizedWithoutAdvertisingUnpackagedEngines() {
        assertEquals("3.5.1", PlaybackEnginePolicy.packagedLibVlcVersion("3.5.1"))
        assertEquals(listOf("Media3", "LibVLC"), PlaybackEnginePolicy.selectableEngines("3.5.1"))
        for (version in listOf("3.7.7", "4.0.0-eap29")) {
            assertNull(PlaybackEnginePolicy.packagedLibVlcVersion(version))
            assertEquals(listOf("Media3"), PlaybackEnginePolicy.selectableEngines(version))
        }
        assertNull(PlaybackEnginePolicy.packagedLibVlcVersion("unknown"))
        assertEquals(listOf("Media3"), PlaybackEnginePolicy.selectableEngines("unknown"))
    }
    @Test fun isolatedVersionIdsAreStableButNotEnabledWithoutVerification() {
        assertEquals("LibVLC-3.5.1", PlaybackEnginePolicy.versionEngineId("3.5.1"))
        assertEquals("LibVLC-3.7.7", PlaybackEnginePolicy.versionEngineId("3.7.7"))
        assertEquals("LibVLC-4.0.0-eap29", PlaybackEnginePolicy.versionEngineId("4.0.0-eap29"))
        assertNull(PlaybackEnginePolicy.versionEngineId("unknown"))
        assertEquals(listOf("Media3"), PlaybackEnginePolicy.isolatedSelectableEngines(emptySet()))
        assertFalse(PlaybackEnginePolicy.isVersionRuntimeReady("3.7.7", emptySet()))
        assertEquals(
            listOf("Media3", "LibVLC-3.7.7"),
            PlaybackEnginePolicy.isolatedSelectableEngines(setOf("LibVLC-3.7.7", "unrecognized"))
        )
        assertTrue(PlaybackEnginePolicy.isVersionRuntimeReady("3.7.7", setOf("LibVLC-3.7.7")))
    }
    @Test fun runtimeVerificationMustAlsoMatchPackagedArtifacts() {
        val verified = setOf("LibVLC-3.5.1", "LibVLC-3.7.7")
        assertEquals(listOf("Media3", "LibVLC", "LibVLC-3.7.7"),
            PlaybackEnginePolicy.selectableWithRuntimeVerification(
                "3.5.1", setOf("LibVLC-3.7.7"), verified))
        assertEquals(listOf("Media3", "LibVLC"),
            PlaybackEnginePolicy.selectableWithRuntimeVerification(
                "3.5.1", emptySet(), verified))
    }
    @Test fun verifiedIsolatedVersionsNeverHideLegacyFlavor() {
        val available = PlaybackEnginePolicy.selectableWithRuntimeVerification(
            "3.5.1",
            setOf("LibVLC-3.7.7", "LibVLC-4.0.0-eap29"),
            setOf("LibVLC-3.7.7", "LibVLC-4.0.0-eap29")
        )
        assertEquals(listOf("Media3", "LibVLC", "LibVLC-3.7.7", "LibVLC-4.0.0-eap29"), available)
        assertEquals("LibVLC", PlaybackEnginePolicy.resolveRequestedEngine("LibVLC", available))
    }
    @Test fun unverifiedAndUnpackagedIsolatedEnginesStayHidden() {
        assertEquals(
            listOf("Media3", "LibVLC"),
            PlaybackEnginePolicy.selectableWithRuntimeVerification(
                "3.5.1", setOf("LibVLC-3.7.7"), setOf("LibVLC-4.0.0-eap29"))
        )
    }
    @Test fun unavailableVersionCannotFallBackToWrongNativeEngine() {
        val available = listOf("Media3", "LibVLC-3.5.1")
        assertEquals("Media3", PlaybackEnginePolicy.resolveRequestedEngine("LibVLC-3.7.7", available))
        assertEquals("LibVLC-3.5.1", PlaybackEnginePolicy.resolveRequestedEngine("LibVLC-3.5.1", available))
    }
    @Test fun legacyLibVlcRequestRespectsPackagedFlavor() {
        assertEquals("LibVLC", PlaybackEnginePolicy.resolveRequestedEngine("LibVLC", listOf("Media3", "LibVLC")))
        assertEquals("Media3", PlaybackEnginePolicy.resolveRequestedEngine("LibVLC", listOf("Media3", "LibVLC-3.5.1")))
    }
    @Test fun disabledEngineIsNotSelected() {
        assertFalse(PlaybackEnginePolicy.isEnabled("MPV", listOf("LibVLC")))
        assertTrue(PlaybackEnginePolicy.isEnabled("LibVLC", listOf("LibVLC")))
    }
    @Test fun allDisabledPreferencesRecoverToMedia3() {
        assertEquals(listOf("Media3"), PlaybackEnginePolicy.normalized(emptyList()))
        assertTrue(PlaybackEnginePolicy.isEnabled("Media3", emptyList()))
        assertFalse(PlaybackEnginePolicy.isEnabled("MPV", emptyList()))
    }
    @Test fun unsupportedEnginesCannotBeEnabled() {
        assertEquals(listOf("Media3"), PlaybackEnginePolicy.normalized(listOf("Unknown")))
    }
    @Test fun removedMpvCannotBeReenabledFromLegacyPreferences() {
        assertEquals(listOf("LibVLC"), PlaybackEnginePolicy.normalized(listOf("MPV", "LibVLC")))
        assertFalse(PlaybackEnginePolicy.isEnabled("MPV", listOf("MPV")))
        assertEquals(listOf("Media3"), PlaybackEnginePolicy.normalized(listOf("MPV")))
    }
    @Test fun runtimeScopedPreferencesRetainOnlyVerifiedVersionIds() {
        val available = listOf("Media3", "LibVLC", "LibVLC-3.7.7")
        val enabled = listOf("LibVLC-3.7.7", "LibVLC-4.0.0-eap29", "Unknown")
        assertEquals(listOf("LibVLC-3.7.7"),
            PlaybackEnginePolicy.normalized(enabled, available))
        assertTrue(PlaybackEnginePolicy.isEnabled("LibVLC-3.7.7", enabled, available))
        assertFalse(PlaybackEnginePolicy.isEnabled("LibVLC-4.0.0-eap29", enabled, available))
    }
    @Test fun runtimeScopedPreferencesRecoverOnlyToAvailableMedia3() {
        assertEquals(listOf("Media3"),
            PlaybackEnginePolicy.normalized(listOf("LibVLC-3.7.7"), listOf("Media3", "LibVLC")))
        assertEquals(emptyList<String>(),
            PlaybackEnginePolicy.normalized(emptyList(), emptyList()))
    }
    @Test fun runtimeScopedPreferencesDiscardBlankAndDuplicateAvailableIds() {
        val available = listOf("", " ", "Media3", "Media3", "LibVLC", "LibVLC")
        assertEquals(listOf("Media3", "LibVLC"),
            PlaybackEnginePolicy.normalized(listOf("", " ", "Media3", "LibVLC"), available))
        assertEquals(listOf("Media3"),
            PlaybackEnginePolicy.normalized(listOf("", " "), available))
        assertFalse(PlaybackEnginePolicy.isEnabled("", listOf(""), available))
    }

    @Test fun preferredFallbackRespectsFilteredOrder() {
        assertEquals("LibVLC", PlaybackEnginePolicy.firstEnabled(listOf("LibVLC", "Media3")))
        assertEquals("Media3", PlaybackEnginePolicy.firstEnabled(emptyList()))
    }
    @Test fun unknownBackendCannotBeSelectedEvenIfAvailabilityIsCorrupted() {
        val available = listOf("Media3", "MysteryEngine", "LibVLC-3.7.7")
        assertEquals("Media3",
            PlaybackEnginePolicy.resolveRequestedEngine("MysteryEngine", available))
        assertEquals("LibVLC-3.7.7",
            PlaybackEnginePolicy.resolveRequestedEngine("LibVLC-3.7.7", available))
        assertEquals(listOf("LibVLC-3.7.7"),
            PlaybackEnginePolicy.normalized(listOf("MysteryEngine", "LibVLC-3.7.7"), available))
        assertFalse(PlaybackEnginePolicy.isEnabled("MysteryEngine",
            listOf("MysteryEngine"), available))
    }

    @Test fun firstEnabledSkipsInvalidPreferencesAndPreservesVerifiedVersionIds() {
        assertEquals("LibVLC",
            PlaybackEnginePolicy.firstEnabled(listOf("Unknown", "", "LibVLC", "Media3")))
        assertEquals("LibVLC-4.0.0-eap29",
            PlaybackEnginePolicy.firstEnabled(listOf("MPV", "LibVLC-4.0.0-eap29")))
        assertEquals("Media3",
            PlaybackEnginePolicy.firstEnabled(listOf("Unknown", "MPV", "")))
    }

    @Test fun paddedEnginePreferencesResolveToStableKnownIds() {
        val available = listOf(" Media3 ", " LibVLC ", " LibVLC-3.7.7 ", "LibVLC")
        assertEquals(listOf("Media3", "LibVLC", "LibVLC-3.7.7"),
            PlaybackEnginePolicy.normalized(
                listOf(" Media3 ", " LibVLC ", "LibVLC-3.7.7"), available))
        assertEquals("LibVLC-3.7.7",
            PlaybackEnginePolicy.resolveRequestedEngine(" LibVLC-3.7.7 ", available))
        assertTrue(PlaybackEnginePolicy.isEnabled(" LibVLC ", listOf("LibVLC"), available))
        assertEquals("LibVLC",
            PlaybackEnginePolicy.firstEnabled(listOf(" unknown ", " LibVLC ", "Media3")))
    }

    @Test fun paddedUnknownEnginesCannotBypassRuntimeAvailability() {
        assertEquals(emptyList<String>(), PlaybackEnginePolicy.normalized(
            listOf(" Mystery ", " LibVLC-3.7.7 "), listOf("Mystery")))
        assertEquals("Media3", PlaybackEnginePolicy.resolveRequestedEngine(
            " LibVLC-3.7.7 ", listOf("Media3", "LibVLC")))
        assertFalse(PlaybackEnginePolicy.isEnabled(
            " Mystery ", listOf(" Mystery "), listOf("Mystery")))
    }

    @Test fun availabilityWithOnlyUnknownEnginesCannotEnablePlayback() {
        assertEquals(emptyList<String>(),
            PlaybackEnginePolicy.normalized(listOf("MysteryEngine"),
                listOf("MysteryEngine")))
        assertEquals("Media3",
            PlaybackEnginePolicy.resolveRequestedEngine("MysteryEngine",
                listOf("MysteryEngine")))
    }

}
