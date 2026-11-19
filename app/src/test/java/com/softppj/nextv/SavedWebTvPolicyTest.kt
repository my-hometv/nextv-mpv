package com.softppj.nextv

import org.junit.Assert.*
import org.junit.Test

class SavedWebTvPolicyTest {
    private val url="https://example.com/live"
    @Test fun providerChannelWithSameUrlCannotBlockIndependentSave() {
        val provider=Channel("From playlist",url,"Live",source="Provider")
        assertFalse(SavedWebTvPolicy.isIndependentCopy(provider,url))
        val standalone=Channel("Saved",url,"Web TV",mediaType="web",webChannel="auto")
        assertTrue(SavedWebTvPolicy.isIndependentCopy(standalone,url))
    }
    @Test fun separateSavedUrlsRemainDistinct() {
        val existing=Channel("Saved","https://example.com/other","Web TV",mediaType="web")
        assertFalse(SavedWebTvPolicy.isIndependentCopy(existing,url))
        assertFalse(SavedWebTvPolicy.isIndependentCopy(
            Channel("Live",url,"News",mediaType="live"),url))
    }
}
