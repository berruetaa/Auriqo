package com.auriqo.music.security

import java.net.URI

/** Accepts only URI schemes and hosts that MainActivity declares as app links. */
internal object DeepLinkPolicy {
    private val supportedWebHosts = setOf(
        "youtube.com",
        "m.youtube.com",
        "www.youtube.com",
        "music.youtube.com",
        "youtu.be",
        "share.echomusic.fun",
        "echomusic-listen-together.onrender.com",
    )

    fun isSupported(value: String): Boolean {
        val uri = runCatching { URI(value) }.getOrNull() ?: return false
        if (uri.userInfo != null) return false

        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase()
        return when (scheme) {
            "http", "https" -> host in supportedWebHosts
            "echomusic" -> host == "listen"
            "vnd.youtube", "vnd.youtube.launch" -> host == null
            else -> false
        }
    }
}
