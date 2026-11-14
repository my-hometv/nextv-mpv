package com.softppj.nextv

/** Manual operations must remain available when their automatic counterpart is OFF. */
internal object FeatureTogglePolicy {
    fun shouldRunAutomaticRefresh(automatic: Boolean, enabled: Boolean): Boolean =
        !automatic || enabled
}
