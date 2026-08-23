package com.auriqo.music.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebViewOriginPolicyTest {
    @Test
    fun discordPolicyRequiresHttpsAndTrustedHost() {
        assertTrue(isAllowedDiscordWebViewUrl("https://discord.com/login"))
        assertTrue(isAllowedDiscordWebViewUrl("https://cdn.discord.com/assets/app.js"))
        assertFalse(isAllowedDiscordWebViewUrl("http://discord.com/login"))
        assertFalse(isAllowedDiscordWebViewUrl("https://discord.com.evil.test/login"))
        assertFalse(isAllowedDiscordWebViewUrl("https://user:pass@discord.com/login"))
    }

    @Test
    fun discordTokenExtractionIsLimitedToAuthenticatedRoutes() {
        assertTrue(isDiscordTokenPage("https://discord.com/app"))
        assertTrue(isDiscordTokenPage("https://discord.com/channels/123/456"))
        assertFalse(isDiscordTokenPage("https://discord.com/login"))
        assertFalse(isDiscordTokenPage("https://evil.test/?next=discord.com/app"))
    }

    @Test
    fun youtubeLoginPolicyAcceptsGoogleAndYoutubeHttpsHostsOnly() {
        assertTrue(isAllowedYoutubeLoginUrl("https://accounts.google.com/ServiceLogin"))
        assertTrue(isAllowedYoutubeLoginUrl("https://music.youtube.com/"))
        assertFalse(isAllowedYoutubeLoginUrl("http://accounts.google.com/ServiceLogin"))
        assertFalse(isAllowedYoutubeLoginUrl("https://accounts.google.com.evil.test/"))
    }

    @Test
    fun spotifyLoginPolicyAcceptsSpotifyHttpsHostsOnly() {
        assertTrue(isAllowedSpotifyLoginUrl("https://accounts.spotify.com/login"))
        assertTrue(isAllowedSpotifyLoginUrl("https://open.spotify.com/"))
        assertFalse(isAllowedSpotifyLoginUrl("http://accounts.spotify.com/login"))
        assertFalse(isAllowedSpotifyLoginUrl("https://spotify.com.evil.test/"))
    }
}
