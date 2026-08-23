package com.auriqo.music.utils.cipher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerJsUrlParserTest {
    @Test
    fun iframeApi_parsesEscapedWidgetApiPath() {
        assertEquals(
            "https://www.youtube.com/s/player/2574220e/player_ias.vflset/en_GB/base.js",
            PlayerJsUrlParser.fromIframeApi(fixture("iframe-api-2574220e.js")),
        )
    }

    @Test
    fun embedPage_parsesPlayerEmbedJsUrl() {
        assertEquals(
            "https://www.youtube.com/s/player/2574220e/player_embed.vflset/es_MX/base.js",
            PlayerJsUrlParser.fromEmbedPage(fixture("embed-2574220e.html")),
        )
    }

    @Test
    fun unrelatedHtml_isRejected() {
        assertNull(PlayerJsUrlParser.fromIframeApi("var scriptUrl = '/s/player/not-a-hash/base.js'"))
        assertNull(PlayerJsUrlParser.fromEmbedPage("{\"jsUrl\":\"/assets/player.js\"}"))
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/player/$name")) {
            "Missing player fixture: $name"
        }.readText()
}
