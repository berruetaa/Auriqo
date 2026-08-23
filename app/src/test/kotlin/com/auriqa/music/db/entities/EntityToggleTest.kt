package com.auriqo.music.db.entities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityToggleTest {
    @Test
    fun songLikeToggleOnlyUpdatesLocalState() {
        val song = SongEntity(id = "song", title = "Song")

        val liked = song.toggleLike()

        assertTrue(liked.liked)
        assertNotNull(liked.likedDate)
        assertNotNull(liked.inLibrary)
        assertFalse(song.liked)
        assertNull(song.likedDate)
    }

    @Test
    fun songLibraryToggleOnlyUpdatesLocalState() {
        val song = SongEntity(id = "song", title = "Song", liked = true)

        val inLibrary = song.toggleLibrary()
        val removed = inLibrary.toggleLibrary()

        assertNotNull(inLibrary.inLibrary)
        assertEquals(song.liked, inLibrary.liked)
        assertNull(removed.inLibrary)
        assertFalse(removed.liked)
    }

    @Test
    fun otherEntityTogglesOnlyUpdateLocalState() {
        val album = AlbumEntity(
            id = "album",
            title = "Album",
            songCount = 1,
            duration = 1,
            playlistId = "playlist",
        )
        val artist = ArtistEntity(id = "artist", name = "Artist", channelId = "channel")
        val playlist = PlaylistEntity(id = "playlist", name = "Playlist", browseId = "browse")

        assertNotNull(album.toggleLike().bookmarkedAt)
        assertNotNull(artist.toggleLike().bookmarkedAt)
        assertNotNull(playlist.toggleLike().bookmarkedAt)
        assertNull(album.bookmarkedAt)
        assertNull(artist.bookmarkedAt)
        assertNull(playlist.bookmarkedAt)
    }
}
