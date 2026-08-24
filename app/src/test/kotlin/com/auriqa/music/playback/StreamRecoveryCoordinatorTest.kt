package com.auriqo.music.playback

import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamRecoveryCoordinatorTest {
    private var nowMs = 1_000L
    private val coordinator = StreamRecoveryCoordinator { nowMs }
    private val key = StreamRecoveryCoordinator.StreamKey("video-id", "OPUS")
    private val snapshot = StreamRecoveryCoordinator.PlaybackSnapshot(
        mediaId = "video-id",
        queueIndex = 3,
        positionMs = 42_000L,
        playWhenReady = true,
    )

    private fun cache(url: String, expiresAtMs: Long): StreamRecoveryCoordinator.CacheWriteResult =
        coordinator.cacheStream(
            key = key,
            url = url,
            expiresAtMs = expiresAtMs,
            token = coordinator.resolutionToken(key.mediaId),
        )

    @Test
    fun validCachedStreamIsUsedWithoutResolutionOrRecovery() {
        cache("https://cdn.example/valid", nowMs + 60_000L)

        var resolverCalls = 0
        val url = coordinator.cachedStream(key)?.url ?: run {
            resolverCalls += 1
            "https://cdn.example/resolved"
        }

        assertEquals("https://cdn.example/valid", url)
        assertEquals(0, resolverCalls)
    }

    @Test
    fun expiredCachedStreamIsEvictedBeforeItCanBeReused() {
        cache("https://cdn.example/expired", nowMs + 1L)
        nowMs += 1L

        assertNull(coordinator.cachedStream(key))
    }

    @Test
    fun rejectedCachedStreamIsInvalidatedAndCanBeReplacedWithFreshResolution() {
        cache("https://cdn.example/stale", nowMs + 60_000L)
        coordinator.beginPlayback(snapshot.mediaId)

        val decision = coordinator.onFailure(
            snapshot,
            StreamRecoveryCoordinator.FailureKind.RejectedStream,
        )

        val recovery = decision as StreamRecoveryCoordinator.RecoveryDecision.Recover
        assertNull(coordinator.cachedStream(key))

        val freshToken = coordinator.resolutionToken(snapshot.mediaId)
        assertEquals(
            StreamRecoveryCoordinator.CacheWriteResult.Stored,
            coordinator.cacheStream(
                key,
                "https://cdn.example/fresh",
                nowMs + 60_000L,
                freshToken,
            ),
        )
        coordinator.completeRecovery(recovery.token)

        assertEquals("https://cdn.example/fresh", coordinator.cachedStream(key)?.url)
    }

    @Test
    fun secondRejectedStreamIsTerminalAndDoesNotStartAnotherRecoveryLoop() {
        coordinator.beginPlayback(snapshot.mediaId)
        val first = coordinator.onFailure(
            snapshot,
            StreamRecoveryCoordinator.FailureKind.RejectedStream,
        ) as StreamRecoveryCoordinator.RecoveryDecision.Recover
        coordinator.completeRecovery(first.token)

        cache("https://cdn.example/also-stale", nowMs + 60_000L)
        val second = coordinator.onFailure(
            snapshot,
            StreamRecoveryCoordinator.FailureKind.RejectedStream,
        )

        assertEquals(StreamRecoveryCoordinator.RecoveryDecision.Exhausted, second)
        assertNull(coordinator.cachedStream(key))
    }

    @Test
    fun permanentErrorDoesNotInvalidateOrRefetchTheStream() {
        cache("https://cdn.example/valid", nowMs + 60_000L)
        coordinator.beginPlayback(snapshot.mediaId)

        val decision = coordinator.onFailure(
            snapshot,
            StreamRecoveryCoordinator.FailureKind.Permanent,
        )

        assertEquals(StreamRecoveryCoordinator.RecoveryDecision.NotRecoverable, decision)
        assertEquals("https://cdn.example/valid", coordinator.cachedStream(key)?.url)
    }

    @Test
    fun localSourceRecoveryDoesNotInvalidateAStreamResolution() {
        cache("https://cdn.example/valid", nowMs + 60_000L)
        coordinator.beginPlayback(snapshot.mediaId)

        val decision = coordinator.onFailure(
            snapshot,
            StreamRecoveryCoordinator.FailureKind.LocalSourceCorruption,
        )

        assertTrue(decision is StreamRecoveryCoordinator.RecoveryDecision.Recover)
        assertEquals("https://cdn.example/valid", coordinator.cachedStream(key)?.url)
    }

    @Test
    fun concurrentCallbacksStartOnlyOneRecovery() {
        coordinator.beginPlayback(snapshot.mediaId)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val decisions = ConcurrentLinkedQueue<StreamRecoveryCoordinator.RecoveryDecision>()

        val callbacks = List(2) {
            thread(start = true) {
                ready.countDown()
                assertTrue(start.await(1, TimeUnit.SECONDS))
                decisions += coordinator.onFailure(
                    snapshot,
                    StreamRecoveryCoordinator.FailureKind.RejectedStream,
                )
            }
        }

        assertTrue(ready.await(1, TimeUnit.SECONDS))
        start.countDown()
        callbacks.forEach { it.join(1_000L) }

        assertEquals(1, decisions.count { it is StreamRecoveryCoordinator.RecoveryDecision.Recover })
        assertEquals(1, decisions.count { it == StreamRecoveryCoordinator.RecoveryDecision.RecoveryInProgress })
    }

    @Test
    fun recoveryDecisionPreservesThePlaybackSnapshot() {
        coordinator.beginPlayback(snapshot.mediaId)

        val decision = coordinator.onFailure(
            snapshot,
            StreamRecoveryCoordinator.FailureKind.CacheOrStreamCorruption,
        ) as StreamRecoveryCoordinator.RecoveryDecision.Recover

        assertEquals(snapshot.mediaId, decision.snapshot.mediaId)
        assertEquals(snapshot.queueIndex, decision.snapshot.queueIndex)
        assertEquals(snapshot.positionMs, decision.snapshot.positionMs)
        assertEquals(snapshot.playWhenReady, decision.snapshot.playWhenReady)
    }

    @Test
    fun invalidationRejectsLateWriteFromAnOlderResolutionGeneration() {
        val oldResolution = coordinator.resolutionToken(key.mediaId)
        coordinator.invalidateStream(key.mediaId)

        assertEquals(
            StreamRecoveryCoordinator.CacheWriteResult.Superseded,
            coordinator.cacheStream(
                key,
                "https://cdn.example/late",
                nowMs + 60_000L,
                oldResolution,
            ),
        )
        assertNull(coordinator.cachedStream(key))
    }

    @Test
    fun retainOnlyInvalidatesDiscardedMediaGenerations() {
        val retainedKey = StreamRecoveryCoordinator.StreamKey("retained-id", "OPUS")
        val discardedKey = StreamRecoveryCoordinator.StreamKey("discarded-id", "OPUS")
        val discardedToken = coordinator.resolutionToken(discardedKey.mediaId)
        coordinator.cacheStream(
            retainedKey,
            "https://cdn.example/retained",
            nowMs + 60_000L,
            coordinator.resolutionToken(retainedKey.mediaId),
        )
        coordinator.cacheStream(
            discardedKey,
            "https://cdn.example/discarded",
            nowMs + 60_000L,
            discardedToken,
        )

        coordinator.retainOnly(retainedKey.mediaId)

        assertEquals("https://cdn.example/retained", coordinator.cachedStream(retainedKey)?.url)
        assertNull(coordinator.cachedStream(discardedKey))
        assertEquals(
            StreamRecoveryCoordinator.CacheWriteResult.Superseded,
            coordinator.cacheStream(
                discardedKey,
                "https://cdn.example/late-discarded",
                nowMs + 60_000L,
                discardedToken,
            ),
        )
    }

    @Test
    fun retainOnlyInvalidatesAnUncachedDiscardedResolution() {
        val retainedKey = StreamRecoveryCoordinator.StreamKey("retained-id", "OPUS")
        val inFlightKey = StreamRecoveryCoordinator.StreamKey("in-flight-id", "OPUS")
        val inFlightToken = coordinator.resolutionToken(inFlightKey.mediaId)

        coordinator.cacheStream(
            retainedKey,
            "https://cdn.example/retained",
            nowMs + 60_000L,
            coordinator.resolutionToken(retainedKey.mediaId),
        )

        coordinator.retainOnly(retainedKey.mediaId)

        assertEquals(
            StreamRecoveryCoordinator.CacheWriteResult.Superseded,
            coordinator.cacheStream(
                inFlightKey,
                "https://cdn.example/late-in-flight",
                nowMs + 60_000L,
                inFlightToken,
            ),
        )
    }

    @Test
    fun activeQualityPurgesExpiredEntriesAndIgnoresOtherMedia() {
        val expiredKey = StreamRecoveryCoordinator.StreamKey(key.mediaId, "M4A")
        val otherKey = StreamRecoveryCoordinator.StreamKey("other-id", "OPUS")
        coordinator.cacheStream(
            expiredKey,
            "https://cdn.example/expired",
            nowMs + 1L,
            coordinator.resolutionToken(expiredKey.mediaId),
        )
        nowMs += 1L
        coordinator.cacheStream(
            otherKey,
            "https://cdn.example/other",
            nowMs + 60_000L,
            coordinator.resolutionToken(otherKey.mediaId),
        )

        assertNull(coordinator.activeQuality(key.mediaId))

        cache("https://cdn.example/current", nowMs + 60_000L)

        assertEquals(key.quality, coordinator.activeQuality(key.mediaId))
    }

    @Test
    fun aNewPlaybackGenerationRearmsRecoveryForTheSameMediaId() {
        coordinator.beginPlayback(snapshot.mediaId)
        val first = coordinator.onFailure(
            snapshot,
            StreamRecoveryCoordinator.FailureKind.RejectedStream,
        ) as StreamRecoveryCoordinator.RecoveryDecision.Recover
        coordinator.completeRecovery(first.token)

        coordinator.beginPlayback(snapshot.mediaId, force = true)
        val replay = coordinator.onFailure(
            snapshot,
            StreamRecoveryCoordinator.FailureKind.RejectedStream,
        )

        assertTrue(replay is StreamRecoveryCoordinator.RecoveryDecision.Recover)
    }

    @Test
    fun alreadyExpiredResolutionIsNotCached() {
        assertEquals(
            StreamRecoveryCoordinator.CacheWriteResult.Expired,
            cache("https://cdn.example/already-expired", nowMs),
        )

        assertNull(coordinator.cachedStream(key))
    }

    @Test
    fun safetyMarginRejectsAUrlThatWouldExpireDuringStartup() {
        assertEquals(
            StreamRecoveryCoordinator.CacheWriteResult.Expired,
            cache("https://cdn.example/too-close", nowMs + 10_000L),
        )
        assertEquals(
            StreamRecoveryCoordinator.CacheWriteResult.Stored,
            cache("https://cdn.example/safe", nowMs + 16_000L),
        )
        assertEquals("https://cdn.example/safe", coordinator.cachedStream(key)?.url)
    }

    @Test
    fun cachedStreamCarriesResolutionMetadataAndGeneration() {
        val token = coordinator.resolutionToken(key.mediaId)
        assertEquals(
            StreamRecoveryCoordinator.CacheWriteResult.Stored,
            coordinator.cacheStream(
                key = key,
                url = "https://cdn.example/metadata",
                expiresAtMs = nowMs + 60_000L,
                token = token,
                resolvedAtMs = nowMs + 25L,
                itag = 251,
                mimeType = "audio/webm",
                bitrate = 128_000,
            ),
        )

        val cached = coordinator.cachedStream(key)
        assertEquals(25L, cached?.resolvedAtMs?.minus(nowMs))
        assertEquals(token.generation, cached?.generation)
        assertEquals(251, cached?.itag)
        assertEquals("audio/webm", cached?.mimeType)
        assertEquals(128_000, cached?.bitrate)
    }

    @Test
    fun playbackGenerationChangesOnlyForForcedOrNewMediaPlayback() {
        val initial = coordinator.playbackGeneration()
        coordinator.beginPlayback("video-id")
        val first = coordinator.playbackGeneration()
        coordinator.beginPlayback("video-id")
        assertEquals(first, coordinator.playbackGeneration())
        coordinator.beginPlayback("video-id", force = true)
        assertTrue(coordinator.playbackGeneration() > first)
        assertTrue(coordinator.playbackGeneration() > initial)
    }

    @Test
    fun rapidPlaybackSelectionDiscardsLateAAndBResolutions() {
        val aKey = StreamRecoveryCoordinator.StreamKey("A", "OPUS")
        val bKey = StreamRecoveryCoordinator.StreamKey("B", "OPUS")
        val cKey = StreamRecoveryCoordinator.StreamKey("C", "OPUS")

        coordinator.beginPlayback("A", force = true)
        val aToken = coordinator.resolutionToken("A")
        coordinator.beginPlayback("B", force = true)
        val bToken = coordinator.resolutionToken("B")
        coordinator.beginPlayback("C", force = true)
        val cToken = coordinator.resolutionToken("C")

        assertEquals(
            StreamRecoveryCoordinator.CacheWriteResult.Superseded,
            coordinator.cacheStream(aKey, "https://cdn.example/A", nowMs + 60_000L, aToken),
        )
        assertEquals(
            StreamRecoveryCoordinator.CacheWriteResult.Superseded,
            coordinator.cacheStream(bKey, "https://cdn.example/B", nowMs + 60_000L, bToken),
        )
        assertEquals(
            StreamRecoveryCoordinator.CacheWriteResult.Stored,
            coordinator.cacheStream(cKey, "https://cdn.example/C", nowMs + 60_000L, cToken),
        )
        assertEquals("https://cdn.example/C", coordinator.cachedStream(cKey)?.url)
    }

    @Test
    fun changingMediaCancelsAnInProgressRecoveryToken() {
        coordinator.beginPlayback("A", force = true)
        val decision = coordinator.onFailure(
            StreamRecoveryCoordinator.PlaybackSnapshot("A", 0, 0L, true),
            StreamRecoveryCoordinator.FailureKind.RateLimited,
        ) as StreamRecoveryCoordinator.RecoveryDecision.Recover

        coordinator.beginPlayback("B", force = true)

        assertTrue(!coordinator.isCurrentRecovery(decision.token))
    }
}
