package com.music.innertube

import com.music.innertube.models.YouTubeClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeClientCapabilityTest {
    @Test
    fun visionOsStaysJslessAndDoesNotClaimWebPoTokens() {
        assertFalse(YouTubeClient.VISIONOS.useSignatureTimestamp)
        assertFalse(YouTubeClient.VISIONOS.useWebPoTokens)
    }

    @Test
    fun webMusicAndCreatorDeclareWebPoTokenCapability() {
        assertTrue(YouTubeClient.WEB_REMIX.useWebPoTokens)
        assertTrue(YouTubeClient.WEB_CREATOR.useWebPoTokens)
    }
}
