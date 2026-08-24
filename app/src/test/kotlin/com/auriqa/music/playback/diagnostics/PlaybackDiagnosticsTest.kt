package com.auriqo.music.playback.diagnostics

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDiagnosticsTest {
    @Test
    fun traceIdsHaveStableIssueFriendlyShape() {
        val traceId = newPlaybackTraceId(java.util.UUID.fromString("a913cf21-0000-0000-0000-000000000000"))

        assertEquals("PB-A913CF21", traceId)
    }

    @Test
    fun bufferEvictsOldestEventsWithoutGrowing() {
        val buffer = PlaybackDiagnosticBuffer(capacity = 2)
        val traceId = "PB-11111111"
        buffer.append(PlaybackDiagnosticEvent.Breadcrumb(traceId, 1, "a", "one", null))
        buffer.append(PlaybackDiagnosticEvent.Breadcrumb(traceId, 2, "a", "two", null))
        buffer.append(PlaybackDiagnosticEvent.Breadcrumb(traceId, 3, "a", "three", null))

        assertEquals(listOf("two", "three"), buffer.snapshot().map { (it as PlaybackDiagnosticEvent.Breadcrumb).name })
    }

    @Test
    fun traceRecorderCorrelatesEventsAndRecordsLatency() {
        var nowNs = 0L
        val buffer = PlaybackDiagnosticBuffer(32)
        val metrics = PlaybackMetrics()
        val trace = PlaybackTraceRecorder(
            traceId = "PB-ABCDEF12",
            mediaId = "video-id",
            buffer = buffer,
            metrics = metrics,
            clockNs = { nowNs },
        )

        trace.recordTap()
        nowNs = 100_000_000
        trace.resolutionRequested("video-id", "OPUS")
        nowNs = 350_000_000
        trace.streamSelected(251, "audio/webm", 160_000)
        nowNs = 500_000_000
        trace.firstAudio()
        trace.firstAudio()
        trace.recoveryStart(attempt = 1, maxAttempts = 1, reason = "HTTP_403")
        nowNs = 650_000_000
        trace.recoveryEnd(attempt = 1, success = true, result = "recovered")

        assertTrue(buffer.snapshot().isNotEmpty())
        assertTrue(buffer.snapshot().all { it.traceId == "PB-ABCDEF12" })
        assertEquals(500L, metrics.snapshot().histograms.getValue(PlaybackMetric.TAP_TO_FIRST_AUDIO).p50Ms)
        assertEquals(250L, metrics.snapshot().histograms.getValue(PlaybackMetric.RESOLUTION_LATENCY).p50Ms)
        assertEquals(1, buffer.snapshot().count { it is PlaybackDiagnosticEvent.FirstAudio })
        assertEquals(
            150L,
            buffer.snapshot().filterIsInstance<PlaybackDiagnosticEvent.RecoveryEnd>().single().durationMs,
        )
    }

    @Test
    fun classifierDoesNotTurnEvery403IntoAgeRestriction() {
        val failure = PlaybackFailureClassifier.classify(
            PlaybackFailureInput(
                traceId = "PB-40340340",
                mediaId = "video-id",
                stage = PlaybackFailureStage.CDN_HTTP,
                http = PlaybackHttpDetails(403, "Forbidden", host = "rr5---sn.example.googlevideo.com"),
                media3Code = 2004,
                media3CodeName = "IO_BAD_HTTP_STATUS",
            ),
        )

        assertEquals(PlaybackFailureCode.STREAM_HTTP_403, failure.exactCode)
        assertFalse(failure.humanMessage.contains("edad", ignoreCase = true))
        assertEquals(403, failure.httpStatus)
        assertEquals(2004, failure.media3Code)
    }

    @Test
    fun explicitPlayabilityEvidenceWinsOverHttpStatus() {
        val age = PlaybackFailureClassifier.classify(
            PlaybackFailureInput(
                traceId = "PB-AGE00001",
                mediaId = "video-id",
                stage = PlaybackFailureStage.PLAYABILITY,
                http = PlaybackHttpDetails(403),
                playabilityStatus = "AGE_VERIFICATION_REQUIRED",
                playabilityReason = "Sign in to confirm your age",
            ),
        )
        val login = PlaybackFailureClassifier.classify(
            PlaybackFailureInput(
                traceId = "PB-LOGIN001",
                mediaId = "video-id",
                stage = PlaybackFailureStage.PLAYABILITY,
                playabilityStatus = "LOGIN_REQUIRED",
                playabilityReason = "Sign in to play this video",
            ),
        )

        assertEquals(PlaybackFailureCode.PLAYABILITY_AGE_RESTRICTED, age.exactCode)
        assertEquals(PlaybackFailureCode.PLAYABILITY_LOGIN_REQUIRED, login.exactCode)
        assertTrue(login.terminal)
    }

    @Test
    fun httpAndTransportTaxonomyIsStable() {
        val expected = mapOf(
            404 to PlaybackFailureCode.STREAM_HTTP_404,
            410 to PlaybackFailureCode.STREAM_HTTP_410,
            429 to PlaybackFailureCode.STREAM_HTTP_429,
            500 to PlaybackFailureCode.STREAM_HTTP_5XX,
            503 to PlaybackFailureCode.STREAM_HTTP_5XX,
        )
        expected.forEach { (status, code) ->
            val result = PlaybackFailureClassifier.classify(
                PlaybackFailureInput(
                    traceId = "PB-HTTP0001",
                    mediaId = "video-id",
                    stage = PlaybackFailureStage.CDN_HTTP,
                    http = PlaybackHttpDetails(status),
                ),
            )
            assertEquals("status=$status", code, result.exactCode)
        }

        val timeout = PlaybackFailureClassifier.classify(
            PlaybackFailureInput(
                traceId = "PB-TIME001",
                mediaId = "video-id",
                stage = PlaybackFailureStage.NETWORK,
                hint = PlaybackFailureHint.TIMEOUT,
                cause = SocketTimeoutException("read timed out"),
            ),
        )
        assertEquals(PlaybackFailureCode.NETWORK_TIMEOUT, timeout.exactCode)
    }

    @Test
    fun causeChainAndRedactionPreserveUsefulContextWithoutSecrets() {
        val root = IOException(
            "GET https://rr5---sn.example.googlevideo.com/videoplayback?expire=123&itag=251&sig=secret-value&pot=private-pot",
            SocketTimeoutException("Cookie=secret-cookie Authorization: bearer-secret"),
        )
        val chain = PlaybackCauseChainExtractor.extract(root)
        val sanitized = chain.joinToString(" ") { it.message.orEmpty() }
        val url = PlaybackRedactor.redactUrl(
            "https://rr5---sn.example.googlevideo.com/videoplayback?expire=123&itag=251&sig=secret-value&pot=private-pot",
        )

        assertEquals(2, chain.size)
        assertTrue(sanitized.isNotEmpty())
        assertFalse(sanitized.contains("secret-value"))
        assertFalse(sanitized.contains("private-pot"))
        val visitor = PlaybackRedactor.sanitizeText("visitorData=full-visitor-value x-goog-visitor-id:full-id")
        assertFalse(visitor.contains("full-visitor-value"))
        assertFalse(visitor.contains("full-id"))
        assertTrue(url.contains("host=rr5---sn.example.googlevideo.com"))
        assertTrue(url.contains("expireEpoch=123"))
        assertFalse(url.contains("secret-value"))
        assertFalse(url.contains("private-pot"))
    }

    @Test
    fun histogramExposesPercentilesAndCounters() {
        val metrics = PlaybackMetrics()
        listOf(100L, 200L, 300L, 400L, 500L).forEach { metrics.record(PlaybackMetric.TAP_TO_FIRST_AUDIO, it) }
        metrics.recordCacheHit()
        metrics.recordCacheMiss()
        metrics.recordRecovery(true)
        metrics.recordTerminalFailure()

        val snapshot = metrics.snapshot()
        val histogram = snapshot.histograms.getValue(PlaybackMetric.TAP_TO_FIRST_AUDIO)
        assertEquals(5, histogram.count)
        assertEquals(300L, histogram.p50Ms)
        assertEquals(500L, histogram.p95Ms)
        assertEquals(1L, snapshot.cacheHits)
        assertEquals(0.5, snapshot.cacheHitRate, 0.000001)
        assertEquals(1.0, snapshot.recoverySuccessRate, 0.000001)
        assertEquals(1L, snapshot.terminalFailures)
        assertNotNull(snapshot.histograms[PlaybackMetric.RESOLUTION_LATENCY])
    }

    @Test
    fun detachedResolutionDoesNotStealAnUnboundActiveTrace() {
        PlaybackDiagnostics.clear()
        try {
            val active = PlaybackDiagnostics.start(mediaId = null, source = "queue")
            val preload = PlaybackDiagnostics.startResolution("video-id", "preload")

            assertEquals(active.traceId, PlaybackDiagnostics.current()?.traceId)
            assertEquals(preload.traceId, PlaybackDiagnostics.currentFor("video-id")?.traceId)
        } finally {
            PlaybackDiagnostics.clear()
        }
    }

    @Test
    fun boundedMediaTraceLookupKeepsLateDataSourceEventsCorrelated() {
        PlaybackDiagnostics.clear()
        try {
            val first = PlaybackDiagnostics.start("A", "tap")
            PlaybackDiagnostics.start("B", "tap")

            assertEquals(first.traceId, PlaybackDiagnostics.currentFor("A")?.traceId)
        } finally {
            PlaybackDiagnostics.clear()
        }
    }

    @Test
    fun debugReportContainsTraceAndRedactsSignedValues() {
        val failure = PlaybackFailureClassifier.classify(
            PlaybackFailureInput(
                traceId = "PB-REPORT01",
                mediaId = "video-id",
                stage = PlaybackFailureStage.CDN_HTTP,
                http = PlaybackHttpDetails(
                    responseCode = 403,
                    host = "rr5---sn.example.googlevideo.com",
                    queryKeys = listOf("expire", "itag", "sig", "pot"),
                    expireEpoch = 123L,
                ),
                media3Code = 2004,
                media3CodeName = "IO_BAD_HTTP_STATUS",
                quality = "AAC",
                queueIndex = 7,
                cause = IOException(
                    "https://rr5---sn.example.googlevideo.com/videoplayback?sig=secret",
                ),
                terminalOverride = true,
            ),
        )
        val report = PlaybackDebugReportFormatter.format(
            failure,
            PlaybackDebugReportContext(
                appVersion = "1.0.5",
                sourceRevision = "abc123",
                manufacturer = "Acme",
                model = "Player",
                androidVersion = "14",
                api = 34,
                abi = "arm64-v8a",
                quality = null,
                localOrRemote = "remote",
                queueIndex = null,
                networkType = "wifi",
                proxyEnabled = false,
            ),
            events = listOf(
                PlaybackDiagnosticEvent.Tap("PB-REPORT01", 0, "video-id", "user"),
                PlaybackDiagnosticEvent.HttpStatus(
                    "PB-REPORT01",
                    250,
                    "video-id",
                    failure.let { PlaybackHttpDetails(403, host = it.httpStatus?.toString()) },
                ),
            ),
        )

        assertTrue(report.contains("Trace ID: PB-REPORT01"))
        assertTrue(report.contains("AURIQO_STREAM_HTTP_403"))
        assertTrue(report.contains("IO_BAD_HTTP_STATUS (2004)"))
        assertTrue(report.contains("quality=AAC"))
        assertTrue(report.contains("queueIndex=7"))
        assertTrue(report.contains("proxyEnabled=false"))
        assertFalse(report.contains("secret"))
        assertFalse(report.contains("Cookie"))
    }

    @Test
    fun typedResolverEvidenceIsClassifiedWithMedia3Metadata() {
        val resolverFailure = PlaybackResolutionException(
            message = "Sign in to confirm your age",
            playabilityStatus = "LOGIN_REQUIRED",
            playabilityReason = "Sign in to confirm your age",
        )
        val failure = PlaybackFailureClassifier.classify(
            PlaybackFailureInput(
                traceId = "PB-RESOLVER1",
                mediaId = "video-id",
                stage = PlaybackFailureStage.PLAYER_RESPONSE,
                media3Code = 2000,
                media3CodeName = "REMOTE_ERROR",
                playabilityStatus = resolverFailure.playabilityStatus,
                playabilityReason = resolverFailure.playabilityReason,
                cause = resolverFailure,
            ),
        )

        assertEquals(PlaybackFailureCode.PLAYABILITY_AGE_RESTRICTED, failure.exactCode)
        assertEquals("LOGIN_REQUIRED", failure.playabilityStatus)
        assertEquals(2000, failure.media3Code)
        assertTrue(failure.causeChain.any { it.className == "PlaybackResolutionException" })
    }

}
