package com.auriqo.music.debug

import com.auriqo.music.playback.diagnostics.PlaybackDiagnosticEvent
import com.auriqo.music.playback.diagnostics.PlaybackDiagnostics
import com.auriqo.music.playback.diagnostics.PlaybackFailureClassifier
import com.auriqo.music.playback.diagnostics.PlaybackFailureInput
import com.auriqo.music.playback.diagnostics.PlaybackFailureStage
import com.auriqo.music.playback.diagnostics.PlaybackHttpDetails
import com.auriqo.music.playback.diagnostics.PlaybackRedactor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DebugToolingTest {
    @Test
    fun chaosFaultsAreOneShotAndResettable() {
        val chaos = DebugChaosController()
        chaos.arm(
            label = "403",
            point = DebugFaultPoint.DATASOURCE_OPEN,
            spec = DebugFaultSpec(DebugFaultSpec.Kind.HTTP_STATUS, httpStatus = 403),
        )

        assertEquals(1, chaos.pending.value.size)
        assertEquals(403, chaos.consume(DebugFaultPoint.DATASOURCE_OPEN)?.httpStatus)
        assertEquals(null, chaos.consume(DebugFaultPoint.DATASOURCE_OPEN))

        chaos.arm(
            label = "timeout",
            point = DebugFaultPoint.PLAYER_RESPONSE,
            spec = DebugFaultSpec(DebugFaultSpec.Kind.RESOLUTION_TIMEOUT),
        )
        chaos.clear()
        assertTrue(chaos.pending.value.isEmpty())
    }

    @Test
    fun collectorClassifiesTracesAndComputesPercentilesWithoutCrossTraceContamination() = runBlocking {
        PlaybackDiagnostics.clear()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val collector = PlaybackDebugCollector(scope)
            appendHot("PB-HOT0001", 100L)
            appendCold("PB-COLD001", 200L)
            appendPreloaded("PB-PRE0001", 300L)
            appendRecovered("PB-REC0001", 800L)

            val state = withTimeout(2_000L) {
                collector.state.first { current ->
                    current.traces.size == 4 && current.traces.all { trace ->
                        trace.events.any { it is PlaybackDiagnosticEvent.FirstAudio }
                    }
                }
            }

            assertEquals(DebugPerformanceClass.HOT, state.traces.first { it.traceId == "PB-HOT0001" }.classification)
            assertEquals(DebugPerformanceClass.COLD, state.traces.first { it.traceId == "PB-COLD001" }.classification)
            assertEquals(DebugPerformanceClass.PRELOADED, state.traces.first { it.traceId == "PB-PRE0001" }.classification)
            assertEquals(DebugPerformanceClass.RECOVERED, state.traces.first { it.traceId == "PB-REC0001" }.classification)
            assertEquals(4, state.metrics.plays)
            assertEquals(4, state.metrics.successful)
            assertEquals(300L, state.metrics.tapToFirstAudio.p50Ms)
            assertEquals(800L, state.metrics.tapToFirstAudio.p95Ms)
            assertEquals(1, state.metrics.byClass.getValue(DebugPerformanceClass.PRELOADED).plays)
            assertEquals(1, state.metrics.byClass.getValue(DebugPerformanceClass.RECOVERED).recovered)
            assertTrue(collector.eventsForTrace("PB-COLD001").all { it.traceId == "PB-COLD001" })
        } finally {
            scope.cancel()
            PlaybackDiagnostics.clear()
        }
    }

    @Test
    fun streamCandidateEvidenceIsPromotedIntoTraceSummary() = runBlocking {
        PlaybackDiagnostics.clear()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val collector = PlaybackDebugCollector(scope)
            val traceId = "PB-CLIENT01"
            PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.Tap(traceId, 0L, "video", "tap"))
            PlaybackDiagnostics.buffer.append(
                PlaybackDiagnosticEvent.Breadcrumb(
                    traceId,
                    20L,
                    "video",
                    "PLAYER_JS_SKIPPED",
                    "client=VISIONOS reason=signature_timestamp_not_required",
                ),
            )
            PlaybackDiagnostics.buffer.append(
                PlaybackDiagnosticEvent.Breadcrumb(
                    traceId,
                    40L,
                    "video",
                    "STREAM_CANDIDATE",
                    "VISIONOS/1.02 source=RawPlayer pot=false itag=251 context=7",
                ),
            )
            PlaybackDiagnostics.buffer.append(
                PlaybackDiagnosticEvent.StreamSelected(traceId, 42L, "video", 251, "audio/webm", 160_000),
            )
            PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.FirstAudio(traceId, 100L, "video"))

            val trace = withTimeout(2_000L) {
                collector.state.first {
                    it.traces.any { snapshot -> snapshot.traceId == traceId && snapshot.succeeded }
                }.traces.first { it.traceId == traceId }
            }

            assertEquals("VISIONOS/1.02", trace.streamClient)
            assertEquals("RawPlayer", trace.streamSource)
            assertEquals(false, trace.poTokenAttached)
            assertEquals(7L, trace.streamContextGeneration)
            assertEquals("PRIMARY", trace.resolverPath)
            assertFalse(trace.playerJsUsed)
        } finally {
            scope.cancel()
            PlaybackDiagnostics.clear()
        }
    }

    @Test
    fun candidateParserHandlesWebFallbackEvidence() {
        val evidence = parseStreamCandidateEvidence(
            "WEB_REMIX/1.20260811.15.00 source=RawPlayer pot=true itag=251 context=12",
        )

        assertEquals("WEB_REMIX/1.20260811.15.00", evidence.client)
        assertEquals("RawPlayer", evidence.source)
        assertEquals(true, evidence.poTokenAttached)
        assertEquals(12L, evidence.contextGeneration)
    }

    @Test
    fun detachedPreloadNeverBecomesTheActiveHudTrace() = runBlocking {
        PlaybackDiagnostics.clear()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val collector = PlaybackDebugCollector(scope)
            val active = PlaybackDiagnostics.start("playing", "transition")
            active.recordTap("user")
            active.firstAudio()

            val preload = PlaybackDiagnostics.startResolution("next", "preload")
            preload.resolutionRequested("next", "OPUS")

            val state = withTimeout(2_000L) {
                collector.state.first { current ->
                    current.traces.any { it.traceId == active.traceId } &&
                        current.traces.any { it.traceId == preload.traceId }
                }
            }

            assertEquals(active.traceId, state.activeTrace?.traceId)
            assertEquals("playing", state.activeTrace?.mediaId)
        } finally {
            scope.cancel()
            PlaybackDiagnostics.clear()
        }
    }

    @Test
    fun collectorDetectsSlowTraceAndDominantStage() = runBlocking {
        PlaybackDiagnostics.clear()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val collector = PlaybackDebugCollector(scope)
            val traceId = "PB-SLOW0001"
            PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.Tap(traceId, 0L, "slow", "tap"))
            PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.ResolutionRequested(traceId, 2L, "slow", "HIGH"))
            PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.PlayerResponseEnd(traceId, 800L, "slow", 798L, "OK", true))
            PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.StreamSelected(traceId, 805L, "slow", 251, "audio/webm", 160_000))
            PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.DataSourceOpenEnd(traceId, 1_650L, "slow", 845L, true))
            PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.FirstAudio(traceId, 1_700L, "slow"))

            val trace = withTimeout(2_000L) {
                collector.state.first { it.traces.any { snapshot -> snapshot.traceId == traceId && snapshot.slow } }
                    .traces.first { it.traceId == traceId }
            }
            assertTrue(trace.slow)
            assertEquals("DATASOURCE_OPEN", trace.dominantStage)
            assertEquals(1_700L, trace.tapToFirstAudioMs)
        } finally {
            scope.cancel()
            PlaybackDiagnostics.clear()
        }
    }

    @Test
    fun boundedHistoryAndBundleSnapshotRemainRedacted() {
        val buffer = com.auriqo.music.playback.diagnostics.PlaybackDiagnosticBuffer(capacity = 200)
        repeat(250) { index ->
            buffer.append(
                PlaybackDiagnosticEvent.Breadcrumb(
                    traceId = "PB-${index.toString().padStart(8, '0')}",
                    elapsedMs = index.toLong(),
                    mediaId = "media-$index",
                    name = "EVENT",
                    value = "value-$index",
                ),
            )
        }
        assertEquals(200, buffer.snapshot().size)
        assertEquals("PB-00000050", buffer.snapshot().first().traceId)

        val state = PlaybackDebugState(
            events = listOf(
                PlaybackDiagnosticEvent.Breadcrumb(
                    traceId = "PB-REDACT01",
                    elapsedMs = 1L,
                    mediaId = "media",
                    name = "SECRET_TEST",
                    value = "Cookie: SUPER_SECRET_AURIQO_TEST_VALUE",
                ),
            ),
        )
        val network = NetworkDebugRequest(
            id = 1L,
            traceId = "PB-REDACT01",
            method = "GET",
            host = "rr5---sn.example.googlevideo.com",
            pathHash = "abcd1234",
            queryKeys = listOf("expire", "itag", "sig"),
            startedAtMs = 0L,
            requestHeaders = mapOf("Cookie" to "SUPER_SECRET_AURIQO_TEST_VALUE", "Authorization" to "Bearer SUPER_SECRET_AURIQO_TEST_VALUE"),
            responseHeaders = mapOf("Content-Type" to "audio/webm"),
        )
        val snapshot = DebugBundleExporter.sessionSnapshotJson(state, listOf(network))

        assertFalse(snapshot.contains("SUPER_SECRET_AURIQO_TEST_VALUE"))
        assertTrue(snapshot.contains("sensitiveHeadersPresent"))
        assertTrue(snapshot.contains("PB-REDACT01"))
        assertTrue(snapshot.contains("requestHeaders"))
        assertTrue(PlaybackRedactor.redactUrl("https://example.test/videoplayback?expire=123&sig=SUPER_SECRET_AURIQO_TEST_VALUE")
            .contains("expireEpoch=123"))
    }

    @Test
    fun terminalFailureHistoryKeepsAuriqoAndMedia3Codes() = runBlocking {
        PlaybackDiagnostics.clear()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val collector = PlaybackDebugCollector(scope)
            val failure = PlaybackFailureClassifier.classify(
                PlaybackFailureInput(
                    traceId = "PB-FAIL0001",
                    mediaId = "video",
                    stage = PlaybackFailureStage.CDN_HTTP,
                    http = PlaybackHttpDetails(403, "Forbidden", host = "googlevideo.example"),
                    media3Code = 2004,
                    media3CodeName = "IO_BAD_HTTP_STATUS",
                ),
            )
            PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.Tap("PB-FAIL0001", 0L, "video", "tap"))
            PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.TerminalFailure("PB-FAIL0001", 420L, "video", failure))
            val trace = withTimeout(2_000L) {
                collector.state.first { it.traces.any { snapshot -> snapshot.failure != null } }
                    .traces.first { it.traceId == "PB-FAIL0001" }
            }
            assertNotNull(trace.failure)
            assertEquals("AURIQO_STREAM_HTTP_403", trace.failure?.stableCode)
            assertEquals(2004, trace.failure?.media3Code)
            assertEquals("IO_BAD_HTTP_STATUS", trace.failure?.media3CodeName)
        } finally {
            scope.cancel()
            PlaybackDiagnostics.clear()
        }
    }

    private fun appendHot(traceId: String, firstAudioMs: Long) {
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.Tap(traceId, 0L, "hot", "tap"))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.ResolutionCacheHit(traceId, 2L, "hot", 30_000L))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.StreamSelected(traceId, 4L, "hot", 251, "audio/webm", 160_000))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.FirstAudio(traceId, firstAudioMs, "hot"))
    }

    private fun appendCold(traceId: String, firstAudioMs: Long) {
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.Tap(traceId, 0L, "cold", "tap"))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.ResolutionRequested(traceId, 2L, "cold", "HIGH"))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.ResolutionCacheMiss(traceId, 3L, "cold", "not_cached"))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.PlayerResponseEnd(traceId, 80L, "cold", 77L, "OK", true))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.StreamSelected(traceId, 90L, "cold", 251, "audio/webm", 160_000))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.DataSourceOpenEnd(traceId, 120L, "cold", 30L, true))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.FirstAudio(traceId, firstAudioMs, "cold"))
    }

    private fun appendPreloaded(traceId: String, firstAudioMs: Long) {
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.Tap(traceId, 0L, "preloaded", "tap"))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.Breadcrumb(traceId, 1L, "preloaded", "CACHE_ORIGIN", "player_preload"))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.ResolutionCacheHit(traceId, 2L, "preloaded", 20_000L))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.FirstAudio(traceId, firstAudioMs, "preloaded"))
    }

    private fun appendRecovered(traceId: String, firstAudioMs: Long) {
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.Tap(traceId, 0L, "recovered", "tap"))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.ResolutionCacheMiss(traceId, 2L, "recovered", "expired"))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.RecoveryStart(traceId, 100L, "recovered", 1, 2, "HTTP_403"))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.RecoveryEnd(traceId, 400L, "recovered", 1, true, "refreshed", 300L))
        PlaybackDiagnostics.buffer.append(PlaybackDiagnosticEvent.FirstAudio(traceId, firstAudioMs, "recovered"))
    }
}
