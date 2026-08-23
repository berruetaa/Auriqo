package com.auriqo.music.lyrics

import com.auriqa.music.letras.LetrasCom

object LetrasComLyricsProvider : LyricsProvider {
    override val name = "LetrasCom"

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = LetrasCom.getLyrics(title, artist)
}
