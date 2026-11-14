package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class BackupSafetyTest {
    @Test fun nonPortableSecretsAndSessionStateAreExcluded() {
        listOf(
            "pin", "PIN", "pin_failures_group:News", "last_url",
            "last_url_HomeTV Live", "last_channel_identity",
            "startup_in_progress", "startup_crash_count", "safe_startup_mode",
            "last_processed_exit_timestamp", "license_token",
            "License_Expiry", "signed_license_cache", "custom_password",
            "private_key_abc", "authorization_header", "session_key"
        ).forEach { key ->
            assertFalse("Must not back up: $key", BackupSafety.mayTransferSetting(key))
        }
    }

    @Test fun portablePreferencesAreAllowed() {
        listOf(
            "viewer_profiles", "viewer_Parent_favorites", "playlists",
            "channel_sort_mode", "nextv_profiles_enabled",
            "remote_input_diagnostics"
        ).forEach { key ->
            assertTrue("Should allow: $key", BackupSafety.mayTransferSetting(key))
        }
    }

    @Test fun boundedReaderRejectsOversizedBackups() {
        val oversized = ByteArray(BackupSafety.MAX_BACKUP_BYTES + 1) { 'x'.code.toByte() }
        assertThrows(IllegalArgumentException::class.java) {
            BackupSafety.readLimitedUtf8(ByteArrayInputStream(oversized))
        }
    }

    @Test fun boundedReaderAcceptsValidUtf8() {
        val content = """{"format":"HomeTV-settings-v1","settings":{}}"""
        assertEquals(content, BackupSafety.readLimitedUtf8(ByteArrayInputStream(content.toByteArray())))
    }
}
