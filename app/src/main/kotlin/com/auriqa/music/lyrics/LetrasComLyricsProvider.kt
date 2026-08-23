package com.auriqo.music.lyrics

import android.content.Context
import com.auriqo.music.constants.EnableLetrasComKey
import com.auriqo.music.utils.dataStore
import com.auriqo.music.utils.snapshot
import com.auriqa.music.letras.LetrasCom

object LetrasComLyricsProvider : LyricsProvider {
    override val name = "LetrasCom"

    override fun isEnabled(context: Context): Boolean =
        context.dataStore.snapshot(EnableLetrasComKey) ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = LetrasCom.getLyrics(title, artist)
}
