

package com.auriqo.music.lyrics

import com.auriqo.music.betterlyrics.BetterLyrics

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

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
