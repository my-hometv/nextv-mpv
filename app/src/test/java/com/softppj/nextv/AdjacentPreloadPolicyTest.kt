package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class AdjacentPreloadPolicyTest {
    private fun allowed(
        alreadyRotated:Boolean=false,
        currentGeneration:Boolean=true,
        foregroundPlaying:Boolean=true,
        appInForeground:Boolean=true,
        enabled:Boolean=true,
        previewSelected:Boolean=false,
        multiViewActive:Boolean=false
    )=AdjacentPreloadPolicy.mayRotate(alreadyRotated,currentGeneration,
        foregroundPlaying,appInForeground,enabled,previewSelected,multiViewActive)

    @Test fun oneSequentialOppositePreloadIsAllowedWhenPlaybackIsStable(){
        assertTrue(allowed())
        assertFalse(allowed(alreadyRotated=true))
    }
    @Test fun neverRotateDuringForegroundInterruptionOrStaleSession(){
        assertFalse(allowed(currentGeneration=false))
        assertFalse(allowed(foregroundPlaying=false))
        assertFalse(allowed(appInForeground=false))
    }
    @Test fun disablingOrOpeningOtherDecodersStopsRotation(){
        assertFalse(allowed(enabled=false))
        assertFalse(allowed(previewSelected=true))
        assertFalse(allowed(multiViewActive=true))
    }
}
