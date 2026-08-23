package com.auriqo.music.innertube

import com.music.innertube.models.PlaylistPanelVideoRenderer
import com.music.innertube.pages.NextPage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class NextFixtureTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun playlistPanelVideoRenderer_mapsStableNextFields() {
        val renderer = json.decodeFromString<PlaylistPanelVideoRenderer>(fixture())

        val song = NextPage.fromPlaylistPanelVideoRenderer(renderer)

        assertNotNull(song)
        assertEquals("fixture-video-id", song?.id)
        assertEquals("Fixture Song", song?.title)
        assertEquals("Fixture Artist", song?.artists?.single()?.name)
        assertEquals("UC_fixture_artist", song?.artists?.single()?.id)
        assertEquals("Fixture Album", song?.album?.name)
        assertEquals("MPRE_fixture_album", song?.album?.id)
        assertEquals(201, song?.duration)
        assertEquals("https://img.example.invalid/fixture.jpg", song?.thumbnail)
    }

    private fun fixture(): String =
        requireNotNull(javaClass.getResource("/fixtures/next/playlist-panel-video-renderer.json")) {
            "Missing next fixture"
        }.readText()
}
