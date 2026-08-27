package com.auriqo.music.debug

import com.auriqo.music.playback.diagnostics.PlaybackDiagnosticEvent
import com.auriqo.music.playback.diagnostics.PlaybackDiagnostics
import com.auriqo.music.playback.diagnostics.PlaybackFailure
import com.auriqo.music.playback.diagnostics.PlaybackMetric
import com.auriqo.music.playback.diagnostics.PlaybackMetricsSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.ceil

enum class DebugPerformanceClass {
    HOT,
    COLD,
    PRELOADED,
    RECOVERED,
    UNKNOWN,
}

data class DebugStreamEvidence(
    val client: String? = null,
    val source: String? = null,
    val poTokenAttached: Boolean? = null,
    val contextGeneration: Long? = null,
)

internal fun parseStreamCandidateEvidence(value: String?): DebugStreamEvidence {
    if (value.isNullOrBlank()) return DebugStreamEvidence()
    val client = value.substringBefore(" source=").takeIf { it.isNotBlank() }
    val tokens = value.split(' ')
        .drop(1)
        .mapNotNull { token ->
            val separator = token.indexOf('=')
            if (separator <= 0 || separator == token.lastIndex) null
            else token.substring(0, separator) to token.substring(separator + 1)
        }
        .toMap()
    return DebugStreamEvidence(
        client = client,
        source = tokens["source"],
        poTokenAttached = tokens["pot"]?.toBooleanStrictOrNull(),
        contextGeneration = tokens["context"]?.toLongOrNull(),
    )
}

data class DebugTraceSnapshot(
    val traceId: String,
    val mediaId: String?,
    val events: List<PlaybackDiagnosticEvent>,
    val failure: PlaybackFailure?,
    val classification: DebugPerformanceClass,
    val tapToFirstAudioMs: Long?,
    val resolutionMs: Long?,
    val playerResponseMs: Long?,
    val dataSourceMs: Long?,
    val recoveryMs: Long?,
    val dominantStage: String?,
    val slow: Boolean,
    val cacheState: String,
    val itag: Int?,
    val mimeType: String?,
    val bitrate: Int?,
    val expiresInMs: Long?,
    val preloadedBytes: Int?,
    val streamClient: String?,
    val streamSource: String?,
    val poTokenAttached: Boolean?,
    val streamContextGeneration: Long?,
    val resolverPath: String,
    val playerJsUsed: Boolean,
) {
    val lastEvent: PlaybackDiagnosticEvent?
        get() = events.lastOrNull()

    val succeeded: Boolean
        get() = events.any { it is PlaybackDiagnosticEvent.FirstAudio }

    val recovered: Boolean
        get() = classification == DebugPerformanceClass.RECOVERED
}

data class DebugHistogram(
    val count: Int,
    val p50Ms: Long?,
    val p90Ms: Long?,
    val p95Ms: Long?,
    val p99Ms: Long?,
    val maxMs: Long?,
)

data class DebugClassMetrics(
    val classification: DebugPerformanceClass,
    val plays: Int,
    val successful: Int,
    val recovered: Int,
    val terminal: Int,
    val tapToFirstAudio: DebugHistogram,
)

data class DebugSessionMetrics(
    val plays: Int,
    val successful: Int,
    val recovered: Int,
    val terminal: Int,
    val recoveryAttempts: Long,
    val recoverySuccesses: Long,
    val cacheHits: Long,
    val cacheMisses: Long,
    val cacheHitRate: Double,
    val preloadHits: Int,
    val preloadHitRate: Double,
    val terminalFailureRate: Double,
    val recoverySuccessRate: Double,
    val tapToFirstAudio: DebugHistogram,
    val resolution: DebugHistogram,
    val playerResponse: DebugHistogram,
    val dataSource: DebugHistogram,
    val recovery: DebugHistogram,
    val byClass: Map<DebugPerformanceClass, DebugClassMetrics>,
) {
    companion object {
        fun empty(): DebugSessionMetrics = DebugSessionMetrics(
            plays = 0,
            successful = 0,
            recovered = 0,
            terminal = 0,
            recoveryAttempts = 0,
            recoverySuccesses = 0,
            cacheHits = 0,
            cacheMisses = 0,
            cacheHitRate = 0.0,
            preloadHits = 0,
            preloadHitRate = 0.0,
            terminalFailureRate = 0.0,
            recoverySuccessRate = 0.0,
            tapToFirstAudio = DebugHistogram(0, null, null, null, null, null),
            resolution = DebugHistogram(0, null, null, null, null, null),
            playerResponse = DebugHistogram(0, null, null, null, null, null),
            dataSource = DebugHistogram(0, null, null, null, null, null),
            recovery = DebugHistogram(0, null, null, null, null, null),
            byClass = emptyMap(),
        )
    }
}

data class PlaybackDebugState(
    val events: List<PlaybackDiagnosticEvent> = emptyList(),
    val traces: List<DebugTraceSnapshot> = emptyList(),
    val metrics: DebugSessionMetrics = DebugSessionMetrics.empty(),
    val rawMetrics: PlaybackMetricsSnapshot = PlaybackDiagnostics.metrics.snapshot(),
    val activeTraceId: String? = null,
) {
    val activeTrace: DebugTraceSnapshot?
        get() = activeTraceId?.let { id -> traces.lastOrNull { it.traceId == id } }
            ?: traces.lastOrNull()
}

class PlaybackDebugCollector(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val _state = MutableStateFlow(PlaybackDebugState())
    val state: StateFlow<PlaybackDebugState> = _state.asStateFlow()

    init {
        scope.launch {
            PlaybackDiagnostics.buffer.events.collect { events ->
                _state.value = buildState(events)
            }
        }
    }

    fun clear() {
        PlaybackDiagnostics.clear()
        _state.value = PlaybackDebugState()
    }

    fun eventsForTrace(traceId: String): List<PlaybackDiagnosticEvent> =
        _state.value.events.filter { it.traceId == traceId }

    private fun buildState(events: List<PlaybackDiagnosticEvent>): PlaybackDebugState {
        // The shared buffer is chronological. Preserve that order so the
        // history really contains the last ten attempts, not the traces with
        // the largest relative elapsed value.
        val grouped = events.groupBy { it.traceId }
            .values
            .map { traceEvents -> traceSnapshot(traceEvents.sortedBy { it.elapsedMs }) }
            .takeLast(10)
        val rawMetrics = PlaybackDiagnostics.metrics.snapshot()
        return PlaybackDebugState(
            events = events,
            traces = grouped,
            metrics = metricsFor(grouped, rawMetrics),
            rawMetrics = rawMetrics,
            activeTraceId = PlaybackDiagnostics.current()?.traceId,
        )
    }

    private fun traceSnapshot(events: List<PlaybackDiagnosticEvent>): DebugTraceSnapshot {
        val first = events.firstOrNull()
        val mediaId = events.firstNotNullOfOrNull { it.mediaId }
        val failure = events.filterIsInstance<PlaybackDiagnosticEvent.TerminalFailure>()
            .lastOrNull()?.failure
        val firstAudioMs = events.filterIsInstance<PlaybackDiagnosticEvent.FirstAudio>()
            .firstOrNull()?.elapsedMs
        val resolutionRequested = events.filterIsInstance<PlaybackDiagnosticEvent.ResolutionRequested>().firstOrNull()
        val streamSelected = events.filterIsInstance<PlaybackDiagnosticEvent.StreamSelected>().lastOrNull()
        val cacheHit = events.filterIsInstance<PlaybackDiagnosticEvent.ResolutionCacheHit>().lastOrNull()
        val cacheMiss = events.filterIsInstance<PlaybackDiagnosticEvent.ResolutionCacheMiss>().lastOrNull()
        val playerResponse = events.filterIsInstance<PlaybackDiagnosticEvent.PlayerResponseEnd>().lastOrNull()
        val dataSource = events.filterIsInstance<PlaybackDiagnosticEvent.DataSourceOpenEnd>().lastOrNull()
        val recovery = events.filterIsInstance<PlaybackDiagnosticEvent.RecoveryEnd>().lastOrNull()
        val recoveryStarted = events.any { it is PlaybackDiagnosticEvent.RecoveryStart }
        val breadcrumbs = events.filterIsInstance<PlaybackDiagnosticEvent.Breadcrumb>()
        val preloadedBytes = breadcrumbs
            .firstOrNull { it.name == "FIRST_BYTES_WARMED" }
            ?.value?.toIntOrNull()
        val streamEvidence = parseStreamCandidateEvidence(
            breadcrumbs.lastOrNull { it.name == "STREAM_CANDIDATE" }?.value,
        )
        val primarySkipped = breadcrumbs.any {
            it.name == "PRIMARY_CLIENT_SKIPPED" ||
                it.name == "PRIMARY_PLAYER_REQUEST_SKIPPED"
        }
        val primaryFailed = breadcrumbs.any { it.name == "PRIMARY_PLAYER_REQUEST_FAILED" }
        val playerJsUsed = breadcrumbs.any { it.name == "PLAYER_JS_REQUIRED" }
        val resolverPath = when {
            primarySkipped -> "RECOVERY_FALLBACK"
            primaryFailed -> "PRIMARY_FAILED_FALLBACK"
            streamEvidence.client?.startsWith("VISIONOS/") == true -> "PRIMARY"
            streamEvidence.client != null -> "FALLBACK"
            else -> "N/A"
        }
        val hasPreloadMarker = events.filterIsInstance<PlaybackDiagnosticEvent.Breadcrumb>().any {
            it.name == "PRELOAD_STORED" || it.name == "FIRST_BYTES_WARMED" ||
                (it.name == "CACHE_ORIGIN" && it.value?.contains("preload", ignoreCase = true) == true)
        }
        val classification = when {
            recoveryStarted && recovery?.success == true -> DebugPerformanceClass.RECOVERED
            hasPreloadMarker -> DebugPerformanceClass.PRELOADED
            cacheHit != null -> DebugPerformanceClass.HOT
            cacheMiss != null || resolutionRequested != null -> DebugPerformanceClass.COLD
            else -> DebugPerformanceClass.UNKNOWN
        }
        val resolutionMs = resolutionRequested?.let { requested ->
            streamSelected?.let { selected -> (selected.elapsedMs - requested.elapsedMs).coerceAtLeast(0L) }
        }
        val dominant = buildMap {
            resolutionMs?.let { put("RESOLUTION", it) }
            playerResponse?.durationMs?.let { put("PLAYER_RESPONSE", it) }
            dataSource?.durationMs?.let { put("DATASOURCE_OPEN", it) }
            recovery?.durationMs?.let { put("RECOVERY", it) }
            firstAudioMs?.let { firstMs ->
                val ready = events.filterIsInstance<PlaybackDiagnosticEvent.PlayerReady>().lastOrNull()?.elapsedMs
                val start = maxOf(ready ?: 0L, dataSource?.elapsedMs ?: 0L)
                put("BUFFER_PLAYER", (firstMs - start).coerceAtLeast(0L))
            }
        }.maxByOrNull { it.value }?.key
        return DebugTraceSnapshot(
            traceId = first?.traceId.orEmpty(),
            mediaId = mediaId,
            events = events,
            failure = failure,
            classification = classification,
            tapToFirstAudioMs = firstAudioMs,
            resolutionMs = resolutionMs,
            playerResponseMs = playerResponse?.durationMs,
            dataSourceMs = dataSource?.durationMs,
            recoveryMs = recovery?.durationMs,
            dominantStage = dominant,
            slow = (firstAudioMs ?: 0L) >= SLOW_TRACE_THRESHOLD_MS,
            cacheState = when {
                cacheHit != null -> "HIT"
                cacheMiss != null -> "MISS"
                else -> "N/A"
            },
            itag = streamSelected?.itag ?: cacheHit?.let { null },
            mimeType = streamSelected?.mimeType,
            bitrate = streamSelected?.bitrate,
            expiresInMs = cacheHit?.expiresInMs,
            preloadedBytes = preloadedBytes,
            streamClient = streamEvidence.client,
            streamSource = streamEvidence.source,
            poTokenAttached = streamEvidence.poTokenAttached,
            streamContextGeneration = streamEvidence.contextGeneration,
            resolverPath = resolverPath,
            playerJsUsed = playerJsUsed,
        )
    }

    private fun metricsFor(
        traces: List<DebugTraceSnapshot>,
        rawMetrics: PlaybackMetricsSnapshot,
    ): DebugSessionMetrics {
        val plays = traces.count { it.events.any { event -> event is PlaybackDiagnosticEvent.Tap } }
        val successful = traces.count { it.succeeded }
        val recovered = traces.count { it.recovered }
        val terminal = traces.count { it.failure != null }
        val firstAudioValues = traces.mapNotNull { it.tapToFirstAudioMs }
        val cacheHits = rawMetrics.cacheHits
        val cacheMisses = rawMetrics.cacheMisses
        val byClass = DebugPerformanceClass.entries.associateWith { classification ->
            val subset = traces.filter { it.classification == classification }
            DebugClassMetrics(
                classification = classification,
                plays = subset.size,
                successful = subset.count { it.succeeded },
                recovered = subset.count { it.recovered },
                terminal = subset.count { it.failure != null },
                tapToFirstAudio = histogram(subset.mapNotNull { it.tapToFirstAudioMs }),
            )
        }
        return DebugSessionMetrics(
            plays = plays,
            successful = successful,
            recovered = recovered,
            terminal = terminal,
            recoveryAttempts = rawMetrics.recoveryAttempts,
            recoverySuccesses = rawMetrics.recoverySuccesses,
            cacheHits = cacheHits,
            cacheMisses = cacheMisses,
            cacheHitRate = cacheHits.toDouble() / (cacheHits + cacheMisses).coerceAtLeast(1L),
            preloadHits = traces.count { it.classification == DebugPerformanceClass.PRELOADED },
            preloadHitRate = traces.count { it.classification == DebugPerformanceClass.PRELOADED }
                .toDouble() / plays.coerceAtLeast(1),
            terminalFailureRate = terminal.toDouble() / plays.coerceAtLeast(1),
            recoverySuccessRate = rawMetrics.recoverySuccesses.toDouble() / rawMetrics.recoveryAttempts.coerceAtLeast(1L),
            tapToFirstAudio = histogram(firstAudioValues),
            resolution = histogram(traces.mapNotNull { it.resolutionMs }),
            playerResponse = histogram(traces.mapNotNull { it.playerResponseMs }),
            dataSource = histogram(traces.mapNotNull { it.dataSourceMs }),
            recovery = histogram(traces.mapNotNull { it.recoveryMs }),
            byClass = byClass,
        )
    }

    private fun histogram(values: List<Long>): DebugHistogram {
        val sorted = values.sorted()
        fun percentile(fraction: Double): Long? = sorted.getOrNull(
            ceil((sorted.size - 1) * fraction).toInt().coerceIn(0, (sorted.size - 1).coerceAtLeast(0)),
        )
        return DebugHistogram(
            count = sorted.size,
            p50Ms = percentile(0.50),
            p90Ms = percentile(0.90),
            p95Ms = percentile(0.95),
            p99Ms = percentile(0.99),
            maxMs = sorted.maxOrNull(),
        )
    }

    private companion object {
        const val SLOW_TRACE_THRESHOLD_MS = 1_000L
    }
}
