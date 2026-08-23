

package com.auriqo.music.lyrics

import android.content.Context
import com.music.kugou.KuGou
import com.auriqo.music.constants.EnableKugouKey
import com.auriqo.music.utils.dataStore
import com.auriqo.music.utils.snapshot

object KuGouLyricsProvider : LyricsProvider {
    override val name = "Kugou"
    override fun isEnabled(context: Context): Boolean =
        context.dataStore.snapshot(EnableKugouKey) ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> =
        KuGou.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        KuGou.getAllPossibleLyricsOptions(title, artist, duration, album, callback)
    }
}
