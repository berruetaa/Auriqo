package com.music.innertube

import com.music.innertube.models.IpVersion
import com.music.innertube.models.YouTubeLocale
import java.net.Proxy

/**
 * Immutable snapshot of request-scoped InnerTube connection settings.
 *
 * Keeping these values together prevents callers from partially mutating the global YouTube facade
 * when several settings change at the same time.
 */
data class YouTubeConnectionConfig(
    val locale: YouTubeLocale,
    val proxy: Proxy?,
    val proxyAuth: String?,
    val useLoginForBrowse: Boolean,
    val ipVersion: IpVersion,
)

/** Immutable snapshot of account/session state used by InnerTube requests. */
data class YouTubeAccountSession(
    val cookie: String?,
    val visitorData: String?,
    val dataSyncId: String?,
)
