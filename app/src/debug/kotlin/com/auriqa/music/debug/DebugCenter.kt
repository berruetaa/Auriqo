package com.auriqo.music.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.media.AudioManager
import android.net.ConnectivityManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.navigation.NavController
import com.auriqo.music.BuildConfig
import com.auriqo.music.LocalPlayerConnection
import com.auriqo.music.playback.PlayerConnection
import com.auriqo.music.playback.diagnostics.PlaybackDiagnosticEvent
import com.auriqo.music.playback.diagnostics.PlaybackRedactor
import com.auriqo.music.playback.diagnostics.toLogLine
import com.music.innertube.YouTube
import java.net.URLEncoder
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugCenter(navController: NavController, initialTraceId: String? = null) {
    val context = LocalContext.current
    val collector = DebugRuntimeAccess.collector
    val network = DebugRuntimeAccess.network
    val chaos = DebugRuntimeAccess.chaos
    val session = DebugRuntimeAccess.session
    val state by collector.state.collectAsState()
    val networkRecords by network.records.collectAsState()
    val pendingFaults by chaos.pending.collectAsState()
    val previousSession by session.previous.collectAsState()
    val playerConnection by DebugRuntimeAccess.connection.collectAsState()
    val hudEnabled by DebugRuntimeAccess.hudEnabled.collectAsState()
    var selectedTraceId by rememberSaveable(initialTraceId) { mutableStateOf(initialTraceId) }
    var logFilter by rememberSaveable { mutableStateOf("") }
    var delayMs by rememberSaveable { mutableStateOf("500") }
    var offlineMs by rememberSaveable { mutableStateOf("5000") }
    var toastMessage by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(initialTraceId) {
        if (!initialTraceId.isNullOrBlank()) selectedTraceId = initialTraceId
    }

    val activeTrace = state.activeTrace
    val selectedTrace = state.traces.firstOrNull { it.traceId == selectedTraceId }
        ?: activeTrace
    val viewingHistoricalTrace =
        selectedTrace != null && activeTrace?.traceId != selectedTrace.traceId
    val runtime = playerConnection?.let { runtimeSnapshot(context, it, activeTrace) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Debug Center") },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        selectedTrace?.let {
                            copyToClipboard(context, "Auriqo Playback Trace", formatTrace(it, networkRecords))
                            toastMessage = "Trace copied"
                        }
                    }) {
                        Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy trace")
                    }
                    IconButton(onClick = {
                        val file = DebugBundleExporter.export(
                            context = context,
                            collector = collector,
                            network = network,
                            previous = previousSession,
                            runtime = runtime?.toExportContext() ?: DebugExportContext(),
                        )
                        shareFile(context, file)
                        toastMessage = "Debug bundle ready"
                    }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Export debug bundle")
                    }
                    IconButton(onClick = {
                        collector.clear()
                        toastMessage = "Diagnostics cleared"
                    }) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Clear diagnostics")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp),
        ) {
            item {
                if (pendingFaults.isNotEmpty()) {
                    ChaosActiveBanner(pendingFaults.size)
                }
                toastMessage?.let { message ->
                    TextButton(onClick = { toastMessage = null }) { Text(message) }
                }
            }
            item { SectionTitle("LIVE PLAYER") }
            item { RuntimeCard(runtime) }
            item { SectionTitle("SELECTED TRACE") }
            item {
                LiveTraceCard(
                    selected = selectedTrace,
                    traces = state.traces,
                    historical = viewingHistoricalTrace,
                    onSelect = { selectedTraceId = it },
                )
            }
            if (viewingHistoricalTrace) {
                item { HistoricalTraceBanner(selectedTrace?.traceId) }
            }
            item { SectionTitle("SESSION PERFORMANCE") }
            item { PerformanceCard(state.metrics) }
            item { HudCard(context, hudEnabled) { DebugRuntimeAccess.setHudEnabled(context, it) } }
            item { SectionTitle("TRACE TIMELINE") }
            item { TimelineCard(selectedTrace) }
            item { SectionTitle("TRACE NETWORK") }
            item { NetworkCard(networkRecords, selectedTrace?.traceId) }
            item { SectionTitle("INNERTUBE") }
            item { InnerTubeCard(selectedTrace, viewingHistoricalTrace) }
            item { SectionTitle("LIVE STREAM / CACHE") }
            item { CacheCard(runtime, playerConnection) }
            item { SectionTitle("LIVE MEDIA3") }
            item { Media3Card(runtime) }
            item { SectionTitle("LIVE AUDIO") }
            item { AudioCard(runtime) }
            item { SectionTitle("LIVE QUEUE") }
            item { QueueCard(runtime) }
            item { SectionTitle("ERRORS") }
            item { ErrorHistoryCard(state.traces) { selectedTraceId = it } }
            item { SectionTitle("LOGS") }
            item {
                OutlinedTextField(
                    value = logFilter,
                    onValueChange = { logFilter = it },
                    label = { Text("Trace/event filter") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            items(
                state.events.asReversed().filter { event ->
                    logFilter.isBlank() ||
                        event.traceId.contains(logFilter, ignoreCase = true) ||
                        event.type.contains(logFilter, ignoreCase = true) ||
                        event.toLogLine().contains(logFilter, ignoreCase = true)
                }.take(80),
                key = { "${it.traceId}:${it.elapsedMs}:${it.type}:${it.hashCode()}" },
            ) { event ->
                EventRow(event)
            }
            item { SectionTitle("CHAOS") }
            item { ChaosCard(chaos, delayMs, offlineMs, { delayMs = it }, { offlineMs = it }) }
            item { SectionTitle("APP / DEVICE") }
            item { AppDeviceCard(context) }
            item { SectionTitle("PREVIOUS SESSION") }
            item { PreviousSessionCard(previousSession, session) }
        }
    }
}

@Composable
fun DebugCenterAboutEntry(navController: NavController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text("Developer laboratory", fontWeight = FontWeight.Bold)
            Text(
                "Live playback traces, metrics, network timings and fault injection",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { navController.navigate("settings/debug_center") }) {
                Text("Open Auriqo Debug Center")
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}

@Composable
private fun ChaosActiveBanner(count: Int) {
    Surface(
        color = Color(0xFF8B0000),
        contentColor = Color.White,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("⚠ CHAOS ACTIVE · $count one-shot fault(s) armed", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun HistoricalTraceBanner(traceId: String?) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text("Historical trace ${traceId ?: "N/A"}", fontWeight = FontWeight.SemiBold)
            Text(
                "Timeline and trace network belong to this selection. Cards labeled LIVE show the player as it is now.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RuntimeCard(runtime: DebugRuntimeSnapshot?) {
    InfoCard {
        KeyValue("trace", runtime?.traceId ?: "N/A")
        KeyValue("mediaId", runtime?.mediaId ?: "N/A")
        KeyValue("playbackGeneration", runtime?.playbackGeneration ?: "N/A")
        KeyValue("resolutionGeneration", runtime?.resolutionGeneration ?: "N/A")
        KeyValue("queueIndex", runtime?.queueIndex ?: "N/A")
        KeyValue("playerState", runtime?.playbackState ?: "N/A")
        KeyValue("playWhenReady", runtime?.playWhenReady ?: "N/A")
        KeyValue("isPlaying", runtime?.isPlaying ?: "N/A")
        KeyValue("suppressionReason", runtime?.suppressionReason ?: "N/A")
        KeyValue("position", runtime?.positionMs?.let(::formatMs) ?: "N/A")
        KeyValue("buffered", runtime?.bufferedPositionMs?.let(::formatMs) ?: "N/A")
        KeyValue("buffered %", runtime?.bufferedPercent ?: "N/A")
        KeyValue("network", runtime?.network ?: "N/A")
    }
}

@Composable
private fun LiveTraceCard(
    selected: DebugTraceSnapshot?,
    traces: List<DebugTraceSnapshot>,
    historical: Boolean,
    onSelect: (String) -> Unit,
) {
    InfoCard {
        Text("Last traces", fontWeight = FontWeight.Bold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
        ) {
            traces.takeLast(8).forEach { trace ->
                FilterChip(
                    selected = selected?.traceId == trace.traceId,
                    onClick = { onSelect(trace.traceId) },
                    label = { Text(trace.traceId.removePrefix("PB-"), fontSize = 10.sp) },
                )
            }
        }
        selected?.let { trace ->
            KeyValue("view", if (historical) "HISTORICAL" else "ACTIVE")
            KeyValue("classification", trace.classification.name)
            KeyValue("resolver path", trace.resolverPath)
            KeyValue("stream client", trace.streamClient ?: "N/A")
            KeyValue("stream source", trace.streamSource ?: "N/A")
            KeyValue("PoToken", trace.poTokenAttached?.toString() ?: "N/A")
            KeyValue("context generation", trace.streamContextGeneration ?: "N/A")
            KeyValue("Player JS used", trace.playerJsUsed)
            KeyValue("cache", trace.cacheState)
            KeyValue("itag", trace.itag ?: "N/A")
            KeyValue("format", trace.mimeType ?: "N/A")
            KeyValue("bitrate", trace.bitrate ?: "N/A")
            KeyValue("expiry", trace.expiresInMs?.let(::formatMs) ?: "N/A")
            KeyValue("preloaded bytes", trace.preloadedBytes ?: "N/A")
            KeyValue("dominant stage", trace.dominantStage ?: "N/A")
            KeyValue("last event", trace.lastEvent?.type ?: "N/A")
        }
    }
}

@Composable
private fun PerformanceCard(metrics: DebugSessionMetrics) {
    InfoCard {
        KeyValue("plays", metrics.plays)
        KeyValue("success", metrics.successful)
        KeyValue("recovered", metrics.recovered)
        KeyValue("terminal", metrics.terminal)
        KeyValue("cache hit rate", percent(metrics.cacheHitRate))
        KeyValue("preload hit rate", percent(metrics.preloadHitRate))
        KeyValue("recovery success", percent(metrics.recoverySuccessRate))
        HistogramRows("tap → FIRST_AUDIO", metrics.tapToFirstAudio)
        HistogramRows("resolution", metrics.resolution)
        HistogramRows("player response", metrics.playerResponse)
        HistogramRows("DataSource open", metrics.dataSource)
        HistogramRows("recovery", metrics.recovery)
        BudgetLine("HOT target p50 < 150ms", metrics.byClass[DebugPerformanceClass.HOT]?.tapToFirstAudio, 150L)
        BudgetLine("PRELOADED target p50 < 250ms", metrics.byClass[DebugPerformanceClass.PRELOADED]?.tapToFirstAudio, 250L)
        BudgetLine("COLD target p50 < 500ms", metrics.byClass[DebugPerformanceClass.COLD]?.tapToFirstAudio, 500L)
        BudgetLine("COLD p95 target < 1200ms", metrics.byClass[DebugPerformanceClass.COLD]?.tapToFirstAudio, 1_200L, useP95 = true)
        Text("By classification", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        metrics.byClass.values.forEach { klass ->
            KeyValue(klass.classification.name, "n=${klass.plays} ${histogramText(klass.tapToFirstAudio)}")
        }
    }
}

@Composable
private fun BudgetLine(label: String, histogram: DebugHistogram?, targetMs: Long, useP95: Boolean = false) {
    val measured = if (useP95) histogram?.p95Ms else histogram?.p50Ms
    val color = when {
        measured == null -> MaterialTheme.colorScheme.onSurfaceVariant
        measured <= targetMs -> MaterialTheme.colorScheme.primary
        measured <= targetMs * 2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Text(
        "$label: ${measured?.let { "${it}ms" } ?: "N/A"}",
        color = color,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
    )
}

@Composable
private fun HudCard(context: Context, enabled: Boolean, onChange: (Boolean) -> Unit) {
    InfoCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Performance badge", fontWeight = FontWeight.Bold)
                Text("Optional overlay over the player; tap opens the selected trace", style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun TimelineCard(trace: DebugTraceSnapshot?) {
    InfoCard {
        if (trace == null) {
            Text("N/A — no trace in the bounded buffer")
        } else {
            val visibleEvents = trace.events.takeLast(MAX_TIMELINE_EVENTS)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("${trace.events.size} events", fontWeight = FontWeight.Bold)
                Text(
                    if (visibleEvents.size < trace.events.size) {
                        "Last ${visibleEvents.size} · tap to expand"
                    } else {
                        "Tap a row to expand"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            visibleEvents.forEach { EventRow(it) }
        }
    }
}

@Composable
private fun NetworkCard(records: List<NetworkDebugRequest>, traceId: String?) {
    InfoCard {
        val filtered = records.filter { traceId == null || it.traceId == traceId }.takeLast(12)
        if (filtered.isEmpty()) Text("N/A — no playback OkHttp calls captured")
        filtered.forEach { record ->
            Text(
                "${record.method} ${record.host} · ${record.status ?: "—"} · ${record.durationMs ?: "—"}ms",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Text(
                "dns=${record.dnsMs ?: "—"} connect=${record.connectMs ?: "—"} tls=${record.tlsMs ?: "—"} " +
                    "bytes=${record.bytes ?: "—"} protocol=${record.protocol ?: "—"} pathHash=${record.pathHash ?: "—"}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun InnerTubeCard(trace: DebugTraceSnapshot?, historical: Boolean) {
    InfoCard {
        if (historical) {
            Text(
                "Current session/config + evidence captured in the selected trace.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        KeyValue("locale", "${YouTube.locale.gl}-${YouTube.locale.hl}")
        KeyValue("login browse", YouTube.useLoginForBrowse)
        KeyValue("proxy enabled", YouTube.proxy != null)
        KeyValue("IP mode", YouTube.ipVersion.name)
        KeyValue("visitorData present", YouTube.visitorData != null)
        KeyValue("visitorData hash", PlaybackRedactor.shortHash(YouTube.visitorData) ?: "N/A")
        KeyValue("cookie present", YouTube.cookie != null)
        KeyValue("player response", trace?.events?.filterIsInstance<PlaybackDiagnosticEvent.PlayerResponseEnd>()?.lastOrNull()?.let { "${it.status ?: "unknown"} ${it.durationMs}ms" } ?: "N/A")
        KeyValue("cipher events", trace?.events?.count { it is PlaybackDiagnosticEvent.CipherEnd } ?: 0)
        KeyValue("player JS/extractor refreshes", trace?.events?.count {
            it is PlaybackDiagnosticEvent.Breadcrumb &&
                (it.name.contains("PLAYER_JS", true) || it.name.contains("EXTRACTOR", true) || it.name.contains("REFRESH", true))
        } ?: 0)
        KeyValue("N-transform state", trace?.events?.filterIsInstance<PlaybackDiagnosticEvent.Breadcrumb>()
            ?.lastOrNull { it.name.contains("N_TRANSFORM", true) }
            ?.value ?: "N/A")
        KeyValue("PoToken state", trace?.events?.filterIsInstance<PlaybackDiagnosticEvent.Breadcrumb>()
            ?.lastOrNull { it.name.contains("POTOKEN", true) }
            ?.value ?: "N/A")
        KeyValue("refresh breadcrumbs", trace?.events?.count { it is PlaybackDiagnosticEvent.Breadcrumb && it.name.contains("REFRESH", true) } ?: 0)
        Text("Secret values are intentionally unavailable in this view.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun CacheCard(runtime: DebugRuntimeSnapshot?, connection: PlayerConnection?) {
    InfoCard {
        KeyValue("stream cache", runtime?.streamCacheState ?: "N/A")
        KeyValue("stream expiry", runtime?.streamExpiryMs?.let(::formatMs) ?: "N/A")
        KeyValue("stream generation", runtime?.resolutionGeneration ?: "N/A")
        KeyValue("preloaded bytes", runtime?.preloadedBytes ?: "N/A")
        KeyValue("PlayerCache entries", runtime?.playerCacheEntries ?: "N/A")
        KeyValue("DownloadCache entries", runtime?.downloadCacheEntries ?: "N/A")
        KeyValue("cache note", "Debug actions never delete DownloadCache")
        connection?.let { service ->
            Text("Known stream entries", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            service.service.debugStreamSnapshot().entries.takeLast(8).forEach { entry ->
                Text(
                    "${entry.mediaId} · ${entry.quality} · itag=${entry.itag ?: "—"} · expires=${formatMs(entry.expiresInMs)}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun Media3Card(runtime: DebugRuntimeSnapshot?) {
    InfoCard {
        KeyValue("state", runtime?.playbackState ?: "N/A")
        KeyValue("isPlaying", runtime?.isPlaying ?: "N/A")
        KeyValue("playWhenReady", runtime?.playWhenReady ?: "N/A")
        KeyValue("suppression", runtime?.suppressionReason ?: "N/A")
        KeyValue("position", runtime?.positionMs?.let(::formatMs) ?: "N/A")
        KeyValue("buffered", runtime?.bufferedPositionMs?.let(::formatMs) ?: "N/A")
        KeyValue("duration", runtime?.durationMs?.let(::formatMs) ?: "N/A")
        KeyValue("repeat", runtime?.repeatMode ?: "N/A")
        KeyValue("shuffle", runtime?.shuffle ?: "N/A")
        KeyValue("audio format", runtime?.formatSummary ?: "N/A")
        KeyValue("last failure", runtime?.lastFailure ?: "none")
    }
}

@Composable
private fun AudioCard(runtime: DebugRuntimeSnapshot?) {
    InfoCard {
        KeyValue("audio focus", runtime?.audioFocus ?: "N/A")
        KeyValue("last focus event", runtime?.lastFocusEvent ?: "N/A")
        KeyValue("resume on gain", runtime?.resumeOnGain ?: "N/A")
        KeyValue("output", runtime?.outputDevice ?: "N/A")
        KeyValue("volume", runtime?.volume ?: "N/A")
        KeyValue("muted", runtime?.muted ?: "N/A")
        KeyValue("player volume", runtime?.playerVolume ?: "N/A")
        KeyValue("audio session", runtime?.audioSessionId ?: "N/A")
        KeyValue("FIRST_AUDIO", "READY + isPlaying proxy; not sink-written confirmation")
    }
}

@Composable
private fun QueueCard(runtime: DebugRuntimeSnapshot?) {
    InfoCard {
        KeyValue("queue size", runtime?.queueSize ?: "N/A")
        KeyValue("queue index", runtime?.queueIndex ?: "N/A")
        KeyValue("shuffle", runtime?.shuffle ?: "N/A")
        KeyValue("repeat", runtime?.repeatMode ?: "N/A")
        runtime?.queueItems.orEmpty().forEach { item ->
            Text(item, fontFamily = FontFamily.Monospace, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ErrorHistoryCard(traces: List<DebugTraceSnapshot>, onSelect: (String) -> Unit) {
    InfoCard {
        val failures = traces.filter { it.failure != null }
        if (failures.isEmpty()) Text("No terminal failures in the bounded history")
        failures.forEach { trace ->
            TextButton(onClick = { onSelect(trace.traceId) }) {
                Text("${trace.traceId} · ${trace.failure?.stableCode} · ${trace.failure?.media3CodeName ?: "—"}")
            }
        }
    }
}

@Composable
private fun EventRow(event: PlaybackDiagnosticEvent) {
    var expanded by rememberSaveable(event.traceId, event.elapsedMs, event.type) { mutableStateOf(false) }
    val isAttentionEvent =
        event is PlaybackDiagnosticEvent.HttpStatus ||
            event is PlaybackDiagnosticEvent.RecoveryStart ||
            event is PlaybackDiagnosticEvent.RecoveryEnd ||
            event is PlaybackDiagnosticEvent.TerminalFailure

    Text(
        text = "${event.elapsedMs.toString().padStart(5)} ms  ${event.type}  ${event.toLogLine().substringAfter(event.type).trim()}",
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = if (isAttentionEvent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(vertical = 5.dp),
        maxLines = if (expanded) Int.MAX_VALUE else 2,
        overflow = if (expanded) TextOverflow.Clip else TextOverflow.Ellipsis,
    )
}

@Composable
private fun ChaosCard(
    chaos: DebugChaosController,
    delayMs: String,
    offlineMs: String,
    onDelayChange: (String) -> Unit,
    onOfflineChange: (String) -> Unit,
) {
    InfoCard {
        Text("One-shot by default. Every pending fault is shown above as CHAOS ACTIVE.", style = MaterialTheme.typography.bodySmall)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(403, 404, 410, 429, 500, 503).forEach { status ->
                OutlinedButton(onClick = {
                    chaos.arm("Force HTTP $status", DebugFaultPoint.DATASOURCE_OPEN, DebugFaultSpec(DebugFaultSpec.Kind.HTTP_STATUS, httpStatus = status))
                }) { Text(status.toString(), fontSize = 10.sp) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(delayMs, onDelayChange, label = { Text("delay ms") }, modifier = Modifier.weight(1f), singleLine = true)
            Button(onClick = {
                chaos.arm("Delay player response", DebugFaultPoint.PLAYER_RESPONSE, DebugFaultSpec(DebugFaultSpec.Kind.DELAY, delayMs.toLongOrNull()?.coerceAtLeast(0L) ?: 500L))
            }) { Text("Delay resolution") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(offlineMs, onOfflineChange, label = { Text("offline ms") }, modifier = Modifier.weight(1f), singleLine = true)
            Button(onClick = {
                chaos.arm("Pretend offline", DebugFaultPoint.PLAYER_RESPONSE, DebugFaultSpec(DebugFaultSpec.Kind.OFFLINE, offlineMs.toLongOrNull()?.coerceAtLeast(0L) ?: 5_000L))
            }) { Text("Offline") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = { chaos.arm("Resolution timeout", DebugFaultPoint.PLAYER_RESPONSE, DebugFaultSpec(DebugFaultSpec.Kind.RESOLUTION_TIMEOUT, delayMs.toLongOrNull() ?: 5_000L)) }) { Text("Resolver timeout") }
            OutlinedButton(onClick = { chaos.arm("DataSource timeout", DebugFaultPoint.DATASOURCE_OPEN, DebugFaultSpec(DebugFaultSpec.Kind.DATASOURCE_TIMEOUT, delayMs.toLongOrNull() ?: 5_000L)) }) { Text("CDN timeout") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = { chaos.arm("Expire stream", DebugFaultPoint.PLAYER_RESPONSE, DebugFaultSpec(DebugFaultSpec.Kind.EXPIRE_STREAM)) }) { Text("Expire URL") }
            OutlinedButton(onClick = { chaos.arm("Invalidate extractor", DebugFaultPoint.PLAYER_RESPONSE, DebugFaultSpec(DebugFaultSpec.Kind.INVALIDATE_EXTRACTOR)) }) { Text("Extractor") }
            OutlinedButton(onClick = { chaos.arm("Signature failure", DebugFaultPoint.PLAYER_RESPONSE, DebugFaultSpec(DebugFaultSpec.Kind.SIGNATURE_FAILURE)) }) { Text("Signature") }
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutlinedButton(onClick = { chaos.arm("N transform failure", DebugFaultPoint.PLAYER_RESPONSE, DebugFaultSpec(DebugFaultSpec.Kind.N_TRANSFORM_FAILURE)) }) { Text("N transform") }
            OutlinedButton(onClick = { chaos.arm("PoToken failure", DebugFaultPoint.PLAYER_RESPONSE, DebugFaultSpec(DebugFaultSpec.Kind.POTOKEN_FAILURE)) }) { Text("PoToken") }
            OutlinedButton(onClick = { chaos.arm("Format failure", DebugFaultPoint.PLAYER_RESPONSE, DebugFaultSpec(DebugFaultSpec.Kind.FORMAT_FAILURE)) }) { Text("Format") }
        }
        TextButton(onClick = chaos::clear) { Text("Disarm all") }
    }
}

@Composable
private fun AppDeviceCard(context: Context) {
    InfoCard {
        KeyValue("version", BuildConfig.VERSION_NAME)
        KeyValue("versionCode", BuildConfig.VERSION_CODE)
        KeyValue("applicationId", BuildConfig.APPLICATION_ID)
        KeyValue("variant", "Universal${if (BuildConfig.CAST_AVAILABLE) "Gms" else "Foss"}Debug")
        KeyValue("BuildConfig.DEBUG", BuildConfig.DEBUG)
        KeyValue("CAST_AVAILABLE", BuildConfig.CAST_AVAILABLE)
        KeyValue("ARCHITECTURE", BuildConfig.ARCHITECTURE)
        KeyValue("RELEASE_IDENTIFIER", BuildConfig.RELEASE_IDENTIFIER)
        KeyValue("SOURCE_REVISION", BuildConfig.SOURCE_REVISION)
        KeyValue("device", "${Build.MANUFACTURER} ${Build.MODEL}")
        KeyValue("Android/API", "${Build.VERSION.RELEASE} / ${Build.VERSION.SDK_INT}")
        KeyValue("ABI", Build.SUPPORTED_ABIS.joinToString(","))
        KeyValue("process uptime", formatMs(android.os.SystemClock.uptimeMillis()))
        val memory = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo().also(memory::getMemoryInfo)
        KeyValue("available memory", "${info.availMem / (1024 * 1024)} MB")
    }
}

@Composable
private fun PreviousSessionCard(previous: PreviousDebugSession?, store: DebugSessionStore) {
    InfoCard {
        if (previous == null) {
            Text("N/A — no previous debug session persisted yet")
        } else {
            KeyValue("saved", previous.savedAtMs)
            KeyValue("events", previous.eventCount)
            KeyValue("traces", previous.traceCount)
            KeyValue("network records", previous.networkCount)
            Text("Only bounded, sanitized JSON is persisted asynchronously.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = store::clear) { Text("Clear previous session") }
        }
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            content = content,
        )
    }
}

@Composable
private fun KeyValue(key: String, value: Any?) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            key,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.42f),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            PlaybackRedactor.sanitizeScalar(value),
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f),
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HistogramRows(label: String, histogram: DebugHistogram) {
    KeyValue(label, histogramText(histogram))
}

private const val MAX_TIMELINE_EVENTS = 120

private fun histogramText(histogram: DebugHistogram): String =
    "n=${histogram.count} p50=${histogram.p50Ms ?: "N/A"} p90=${histogram.p90Ms ?: "N/A"} " +
        "p95=${histogram.p95Ms ?: "N/A"} p99=${histogram.p99Ms ?: "N/A"} max=${histogram.maxMs ?: "N/A"}"

private fun percent(value: Double): String = "%.1f%%".format(Locale.US, value * 100.0)

private fun formatMs(value: Long): String = "${value}ms"

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

private fun shareFile(context: Context, file: java.io.File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.FileProvider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/zip"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Export Auriqo debug bundle"))
}

private fun formatTrace(trace: DebugTraceSnapshot, networkRecords: List<NetworkDebugRequest>): String = buildString {
    appendLine("Auriqo Playback Diagnostic")
    appendLine("Trace: ${trace.traceId}")
    appendLine("Media: ${PlaybackRedactor.sanitizeScalar(trace.mediaId)}")
    appendLine("classification=${trace.classification}")
    appendLine("tapToFirstAudio=${trace.tapToFirstAudioMs ?: "N/A"}ms")
    appendLine("resolution=${trace.resolutionMs ?: "N/A"}ms")
    appendLine("playerResponse=${trace.playerResponseMs ?: "N/A"}ms")
    appendLine("datasourceOpen=${trace.dataSourceMs ?: "N/A"}ms")
    appendLine("recovery=${trace.recoveryMs ?: "N/A"}ms")
    appendLine("dominantStage=${trace.dominantStage ?: "N/A"}")
    appendLine("slowTrace=${trace.slow}")
    appendLine("cache=${trace.cacheState}")
    appendLine("itag=${trace.itag ?: "N/A"} mime=${trace.mimeType ?: "N/A"} bitrate=${trace.bitrate ?: "N/A"}")
    appendLine("Timeline:")
    trace.events.forEach { appendLine(it.toLogLine()) }
    trace.failure?.let { failure ->
        appendLine("Failure:")
        appendLine("code=${failure.stableCode}")
        appendLine("stage=${failure.stage}")
        appendLine("media3=${failure.media3CodeName}(${failure.media3Code})")
        appendLine("http=${failure.httpStatus ?: "N/A"}")
        appendLine("playability=${failure.playabilityStatus ?: "N/A"}")
        appendLine("humanMessage=${PlaybackRedactor.sanitizeScalar(failure.humanMessage)}")
        appendLine("technicalMessage=${PlaybackRedactor.sanitizeScalar(failure.technicalMessage)}")
        failure.recoveryActions.forEach { action ->
            appendLine("recoveryAction=${PlaybackRedactor.sanitizeScalar(action.action)} result=${PlaybackRedactor.sanitizeScalar(action.result)}")
        }
        appendLine("causeChain=${failure.causeChain.joinToString(" <- ") { it.className + ":" + it.message }}")
    }
    appendLine("Network:")
    networkRecords.filter { it.traceId == trace.traceId }.forEach { record ->
        appendLine("${record.method} host=${record.host} status=${record.status ?: "N/A"} durationMs=${record.durationMs ?: "N/A"} dnsMs=${record.dnsMs ?: "N/A"} connectMs=${record.connectMs ?: "N/A"} tlsMs=${record.tlsMs ?: "N/A"} protocol=${record.protocol ?: "N/A"}")
    }
}

private data class DebugRuntimeSnapshot(
    val traceId: String?,
    val mediaId: String?,
    val playbackGeneration: Long,
    val resolutionGeneration: Long?,
    val queueIndex: Int,
    val playbackState: String,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val suppressionReason: String,
    val positionMs: Long,
    val bufferedPositionMs: Long,
    val bufferedPercent: Int,
    val durationMs: Long,
    val repeatMode: String,
    val shuffle: Boolean,
    val formatSummary: String,
    val network: String,
    val streamCacheState: String,
    val streamExpiryMs: Long?,
    val preloadedBytes: Int,
    val playerCacheEntries: Int,
    val downloadCacheEntries: Int,
    val lastFailure: String,
    val audioFocus: String,
    val lastFocusEvent: String,
    val resumeOnGain: Boolean,
    val outputDevice: String,
    val volume: String,
    val muted: Boolean,
    val playerVolume: String,
    val audioSessionId: Int,
    val queueSize: Int,
    val queueItems: List<String>,
) {
    fun toExportContext(): DebugExportContext = DebugExportContext(
        media3 = "{\"state\":${json(playbackState)},\"isPlaying\":$isPlaying,\"playWhenReady\":$playWhenReady,\"positionMs\":$positionMs,\"bufferedPositionMs\":$bufferedPositionMs,\"format\":${json(formatSummary)}}",
        audio = "{\"focus\":${json(audioFocus)},\"lastEvent\":${json(lastFocusEvent)},\"output\":${json(outputDevice)},\"volume\":${json(volume)},\"muted\":$muted,\"audioSessionId\":$audioSessionId}",
        queue = "{\"size\":$queueSize,\"index\":$queueIndex,\"shuffle\":$shuffle,\"repeat\":${json(repeatMode)},\"items\":${queueItems.joinToString(prefix = "[", postfix = "]") { json(it) }}}",
        innertube = "{\"locale\":${json("${YouTube.locale.gl}-${YouTube.locale.hl}")},\"proxyEnabled\":${YouTube.proxy != null},\"loginBrowse\":${YouTube.useLoginForBrowse},\"visitorDataPresent\":${YouTube.visitorData != null},\"cookiePresent\":${YouTube.cookie != null}}",
        summary = "liveTrace=$traceId\nclassification=${if (streamCacheState == "HIT") "HOT" else "COLD"}\nmediaId=${PlaybackRedactor.sanitizeScalar(mediaId)}\n",
    )
}

private fun runtimeSnapshot(context: Context, connection: PlayerConnection, trace: DebugTraceSnapshot?): DebugRuntimeSnapshot? =
    runCatching {
        val player = connection.player
        val service = connection.service
        val mediaId = player.currentMediaItem?.mediaId
        val cache = service.debugStreamSnapshot()
        val entry = cache.entries.lastOrNull { it.mediaId == mediaId }
        val format = player.currentTracks.groups.firstOrNull { group -> group.type == androidx.media3.common.C.TRACK_TYPE_AUDIO && group.isSelected }
            ?.getTrackFormat(0)
        val audioFocus = service.debugAudioFocusSnapshot()
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .joinToString(",") { it.productName?.toString().orEmpty().ifBlank { it.type.toString() } }
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork)
        val network = when {
            capabilities == null -> "offline"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi${if (connectivity.isActiveNetworkMetered) "/metered" else "/unmetered"}"
            capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile${if (connectivity.isActiveNetworkMetered) "/metered" else "/unmetered"}"
            else -> "other"
        }
        val queueItems = (0 until player.mediaItemCount).map { index ->
            val item = player.getMediaItemAt(index)
            val distance = index - player.currentMediaItemIndex
            val lookahead = when {
                distance == 0 -> "CURRENT"
                distance in 1..3 -> {
                    val warmed = runCatching { service.playerCache.isCached(item.mediaId, 0L, 64 * 1024L) }.getOrDefault(false)
                    val resolved = cache.entries.any { entry -> entry.mediaId == item.mediaId && entry.expiresInMs > 0L }
                    val state = when {
                        warmed -> "BYTES_WARMED"
                        resolved -> "RESOLVED"
                        else -> "MISS"
                    }
                    "N+$distance $state"
                }
                else -> ""
            }
            "$lookahead $index ${item.mediaId} ${item.mediaMetadata.title ?: ""}".trim()
        }
        DebugRuntimeSnapshot(
            traceId = trace?.traceId,
            mediaId = mediaId,
            playbackGeneration = service.debugPlaybackGeneration(),
            resolutionGeneration = mediaId?.let { cache.resolutionGenerations[it] },
            queueIndex = player.currentMediaItemIndex,
            playbackState = playerStateName(player.playbackState),
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
            suppressionReason = player.playbackSuppressionReason.toString(),
            positionMs = player.currentPosition,
            bufferedPositionMs = player.bufferedPosition,
            bufferedPercent = player.bufferedPercentage,
            durationMs = player.duration,
            repeatMode = player.repeatMode.toString(),
            shuffle = player.shuffleModeEnabled,
            formatSummary = format?.let { formatSummary(it) } ?: "N/A",
            network = network,
            streamCacheState = when {
                entry == null -> "MISS"
                entry.expiresInMs <= 0L -> "EXPIRED"
                else -> "HIT"
            },
            streamExpiryMs = entry?.expiresInMs,
            preloadedBytes = runCatching { if (mediaId != null && service.playerCache.isCached(mediaId, 0L, 64 * 1024L)) 64 * 1024 else 0 }.getOrDefault(0),
            playerCacheEntries = runCatching { service.playerCache.keys.size }.getOrDefault(0),
            downloadCacheEntries = runCatching { service.downloadCache.keys.size }.getOrDefault(0),
            lastFailure = trace?.failure?.stableCode ?: "none",
            audioFocus = audioFocus?.hasFocus?.toString() ?: "N/A",
            lastFocusEvent = audioFocus?.lastEvent ?: "N/A",
            resumeOnGain = audioFocus?.resumeOnGain ?: false,
            outputDevice = outputs,
            volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toString(),
            muted = connection.isMuted.value,
            playerVolume = player.volume.toString(),
            audioSessionId = player.audioSessionId,
            queueSize = player.mediaItemCount,
            queueItems = queueItems,
        )
    }.getOrNull()

private fun playerStateName(state: Int): String = when (state) {
    Player.STATE_IDLE -> "IDLE"
    Player.STATE_BUFFERING -> "BUFFERING"
    Player.STATE_READY -> "READY"
    Player.STATE_ENDED -> "ENDED"
    else -> state.toString()
}

private fun formatSummary(format: Format): String =
    "mime=${format.sampleMimeType ?: format.containerMimeType ?: "?"} codec=${format.codecs ?: "?"} " +
        "${format.sampleRate}Hz/${format.channelCount}ch bitrate=${format.bitrate}"

private fun json(value: String?): String = value?.replace("\\", "\\\\")?.replace("\"", "\\\"")?.let { "\"$it\"" } ?: "null"
