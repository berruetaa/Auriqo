package com.auriqo.music.utils.debug

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

class DebugLogTree : Timber.Tree() {
    data class LogEntry(
        val id: Long = nextId++,
        val timestamp: Long = System.currentTimeMillis(),
        val level: Int,
        val tag: String?,
        val message: String,
        val throwable: Throwable? = null,
    ) {
        val levelStr: String get() = when (level) {
            Log.VERBOSE -> "V"
            Log.DEBUG -> "D"
            Log.INFO -> "I"
            Log.WARN -> "W"
            Log.ERROR -> "E"
            Log.ASSERT -> "WTF"
            else -> "?"
        }

        val formattedTime: String
            get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))

        val fullMessage: String get() = buildString {
            append(message)
            throwable?.let {
                append('\n')
                append(it.stackTraceToString())
            }
        }
    }

    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()
    private val buffer = CopyOnWriteArrayList<LogEntry>()
    private val maxEntries = 500

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        buffer.add(LogEntry(level = priority, tag = tag, message = message, throwable = t))
        if (buffer.size > maxEntries) buffer.removeAt(0)
        _logs.value = buffer.toList()
    }

    fun clear() {
        buffer.clear()
        _logs.value = emptyList()
    }

    companion object {
        @Volatile private var nextId: Long = 0L
        private var instance: DebugLogTree? = null

        fun install(): DebugLogTree = DebugLogTree().also { tree ->
            Timber.plant(tree)
            instance = tree
        }

        fun getInstance(): DebugLogTree? = instance
    }
}
