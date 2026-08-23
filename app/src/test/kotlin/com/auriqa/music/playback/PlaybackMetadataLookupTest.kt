package com.auriqo.music.playback

import com.auriqo.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackMetadataLookupTest {
    private val metadata = MediaMetadata(
        id = "song-1",
        title = "Known title",
        artists = listOf(MediaMetadata.Artist(id = null, name = "Known artist")),
        duration = 240,
    )

    @Test
    fun returnsMetadataAttachedToMatchingQueueItem() {
        assertEquals(
            metadata,
            lookupPlaybackMetadata(sequenceOf(metadata.id to metadata), metadata.id),
        )
    }

    @Test
    fun returnsNullWhenQueueHasNoMatchingTaggedItem() {
        assertNull(
            lookupPlaybackMetadata(sequenceOf("other-song" to metadata), metadata.id),
        )
    }
}
