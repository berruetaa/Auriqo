package com.auriqo.music.security

import java.net.URI

private fun parseHttpsUri(value: String): URI? = runCatching {
    URI(value).takeIf { it.scheme.equals("https", ignoreCase = true) && it.userInfo == null }
}.getOrNull()

private fun isHostOrSubdomain(host: String?, domain: String): Boolean =
    host?.lowercase()?.let { it == domain || it.endsWith(".$domain") } == true

internal fun isAllowedDiscordWebViewUrl(value: String): Boolean =
    parseHttpsUri(value)?.let { isHostOrSubdomain(it.host, "discord.com") } == true

internal fun isDiscordTokenPage(value: String): Boolean {
    val uri = parseHttpsUri(value) ?: return false
    if (!isHostOrSubdomain(uri.host, "discord.com")) return false
    val path = uri.path.orEmpty()
    return path == "/app" || path.startsWith("/app/") ||
        path == "/channels" || path.startsWith("/channels/")
}

internal fun isAllowedYoutubeLoginUrl(value: String): Boolean {
    val uri = parseHttpsUri(value) ?: return false
    return isHostOrSubdomain(uri.host, "google.com") ||
        isHostOrSubdomain(uri.host, "youtube.com")
}
