package com.auriqo.music.lyrics

import androidx.datastore.preferences.core.preferencesOf
import com.auriqo.music.constants.EnableBetterLyricsKey
import com.auriqo.music.constants.PreferredLyricsProvider
import com.auriqo.music.constants.PreferredLyricsProviderKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsProviderRegistryTest {
    @Test
    fun defaultOrderIsStable() {
        assertEquals(
            listOf(
                "YouLyPlus",
                "Paxsenix",
                "Unison",
                "BetterLyrics",
                "SimpMusic",
                "LrcLib",
                "Kugou",
                "LetrasCom",
                "YouTubeSubtitle",
                "YouTubeMusic",
            ),
            LyricsProviderRegistry.getDefaultProviderOrder(),
        )
    }

    @Test
    fun serializationDropsUnknownNamesAndDuplicates() {
        assertEquals(
            "LrcLib,YouLyPlus",
            LyricsProviderRegistry.serializeProviderOrder(
                listOf("LrcLib", "unknown", "YouLyPlus", "LrcLib"),
            ),
        )
        assertEquals(
            listOf("YouLyPlus"),
            LyricsProviderRegistry.deserializeProviderOrder("unknown,YouLyPlus,YouLyPlus"),
        )
    }

    @Test
    fun blankOrderUsesDefaultAndUnknownOnlyOrderDoesNotInventProviders() {
        assertEquals(
            LyricsProviderRegistry.getDefaultProviderOrder(),
            LyricsProviderRegistry.deserializeProviderOrder(""),
        )
        assertTrue(LyricsProviderRegistry.deserializeProviderOrder("missing").isEmpty())
        assertEquals(null, LyricsProviderRegistry.getProviderByName("missing"))
    }

    @Test
    fun enablementUsesOnePreferencesSnapshot() {
        val order = LyricsProviderRegistry.getDefaultProviderOrder()
        val disabled = LyricsProviderRegistry.getEnabledProviderNames(
            order,
            preferencesOf(EnableBetterLyricsKey to false),
        )

        assertFalse(disabled.contains("BetterLyrics"))
        assertTrue(disabled.contains("LrcLib"))
    }

    @Test
    fun preferredProviderIsMigratedWhenPersistedOrderIsAbsent() {
        val order = LyricsProviderRegistry.resolveProviderOrder(
            preferencesOf(PreferredLyricsProviderKey to PreferredLyricsProvider.LRCLIB.name),
        )

        assertEquals("LrcLib", order.first())
        assertEquals(
            LyricsProviderRegistry.getDefaultProviderOrder().size,
            order.size,
        )
    }
}
