package com.music.innertube

import com.music.innertube.models.IpVersion
import com.music.innertube.models.YouTubeLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeSessionConfigTest {
    @Test
    fun `connection config is applied as one snapshot`() {
        val original = YouTubeConnectionConfig(
            locale = YouTube.locale,
            proxy = YouTube.proxy,
            proxyAuth = YouTube.proxyAuth,
            useLoginForBrowse = YouTube.useLoginForBrowse,
            ipVersion = YouTube.ipVersion,
        )

        try {
            val config = YouTubeConnectionConfig(
                locale = YouTubeLocale(gl = "UY", hl = "es"),
                proxy = null,
                proxyAuth = "Basic test",
                useLoginForBrowse = false,
                ipVersion = IpVersion.IPV4,
            )

            YouTube.applyConnectionConfig(config)

            assertEquals(config.locale, YouTube.locale)
            assertNull(YouTube.proxy)
            assertEquals(config.proxyAuth, YouTube.proxyAuth)
            assertEquals(config.useLoginForBrowse, YouTube.useLoginForBrowse)
            assertEquals(config.ipVersion, YouTube.ipVersion)
        } finally {
            YouTube.applyConnectionConfig(original)
        }
    }

    @Test
    fun `stream context generation changes only when connection state changes`() {
        val original = YouTubeConnectionConfig(
            locale = YouTube.locale,
            proxy = YouTube.proxy,
            proxyAuth = YouTube.proxyAuth,
            useLoginForBrowse = YouTube.useLoginForBrowse,
            ipVersion = YouTube.ipVersion,
        )
        val before = YouTube.streamContextGeneration

        YouTube.applyConnectionConfig(original)
        assertEquals(before, YouTube.streamContextGeneration)

        val changed = original.copy(
            locale = YouTubeLocale(
                gl = if (original.locale.gl == "UY") "US" else "UY",
                hl = original.locale.hl,
            ),
        )
        try {
            YouTube.applyConnectionConfig(changed)
            assertEquals(before + 1L, YouTube.streamContextGeneration)
            YouTube.applyConnectionConfig(changed)
            assertEquals(before + 1L, YouTube.streamContextGeneration)
        } finally {
            YouTube.applyConnectionConfig(original)
        }
    }

    @Test
    fun `stream context generation changes only when account state changes`() {
        val original = YouTubeAccountSession(
            cookie = YouTube.cookie,
            visitorData = YouTube.visitorData,
            dataSyncId = YouTube.dataSyncId,
        )
        val before = YouTube.streamContextGeneration

        YouTube.applyAccountSession(original)
        assertEquals(before, YouTube.streamContextGeneration)

        val changed = original.copy(visitorData = (original.visitorData ?: "visitor") + "-generation-test")
        try {
            YouTube.applyAccountSession(changed)
            assertEquals(before + 1L, YouTube.streamContextGeneration)
            YouTube.applyAccountSession(changed)
            assertEquals(before + 1L, YouTube.streamContextGeneration)
        } finally {
            YouTube.applyAccountSession(original)
        }
    }

    @Test
    fun `account session is applied and can be cleared`() {
        val original = YouTubeAccountSession(
            cookie = YouTube.cookie,
            visitorData = YouTube.visitorData,
            dataSyncId = YouTube.dataSyncId,
        )

        try {
            YouTube.applyAccountSession(
                YouTubeAccountSession(
                    cookie = null,
                    visitorData = "visitor-test",
                    dataSyncId = "sync-test",
                ),
            )

            assertNull(YouTube.cookie)
            assertEquals("visitor-test", YouTube.visitorData)
            assertEquals("sync-test", YouTube.dataSyncId)

            YouTube.applyAccountSession(
                YouTubeAccountSession(
                    cookie = null,
                    visitorData = null,
                    dataSyncId = null,
                ),
            )

            assertNull(YouTube.cookie)
            assertNull(YouTube.visitorData)
            assertNull(YouTube.dataSyncId)
        } finally {
            YouTube.applyAccountSession(original)
        }
    }
}
