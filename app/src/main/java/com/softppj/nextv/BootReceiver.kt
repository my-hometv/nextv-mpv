package com.softppj.nextv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Android 10+ disallows an ordinary boot receiver from launching an Activity. */
internal object BootLaunchPolicy {
    const val PREFERENCES_NAME = "nextv"
    const val BOOT_OPT_IN_KEY = "boot"
    fun mayLaunch(action: String?, enabled: Boolean, sdkInt: Int): Boolean =
        action == Intent.ACTION_BOOT_COMPLETED &&
            enabled &&
            sdkInt < Build.VERSION_CODES.Q
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val enabled = context.getSharedPreferences(BootLaunchPolicy.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .getBoolean(BootLaunchPolicy.BOOT_OPT_IN_KEY, false)
        if (!BootLaunchPolicy.mayLaunch(intent.action, enabled, Build.VERSION.SDK_INT)) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launch)
    }
}
