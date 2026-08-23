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

    data class Input(
        val action: String?,
        val hasData: Boolean = false,
        val sharedText: String? = null,
        val recognitionAutoStart: Boolean = false,
        val assistantQuery: String? = null,
    )

    sealed interface Route {
        data object Update : Route
        data class Recognition(val autoStart: Boolean) : Route
        data class AssistantSearch(val query: String) : Route
        data object DeepLink : Route
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
            ?.let(Route::AssistantSearch)
            ?: Route.None
        ACTION_VIEW -> if (input.hasData) Route.DeepLink else Route.None
        ACTION_SEND -> if (!input.sharedText.isNullOrBlank()) Route.DeepLink else Route.None
        else -> Route.None
    }
}
