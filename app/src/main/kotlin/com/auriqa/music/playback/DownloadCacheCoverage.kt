package com.auriqo.music.playback

internal enum class DownloadCacheState {
    Empty,
    Partial,
    Full,
}

internal data class DownloadCacheCoverage(
    val state: DownloadCacheState,
    val contentLength: Long?,
    val cachedBytes: Long,
)

internal fun classifyDownloadCacheCoverage(
    contentLength: Long?,
    cachedBytes: Long,
    coversEntireContent: Boolean,
): DownloadCacheCoverage {
    require(cachedBytes >= 0L) { "cachedBytes must not be negative" }
    val normalizedLength = contentLength?.takeIf { it > 0L }
    val state = when {
        normalizedLength != null && coversEntireContent -> DownloadCacheState.Full
        cachedBytes > 0L -> DownloadCacheState.Partial
        else -> DownloadCacheState.Empty
    }
    return DownloadCacheCoverage(
        state = state,
        contentLength = normalizedLength,
        cachedBytes = cachedBytes,
    )
}
