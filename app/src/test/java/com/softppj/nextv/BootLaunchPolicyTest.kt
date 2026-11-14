package com.softppj.nextv

import android.content.Intent
import org.junit.Assert.*
import org.junit.Test

class BootLaunchPolicyTest {
    @Test fun bootReceiverUsesNexTvPreferenceNamespaceAndExplicitOptIn() {
        assertEquals("nextv", BootLaunchPolicy.PREFERENCES_NAME)
        assertEquals("boot", BootLaunchPolicy.BOOT_OPT_IN_KEY)
        assertFalse(BootLaunchPolicy.mayLaunch(Intent.ACTION_BOOT_COMPLETED, false, 28))
    }

    @Test fun enabledBootLaunchesOnLegacyAndroid() {
        assertTrue(BootLaunchPolicy.mayLaunch(Intent.ACTION_BOOT_COMPLETED, true, 28))
        assertTrue(BootLaunchPolicy.mayLaunch(Intent.ACTION_BOOT_COMPLETED, true, 23))
    }

    @Test fun disabledSettingNeverLaunches() {
        assertFalse(BootLaunchPolicy.mayLaunch(Intent.ACTION_BOOT_COMPLETED, false, 28))
    }

    @Test fun unrelatedBroadcastNeverLaunches() {
        assertFalse(BootLaunchPolicy.mayLaunch(Intent.ACTION_MY_PACKAGE_REPLACED, true, 28))
        assertFalse(BootLaunchPolicy.mayLaunch(null, true, 28))
    }

    @Test fun androidNineBoundaryStillAllowsOptInBoot() {
        assertTrue(BootLaunchPolicy.mayLaunch(Intent.ACTION_BOOT_COMPLETED, true, 28))
        assertFalse(BootLaunchPolicy.mayLaunch(Intent.ACTION_BOOT_COMPLETED, false, 28))
    }

    @Test fun androidTenBoundaryRejectsBootLaunch() {
        assertFalse(BootLaunchPolicy.mayLaunch(Intent.ACTION_BOOT_COMPLETED, true, 29))
    }

    @Test fun modernAndroidDoesNotLaunchActivityFromBootReceiver() {
        assertFalse(BootLaunchPolicy.mayLaunch(Intent.ACTION_BOOT_COMPLETED, true, 29))
        assertFalse(BootLaunchPolicy.mayLaunch(Intent.ACTION_BOOT_COMPLETED, true, 35))
    }
}
