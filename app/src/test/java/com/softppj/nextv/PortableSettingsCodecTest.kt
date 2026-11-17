package com.softppj.nextv

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PortableSettingsCodecTest {
    @Test fun portableTypedPreferencesSurviveFullRoundTrip() {
        val input:Map<String,Any> = mapOf(
            "playlists" to "[{\"name\":\"Home TV\",\"url\":\"https://example.invalid/list.m3u\"}]",
            "viewer_profiles" to "[\"Parent\",\"Child\"]",
            "viewer_Parent_favorites" to setOf("a","b"),
            "viewer_Child_recent_channels" to "[\"c\"]",
            "nextv_profiles_enabled" to true,
            "channel_preview_seconds" to 5,
            "clock_time_seconds" to 10L,
            "information_opacity_float" to 0.75f,
            "pin" to "should-not-export",
            "license_token" to "secret",
            "last_working_url" to "https://example.invalid/private"
        )
        val payload=PortableSettingsCodec.encode(input)
        val parsed=PortableSettingsCodec.decode(payload).toMap()
        assertEquals(input["playlists"],parsed["playlists"])
        assertEquals(input["viewer_profiles"],parsed["viewer_profiles"])
        assertEquals(input["viewer_Parent_favorites"],parsed["viewer_Parent_favorites"])
        assertEquals(input["viewer_Child_recent_channels"],parsed["viewer_Child_recent_channels"])
        assertEquals(true,parsed["nextv_profiles_enabled"])
        assertEquals(5,parsed["channel_preview_seconds"])
        assertEquals(10L,parsed["clock_time_seconds"])
        assertEquals(0.75f,parsed["information_opacity_float"])
        assertFalse(parsed.containsKey("pin"))
        assertFalse(parsed.containsKey("license_token"))
        assertFalse(parsed.containsKey("last_working_url"))
        assertFalse(payload.contains("should-not-export"))
    }

    @Test fun importedSecretsAreDiscardedNotReinstated() {
        val settings=JSONObject()
            .put("license_token",JSONObject().put("type","string").put("value","bad"))
            .put("viewer_profiles",JSONObject().put("type","string").put("value","[]"))
        val raw=JSONObject().put("format","HomeTV-settings-v1").put("settings",settings).toString()
        val restored=PortableSettingsCodec.decode(raw).toMap()
        assertEquals("[]",restored["viewer_profiles"])
        assertFalse(restored.containsKey("license_token"))
    }

    @Test fun rejectOversizedExportAndMalformedImport() {
        assertThrows(IllegalArgumentException::class.java) {
            PortableSettingsCodec.encode(mapOf("playlists" to "x".repeat(BackupSafety.MAX_BACKUP_BYTES)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PortableSettingsCodec.decode("{}")
        }
        assertThrows(Exception::class.java) {
            PortableSettingsCodec.decode("not json")
        }
    }
}
