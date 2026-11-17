package com.softppj.nextv

import android.content.Context
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small, bounded, private diagnostic log store.
 * Logs remain in app-private storage and are retained for at most 30 days.
 */
internal object HomeTvDiagnostics {
    private const val RETENTION_MS = 30L * 24L * 60L * 60L * 1000L
    private const val MAX_DAILY_BYTES = 512L * 1024L
    private const val MAX_REPORT_CHARS = 1_000_000
    private const val MAX_EXCEPTION_CHARS = 16_384
    // IPTV playlist URLs can contain access tokens in query parameters.
    // Redact complete URLs in diagnostic messages and exception traces before
    // either local persistence or Android logcat receives them.
    private val urlPattern = Regex("""(?i)\b(?:https?|rtsp|rtmp|rtmps|rtp|udp|mms|mmsh)://[^\s<>"']+""")
    private fun redact(value: String): String =
        urlPattern.replace(value, "[stream URL redacted]")

    @Volatile private var appContext: Context? = null
    @Volatile private var crashHandlerInstalled = false

    @Synchronized fun initialize(context: Context): Boolean {
        appContext = context.applicationContext
        val prefs = appContext!!.getSharedPreferences("nextv", Context.MODE_PRIVATE)
        // On Android 11+, use the OS exit reason so native crashes are
        // counted while normal background/low-memory process eviction is not.
        var crashCount = prefs.getInt("startup_crash_count", 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                val latest = manager?.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                    ?.maxByOrNull { it.timestamp }
                val lastProcessed = prefs.getLong("last_processed_exit_timestamp", 0L)
                if (latest != null && latest.timestamp > lastProcessed) {
                    val crashed = latest.reason == ApplicationExitInfo.REASON_CRASH ||
                        latest.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                        latest.reason == ApplicationExitInfo.REASON_ANR ||
                        latest.reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
                    if (crashed) crashCount++
                    prefs.edit()
                        .putLong("last_processed_exit_timestamp", latest.timestamp)
                        .putInt("startup_crash_count", crashCount)
                        .putBoolean("safe_startup_mode", crashCount >= 2)
                        .apply()
                }
            } catch (_: Exception) { }
        }
        val safeMode = prefs.getBoolean("safe_startup_mode", false) || crashCount >= 2
        prefs.edit()
            .putBoolean("startup_in_progress", true)
            .putBoolean("safe_startup_mode", safeMode)
            .apply()
        prune()
        if (!crashHandlerInstalled) {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                // Persist the crash before delegating to Android's process handler.
                try {
                    val context = appContext
                    if (context != null) {
                        val crashPrefs = context.getSharedPreferences("nextv", Context.MODE_PRIVATE)
                        crashPrefs.edit().putBoolean("startup_in_progress", false).commit()
                        // Android 11+ records this crash in ApplicationExitInfo
                        // on the next launch; counting here as well would double-count it.
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                            val crashes = crashPrefs.getInt("startup_crash_count", 0) + 1
                            crashPrefs.edit()
                                .putInt("startup_crash_count", crashes)
                                .putBoolean("safe_startup_mode", crashes >= 2)
                                .commit()
                        }
                    }
                } catch (_: Exception) { }
                e("HomeTV", "Uncaught crash on thread " + thread.name, error)
                if (previous != null) previous.uncaughtException(thread, error)
                else {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(10)
                }
            }
            crashHandlerInstalled = true
        }
        return safeMode
    }

    @Synchronized fun markCleanExit(context: Context) {
        context.applicationContext.getSharedPreferences("nextv", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("startup_in_progress", false)
            .putInt("startup_crash_count", 0)
            .putBoolean("safe_startup_mode", false)
            .apply()
    }

    fun w(tag: String, message: String, error: Throwable? = null) {
        record("WARN", tag, message, error)
        android.util.Log.w(tag, redact(message))
    }

    fun e(tag: String, message: String, error: Throwable? = null) {
        record("ERROR", tag, message, error)
        android.util.Log.e(tag, redact(message))
    }

    fun i(tag: String, message: String, error: Throwable? = null) {
        record("INFO", tag, message, error)
        android.util.Log.i(tag, redact(message))
    }

    @Synchronized private fun record(level: String, tag: String, message: String, error: Throwable?) {
        val context = appContext ?: return
        try {
            val dir = File(context.filesDir, "diagnostics")
            if (!dir.exists() && !dir.mkdirs()) return
            // initialize()/readReport() prune expired logs. Avoid scanning
            // the diagnostics directory for every playback-state message:
            // this is called from Android's real-time playback UI looper.
            val day = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val file = File(dir, "hometv-$day.log")
            val detail = if (error == null) "" else {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))
                "\n" + writer.toString().take(MAX_EXCEPTION_CHARS)
            }
            val line = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(Date()) + " [" + level + "] " + tag + ": " + message + detail + "\n"
            val bytes = redact(line).toByteArray(Charsets.UTF_8)
            if (file.length() + bytes.size > MAX_DAILY_BYTES) return
            file.appendBytes(bytes)
        } catch (_: Exception) {
            // Diagnostics must never interrupt playback or app startup.
        }
    }

    @Synchronized private fun prune(directory: File? = null) {
        val dir = directory ?: appContext?.let { File(it.filesDir, "diagnostics") } ?: return
        val files = dir.listFiles()?.filter { it.isFile && it.name.startsWith("hometv-") && it.name.endsWith(".log") } ?: return
        val cutoff = System.currentTimeMillis() - RETENTION_MS
        files.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    // An abrupt native process crash cannot execute Kotlin's uncaught exception
    // handler. Android 11+ records its exit reason outside our process, so add
    // that information to a user-exported report on the next launch.
    private fun recentProcessExits(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ""
        val context = appContext ?: return ""
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                ?: return ""
            val entries = manager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
                .filter { exit ->
                    exit.reason == ApplicationExitInfo.REASON_CRASH ||
                        exit.reason == ApplicationExitInfo.REASON_CRASH_NATIVE ||
                        exit.reason == ApplicationExitInfo.REASON_ANR ||
                        exit.reason == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE
                }
            if (entries.isEmpty()) "" else "===== Recent Android process exits =====\n" +
                entries.joinToString("\n") { exit ->
                    "time=" + Date(exit.timestamp) +
                        " reason=" + exit.reason +
                        " status=" + exit.status +
                        " description=" + redact((exit.description ?: "").take(1000))
                } + "\n"
        } catch (_: Exception) {
            ""
        }
    }

    @Synchronized fun readReport(): String {
        val dir = appContext?.let { File(it.filesDir, "diagnostics") } ?: return ""
        prune(dir)
        val files = dir.listFiles()?.filter { it.isFile && it.name.startsWith("hometv-") && it.name.endsWith(".log") }
            ?.sortedBy { it.name } ?: return ""
        val all = files.joinToString("\n") { file ->
            "===== " + file.name + " =====\n" + try {
                // Re-sanitize historical logs created before redaction was added.
                redact(file.readText(Charsets.UTF_8).takeLast(MAX_REPORT_CHARS))
            } catch (_: Exception) { "[Unable to read log]\n" }
        }
        val report = all + (if (all.isNotEmpty()) "\n" else "") + recentProcessExits()
        return if (report.length > MAX_REPORT_CHARS) report.takeLast(MAX_REPORT_CHARS) else report
    }
}
