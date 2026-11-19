package com.softppj.nextv

import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Backup files are portable settings only, never activation or secret state.
 * Keep this policy shared between export, merge and replace-restore.
 */
internal object BackupSafety {
    const val MAX_BACKUP_BYTES = 2_000_000

    private val nonPortableKeys = setOf(
        "pin", "last_url", "last_channel_identity",
        "last_working_url", "last_working_channel_identity", "startup_in_progress",
        "startup_crash_count", "safe_startup_mode", "last_processed_exit_timestamp"
    )
    private val secretKeyParts = listOf(
        "password", "secret", "token", "credential", "authorization",
        "api_key", "session_key", "private_key"
    )

    fun mayTransferSetting(key: String): Boolean {
        val name = key.lowercase(java.util.Locale.ROOT)
        if (name in nonPortableKeys || name.startsWith("last_url_") ||
            name.startsWith("last_working_url_") ||
            name.startsWith("license_") || name.startsWith("signed_license_") ||
            name.startsWith("pin_failures_")) return false
        return secretKeyParts.none { it in name }
    }

    /** Exports must fit the same byte ceiling that import enforces. */
    fun fitsBackupLimit(payload:String):Boolean =
        payload.toByteArray(Charsets.UTF_8).size<=MAX_BACKUP_BYTES

    /** Reject oversized input while streaming, before allocating the JSON document. */
    fun readLimitedUtf8(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (read == 0) continue
            if (read > MAX_BACKUP_BYTES - total) {
                throw IllegalArgumentException("Backup exceeds the 2 MB safety limit")
            }
            output.write(buffer, 0, read)
            total += read
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
