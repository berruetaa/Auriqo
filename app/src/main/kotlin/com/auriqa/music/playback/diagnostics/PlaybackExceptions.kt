package com.auriqo.music.playback.diagnostics

import java.io.IOException

/**
 * Carries resolver semantics across the synchronous Media3 resolver boundary.
 *
 * Media3 will add its own error code around this exception. Keeping the hint and the original
 * cause here lets the service classify the failure without parsing a translated message.
 */
class PlaybackResolutionException(
    message: String,
    val hint: PlaybackFailureHint = PlaybackFailureHint.UNKNOWN,
    val playabilityStatus: String? = null,
    val playabilityReason: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)
