package com.auriqo.music.debug

import android.content.Context
import android.os.Build
import com.auriqo.music.BuildConfig
import com.auriqo.music.playback.diagnostics.PlaybackDiagnosticEvent
import com.auriqo.music.playback.diagnostics.PlaybackFailure
import com.auriqo.music.playback.diagnostics.PlaybackRedactor
import com.auriqo.music.playback.diagnostics.toLogLine
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class DebugExportContext(
    val media3: String = "{}",
    val audio: String = "{}",
    val queue: String = "{}",
    val innertube: String = "{}",
    val summary: String = "",
)

object DebugBundleExporter {
    fun export(
        context: Context,
        collector: PlaybackDebugCollector,
        network: DebugNetworkCollector,
        previous: PreviousDebugSession?,
        runtime: DebugExportContext = DebugExportContext(),
    ): File {
        val shortSha = BuildConfig.SOURCE_REVISION.take(8).ifBlank { "unknown" }
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val output = context.cacheDir.resolve("auriqo-debug-$timestamp-$shortSha.zip")
        val state = collector.state.value
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            add(zip, "manifest.txt", "Auriqo Debug Bundle\nformat=1\nredaction=central\n")
            add(zip, "build.txt", buildText(context))
            add(zip, "device.txt", deviceText())
            add(zip, "playback/summary.txt", runtime.summary.ifBlank { summaryText(state) })
            add(zip, "playback/traces.json", tracesJson(state.traces))
            add(zip, "playback/events.jsonl", eventsJsonl(state.events))
            add(zip, "playback/metrics.json", metricsJson(state.metrics))
            add(zip, "network/requests.jsonl", networkJsonl(network.records.value))
            add(zip, "network/failures.jsonl", networkJsonl(network.records.value.filter { it.failureClass != null }))
            add(zip, "innertube/state.json", runtime.innertube)
            add(zip, "media3/state.json", runtime.media3)
            add(zip, "audio/state.json", runtime.audio)
            add(zip, "queue/state.json", runtime.queue)
            add(zip, "errors/recent.json", failuresJson(state.traces.mapNotNull { it.failure }))
            add(zip, "logs/structured.jsonl", eventsJsonl(state.events))
            previous?.file?.takeIf(File::exists)?.let { previousFile ->
                addFile(zip, "previous-session/session.json", previousFile)
            }
        }
        return output
    }

    fun sessionSnapshotJson(
        state: PlaybackDebugState,
        network: List<NetworkDebugRequest>,
    ): String = buildString {
        append("{\n")
        append("  \"savedAtMs\": ").append(System.currentTimeMillis()).append(",\n")
        append("  \"eventCount\": ").append(state.events.takeLast(1_000).size).append(",\n")
        append("  \"traceCount\": ").append(state.traces.size).append(",\n")
        append("  \"networkCount\": ").append(network.takeLast(50).size).append(",\n")
        append("  \"metrics\": ").append(metricsJson(state.metrics).trim()).append(",\n")
        append("  \"events\": [\n")
        val boundedEvents = state.events.takeLast(1_000)
        boundedEvents.forEachIndexed { index, event ->
            append("    ").append(eventJson(event))
            if (index != boundedEvents.lastIndex) append(',')
            append('\n')
        }
        append("  ],\n")
        append("  \"network\": [\n")
        val boundedNetwork = network.takeLast(50)
        boundedNetwork.forEachIndexed { index, record ->
            append("    ").append(networkRecordJson(record))
            if (index != boundedNetwork.lastIndex) append(',')
            append('\n')
        }
        append("  ]\n}")
    }

    private fun buildText(context: Context): String = buildString {
        appendLine("repository=Auriqo/Auriqo")
        appendLine("applicationId=${BuildConfig.APPLICATION_ID}")
        appendLine("versionName=${BuildConfig.VERSION_NAME}")
        appendLine("versionCode=${BuildConfig.VERSION_CODE}")
        appendLine("variant=Universal${if (BuildConfig.CAST_AVAILABLE) "Gms" else "Foss"}Debug")
        appendLine("debuggable=${BuildConfig.DEBUG}")
        appendLine("architecture=${BuildConfig.ARCHITECTURE}")
        appendLine("releaseIdentifier=${PlaybackRedactor.sanitizeScalar(BuildConfig.RELEASE_IDENTIFIER)}")
        appendLine("sourceRevision=${PlaybackRedactor.sanitizeScalar(BuildConfig.SOURCE_REVISION)}")
        appendLine("castAvailable=${BuildConfig.CAST_AVAILABLE}")
        appendLine("processUptimeMs=${android.os.SystemClock.uptimeMillis()}")
        appendLine("package=${context.packageName}")
    }

    private fun deviceText(): String = buildString {
        appendLine("manufacturer=${PlaybackRedactor.sanitizeScalar(Build.MANUFACTURER)}")
        appendLine("model=${PlaybackRedactor.sanitizeScalar(Build.MODEL)}")
        appendLine("android=${PlaybackRedactor.sanitizeScalar(Build.VERSION.RELEASE)}")
        appendLine("api=${Build.VERSION.SDK_INT}")
        appendLine("abi=${Build.SUPPORTED_ABIS.joinToString(",") { PlaybackRedactor.sanitizeScalar(it) }}")
    }

    private fun summaryText(state: PlaybackDebugState): String = buildString {
        val m = state.metrics
        appendLine("plays=${m.plays}")
        appendLine("success=${m.successful}")
        appendLine("recovered=${m.recovered}")
        appendLine("terminal=${m.terminal}")
        appendLine("cacheHitRate=${m.cacheHitRate}")
        appendLine("recoverySuccessRate=${m.recoverySuccessRate}")
        appendLine("tapToFirstAudio=${histogramText(m.tapToFirstAudio)}")
        appendLine("slowest=${state.traces.sortedByDescending { it.tapToFirstAudioMs ?: -1L }.take(3).joinToString(",") { it.traceId }}")
    }

    private fun tracesJson(traces: List<DebugTraceSnapshot>): String = buildString {
        append("[\n")
        traces.forEachIndexed { index, trace ->
            append("  {")
            append("\"traceId\":").append(json(trace.traceId)).append(',')
            append("\"mediaId\":").append(json(trace.mediaId)).append(',')
            append("\"classification\":").append(json(trace.classification.name)).append(',')
            append("\"tapToFirstAudioMs\":").append(trace.tapToFirstAudioMs ?: "null").append(',')
            append("\"dominantStage\":").append(json(trace.dominantStage)).append(',')
            append("\"slow\":").append(trace.slow).append(',')
            append("\"cacheState\":").append(json(trace.cacheState)).append(',')
            append("\"itag\":").append(trace.itag ?: "null").append(',')
            append("\"failure\":").append(failureJson(trace.failure))
            append('}')
            if (index != traces.lastIndex) append(',')
            append('\n')
        }
        append(']')
    }

    private fun eventsJsonl(events: List<PlaybackDiagnosticEvent>): String = buildString {
        events.forEach { event ->
            append(eventJson(event)).append('\n')
        }
    }

    private fun eventJson(event: PlaybackDiagnosticEvent): String =
        "{\"traceId\":${json(event.traceId)},\"elapsedMs\":${event.elapsedMs}," +
            "\"event\":${json(event.type)},\"mediaId\":${json(event.mediaId)}," +
            "\"data\":${json(event.toLogLine())}}"

    private fun networkJsonl(records: List<NetworkDebugRequest>): String = buildString {
        records.forEach { record -> append(networkRecordJson(record)).append('\n') }
    }

    private fun networkRecordJson(record: NetworkDebugRequest): String = buildString {
        append('{')
        append("\"id\":").append(record.id).append(',')
        append("\"traceId\":").append(json(record.traceId)).append(',')
        append("\"method\":").append(json(record.method)).append(',')
        append("\"host\":").append(json(record.host)).append(',')
        append("\"pathHash\":").append(json(record.pathHash)).append(',')
        append("\"queryKeys\":").append(record.queryKeys.joinToString(prefix = "[", postfix = "]") { json(it) }).append(',')
        append("\"status\":").append(record.status ?: "null").append(',')
        append("\"durationMs\":").append(record.durationMs ?: "null").append(',')
        append("\"dnsMs\":").append(record.dnsMs ?: "null").append(',')
        append("\"connectMs\":").append(record.connectMs ?: "null").append(',')
        append("\"tlsMs\":").append(record.tlsMs ?: "null").append(',')
        append("\"bytes\":").append(record.bytes ?: "null").append(',')
        append("\"protocol\":").append(json(record.protocol)).append(',')
        append("\"requestHeaders\":").append(headersJson(record.requestHeaders)).append(',')
        append("\"responseHeaders\":").append(headersJson(record.responseHeaders)).append(',')
        append("\"failureClass\":").append(json(record.failureClass))
        append('}')
    }

    private fun metricsJson(metrics: DebugSessionMetrics): String = buildString {
        append('{')
        append("\"plays\":").append(metrics.plays).append(',')
        append("\"successful\":").append(metrics.successful).append(',')
        append("\"recovered\":").append(metrics.recovered).append(',')
        append("\"terminal\":").append(metrics.terminal).append(',')
        append("\"cacheHitRate\":").append(metrics.cacheHitRate).append(',')
        append("\"preloadHitRate\":").append(metrics.preloadHitRate).append(',')
        append("\"recoverySuccessRate\":").append(metrics.recoverySuccessRate).append(',')
        append("\"tapToFirstAudio\":").append(histogramJson(metrics.tapToFirstAudio)).append(',')
        append("\"resolution\":").append(histogramJson(metrics.resolution)).append(',')
        append("\"playerResponse\":").append(histogramJson(metrics.playerResponse)).append(',')
        append("\"dataSource\":").append(histogramJson(metrics.dataSource)).append(',')
        append("\"recovery\":").append(histogramJson(metrics.recovery))
        append('}')
    }

    private fun failuresJson(failures: List<PlaybackFailure>): String =
        failures.joinToString(prefix = "[", postfix = "]") { failure ->
            "{\"traceId\":${json(failure.traceId)},\"code\":${json(failure.stableCode)}," +
                "\"stage\":${json(failure.stage.name)},\"media3Code\":${failure.media3Code ?: "null"}," +
                "\"media3CodeName\":${json(failure.media3CodeName)},\"httpStatus\":${failure.httpStatus ?: "null"}," +
                "\"playabilityStatus\":${json(failure.playabilityStatus)},\"technicalMessage\":${json(failure.technicalMessage)}}"
        }

    private fun failureJson(failure: PlaybackFailure?): String = failure?.let { failuresJson(listOf(it)).removePrefix("[").removeSuffix("]") } ?: "null"

    private fun histogramJson(histogram: DebugHistogram): String =
        "{\"count\":${histogram.count},\"p50\":${histogram.p50Ms ?: "null"}," +
            "\"p90\":${histogram.p90Ms ?: "null"},\"p95\":${histogram.p95Ms ?: "null"}," +
            "\"p99\":${histogram.p99Ms ?: "null"},\"max\":${histogram.maxMs ?: "null"}}"

    private fun headersJson(headers: Map<String, String>): String =
        PlaybackRedactor.redactHeaders(headers).entries.joinToString(prefix = "{", postfix = "}") { (name, value) ->
            "${json(name)}:${json(value)}"
        }

    private fun histogramText(histogram: DebugHistogram): String =
        "count=${histogram.count},p50=${histogram.p50Ms ?: "N/A"},p95=${histogram.p95Ms ?: "N/A"},p99=${histogram.p99Ms ?: "N/A"},max=${histogram.maxMs ?: "N/A"}"

    private fun json(value: String?): String = value?.let {
        buildString {
            append('"')
            it.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(char)
                }
            }
            append('"')
        }
    } ?: "null"

    private fun add(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray())
        zip.closeEntry()
    }

    private fun addFile(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        FileInputStream(file).use { input -> input.copyTo(zip) }
        zip.closeEntry()
    }
}
