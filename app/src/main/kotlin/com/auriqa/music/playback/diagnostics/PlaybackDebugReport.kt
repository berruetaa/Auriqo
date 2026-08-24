package com.auriqo.music.playback.diagnostics

/** Values supplied by the UI/service; the formatter itself never reads secrets or URLs. */
data class PlaybackDebugReportContext(
    val appVersion: String,
    val sourceRevision: String,
    val manufacturer: String,
    val model: String,
    val androidVersion: String,
    val api: Int,
    val abi: String,
    val quality: String? = null,
    val localOrRemote: String? = null,
    val queueIndex: Int? = null,
    val networkType: String? = null,
    val proxyEnabled: Boolean? = null,
)

object PlaybackDebugReportFormatter {
    fun format(
        failure: PlaybackFailure,
        context: PlaybackDebugReportContext,
        events: List<PlaybackDiagnosticEvent> = PlaybackDiagnostics.events(failure.traceId),
    ): String = buildString {
        appendLine("Auriqo Playback Diagnostic")
        appendLine()
        appendLine("Trace ID: ${failure.traceId}")
        appendLine("App: ${PlaybackRedactor.sanitizeScalar(context.appVersion)}")
        appendLine("Commit: ${PlaybackRedactor.sanitizeScalar(context.sourceRevision)}")
        appendLine()
        appendLine("Device:")
        appendLine("manufacturer=${PlaybackRedactor.sanitizeScalar(context.manufacturer)}")
        appendLine("model=${PlaybackRedactor.sanitizeScalar(context.model)}")
        appendLine("android=${PlaybackRedactor.sanitizeScalar(context.androidVersion)}")
        appendLine("api=${context.api}")
        appendLine("abi=${PlaybackRedactor.sanitizeScalar(context.abi)}")
        appendLine()
        appendLine("Media:")
        appendLine("mediaId=${PlaybackRedactor.sanitizeScalar(failure.mediaId)}")
        appendLine("quality=${PlaybackRedactor.sanitizeScalar(context.quality ?: failure.quality)}")
        appendLine("localOrRemote=${PlaybackRedactor.sanitizeScalar(context.localOrRemote)}")
        appendLine("queueIndex=${context.queueIndex ?: failure.queueIndex ?: "unknown"}")
        appendLine()
        appendLine("Network:")
        appendLine("type=${PlaybackRedactor.sanitizeScalar(context.networkType ?: failure.networkType)}")
        appendLine("proxyEnabled=${context.proxyEnabled ?: "unknown"}")
        appendLine()
        appendLine("Timing:")
        appendLine("tapToFirstAudioMs=${events.filterIsInstance<PlaybackDiagnosticEvent.FirstAudio>().firstOrNull()?.elapsedMs ?: "unknown"}")
        appendLine("resolutionMs=${resolutionDuration(events) ?: "unknown"}")
        appendLine("datasourceOpenMs=${events.filterIsInstance<PlaybackDiagnosticEvent.DataSourceOpenEnd>().lastOrNull()?.durationMs ?: "unknown"}")
        appendLine("firstFailureMs=${events.filterIsInstance<PlaybackDiagnosticEvent.HttpStatus>().firstOrNull()?.elapsedMs ?: failure.elapsedMs}")
        appendLine("recoveryMs=${events.filterIsInstance<PlaybackDiagnosticEvent.RecoveryEnd>().lastOrNull()?.durationMs ?: "unknown"}")
        appendLine("totalMs=${failure.elapsedMs}")
        appendLine()
        appendLine("Failure:")
        appendLine("stage=${failure.stage}")
        appendLine("category=${failure.category}")
        appendLine("auriQoCode=${failure.stableCode}")
        appendLine("media3Code=${failure.media3CodeName ?: "unknown"} (${failure.media3Code ?: "unknown"})")
        appendLine("httpStatus=${failure.httpStatus ?: "unknown"}")
        appendLine("httpMessage=${PlaybackRedactor.sanitizeScalar(failure.http?.responseMessage)}")
        appendLine("httpHost=${PlaybackRedactor.sanitizeScalar(failure.http?.host)}")
        appendLine("httpContentType=${PlaybackRedactor.sanitizeScalar(failure.http?.contentType)}")
        appendLine("httpRange=${PlaybackRedactor.sanitizeScalar(failure.http?.range)}")
        appendLine("httpQueryKeys=${failure.http?.queryKeys ?: "unknown"}")
        appendLine("httpExpireEpoch=${failure.http?.expireEpoch ?: "unknown"}")
        appendLine("playabilityStatus=${PlaybackRedactor.sanitizeScalar(failure.playabilityStatus)}")
        appendLine("terminal=${failure.terminal}")
        appendLine()
        appendLine("Recovery:")
        appendLine("attempt=${failure.attempt}/${failure.maxAttempts}")
        if (failure.recoveryActions.isEmpty()) {
            appendLine("actions=none")
        } else {
            failure.recoveryActions.forEach { action ->
                appendLine(
                    "action=${PlaybackRedactor.sanitizeScalar(action.action)} " +
                        "result=${PlaybackRedactor.sanitizeScalar(action.result)} " +
                        "attempt=${action.attempt ?: "unknown"} elapsedMs=${action.elapsedMs ?: "unknown"}",
                )
            }
        }
        appendLine()
        appendLine("Cause chain:")
        if (failure.causeChain.isEmpty()) {
            appendLine("none")
        } else {
            failure.causeChain.forEachIndexed { index, cause ->
                appendLine(
                    "${"  ".repeat(index)}↳ ${PlaybackRedactor.sanitizeScalar(cause.className)}: " +
                        PlaybackRedactor.sanitizeScalar(cause.message),
                )
                cause.relevantFields.forEach { (name, value) ->
                    appendLine("${"  ".repeat(index + 1)}$name=${PlaybackRedactor.sanitizeScalar(value)}")
                }
            }
        }
        appendLine()
        appendLine("Recent breadcrumbs:")
        events.takeLast(40).forEach { appendLine(it.toLogLine()) }
    }

    private fun resolutionDuration(events: List<PlaybackDiagnosticEvent>): Long? {
        val start = events.filterIsInstance<PlaybackDiagnosticEvent.ResolutionRequested>().firstOrNull()?.elapsedMs
            ?: return null
        val end = events.filterIsInstance<PlaybackDiagnosticEvent.StreamSelected>().lastOrNull()?.elapsedMs
            ?: return null
        return (end - start).coerceAtLeast(0L)
    }
}
