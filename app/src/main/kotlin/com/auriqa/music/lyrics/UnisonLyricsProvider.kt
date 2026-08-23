package com.auriqo.music.lyrics

import android.content.Context
import com.auriqa.music.unison.Unison
import com.auriqo.music.constants.UnisonLyricsEnabledKey
import com.auriqo.music.utils.dataStore
import com.auriqo.music.utils.snapshot

object UnisonLyricsProvider : LyricsProvider {
    override val name: String = "Unison"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore.snapshot(UnisonLyricsEnabledKey) ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = Unison.getLyrics(
        videoId = id,
        title = title,
        artist = artist,
        album = album,
        durationSeconds = duration
    ).map { convertIfTTML(it) }

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        Unison.getAllLyrics(
            videoId = id,
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
            callback = { callback(convertIfTTML(it)) }
        )
    }

    private fun convertIfTTML(content: String): String {
        return if (content.trimStart().startsWith("<tt", ignoreCase = true)) {
            val parsedLines = com.auriqo.music.betterlyrics.TTMLParser.parseTTML(content)
            com.auriqo.music.betterlyrics.TTMLParser.toLRC(parsedLines)
        } else {
            content
        }
    }
}
