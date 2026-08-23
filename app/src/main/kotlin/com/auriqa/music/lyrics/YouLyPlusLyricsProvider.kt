

package com.auriqo.music.lyrics

import android.content.Context
import com.music.youlyplus.YouLyPlus
import com.auriqo.music.constants.EnableYouLyPlusKey
import com.auriqo.music.utils.dataStore
import com.auriqo.music.utils.snapshot

object YouLyPlusLyricsProvider : LyricsProvider {
    override val name = "YouLyPlus"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore.snapshot(EnableYouLyPlusKey) ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = YouLyPlus.getLyrics(title, artist, duration, album, id)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        YouLyPlus.getAllLyrics(title, artist, duration, album, id, null, callback)
    }
}
