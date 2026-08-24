package com.auriqo.music.debug

import com.auriqo.music.playback.diagnostics.PlaybackDiagnostics
import com.auriqo.music.playback.diagnostics.PlaybackRedactor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Headers
import okhttp3.Handshake
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.atomic.AtomicLong

data class NetworkDebugRequest(
    val id: Long,
    val traceId: String?,
    val method: String,
    val host: String,
    val pathHash: String?,
    val queryKeys: List<String>,
    val startedAtMs: Long,
    val durationMs: Long? = null,
    val dnsMs: Long? = null,
    val connectMs: Long? = null,
    val tlsMs: Long? = null,
    val requestHeaders: Map<String, String> = emptyMap(),
    val responseHeaders: Map<String, String> = emptyMap(),
    val status: Int? = null,
    val contentType: String? = null,
    val bytes: Long? = null,
    val protocol: String? = null,
    val connectionReused: Boolean? = null,
    val failureClass: String? = null,
    val completed: Boolean = false,
)

class DebugNetworkCollector {
    private val lock = Any()
    private val nextId = AtomicLong(0L)
    private val recordMap = LinkedHashMap<Long, NetworkDebugRequest>()
    private val _records = MutableStateFlow<List<NetworkDebugRequest>>(emptyList())
    val records: StateFlow<List<NetworkDebugRequest>> = _records.asStateFlow()
    var traceIdProvider: (() -> String?)? = null

    fun listenerFactory(): EventListener.Factory = EventListener.Factory { NetworkDebugListener(this) }

    fun begin(call: Call): Long = synchronized(lock) {
        val request = call.request()
        val url = request.url
        val id = nextId.incrementAndGet()
        recordMap[id] = NetworkDebugRequest(
            id = id,
            traceId = traceIdProvider?.invoke() ?: PlaybackDiagnostics.current()?.traceId,
            method = request.method,
            host = url.host,
            pathHash = PlaybackRedactor.shortHash(url.encodedPath),
            queryKeys = url.queryParameterNames.toList().sorted(),
            startedAtMs = System.currentTimeMillis(),
            requestHeaders = safeHeaders(request),
        )
        trimAndPublishLocked()
        id
    }

    fun update(id: Long, transform: (NetworkDebugRequest) -> NetworkDebugRequest) = synchronized(lock) {
        recordMap[id]?.let { recordMap[id] = transform(it) }
        publishLocked()
    }

    fun finish(id: Long, failure: Throwable? = null) = synchronized(lock) {
        recordMap[id]?.let { record ->
            recordMap[id] = record.copy(
                durationMs = System.currentTimeMillis() - record.startedAtMs,
                failureClass = failure?.let { PlaybackRedactor.sanitizeScalar(it::class.java.simpleName) },
                completed = true,
            )
        }
        publishLocked()
    }

    fun clear() = synchronized(lock) {
        recordMap.clear()
        publishLocked()
    }

    private fun trimAndPublishLocked() {
        while (recordMap.size > 200) recordMap.remove(recordMap.keys.first())
        publishLocked()
    }

    private fun publishLocked() {
        _records.value = recordMap.values.toList()
    }

    private fun safeHeaders(request: Request): Map<String, String> = redactNetworkHeaders(request.headers)

    private class NetworkDebugListener(
        private val collector: DebugNetworkCollector,
    ) : EventListener() {
        private var id: Long = 0L
        private var dnsStartNs: Long? = null
        private var connectStartNs: Long? = null
        private var tlsStartNs: Long? = null

        private fun nowNs(): Long = System.nanoTime()
        private fun elapsedMs(start: Long?): Long? = start?.let { (nowNs() - it) / 1_000_000L }

        override fun callStart(call: Call) {
            id = collector.begin(call)
        }

        override fun dnsStart(call: Call, domainName: String) {
            dnsStartNs = nowNs()
        }

        override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<java.net.InetAddress>) {
            collector.update(id) { it.copy(dnsMs = elapsedMs(dnsStartNs)) }
        }

        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
            connectStartNs = nowNs()
        }

        override fun connectEnd(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
            protocol: Protocol?,
        ) {
            collector.update(id) { it.copy(connectMs = elapsedMs(connectStartNs), protocol = protocol?.toString()) }
        }

        override fun secureConnectStart(call: Call) {
            tlsStartNs = nowNs()
        }

        override fun secureConnectEnd(call: Call, handshake: Handshake?) {
            collector.update(id) { it.copy(tlsMs = elapsedMs(tlsStartNs)) }
        }

        override fun connectionAcquired(call: Call, connection: Connection) {
            collector.update(id) { it.copy(protocol = connection.protocol().toString()) }
        }

        override fun requestHeadersEnd(call: Call, request: Request) {
            collector.update(id) { it.copy(requestHeaders = redactNetworkHeaders(request.headers)) }
        }

        override fun responseHeadersEnd(call: Call, response: Response) {
            collector.update(id) {
                it.copy(
                    responseHeaders = redactNetworkHeaders(response.headers),
                    status = response.code,
                    contentType = response.header("Content-Type")?.let(PlaybackRedactor::sanitizeScalar),
                    protocol = response.protocol.toString(),
                )
            }
        }

        override fun responseBodyEnd(call: Call, byteCount: Long) {
            collector.update(id) { it.copy(bytes = byteCount) }
        }

        override fun callEnd(call: Call) {
            collector.finish(id)
        }

        override fun callFailed(call: Call, ioe: java.io.IOException) {
            collector.finish(id, ioe)
        }

        override fun canceled(call: Call) {
            collector.finish(id, java.io.IOException("canceled"))
        }
    }
}

private fun redactNetworkHeaders(headers: Headers): Map<String, String> =
    PlaybackRedactor.redactHeaders(headers.names().associateWith { name -> headers[name].orEmpty() })
