package com.auriqo.music.lyrics

import android.content.Context

interface LyricsProvider {
    val name: String

    /**
     * Provider enablement is resolved centrally by [LyricsProviderRegistry].
     *
     * Kept temporarily for source compatibility with older call sites. New code must use the
     * registry so a single DataStore snapshot can be shared across every provider decision.
     */
    @Deprecated(
        message = "Provider enablement is owned by LyricsProviderRegistry",
        replaceWith = ReplaceWith("LyricsProviderRegistry"),
    )
    fun isEnabled(context: Context): Boolean = true

    /** Called immediately before an enabled provider is used. */
    fun prepare(context: Context) = Unit

    suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ): Result<String>

    suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
        callback: (String) -> Unit,
    ) {
        getLyrics(id, title, artist, duration, album).onSuccess(callback)
    }
}
