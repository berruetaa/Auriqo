

package com.auriqo.music.lyrics

import android.content.Context
import com.auriqo.music.betterlyrics.BetterLyrics
import com.auriqo.music.constants.EnableBetterLyricsKey
import com.auriqo.music.utils.dataStore
import com.auriqo.music.utils.snapshot

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore.snapshot(EnableBetterLyricsKey) ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(
        title = title,
        artist = artist,
        duration = duration,
        album = album,
        videoId = id.takeIf { it.isNotBlank() },
    )

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        BetterLyrics.getAllLyrics(
            title = title,
            artist = artist,
            duration = duration,
            album = album,
            videoId = id.takeIf { it.isNotBlank() },
            callback = callback,
        )
    }
}
