package com.auriqo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadCacheCoverageTest {
    @Test
    fun partialSpanIsNeverClassifiedAsACompleteDownload() {
        val coverage = classifyDownloadCacheCoverage(
            contentLength = 10_000L,
            cachedBytes = 512L,
            coversEntireContent = false,
        )

        assertEquals(DownloadCacheState.Partial, coverage.state)
        assertEquals(512L, coverage.cachedBytes)
    }

    @Test
    fun unknownLengthWithCachedBytesIsPartial() {
        assertEquals(
            DownloadCacheState.Partial,
            classifyDownloadCacheCoverage(
                contentLength = null,
                cachedBytes = 1_024L,
                coversEntireContent = false,
            ).state,
        )
    }

    @Test
    fun onlyFullRangeCoverageIsComplete() {
        assertEquals(
            DownloadCacheState.Full,
            classifyDownloadCacheCoverage(
                contentLength = 10_000L,
                cachedBytes = 10_000L,
                coversEntireContent = true,
            ).state,
        )
    }

    @Test
    fun noSpansIsEmpty() {
        assertEquals(
            DownloadCacheState.Empty,
            classifyDownloadCacheCoverage(
                contentLength = 10_000L,
                cachedBytes = 0L,
                coversEntireContent = false,
            ).state,
        )
    }
}
