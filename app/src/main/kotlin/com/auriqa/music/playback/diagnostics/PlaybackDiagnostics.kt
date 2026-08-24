package com.auriqo.music.playback.diagnostics

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.net.URI
import java.security.MessageDigest
import java.util.Collections
import java.util.IdentityHashMap
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.ceil

/** Stages are deliberately stable: they are part of copied issue reports. */
enum class PlaybackFailureStage {
    USER_REQUEST,
    QUEUE,
    STREAM_CACHE,
    PLAYER_RESPONSE,
    PLAYABILITY,
    PLAYER_JS,
    CIPHER_SIGNATURE,
    CIPHER_N,
    POTOKEN,
    FORMAT_SELECTION,
    DATASOURCE_OPEN,
    CDN_HTTP,
    CACHE_READ,
    CONTAINER_PARSE,
    DECODER,
    AUDIO_SINK,
    PLAYER_STATE,
    NETWORK,
    RECOVERY,
}

enum class PlaybackFailureCategory {
    PLAYABILITY,
    HTTP,
    NETWORK,
    CIPHER,
    POTOKEN,
    FORMAT,
    CACHE,
    DECODER,
    AUDIO_SINK,
    PLAYER_STATE,
    RECOVERY,
    UNKNOWN,
}

/** Hints come from typed boundaries; the classifier does not need to parse exception strings. */
enum class PlaybackFailureHint {
    PLAYER_RESPONSE_FAILED,
    PLAYER_JS_NOT_FOUND,
    SIGNATURE_FUNCTION_NOT_FOUND,
    SIGNATURE_DECIPHER_FAILED,
    N_TRANSFORM_NOT_FOUND,
    N_TRANSFORM_FAILED,
    POTOKEN_FAILED,
    STREAM_URL_EXPIRED,
    FORMAT_NOT_FOUND,
    CONTENT_TYPE_INVALID,
    CACHE_CORRUPTED,
    CACHE_POSITION_OUT_OF_RANGE,
    CONTAINER_MALFORMED,
    CONTAINER_UNSUPPORTED,
    DECODER_INIT_FAILED,
    DECODING_FAILED,
    AUDIO_TRACK_INIT_FAILED,
    AUDIO_TRACK_WRITE_FAILED,
    SUPERSEDED_RESOLUTION,
    TIMEOUT,
    CONNECTION_FAILED,
    OFFLINE,
    UNKNOWN,
}

/** Stable Auriqo codes. Media3's original code is stored alongside these values. */
enum class PlaybackFailureCode(val recoverableByDefault: Boolean) {
    RESOLUTION_PLAYER_RESPONSE_FAILED(true),
    PLAYABILITY_LOGIN_REQUIRED(false),
    PLAYABILITY_AGE_RESTRICTED(false),
    PLAYABILITY_REGION_BLOCKED(false),
    PLAYABILITY_UNAVAILABLE(false),
    PLAYABILITY_PRIVATE(false),
    STREAM_FORMAT_NOT_FOUND(true),
    PLAYER_JS_NOT_FOUND(true),
    SIGNATURE_FUNCTION_NOT_FOUND(true),
    SIGNATURE_DECIPHER_FAILED(true),
    N_TRANSFORM_NOT_FOUND(true),
    N_TRANSFORM_FAILED(true),
    POTOKEN_FAILED(true),
    STREAM_URL_EXPIRED(true),
    STREAM_HTTP_403(true),
    STREAM_HTTP_404(true),
    STREAM_HTTP_410(true),
    STREAM_HTTP_429(true),
    STREAM_HTTP_5XX(true),
    NETWORK_CONNECTION_FAILED(true),
    NETWORK_TIMEOUT(true),
    CONTENT_TYPE_INVALID(true),
    CACHE_CORRUPTED(true),
    CACHE_POSITION_OUT_OF_RANGE(true),
    CONTAINER_MALFORMED(true),
    CONTAINER_UNSUPPORTED(true),
    DECODER_INIT_FAILED(true),
    DECODING_FAILED(true),
    AUDIO_TRACK_INIT_FAILED(true),
    AUDIO_TRACK_WRITE_FAILED(true),
    SUPERSEDED_RESOLUTION(true),
    RECOVERY_EXHAUSTED(false),
    UNKNOWN_IO(true),
    UNKNOWN_PLAYBACK(false),
}

data class PlaybackCauseEntry(
    val className: String,
    val message: String?,
    val relevantFields: Map<String, String> = emptyMap(),
)

data class PlaybackHttpDetails(
    val responseCode: Int,
    val responseMessage: String? = null,
    val host: String? = null,
    val contentType: String? = null,
    val range: String? = null,
    val itag: Int? = null,
    val queryKeys: List<String> = emptyList(),
    val expireEpoch: Long? = null,
    val sensitiveHeadersPresent: Set<String> = emptySet(),
)

data class PlaybackRecoveryAction(
    val action: String,
    val result: String? = null,
    val attempt: Int? = null,
    val elapsedMs: Long? = null,
)

data class PlaybackFailure(
    val traceId: String,
    val mediaId: String?,
    val stage: PlaybackFailureStage,
    val category: PlaybackFailureCategory,
    val exactCode: PlaybackFailureCode,
    val humanMessage: String,
    val technicalMessage: String,
    val media3Code: Int? = null,
    val media3CodeName: String? = null,
    val http: PlaybackHttpDetails? = null,
    val httpStatus: Int? = null,
    val playabilityStatus: String? = null,
    val attempt: Int = 0,
    val maxAttempts: Int = 0,
    val streamGeneration: Long? = null,
    val extractorGeneration: Long? = null,
    val cacheStatus: String? = null,
    val networkType: String? = null,
    val elapsedMs: Long = 0,
    val causeChain: List<PlaybackCauseEntry> = emptyList(),
    val recoveryActions: List<PlaybackRecoveryAction> = emptyList(),
    val terminal: Boolean,
) {
    val stableCode: String
        get() = "AURIQO_${exactCode.name}"
}

sealed interface PlaybackDiagnosticEvent {
    val traceId: String
    val elapsedMs: Long
    val mediaId: String?
    val type: String

    data class Tap(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val source: String,
    ) : PlaybackDiagnosticEvent {
        override val type = "USER_TAP"
    }

    data class QueueRequest(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val queueSize: Int?,
    ) : PlaybackDiagnosticEvent {
        override val type = "QUEUE_REQUEST"
    }

    data class MediaItemCreated(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val queueIndex: Int?,
    ) : PlaybackDiagnosticEvent {
        override val type = "MEDIA_ITEM_CREATED"
    }

    data class ResolutionRequested(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val quality: String?,
    ) : PlaybackDiagnosticEvent {
        override val type = "RESOLUTION_REQUESTED"
    }

    data class ResolutionCacheHit(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val expiresInMs: Long?,
    ) : PlaybackDiagnosticEvent {
        override val type = "RESOLUTION_CACHE_HIT"
    }

    data class ResolutionCacheMiss(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val reason: String,
    ) : PlaybackDiagnosticEvent {
        override val type = "RESOLUTION_CACHE_MISS"
    }

    data class PlayerResponseStart(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
    ) : PlaybackDiagnosticEvent {
        override val type = "PLAYER_RESPONSE_START"
    }

    data class PlayerResponseEnd(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val durationMs: Long,
        val status: String?,
        val success: Boolean,
    ) : PlaybackDiagnosticEvent {
        override val type = "PLAYER_RESPONSE_END"
    }

    data class CipherStart(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val operation: String,
    ) : PlaybackDiagnosticEvent {
        override val type = "CIPHER_START"
    }

    data class CipherEnd(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val operation: String,
        val durationMs: Long,
        val success: Boolean,
    ) : PlaybackDiagnosticEvent {
        override val type = "CIPHER_END"
    }

    data class StreamSelected(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val itag: Int?,
        val mimeType: String?,
        val bitrate: Int?,
    ) : PlaybackDiagnosticEvent {
        override val type = "STREAM_SELECTED"
    }

    data class DataSourceOpenStart(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
    ) : PlaybackDiagnosticEvent {
        override val type = "DATASOURCE_OPEN_START"
    }

    data class DataSourceOpenEnd(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val durationMs: Long,
        val success: Boolean,
    ) : PlaybackDiagnosticEvent {
        override val type = "DATASOURCE_OPEN_END"
    }

    data class HttpStatus(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val details: PlaybackHttpDetails,
    ) : PlaybackDiagnosticEvent {
        override val type = "HTTP_STATUS"
    }

    data class PlayerBuffering(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
    ) : PlaybackDiagnosticEvent {
        override val type = "PLAYER_BUFFERING"
    }

    data class PlayerReady(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
    ) : PlaybackDiagnosticEvent {
        override val type = "PLAYER_READY"
    }

    data class FirstAudio(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
    ) : PlaybackDiagnosticEvent {
        override val type = "FIRST_AUDIO"
    }

    data class RecoveryStart(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val attempt: Int,
        val maxAttempts: Int,
        val reason: String,
    ) : PlaybackDiagnosticEvent {
        override val type = "RECOVERY_START"
    }

    data class RecoveryEnd(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val attempt: Int,
        val success: Boolean,
        val result: String,
        val durationMs: Long? = null,
    ) : PlaybackDiagnosticEvent {
        override val type = "RECOVERY_END"
    }

    data class FormatFallback(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val fromItag: Int?,
        val toItag: Int?,
        val reason: String,
    ) : PlaybackDiagnosticEvent {
        override val type = "FORMAT_FALLBACK"
    }

    data class NetworkChanged(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val connected: Boolean,
        val networkType: String?,
    ) : PlaybackDiagnosticEvent {
        override val type = "NETWORK_CHANGED"
    }

    data class TerminalFailure(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val failure: PlaybackFailure,
    ) : PlaybackDiagnosticEvent {
        override val type = "TERMINAL_FAILURE"
    }

    data class Breadcrumb(
        override val traceId: String,
        override val elapsedMs: Long,
        override val mediaId: String?,
        val name: String,
        val value: String?,
    ) : PlaybackDiagnosticEvent {
        override val type = "BREADCRUMB"
    }
}

/** Bounded in-memory event storage. No event can grow the process indefinitely. */
class PlaybackDiagnosticBuffer(
    private val capacity: Int = DEFAULT_CAPACITY,
) {
    init {
        require(capacity > 0) { "capacity must be positive" }
    }

    private val lock = Any()
    private val ring = ArrayDeque<PlaybackDiagnosticEvent>(capacity)
    private val _events = MutableStateFlow<List<PlaybackDiagnosticEvent>>(emptyList())
    val events: StateFlow<List<PlaybackDiagnosticEvent>> = _events.asStateFlow()

    fun append(event: PlaybackDiagnosticEvent) {
        synchronized(lock) {
            if (ring.size == capacity) ring.removeFirst()
            ring.addLast(event)
            _events.value = ring.toList()
        }
    }

    fun snapshot(): List<PlaybackDiagnosticEvent> = synchronized(lock) { ring.toList() }

    fun snapshot(traceId: String): List<PlaybackDiagnosticEvent> = synchronized(lock) {
        ring.filter { it.traceId == traceId }
    }

    fun clear() {
        synchronized(lock) {
            ring.clear()
            _events.value = emptyList()
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 200
    }
}

enum class PlaybackMetric {
    TAP_TO_FIRST_AUDIO,
    RESOLUTION_LATENCY,
    DATASOURCE_OPEN_LATENCY,
    RECOVERY_LATENCY,
    TERMINAL_FAILURE_LATENCY,
}

data class PlaybackHistogram(
    val count: Int,
    val p50Ms: Long?,
    val p90Ms: Long?,
    val p95Ms: Long?,
    val p99Ms: Long?,
)

data class PlaybackMetricsSnapshot(
    val histograms: Map<PlaybackMetric, PlaybackHistogram>,
    val cacheHits: Long,
    val cacheMisses: Long,
    val recoveryAttempts: Long,
    val recoverySuccesses: Long,
    val terminalFailures: Long,
) {
    val cacheHitRate: Double
        get() = cacheHits.toDouble() / (cacheHits + cacheMisses).coerceAtLeast(1)

    val recoverySuccessRate: Double
        get() = recoverySuccesses.toDouble() / recoveryAttempts.coerceAtLeast(1)
}

/** Local-only histogram collection used by debug reports and deterministic tests. */
class PlaybackMetrics(
    private val maxSamples: Int = 300,
) {
    private val lock = Any()
    private val samples = PlaybackMetric.entries.associateWithTo(mutableMapOf()) { ArrayDeque<Long>(maxSamples) }
    private var cacheHits = 0L
    private var cacheMisses = 0L
    private var recoveryAttempts = 0L
    private var recoverySuccesses = 0L
    private var terminalFailures = 0L

    fun record(metric: PlaybackMetric, valueMs: Long) {
        if (valueMs < 0) return
        synchronized(lock) {
            val values = samples.getValue(metric)
            if (values.size == maxSamples) values.removeFirst()
            values.addLast(valueMs)
        }
    }

    fun recordCacheHit() = synchronized(lock) { cacheHits++ }
    fun recordCacheMiss() = synchronized(lock) { cacheMisses++ }
    fun recordRecovery(success: Boolean) = synchronized(lock) {
        recoveryAttempts++
        if (success) recoverySuccesses++
    }
    fun recordTerminalFailure() = synchronized(lock) { terminalFailures++ }

    fun snapshot(): PlaybackMetricsSnapshot = synchronized(lock) {
        PlaybackMetricsSnapshot(
            histograms = samples.mapValues { (_, values) ->
                val sorted = values.toList().sorted()
                PlaybackHistogram(
                    count = sorted.size,
                    p50Ms = percentile(sorted, 0.50),
                    p90Ms = percentile(sorted, 0.90),
                    p95Ms = percentile(sorted, 0.95),
                    p99Ms = percentile(sorted, 0.99),
                )
            },
            cacheHits = cacheHits,
            cacheMisses = cacheMisses,
            recoveryAttempts = recoveryAttempts,
            recoverySuccesses = recoverySuccesses,
            terminalFailures = terminalFailures,
        )
    }

    fun clear() = synchronized(lock) {
        samples.values.forEach(ArrayDeque<Long>::clear)
        cacheHits = 0
        cacheMisses = 0
        recoveryAttempts = 0
        recoverySuccesses = 0
        terminalFailures = 0
    }

    private fun percentile(sorted: List<Long>, fraction: Double): Long? {
        if (sorted.isEmpty()) return null
        return sorted[(ceil((sorted.size - 1) * fraction)).toInt().coerceIn(0, sorted.lastIndex)]
    }
}

class PlaybackTraceRecorder internal constructor(
    val traceId: String,
    mediaId: String?,
    private val buffer: PlaybackDiagnosticBuffer,
    private val metrics: PlaybackMetrics,
    private val clockNs: () -> Long,
) {
    private val startedAtNs = clockNs()
    private val timingStarts = mutableMapOf<String, Long>()
    private var firstAudioRecorded = false
    private var terminalRecorded = false
    private var currentMediaId: String? = mediaId

    val mediaId: String?
        get() = currentMediaId

    private fun elapsedMs(): Long = ((clockNs() - startedAtNs) / 1_000_000L).coerceAtLeast(0L)

    fun attachMediaId(mediaId: String?) {
        if (!mediaId.isNullOrBlank()) currentMediaId = mediaId
    }

    private fun append(event: PlaybackDiagnosticEvent) {
        buffer.append(event)
        val level = when (event) {
            is PlaybackDiagnosticEvent.TerminalFailure -> "e"
            is PlaybackDiagnosticEvent.RecoveryStart,
            is PlaybackDiagnosticEvent.HttpStatus,
            is PlaybackDiagnosticEvent.ResolutionCacheMiss -> "w"
            is PlaybackDiagnosticEvent.PlayerReady,
            is PlaybackDiagnosticEvent.FirstAudio,
            is PlaybackDiagnosticEvent.RecoveryEnd -> "i"
            else -> "d"
        }
        val line = event.toLogLine()
        when (level) {
            "e" -> Timber.tag(TAG).e(line)
            "w" -> Timber.tag(TAG).w(line)
            "i" -> Timber.tag(TAG).i(line)
            else -> Timber.tag(TAG).d(line)
        }
    }

    fun recordTap(source: String = "user") = append(
        PlaybackDiagnosticEvent.Tap(traceId, elapsedMs(), currentMediaId, source),
    )

    fun recordQueueRequest(queueSize: Int? = null) = append(
        PlaybackDiagnosticEvent.QueueRequest(traceId, elapsedMs(), currentMediaId, queueSize),
    )

    fun recordMediaItemCreated(mediaId: String?, queueIndex: Int? = null) {
        attachMediaId(mediaId)
        append(PlaybackDiagnosticEvent.MediaItemCreated(traceId, elapsedMs(), currentMediaId, queueIndex))
    }

    fun resolutionRequested(mediaId: String?, quality: String?) {
        attachMediaId(mediaId)
        timingStarts["resolution"] = clockNs()
        append(PlaybackDiagnosticEvent.ResolutionRequested(traceId, elapsedMs(), currentMediaId, quality))
    }

    fun resolutionCacheHit(expiresInMs: Long?) {
        metrics.recordCacheHit()
        append(PlaybackDiagnosticEvent.ResolutionCacheHit(traceId, elapsedMs(), currentMediaId, expiresInMs))
    }

    fun resolutionCacheMiss(reason: String) {
        metrics.recordCacheMiss()
        append(PlaybackDiagnosticEvent.ResolutionCacheMiss(traceId, elapsedMs(), currentMediaId, reason))
    }

    fun playerResponseStart() {
        timingStarts["playerResponse"] = clockNs()
        append(PlaybackDiagnosticEvent.PlayerResponseStart(traceId, elapsedMs(), currentMediaId))
    }

    fun playerResponseEnd(status: String?, success: Boolean, durationMs: Long? = null) {
        val duration = durationMs ?: timingStarts.remove("playerResponse")?.let { (clockNs() - it) / 1_000_000L } ?: 0L
        append(PlaybackDiagnosticEvent.PlayerResponseEnd(traceId, elapsedMs(), currentMediaId, duration, status, success))
    }

    fun cipherStart(operation: String) {
        timingStarts["cipher:$operation"] = clockNs()
        append(PlaybackDiagnosticEvent.CipherStart(traceId, elapsedMs(), currentMediaId, operation))
    }

    fun cipherEnd(operation: String, success: Boolean, durationMs: Long? = null) {
        val duration = durationMs ?: timingStarts.remove("cipher:$operation")?.let { (clockNs() - it) / 1_000_000L } ?: 0L
        append(PlaybackDiagnosticEvent.CipherEnd(traceId, elapsedMs(), currentMediaId, operation, duration, success))
    }

    fun streamSelected(itag: Int?, mimeType: String?, bitrate: Int?) {
        timingStarts.remove("resolution")?.let { metrics.record(PlaybackMetric.RESOLUTION_LATENCY, (clockNs() - it) / 1_000_000L) }
        append(PlaybackDiagnosticEvent.StreamSelected(traceId, elapsedMs(), currentMediaId, itag, mimeType, bitrate))
    }

    fun dataSourceOpenStart() {
        timingStarts["dataSource"] = clockNs()
        append(PlaybackDiagnosticEvent.DataSourceOpenStart(traceId, elapsedMs(), currentMediaId))
    }

    fun dataSourceOpenEnd(success: Boolean, durationMs: Long? = null) {
        val duration = durationMs ?: timingStarts.remove("dataSource")?.let { (clockNs() - it) / 1_000_000L } ?: 0L
        metrics.record(PlaybackMetric.DATASOURCE_OPEN_LATENCY, duration)
        append(PlaybackDiagnosticEvent.DataSourceOpenEnd(traceId, elapsedMs(), currentMediaId, duration, success))
    }

    fun httpStatus(details: PlaybackHttpDetails) = append(
        PlaybackDiagnosticEvent.HttpStatus(traceId, elapsedMs(), currentMediaId, details),
    )

    fun buffering() = append(PlaybackDiagnosticEvent.PlayerBuffering(traceId, elapsedMs(), currentMediaId))
    fun ready() = append(PlaybackDiagnosticEvent.PlayerReady(traceId, elapsedMs(), currentMediaId))

    fun firstAudio() {
        if (firstAudioRecorded) return
        firstAudioRecorded = true
        metrics.record(PlaybackMetric.TAP_TO_FIRST_AUDIO, elapsedMs())
        append(PlaybackDiagnosticEvent.FirstAudio(traceId, elapsedMs(), currentMediaId))
    }

    fun recoveryStart(attempt: Int, maxAttempts: Int, reason: String) {
        timingStarts["recovery"] = clockNs()
        append(PlaybackDiagnosticEvent.RecoveryStart(traceId, elapsedMs(), currentMediaId, attempt, maxAttempts, reason))
    }

    fun recoveryEnd(attempt: Int, success: Boolean, result: String) {
        val duration = timingStarts.remove("recovery")?.let { (clockNs() - it) / 1_000_000L }
        if (duration != null) metrics.record(PlaybackMetric.RECOVERY_LATENCY, duration)
        metrics.recordRecovery(success)
        append(PlaybackDiagnosticEvent.RecoveryEnd(traceId, elapsedMs(), currentMediaId, attempt, success, result, duration))
    }

    fun formatFallback(fromItag: Int?, toItag: Int?, reason: String) = append(
        PlaybackDiagnosticEvent.FormatFallback(traceId, elapsedMs(), currentMediaId, fromItag, toItag, reason),
    )

    fun networkChanged(connected: Boolean, networkType: String?) = append(
        PlaybackDiagnosticEvent.NetworkChanged(traceId, elapsedMs(), currentMediaId, connected, networkType),
    )

    fun breadcrumb(name: String, value: String? = null) = append(
        PlaybackDiagnosticEvent.Breadcrumb(traceId, elapsedMs(), currentMediaId, name, value),
    )

    fun terminalFailure(failure: PlaybackFailure) {
        if (terminalRecorded) return
        terminalRecorded = true
        metrics.record(PlaybackMetric.TERMINAL_FAILURE_LATENCY, elapsedMs())
        metrics.recordTerminalFailure()
        append(PlaybackDiagnosticEvent.TerminalFailure(traceId, elapsedMs(), currentMediaId, failure))
    }

    private companion object {
        const val TAG = "PlaybackTrace"
    }
}

object PlaybackDiagnostics {
    val buffer = PlaybackDiagnosticBuffer()
    val metrics = PlaybackMetrics()
    private val current = AtomicReference<PlaybackTraceRecorder?>(null)
    private val resolutionTraces = ConcurrentHashMap<String, PlaybackTraceRecorder>()
    private val mediaTraces = ConcurrentHashMap<String, PlaybackTraceRecorder>()

    fun startUserRequest(mediaId: String? = null, source: String = "user"): PlaybackTraceRecorder =
        start(mediaId, source).also {
            it.recordTap(source)
            it.recordQueueRequest()
        }

    fun start(mediaId: String? = null, source: String = "transition"): PlaybackTraceRecorder {
        val recorder = PlaybackTraceRecorder(
            traceId = newPlaybackTraceId(),
            mediaId = mediaId,
            buffer = buffer,
            metrics = metrics,
            clockNs = System::nanoTime,
        )
        current.set(recorder)
        mediaId?.let { rememberMediaTrace(it, recorder) }
        return recorder
    }

    fun current(): PlaybackTraceRecorder? = current.get()

    fun currentFor(mediaId: String?): PlaybackTraceRecorder? {
        val recorder = current.get()
        if (recorder != null && (mediaId == null || recorder.mediaId == mediaId)) {
            return recorder
        }
        return mediaId?.let { resolutionTraces[it] ?: mediaTraces[it] }
    }

    /** Creates a bounded trace for an anticipatory resolution without stealing the active trace. */
    fun startResolution(mediaId: String, source: String = "preload"): PlaybackTraceRecorder {
        resolutionTraces[mediaId]?.let { return it }
        if (resolutionTraces.size >= MAX_RESOLUTION_TRACES) {
            resolutionTraces.keys.firstOrNull()?.let(resolutionTraces::remove)
        }
        return PlaybackTraceRecorder(
            traceId = newPlaybackTraceId(),
            mediaId = mediaId,
            buffer = buffer,
            metrics = metrics,
            clockNs = System::nanoTime,
        ).also {
            resolutionTraces[mediaId] = it
            rememberMediaTrace(mediaId, it)
            it.breadcrumb("RESOLUTION_SCOPE", source)
        }
    }

    fun finishResolution(mediaId: String, trace: PlaybackTraceRecorder) {
        resolutionTraces.remove(mediaId, trace)
    }

    fun transitionTo(mediaId: String?, force: Boolean = false): PlaybackTraceRecorder {
        val existing = currentFor(mediaId)
        if (!force && existing != null && existing.mediaId == mediaId) return existing
        return start(mediaId, "transition")
    }

    fun events(traceId: String): List<PlaybackDiagnosticEvent> = buffer.snapshot(traceId)

    fun clear() {
        current.set(null)
        resolutionTraces.clear()
        mediaTraces.clear()
        buffer.clear()
        metrics.clear()
    }

    private fun rememberMediaTrace(mediaId: String, trace: PlaybackTraceRecorder) {
        if (mediaTraces.size >= MAX_TRACKED_MEDIA_TRACES) {
            mediaTraces.keys.firstOrNull()?.let(mediaTraces::remove)
        }
        mediaTraces[mediaId] = trace
    }

    private const val MAX_RESOLUTION_TRACES = 64
    private const val MAX_TRACKED_MEDIA_TRACES = 64
}

fun newPlaybackTraceId(uuid: UUID = UUID.randomUUID()): String =
    "PB-${uuid.toString().replace("-", "").take(8).uppercase(Locale.ROOT)}"

fun PlaybackDiagnosticEvent.toLogLine(): String = buildString {
    append('[').append(traceId).append("] ").append(type)
    append(" elapsedMs=").append(elapsedMs)
    mediaId?.let { append(" mediaId=").append(PlaybackRedactor.sanitizeScalar(it)) }
    when (this@toLogLine) {
        is PlaybackDiagnosticEvent.Tap -> append(" source=").append(PlaybackRedactor.sanitizeScalar(source))
        is PlaybackDiagnosticEvent.QueueRequest -> queueSize?.let { append(" queueSize=").append(it) }
        is PlaybackDiagnosticEvent.MediaItemCreated -> queueIndex?.let { append(" queueIndex=").append(it) }
        is PlaybackDiagnosticEvent.ResolutionRequested -> quality?.let { append(" quality=").append(PlaybackRedactor.sanitizeScalar(it)) }
        is PlaybackDiagnosticEvent.ResolutionCacheHit -> expiresInMs?.let { append(" expiresInMs=").append(it) }
        is PlaybackDiagnosticEvent.ResolutionCacheMiss -> append(" reason=").append(PlaybackRedactor.sanitizeScalar(reason))
        is PlaybackDiagnosticEvent.PlayerResponseEnd -> append(" durationMs=").append(durationMs).append(" success=").append(success).append(" status=").append(status)
        is PlaybackDiagnosticEvent.CipherStart -> append(" operation=").append(PlaybackRedactor.sanitizeScalar(operation))
        is PlaybackDiagnosticEvent.CipherEnd -> append(" operation=").append(PlaybackRedactor.sanitizeScalar(operation)).append(" durationMs=").append(durationMs).append(" success=").append(success)
        is PlaybackDiagnosticEvent.StreamSelected -> append(" itag=").append(itag).append(" mimeType=").append(PlaybackRedactor.sanitizeScalar(mimeType)).append(" bitrate=").append(bitrate)
        is PlaybackDiagnosticEvent.DataSourceOpenEnd -> append(" durationMs=").append(durationMs).append(" success=").append(success)
        is PlaybackDiagnosticEvent.HttpStatus -> append(" status=").append(details.responseCode).append(" host=").append(details.host).append(" queryKeys=").append(details.queryKeys).append(" expireEpoch=").append(details.expireEpoch)
        is PlaybackDiagnosticEvent.RecoveryStart -> append(" attempt=").append(attempt).append('/').append(maxAttempts).append(" reason=").append(PlaybackRedactor.sanitizeScalar(reason))
        is PlaybackDiagnosticEvent.RecoveryEnd -> append(" attempt=").append(attempt).append(" success=").append(success).append(" result=").append(PlaybackRedactor.sanitizeScalar(result)).append(" durationMs=").append(durationMs)
        is PlaybackDiagnosticEvent.FormatFallback -> append(" from=").append(fromItag).append(" to=").append(toItag).append(" reason=").append(PlaybackRedactor.sanitizeScalar(reason))
        is PlaybackDiagnosticEvent.NetworkChanged -> append(" connected=").append(connected).append(" networkType=").append(PlaybackRedactor.sanitizeScalar(networkType))
        is PlaybackDiagnosticEvent.TerminalFailure -> append(" code=").append(failure.stableCode).append(" stage=").append(failure.stage).append(" media3=").append(failure.media3CodeName).append(" http=").append(failure.httpStatus)
        is PlaybackDiagnosticEvent.Breadcrumb -> append(' ').append(PlaybackRedactor.sanitizeScalar(name)).append('=').append(PlaybackRedactor.sanitizeScalar(value))
        is PlaybackDiagnosticEvent.PlayerResponseStart,
        is PlaybackDiagnosticEvent.DataSourceOpenStart,
        is PlaybackDiagnosticEvent.PlayerBuffering,
        is PlaybackDiagnosticEvent.PlayerReady,
        is PlaybackDiagnosticEvent.FirstAudio -> Unit
    }
}

object PlaybackCauseChainExtractor {
    fun extract(root: Throwable?, maxDepth: Int = 12): List<PlaybackCauseEntry> {
        if (root == null) return emptyList()
        val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
        val result = ArrayList<PlaybackCauseEntry>(maxDepth)
        var current: Throwable? = root
        var depth = 0
        while (current != null && depth < maxDepth && seen.add(current)) {
            result += PlaybackCauseEntry(
                className = current::class.java.name.substringAfterLast('.'),
                message = current.message?.let(PlaybackRedactor::sanitizeText),
            )
            current = current.cause
            depth++
        }
        return result
    }
}

object PlaybackRedactor {
    private val urlPattern = Regex("https?://[^\\s\\\"'<>]+", RegexOption.IGNORE_CASE)
    private val secretPattern = Regex(
        "(?i)(cookie|authorization|proxy-authorization|po[-_]?token|visitor[-_]?(?:data|id)|x-goog-visitor-id|signature|oauth(?:2)?|lastfm[-_]?session|spotify[-_]?token)\\s*[:=]\\s*[^\\s,;]+",
    )

    fun sanitizeText(value: String): String = value
        .replace(urlPattern) { redactUrl(it.value) }
        .replace(secretPattern) { "${it.value.substringBefore('=').substringBefore(':')}=[REDACTED]" }
        .replace(Regex("[\\r\\n]+"), " ")
        .trim()
        .take(400)

    fun sanitizeScalar(value: Any?): String = sanitizeText(value?.toString() ?: "null")

    fun redactUrl(rawUrl: String): String {
        val uri = runCatching { URI(rawUrl) }.getOrNull() ?: return "[URL_REDACTED]"
        val queryKeys = uri.rawQuery.orEmpty()
            .split('&')
            .filter { it.isNotBlank() }
            .map { it.substringBefore('=') }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
        val expire = uri.rawQuery.orEmpty()
            .split('&')
            .firstOrNull { it.startsWith("expire=") }
            ?.substringAfter('=')
            ?.toLongOrNull()
        return buildString {
            append("host=").append(uri.host ?: "unknown")
            append(" queryKeys=").append(queryKeys)
            expire?.let { append(" expireEpoch=").append(it) }
        }
    }

    fun redactHeaders(headers: Map<String, String>): Map<String, String> {
        val safe = linkedMapOf<String, String>()
        val sensitive = linkedSetOf<String>()
        headers.forEach { (name, value) ->
            when (name.lowercase(Locale.ROOT)) {
                "content-type", "content-range", "accept-ranges", "content-length", "range" ->
                    safe[name.lowercase(Locale.ROOT)] = sanitizeScalar(value)
                "cookie", "authorization", "proxy-authorization", "x-goog-visitor-id", "x-youtube-client-data" ->
                    sensitive += name.lowercase(Locale.ROOT)
            }
        }
        if (sensitive.isNotEmpty()) safe["sensitiveHeadersPresent"] = sensitive.sorted().joinToString(",")
        return safe
    }

    fun shortHash(value: String?): String? {
        if (value == null) return null
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }.take(8)
    }
}

data class PlaybackFailureInput(
    val traceId: String,
    val mediaId: String?,
    val stage: PlaybackFailureStage,
    val media3Code: Int? = null,
    val media3CodeName: String? = null,
    val http: PlaybackHttpDetails? = null,
    val playabilityStatus: String? = null,
    val playabilityReason: String? = null,
    val hint: PlaybackFailureHint? = null,
    val cause: Throwable? = null,
    val causeChain: List<PlaybackCauseEntry> = emptyList(),
    val attempt: Int = 0,
    val maxAttempts: Int = 0,
    val streamGeneration: Long? = null,
    val extractorGeneration: Long? = null,
    val cacheStatus: String? = null,
    val networkType: String? = null,
    val elapsedMs: Long = 0,
    val terminalOverride: Boolean? = null,
)

object PlaybackFailureClassifier {
    fun classify(input: PlaybackFailureInput): PlaybackFailure {
        val code = classifyCode(input)
        val category = categoryFor(code)
        val chain = input.causeChain.ifEmpty { PlaybackCauseChainExtractor.extract(input.cause) }
        val technical = buildString {
            append(code.name)
            input.playabilityStatus?.let { append(" playabilityStatus=").append(PlaybackRedactor.sanitizeScalar(it)) }
            input.playabilityReason?.let { append(" reason=").append(PlaybackRedactor.sanitizeText(it)) }
            input.http?.let {
                append(" http=").append(it.responseCode)
                    .append(" host=").append(it.host)
                    .append(" message=").append(it.responseMessage)
            }
            input.media3CodeName?.let { append(" media3=").append(it).append('(').append(input.media3Code).append(')') }
        }
        return PlaybackFailure(
            traceId = input.traceId,
            mediaId = input.mediaId,
            stage = stageFor(code, input.stage),
            category = category,
            exactCode = code,
            humanMessage = humanMessage(code),
            technicalMessage = PlaybackRedactor.sanitizeText(technical),
            media3Code = input.media3Code,
            media3CodeName = input.media3CodeName,
            http = input.http,
            httpStatus = input.http?.responseCode,
            playabilityStatus = input.playabilityStatus,
            attempt = input.attempt,
            maxAttempts = input.maxAttempts,
            streamGeneration = input.streamGeneration,
            extractorGeneration = input.extractorGeneration,
            cacheStatus = input.cacheStatus,
            networkType = input.networkType,
            elapsedMs = input.elapsedMs,
            causeChain = chain,
            terminal = input.terminalOverride ?: !code.recoverableByDefault,
        )
    }

    private fun classifyCode(input: PlaybackFailureInput): PlaybackFailureCode {
        val status = input.playabilityStatus?.uppercase(Locale.ROOT)
        val reason = input.playabilityReason.orEmpty().lowercase(Locale.ROOT)
        if (status == "LOGIN_REQUIRED" && reason.contains("age")) return PlaybackFailureCode.PLAYABILITY_AGE_RESTRICTED
        if (status == "LOGIN_REQUIRED") return PlaybackFailureCode.PLAYABILITY_LOGIN_REQUIRED
        if (status in setOf("AGE_CHECK_REQUIRED", "AGE_VERIFICATION_REQUIRED", "CONTENT_CHECK_REQUIRED")) {
            return PlaybackFailureCode.PLAYABILITY_AGE_RESTRICTED
        }
        if (status != null && status != "OK") {
            if (reason.contains("region") || reason.contains("country") || reason.contains("not available in your")) {
                return PlaybackFailureCode.PLAYABILITY_REGION_BLOCKED
            }
            if (reason.contains("private")) return PlaybackFailureCode.PLAYABILITY_PRIVATE
            if (status == "UNPLAYABLE") return PlaybackFailureCode.PLAYABILITY_UNAVAILABLE
        }

        when (input.hint) {
            PlaybackFailureHint.PLAYER_RESPONSE_FAILED -> return PlaybackFailureCode.RESOLUTION_PLAYER_RESPONSE_FAILED
            PlaybackFailureHint.PLAYER_JS_NOT_FOUND -> return PlaybackFailureCode.PLAYER_JS_NOT_FOUND
            PlaybackFailureHint.SIGNATURE_FUNCTION_NOT_FOUND -> return PlaybackFailureCode.SIGNATURE_FUNCTION_NOT_FOUND
            PlaybackFailureHint.SIGNATURE_DECIPHER_FAILED -> return PlaybackFailureCode.SIGNATURE_DECIPHER_FAILED
            PlaybackFailureHint.N_TRANSFORM_NOT_FOUND -> return PlaybackFailureCode.N_TRANSFORM_NOT_FOUND
            PlaybackFailureHint.N_TRANSFORM_FAILED -> return PlaybackFailureCode.N_TRANSFORM_FAILED
            PlaybackFailureHint.POTOKEN_FAILED -> return PlaybackFailureCode.POTOKEN_FAILED
            PlaybackFailureHint.STREAM_URL_EXPIRED -> return PlaybackFailureCode.STREAM_URL_EXPIRED
            PlaybackFailureHint.FORMAT_NOT_FOUND -> return PlaybackFailureCode.STREAM_FORMAT_NOT_FOUND
            PlaybackFailureHint.CONTENT_TYPE_INVALID -> return PlaybackFailureCode.CONTENT_TYPE_INVALID
            PlaybackFailureHint.CACHE_CORRUPTED -> return PlaybackFailureCode.CACHE_CORRUPTED
            PlaybackFailureHint.CACHE_POSITION_OUT_OF_RANGE -> return PlaybackFailureCode.CACHE_POSITION_OUT_OF_RANGE
            PlaybackFailureHint.CONTAINER_MALFORMED -> return PlaybackFailureCode.CONTAINER_MALFORMED
            PlaybackFailureHint.CONTAINER_UNSUPPORTED -> return PlaybackFailureCode.CONTAINER_UNSUPPORTED
            PlaybackFailureHint.DECODER_INIT_FAILED -> return PlaybackFailureCode.DECODER_INIT_FAILED
            PlaybackFailureHint.DECODING_FAILED -> return PlaybackFailureCode.DECODING_FAILED
            PlaybackFailureHint.AUDIO_TRACK_INIT_FAILED -> return PlaybackFailureCode.AUDIO_TRACK_INIT_FAILED
            PlaybackFailureHint.AUDIO_TRACK_WRITE_FAILED -> return PlaybackFailureCode.AUDIO_TRACK_WRITE_FAILED
            PlaybackFailureHint.SUPERSEDED_RESOLUTION -> return PlaybackFailureCode.SUPERSEDED_RESOLUTION
            PlaybackFailureHint.TIMEOUT -> return PlaybackFailureCode.NETWORK_TIMEOUT
            PlaybackFailureHint.CONNECTION_FAILED -> return PlaybackFailureCode.NETWORK_CONNECTION_FAILED
            PlaybackFailureHint.OFFLINE -> return PlaybackFailureCode.NETWORK_CONNECTION_FAILED
            else -> Unit
        }

        when (val statusCode = input.http?.responseCode) {
            403 -> return PlaybackFailureCode.STREAM_HTTP_403
            404 -> return PlaybackFailureCode.STREAM_HTTP_404
            410 -> return PlaybackFailureCode.STREAM_HTTP_410
            416 -> return PlaybackFailureCode.CACHE_POSITION_OUT_OF_RANGE
            429 -> return PlaybackFailureCode.STREAM_HTTP_429
            in 500..599 -> return PlaybackFailureCode.STREAM_HTTP_5XX
            else -> Unit
        }
        return when {
            input.media3CodeName?.contains("TIMEOUT", ignoreCase = true) == true -> PlaybackFailureCode.NETWORK_TIMEOUT
            input.media3CodeName?.contains("NETWORK_CONNECTION_FAILED", ignoreCase = true) == true -> PlaybackFailureCode.NETWORK_CONNECTION_FAILED
            input.media3CodeName?.contains("INVALID_HTTP_CONTENT_TYPE", ignoreCase = true) == true -> PlaybackFailureCode.CONTENT_TYPE_INVALID
            input.media3CodeName?.contains("PARSING_CONTAINER_MALFORMED", ignoreCase = true) == true -> PlaybackFailureCode.CONTAINER_MALFORMED
            input.media3CodeName?.contains("PARSING_CONTAINER_UNSUPPORTED", ignoreCase = true) == true -> PlaybackFailureCode.CONTAINER_UNSUPPORTED
            else -> PlaybackFailureCode.UNKNOWN_PLAYBACK
        }
    }

    private fun categoryFor(code: PlaybackFailureCode): PlaybackFailureCategory = when (code) {
        PlaybackFailureCode.PLAYABILITY_LOGIN_REQUIRED,
        PlaybackFailureCode.PLAYABILITY_AGE_RESTRICTED,
        PlaybackFailureCode.PLAYABILITY_REGION_BLOCKED,
        PlaybackFailureCode.PLAYABILITY_UNAVAILABLE,
        PlaybackFailureCode.PLAYABILITY_PRIVATE -> PlaybackFailureCategory.PLAYABILITY
        PlaybackFailureCode.STREAM_HTTP_403,
        PlaybackFailureCode.STREAM_HTTP_404,
        PlaybackFailureCode.STREAM_HTTP_410,
        PlaybackFailureCode.STREAM_HTTP_429,
        PlaybackFailureCode.STREAM_HTTP_5XX -> PlaybackFailureCategory.HTTP
        PlaybackFailureCode.NETWORK_CONNECTION_FAILED,
        PlaybackFailureCode.NETWORK_TIMEOUT -> PlaybackFailureCategory.NETWORK
        PlaybackFailureCode.PLAYER_JS_NOT_FOUND,
        PlaybackFailureCode.SIGNATURE_FUNCTION_NOT_FOUND,
        PlaybackFailureCode.SIGNATURE_DECIPHER_FAILED,
        PlaybackFailureCode.N_TRANSFORM_NOT_FOUND,
        PlaybackFailureCode.N_TRANSFORM_FAILED -> PlaybackFailureCategory.CIPHER
        PlaybackFailureCode.POTOKEN_FAILED -> PlaybackFailureCategory.POTOKEN
        PlaybackFailureCode.STREAM_FORMAT_NOT_FOUND -> PlaybackFailureCategory.FORMAT
        PlaybackFailureCode.CACHE_CORRUPTED,
        PlaybackFailureCode.CACHE_POSITION_OUT_OF_RANGE -> PlaybackFailureCategory.CACHE
        PlaybackFailureCode.DECODER_INIT_FAILED,
        PlaybackFailureCode.DECODING_FAILED,
        PlaybackFailureCode.CONTAINER_MALFORMED,
        PlaybackFailureCode.CONTAINER_UNSUPPORTED -> PlaybackFailureCategory.DECODER
        PlaybackFailureCode.AUDIO_TRACK_INIT_FAILED,
        PlaybackFailureCode.AUDIO_TRACK_WRITE_FAILED -> PlaybackFailureCategory.AUDIO_SINK
        PlaybackFailureCode.RECOVERY_EXHAUSTED,
        PlaybackFailureCode.SUPERSEDED_RESOLUTION -> PlaybackFailureCategory.RECOVERY
        else -> PlaybackFailureCategory.UNKNOWN
    }

    private fun stageFor(code: PlaybackFailureCode, fallback: PlaybackFailureStage): PlaybackFailureStage = when (code) {
        PlaybackFailureCode.PLAYABILITY_LOGIN_REQUIRED,
        PlaybackFailureCode.PLAYABILITY_AGE_RESTRICTED,
        PlaybackFailureCode.PLAYABILITY_REGION_BLOCKED,
        PlaybackFailureCode.PLAYABILITY_UNAVAILABLE,
        PlaybackFailureCode.PLAYABILITY_PRIVATE -> PlaybackFailureStage.PLAYABILITY
        PlaybackFailureCode.STREAM_HTTP_403,
        PlaybackFailureCode.STREAM_HTTP_404,
        PlaybackFailureCode.STREAM_HTTP_410,
        PlaybackFailureCode.STREAM_HTTP_429,
        PlaybackFailureCode.STREAM_HTTP_5XX -> PlaybackFailureStage.CDN_HTTP
        PlaybackFailureCode.NETWORK_CONNECTION_FAILED,
        PlaybackFailureCode.NETWORK_TIMEOUT -> PlaybackFailureStage.NETWORK
        PlaybackFailureCode.PLAYER_JS_NOT_FOUND,
        PlaybackFailureCode.SIGNATURE_FUNCTION_NOT_FOUND -> PlaybackFailureStage.PLAYER_JS
        PlaybackFailureCode.SIGNATURE_DECIPHER_FAILED -> PlaybackFailureStage.CIPHER_SIGNATURE
        PlaybackFailureCode.N_TRANSFORM_NOT_FOUND,
        PlaybackFailureCode.N_TRANSFORM_FAILED -> PlaybackFailureStage.CIPHER_N
        PlaybackFailureCode.POTOKEN_FAILED -> PlaybackFailureStage.POTOKEN
        PlaybackFailureCode.STREAM_FORMAT_NOT_FOUND -> PlaybackFailureStage.FORMAT_SELECTION
        PlaybackFailureCode.CACHE_CORRUPTED,
        PlaybackFailureCode.CACHE_POSITION_OUT_OF_RANGE -> PlaybackFailureStage.CACHE_READ
        PlaybackFailureCode.CONTAINER_MALFORMED,
        PlaybackFailureCode.CONTAINER_UNSUPPORTED -> PlaybackFailureStage.CONTAINER_PARSE
        PlaybackFailureCode.DECODER_INIT_FAILED,
        PlaybackFailureCode.DECODING_FAILED -> PlaybackFailureStage.DECODER
        PlaybackFailureCode.AUDIO_TRACK_INIT_FAILED,
        PlaybackFailureCode.AUDIO_TRACK_WRITE_FAILED -> PlaybackFailureStage.AUDIO_SINK
        else -> fallback
    }

    private fun humanMessage(code: PlaybackFailureCode): String = when (code) {
        PlaybackFailureCode.PLAYABILITY_LOGIN_REQUIRED -> "Esta canción requiere iniciar sesión en YouTube."
        PlaybackFailureCode.PLAYABILITY_AGE_RESTRICTED -> "Este contenido requiere verificación de edad en YouTube."
        PlaybackFailureCode.PLAYABILITY_REGION_BLOCKED -> "Este contenido no está disponible en tu región."
        PlaybackFailureCode.PLAYABILITY_PRIVATE -> "Este contenido es privado y no se puede reproducir."
        PlaybackFailureCode.PLAYABILITY_UNAVAILABLE -> "Este contenido no está disponible para reproducir."
        PlaybackFailureCode.STREAM_URL_EXPIRED -> "La URL de reproducción expiró y no pudo renovarse."
        PlaybackFailureCode.STREAM_HTTP_403 -> "YouTube rechazó el stream después de reintentar."
        PlaybackFailureCode.STREAM_HTTP_404 -> "El stream ya no está disponible y no pudo renovarse."
        PlaybackFailureCode.STREAM_HTTP_410 -> "La URL de reproducción fue retirada y no pudo renovarse."
        PlaybackFailureCode.STREAM_HTTP_429 -> "YouTube está limitando las solicitudes; reintentá en unos segundos."
        PlaybackFailureCode.STREAM_HTTP_5XX -> "El servidor de audio devolvió un error temporal."
        PlaybackFailureCode.NETWORK_CONNECTION_FAILED -> "No se pudo conectar al servidor de audio."
        PlaybackFailureCode.NETWORK_TIMEOUT -> "La conexión con el servidor de audio agotó el tiempo de espera."
        PlaybackFailureCode.CONTENT_TYPE_INVALID -> "El servidor devolvió un formato de audio inesperado."
        PlaybackFailureCode.STREAM_FORMAT_NOT_FOUND -> "No se encontró un formato de audio compatible."
        PlaybackFailureCode.DECODER_INIT_FAILED,
        PlaybackFailureCode.DECODING_FAILED,
        PlaybackFailureCode.CONTAINER_MALFORMED,
        PlaybackFailureCode.CONTAINER_UNSUPPORTED -> "El dispositivo no pudo decodificar este audio."
        PlaybackFailureCode.AUDIO_TRACK_INIT_FAILED,
        PlaybackFailureCode.AUDIO_TRACK_WRITE_FAILED -> "El dispositivo no pudo iniciar la salida de audio."
        PlaybackFailureCode.RECOVERY_EXHAUSTED -> "La recuperación automática se agotó."
        PlaybackFailureCode.SUPERSEDED_RESOLUTION -> "La resolución anterior fue cancelada por una selección más nueva."
        else -> "No se pudo reproducir esta canción."
    }
}
