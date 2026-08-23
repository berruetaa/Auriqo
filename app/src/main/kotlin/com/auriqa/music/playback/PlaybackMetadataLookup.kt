package com.auriqo.music.playback

import com.auriqo.music.models.MediaMetadata

/**
 * Returns metadata already attached to the playback queue.
 *
 * The Media3 data-source resolver is synchronous, so it must not query Room here. Queue items
 * already carry the metadata needed to improve a playback lookup; a missing tag is intentionally
 * represented as null and lets the resolver use YouTube's defaults.
 */
internal fun lookupPlaybackMetadata(
    mediaItems: Sequence<Pair<String, MediaMetadata>>,
    mediaId: String,
): MediaMetadata? = mediaItems.firstOrNull { it.first == mediaId }?.second
