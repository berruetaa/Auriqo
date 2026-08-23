package com.auriqo.music.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FfmpegAbiSupportTest {
    @Test
    fun supportsPublished64BitAbis() {
        assertTrue(AudioExportService.isFfmpegAbiSupported(listOf("arm64-v8a")))
        assertTrue(AudioExportService.isFfmpegAbiSupported(listOf("x86_64")))
    }

    @Test
    fun rejectsLegacy32BitAbisWhenNo64BitFallbackExists() {
        assertFalse(AudioExportService.isFfmpegAbiSupported(listOf("armeabi-v7a")))
        assertFalse(AudioExportService.isFfmpegAbiSupported(listOf("x86")))
    }

    @Test
    fun acceptsADeviceWithASecondaryPublishedAbi() {
        assertTrue(AudioExportService.isFfmpegAbiSupported(listOf("armeabi-v7a", "arm64-v8a")))
    }
}
