package com.auriqo.music.debug

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.io.File

data class PreviousDebugSession(
    val savedAtMs: Long,
    val file: File,
    val eventCount: Int,
    val traceCount: Int,
    val networkCount: Int,
)

/** Batches a small sanitized snapshot; it never writes once per diagnostic event. */
@OptIn(FlowPreview::class)
class DebugSessionStore(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val _previous = MutableStateFlow<PreviousDebugSession?>(null)
    val previous: StateFlow<PreviousDebugSession?> = _previous.asStateFlow()
    private var file: File? = null
    private var collector: PlaybackDebugCollector? = null
    private var network: DebugNetworkCollector? = null

    fun initialize(
        context: Context,
        collector: PlaybackDebugCollector,
        network: DebugNetworkCollector,
    ) {
        if (file != null) return
        this.file = context.filesDir.resolve(FILE_NAME)
        this.collector = collector
        this.network = network
        scope.launch { loadPrevious() }
        scope.launch {
            collector.state.debounce(1_500L).collect {
                persist()
            }
        }
    }

    fun persistNow() {
        scope.launch { persist() }
    }

    fun clear() {
        val target = file ?: return
        scope.launch {
            runCatching { target.delete() }
            _previous.value = null
        }
    }

    private fun loadPrevious() {
        val target = file ?: return
        if (!target.exists()) return
        val text = runCatching { target.readText() }.getOrNull() ?: return
        _previous.value = PreviousDebugSession(
            savedAtMs = Regex("\\\"savedAtMs\\\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toLongOrNull()
                ?: target.lastModified(),
            file = target,
            eventCount = Regex("\\\"eventCount\\\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0,
            traceCount = Regex("\\\"traceCount\\\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0,
            networkCount = Regex("\\\"networkCount\\\"\\s*:\\s*(\\d+)").find(text)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0,
        )
    }

    private fun persist() {
        val target = file ?: return
        val state = collector?.state?.value ?: return
        val networkRecords = network?.records?.value.orEmpty().takeLast(50)
        val text = DebugBundleExporter.sessionSnapshotJson(state, networkRecords)
        val temporary = File(target.parentFile, "${target.name}.tmp")
        runCatching {
            temporary.writeText(text)
            if (!temporary.renameTo(target)) {
                target.delete()
                temporary.renameTo(target)
            }
            _previous.value = PreviousDebugSession(
                savedAtMs = System.currentTimeMillis(),
                file = target,
                eventCount = state.events.takeLast(1_000).size,
                traceCount = state.traces.size,
                networkCount = networkRecords.size,
            )
        }
    }

    private companion object {
        const val FILE_NAME = "debug_previous_session.json"
    }
}
