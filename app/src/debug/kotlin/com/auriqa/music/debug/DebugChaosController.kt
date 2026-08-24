package com.auriqo.music.debug

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

data class PendingDebugFault(
    val id: Long,
    val label: String,
    val point: DebugFaultPoint,
    val spec: DebugFaultSpec,
    val armedAtMs: Long,
)

/** One-shot fault queue. Consumption is synchronized so recovery cannot consume a second fault. */
class DebugChaosController {
    private val lock = Any()
    private val nextId = AtomicLong(0L)
    private val pendingFaults = ArrayDeque<PendingDebugFault>()
    private val _pending = MutableStateFlow<List<PendingDebugFault>>(emptyList())
    val pending: StateFlow<List<PendingDebugFault>> = _pending.asStateFlow()

    fun arm(
        label: String,
        point: DebugFaultPoint,
        spec: DebugFaultSpec,
    ): PendingDebugFault = synchronized(lock) {
        val fault = PendingDebugFault(
            id = nextId.incrementAndGet(),
            label = label,
            point = point,
            spec = spec,
            armedAtMs = System.currentTimeMillis(),
        )
        pendingFaults.addLast(fault)
        publishLocked()
        fault
    }

    fun consume(point: DebugFaultPoint): DebugFaultSpec? = synchronized(lock) {
        val index = pendingFaults.indexOfFirst { it.point == point }
        if (index < 0) return@synchronized null
        val fault = pendingFaults.removeAt(index)
        publishLocked()
        fault.spec
    }

    fun clear() = synchronized(lock) {
        pendingFaults.clear()
        publishLocked()
    }

    private fun publishLocked() {
        _pending.value = pendingFaults.toList()
    }
}
