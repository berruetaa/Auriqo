package com.auriqo.music.playback.diagnostics

import android.net.Uri
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.HttpDataSource
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Media3-specific extraction kept at the boundary where its context still exists. */
object Media3PlaybackDiagnostics {
    fun toFailureInput(
        error: PlaybackException,
        traceId: String,
        mediaId: String?,
        stage: PlaybackFailureStage = PlaybackFailureStage.PLAYER_STATE,
        attempt: Int = 0,
        maxAttempts: Int = 0,
        streamGeneration: Long? = null,
        cacheStatus: String? = null,
        quality: String? = null,
        queueIndex: Int? = null,
        networkType: String? = null,
        terminalOverride: Boolean? = null,
    ): PlaybackFailureInput {
        val resolution = findCause<PlaybackResolutionException>(error)
        return PlaybackFailureInput(
            traceId = traceId,
            mediaId = mediaId,
            stage = stage,
            media3Code = error.errorCode,
            media3CodeName = errorCodeName(error.errorCode),
            http = findHttpDetails(error),
            playabilityStatus = resolution?.playabilityStatus,
            playabilityReason = resolution?.playabilityReason,
            hint = resolution?.hint ?: hintFor(error),
            cause = error,
            causeChain = causeChain(error),
            attempt = attempt,
            maxAttempts = maxAttempts,
            streamGeneration = streamGeneration,
            cacheStatus = cacheStatus,
            quality = quality,
            queueIndex = queueIndex,
            networkType = networkType,
            terminalOverride = terminalOverride,
        )
    }

    fun findHttpDetails(error: Throwable, knownItag: Int? = null): PlaybackHttpDetails? {
        val invalid = findCause<HttpDataSource.InvalidResponseCodeException>(error) ?: return null
        val uri = invalid.dataSpec.uri
        val headers = invalid.headerFields
            .mapValues { (_, values) -> values.firstOrNull().orEmpty() }
        val redactedHeaders = PlaybackRedactor.redactHeaders(headers)
        val sensitive = redactedHeaders["sensitiveHeadersPresent"]
            ?.split(',')
            ?.filter(String::isNotBlank)
            ?.toSet()
            .orEmpty()
        return PlaybackHttpDetails(
            responseCode = invalid.responseCode,
            responseMessage = invalid.responseMessage?.let(PlaybackRedactor::sanitizeText),
            host = uri.host,
            contentType = headerValue(headers, "content-type")?.let(PlaybackRedactor::sanitizeScalar),
            range = headerValue(headers, "content-range")
                ?.let(PlaybackRedactor::sanitizeScalar)
                ?: headerValue(headers, "range")?.let(PlaybackRedactor::sanitizeScalar),
            itag = knownItag ?: uri.getQueryParameter("itag")?.toIntOrNull(),
            queryKeys = uri.queryParameterNames.toList().sorted(),
            expireEpoch = uri.getQueryParameter("expire")?.toLongOrNull(),
            sensitiveHeadersPresent = sensitive,
        )
    }

    fun hintFor(error: Throwable): PlaybackFailureHint? {
        findCause<PlaybackResolutionException>(error)?.let { return it.hint }
        findCause<SocketTimeoutException>(error)?.let { return PlaybackFailureHint.TIMEOUT }
        findCause<UnknownHostException>(error)?.let { return PlaybackFailureHint.CONNECTION_FAILED }
        findCause<ConnectException>(error)?.let { return PlaybackFailureHint.CONNECTION_FAILED }
        return when (errorCodeOf(error)) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_TIMEOUT -> PlaybackFailureHint.TIMEOUT
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> PlaybackFailureHint.CONNECTION_FAILED
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> PlaybackFailureHint.CONTENT_TYPE_INVALID
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> PlaybackFailureHint.CACHE_POSITION_OUT_OF_RANGE
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> PlaybackFailureHint.CONTAINER_MALFORMED
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> PlaybackFailureHint.CONTAINER_UNSUPPORTED
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> PlaybackFailureHint.DECODER_INIT_FAILED
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> PlaybackFailureHint.DECODING_FAILED
            PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> PlaybackFailureHint.AUDIO_TRACK_INIT_FAILED
            PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> PlaybackFailureHint.AUDIO_TRACK_WRITE_FAILED
            else -> null
        }
    }

    fun errorCodeName(errorCode: Int): String = when (errorCode) {
        PlaybackException.ERROR_CODE_UNSPECIFIED -> "UNSPECIFIED"
        PlaybackException.ERROR_CODE_REMOTE_ERROR -> "REMOTE_ERROR"
        PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> "BEHIND_LIVE_WINDOW"
        PlaybackException.ERROR_CODE_TIMEOUT -> "TIMEOUT"
        PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK -> "FAILED_RUNTIME_CHECK"
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED -> "IO_UNSPECIFIED"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "IO_NETWORK_CONNECTION_FAILED"
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "IO_NETWORK_CONNECTION_TIMEOUT"
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> "IO_INVALID_HTTP_CONTENT_TYPE"
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "IO_BAD_HTTP_STATUS"
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND -> "IO_FILE_NOT_FOUND"
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> "IO_NO_PERMISSION"
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED -> "IO_CLEARTEXT_NOT_PERMITTED"
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> "IO_READ_POSITION_OUT_OF_RANGE"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> "PARSING_CONTAINER_MALFORMED"
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> "PARSING_MANIFEST_MALFORMED"
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "PARSING_CONTAINER_UNSUPPORTED"
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> "PARSING_MANIFEST_UNSUPPORTED"
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED -> "DECODER_INIT_FAILED"
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED -> "DECODER_QUERY_FAILED"
        PlaybackException.ERROR_CODE_DECODING_FAILED -> "DECODING_FAILED"
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> "DECODING_FORMAT_EXCEEDS_CAPABILITIES"
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "DECODING_FORMAT_UNSUPPORTED"
        PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED -> "AUDIO_TRACK_INIT_FAILED"
        PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED -> "AUDIO_TRACK_WRITE_FAILED"
        PlaybackException.ERROR_CODE_DRM_UNSPECIFIED -> "DRM_UNSPECIFIED"
        PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED -> "DRM_SCHEME_UNSUPPORTED"
        PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED -> "DRM_PROVISIONING_FAILED"
        PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR -> "DRM_CONTENT_ERROR"
        PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED -> "DRM_LICENSE_ACQUISITION_FAILED"
        PlaybackException.ERROR_CODE_DRM_DISALLOWED_OPERATION -> "DRM_DISALLOWED_OPERATION"
        PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR -> "DRM_SYSTEM_ERROR"
        PlaybackException.ERROR_CODE_DRM_DEVICE_REVOKED -> "DRM_DEVICE_REVOKED"
        PlaybackException.ERROR_CODE_DRM_LICENSE_EXPIRED -> "DRM_LICENSE_EXPIRED"
        else -> "UNKNOWN_ERROR_$errorCode"
    }

    private fun errorCodeOf(error: Throwable): Int? = when (error) {
        is PlaybackException -> error.errorCode
        else -> findCause<PlaybackException>(error)?.errorCode
    }

    private fun headerValue(headers: Map<String, String>, name: String): String? =
        headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value

    private fun causeChain(error: Throwable): List<PlaybackCauseEntry> {
        val http = findHttpDetails(error)
        return PlaybackCauseChainExtractor.extract(error).map { entry ->
            if (entry.className.contains("InvalidResponseCodeException")) {
                entry.copy(
                    relevantFields = buildMap {
                        http?.let {
                            put("responseCode", it.responseCode.toString())
                            it.responseMessage?.let { message -> put("responseMessage", message) }
                            it.host?.let { host -> put("host", host) }
                            it.itag?.let { itag -> put("itag", itag.toString()) }
                        }
                    },
                )
            } else {
                entry
            }
        }
    }

    private inline fun <reified T : Throwable> findCause(root: Throwable): T? {
        val seen = java.util.Collections.newSetFromMap(java.util.IdentityHashMap<Throwable, Boolean>())
        var current: Throwable? = root
        repeat(16) {
            if (current == null || !seen.add(current)) return null
            if (current is T) return current
            current = current.cause
        }
        return null
    }
}

/** A DataSource decorator that records the real open boundary, including HTTP status. */
class PlaybackTracingDataSource(
    private val upstream: DataSource,
    private val traceProvider: (String?) -> PlaybackTraceRecorder?,
) : DataSource {
    private var trace: PlaybackTraceRecorder? = null

    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
        val diagnosticMediaId = dataSpec.key?.removeSuffix("_diff")
        trace = traceProvider(diagnosticMediaId)
        trace?.attachMediaId(diagnosticMediaId)
        trace?.dataSourceOpenStart()
        return try {
            upstream.open(dataSpec).also { trace?.dataSourceOpenEnd(success = true) }
        } catch (error: Throwable) {
            trace?.let {
                Media3PlaybackDiagnostics.findHttpDetails(error)?.let(it::httpStatus)
                it.dataSourceOpenEnd(success = false)
            }
            throw error
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = upstream.read(buffer, offset, length)
    override fun getUri(): Uri? = upstream.uri
    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders
    override fun close() = upstream.close()

    class Factory(
        private val upstreamFactory: DataSource.Factory,
        private val traceProvider: (String?) -> PlaybackTraceRecorder?,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            PlaybackTracingDataSource(upstreamFactory.createDataSource(), traceProvider)
    }
}
