package com.auriqo.music.playback

/**
 * Owns the short-lived stream-resolution cache and the one-shot recovery gate for a media item.
 *
 * This class intentionally has no Media3, Android, or network dependency. MusicService adapts its
 * decisions to a player operation, which makes cache invalidation and retry limits deterministic.
 */
internal class StreamRecoveryCoordinator(
    private val expirySafetyMarginMs: Long = DEFAULT_EXPIRY_SAFETY_MARGIN_MS,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    init {
        require(expirySafetyMarginMs >= 0L) { "expirySafetyMarginMs must not be negative" }
    }

    data class StreamKey(
        val mediaId: String,
        val quality: String,
        val sessionGeneration: Long = 0L,
        val networkGeneration: Long = 0L,
    )

    data class CachedStream(
        val url: String,
        val expiresAtMs: Long,
        val resolvedAtMs: Long = 0L,
        val generation: Long = 0L,
        val itag: Int? = null,
        val mimeType: String? = null,
        val bitrate: Int? = null,
    )

    /** Whether a completed resolution was accepted into the current generation. */
    enum class CacheWriteResult {
        Stored,
        Superseded,
        Expired,
    }

    data class ResolutionToken internal constructor(
        val mediaId: String,
        val generation: Long,
    )

    data class RecoveryToken internal constructor(
        val mediaId: String,
        val generation: Long,
    )

    data class PlaybackSnapshot(
        val mediaId: String,
        val queueIndex: Int,
        val positionMs: Long,
        val playWhenReady: Boolean,
    )

    enum class FailureKind(
        val refreshExtractorState: Boolean,
        val invalidatesStreamResolution: Boolean,
    ) {
        RejectedStream(refreshExtractorState = true, invalidatesStreamResolution = true),
        ReloadRequired(refreshExtractorState = true, invalidatesStreamResolution = true),
        UnclassifiedStreamIo(refreshExtractorState = true, invalidatesStreamResolution = true),
        CacheOrStreamCorruption(refreshExtractorState = false, invalidatesStreamResolution = true),
        LocalSourceCorruption(refreshExtractorState = false, invalidatesStreamResolution = false),
        Permanent(refreshExtractorState = false, invalidatesStreamResolution = false),
    }

    sealed interface RecoveryDecision {
        data class Recover(
            val token: RecoveryToken,
            val snapshot: PlaybackSnapshot,
            val failure: FailureKind,
        ) : RecoveryDecision

        /** Another callback for the same failed load; the first one owns recovery. */
        data object RecoveryInProgress : RecoveryDecision

        /** The refreshed stream failed too. Surface the player error instead of looping. */
        data object Exhausted : RecoveryDecision

        data object NotRecoverable : RecoveryDecision
    }

    private val lock = Any()
    private val streams = mutableMapOf<StreamKey, CachedStream>()
    private val resolutionGenerations = mutableMapOf<String, Long>()

    private var activeMediaId: String? = null
    private var attemptedRecoveryFor: String? = null
    private var recoveryInProgress: RecoveryToken? = null
    private var recoveryGeneration = 0L

    fun cachedStream(key: StreamKey): CachedStream? = synchronized(lock) {
        val cached = streams[key] ?: return@synchronized null
        if (cached.expiresAtMs <= safeExpiryBoundaryLocked()) {
            streams.remove(key)
            null
        } else {
            cached
        }
    }

    fun resolutionToken(mediaId: String): ResolutionToken = synchronized(lock) {
        ResolutionToken(mediaId, resolutionGenerations.getOrPut(mediaId) { 0L })
    }

    /**
     * A superseded result must be discarded by the caller rather than returned to Media3. This
     * keeps a late preload or resolver from reintroducing a URL invalidated during recovery.
     */
    fun cacheStream(
        key: StreamKey,
        url: String,
        expiresAtMs: Long,
        token: ResolutionToken,
        resolvedAtMs: Long = clockMs(),
        itag: Int? = null,
        mimeType: String? = null,
        bitrate: Int? = null,
    ): CacheWriteResult = synchronized(lock) {
        if (token.mediaId != key.mediaId ||
            token.generation != resolutionGenerationLocked(key.mediaId)
        ) {
            return@synchronized CacheWriteResult.Superseded
        }
        if (expiresAtMs <= safeExpiryBoundaryLocked()) {
            return@synchronized CacheWriteResult.Expired
        }
        streams[key] = CachedStream(
            url = url,
            expiresAtMs = expiresAtMs,
            resolvedAtMs = resolvedAtMs,
            generation = token.generation,
            itag = itag,
            mimeType = mimeType,
            bitrate = bitrate,
        )
        CacheWriteResult.Stored
    }

    fun activeQuality(mediaId: String): String? = synchronized(lock) {
        val now = clockMs()
        var quality: String? = null
        val iterator = streams.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.expiresAtMs <= now + expirySafetyMarginMs) {
                iterator.remove()
            } else if (quality == null && entry.key.mediaId == mediaId) {
                quality = entry.key.quality
            }
        }
        quality
    }

    fun retainOnly(mediaId: String?) = synchronized(lock) {
        // Tokens may have been issued for a preload that has not reached the cache yet. Keep
        // their generation tombstones too, otherwise that late completion could reinsert a
        // stream for an item that was just discarded.
        val invalidatedMediaIds = resolutionGenerations.keys
            .filterTo(mutableSetOf()) { it != mediaId }
        val iterator = streams.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key.mediaId != mediaId) {
                invalidatedMediaIds += entry.key.mediaId
                iterator.remove()
            }
        }
        invalidatedMediaIds.forEach(::invalidateGenerationLocked)
    }

    /** Invalidates all quality variants for this one media id, never the download cache. */
    fun invalidateStream(mediaId: String) = synchronized(lock) {
        invalidateStreamLocked(mediaId)
    }

    /** Arms a new user/media-item playback generation. A successful READY state must not call this. */
    fun beginPlayback(mediaId: String?, force: Boolean = false) = synchronized(lock) {
        if (force || activeMediaId != mediaId) {
            activeMediaId = mediaId
            attemptedRecoveryFor = null
            recoveryInProgress = null
            recoveryGeneration += 1
        }
    }

    fun playbackGeneration(): Long = synchronized(lock) { recoveryGeneration }

    fun onFailure(
        snapshot: PlaybackSnapshot,
        failure: FailureKind,
    ): RecoveryDecision = synchronized(lock) {
        if (failure == FailureKind.Permanent) {
            return@synchronized RecoveryDecision.NotRecoverable
        }

        if (activeMediaId != snapshot.mediaId) {
            activeMediaId = snapshot.mediaId
            attemptedRecoveryFor = null
            recoveryInProgress = null
            recoveryGeneration += 1
        }

        if (recoveryInProgress != null) {
            return@synchronized RecoveryDecision.RecoveryInProgress
        }

        // Even a terminal second stream failure must evict the known-bad fresh URL, so a later
        // user initiated playback does not reuse it. Local-source recovery has no URL to evict.
        if (failure.invalidatesStreamResolution) {
            invalidateStreamLocked(snapshot.mediaId)
        }

        if (attemptedRecoveryFor == snapshot.mediaId) {
            return@synchronized RecoveryDecision.Exhausted
        }

        attemptedRecoveryFor = snapshot.mediaId
        val token = RecoveryToken(snapshot.mediaId, ++recoveryGeneration)
        recoveryInProgress = token
        RecoveryDecision.Recover(token, snapshot, failure)
    }

    fun isCurrentRecovery(token: RecoveryToken): Boolean = synchronized(lock) {
        activeMediaId == token.mediaId && recoveryInProgress == token
    }

    fun completeRecovery(token: RecoveryToken) = synchronized(lock) {
        if (recoveryInProgress == token) {
            recoveryInProgress = null
        }
    }

    private fun invalidateStreamLocked(mediaId: String) {
        streams.keys.removeAll { it.mediaId == mediaId }
        invalidateGenerationLocked(mediaId)
    }

    private fun resolutionGenerationLocked(mediaId: String): Long = resolutionGenerations[mediaId] ?: 0L

    private fun safeExpiryBoundaryLocked(): Long = clockMs() + expirySafetyMarginMs

    private fun invalidateGenerationLocked(mediaId: String) {
        resolutionGenerations[mediaId] = resolutionGenerationLocked(mediaId) + 1
    }

    private companion object {
        const val DEFAULT_EXPIRY_SAFETY_MARGIN_MS = 15_000L
    }
}
