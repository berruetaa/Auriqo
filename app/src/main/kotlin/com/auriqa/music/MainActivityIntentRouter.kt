package com.auriqo.music

/**
 * Pure classifier for intents entering MainActivity.
 *
 * MainActivity currently receives the same intents through onNewIntent, pending-intent replay and
 * the Activity Compose listener. Keeping classification here gives those entry points one contract
 * and lets navigation side effects remain in the Activity.
 */
object MainActivityIntentRouter {
    const val ACTION_VIEW = "android.intent.action.VIEW"
    const val ACTION_SEND = "android.intent.action.SEND"
    const val ACTION_MEDIA_PLAY_FROM_SEARCH = "android.media.action.MEDIA_PLAY_FROM_SEARCH"
    const val ACTION_SEARCH = "com.auriqo.music.action.SEARCH"
    const val ACTION_LIBRARY = "com.auriqo.music.action.LIBRARY"

    data class Input(
        val action: String?,
        val dataUri: String? = null,
        val sharedText: String? = null,
        val recognitionAutoStart: Boolean = false,
        val assistantQuery: String? = null,
    )

    sealed interface Route {
        data object Update : Route
        data class Recognition(val autoStart: Boolean) : Route
        data class Search(val query: String) : Route
        data class OpenDeepLink(val uri: String) : Route
        data object OpenSearch : Route
        data object OpenLibrary : Route
        data object None : Route
    }

    fun route(
        input: Input,
        updateAction: String,
        recognitionAction: String,
    ): Route = when (input.action) {
        updateAction -> Route.Update
        recognitionAction -> Route.Recognition(input.recognitionAutoStart)
        ACTION_MEDIA_PLAY_FROM_SEARCH -> input.assistantQuery
            ?.takeIf { it.isNotBlank() }
            ?.trim()
            ?.let(Route::Search)
            ?: Route.None
        ACTION_VIEW -> input.dataUri
            ?.takeIf { it.isNotBlank() }
            ?.let(Route::OpenDeepLink)
            ?: Route.None
        ACTION_SEND -> extractSharedUrl(input.sharedText)
            ?.let(Route::OpenDeepLink)
            ?: Route.None
        ACTION_SEARCH -> Route.OpenSearch
        ACTION_LIBRARY -> Route.OpenLibrary
        else -> Route.None
    }

    fun extractSharedUrl(text: String?): String? = text
        ?.let(URL_PATTERN::find)
        ?.value
        ?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
        ?.takeIf { it.isNotBlank() }

    private val URL_PATTERN = Regex("https?://[^\\s]+")
}
