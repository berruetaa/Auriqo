package com.auriqo.music

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityIntentRouterTest {
    private val updateAction = "com.auriqo.music.action.OPEN_UPDATE"
    private val recognitionAction = "com.auriqo.music.action.RECOGNITION"

    @Test
    fun `update action has highest priority`() {
        assertEquals(
            MainActivityIntentRouter.Route.Update,
            route(
                MainActivityIntentRouter.Input(
                    action = updateAction,
                    dataUri = "https://example.invalid/update",
                ),
            ),
        )
    }

    @Test
    fun `recognition preserves auto start flag`() {
        assertEquals(
            MainActivityIntentRouter.Route.Recognition(autoStart = true),
            route(
                MainActivityIntentRouter.Input(
                    action = recognitionAction,
                    recognitionAutoStart = true,
                ),
            ),
        )
    }

    @Test
    fun `assistant search requires a non blank query`() {
        assertEquals(
            MainActivityIntentRouter.Route.Search("Daft Punk"),
            route(
                MainActivityIntentRouter.Input(
                    action = MainActivityIntentRouter.ACTION_MEDIA_PLAY_FROM_SEARCH,
                    assistantQuery = "Daft Punk",
                ),
            ),
        )
        assertEquals(
            MainActivityIntentRouter.Route.None,
            route(
                MainActivityIntentRouter.Input(
                    action = MainActivityIntentRouter.ACTION_MEDIA_PLAY_FROM_SEARCH,
                    assistantQuery = " ",
                ),
            ),
        )
    }

    @Test
    fun `view and send require deep link payload`() {
        assertEquals(
            MainActivityIntentRouter.Route.OpenDeepLink("https://music.youtube.com/watch?v=test"),
            route(
                MainActivityIntentRouter.Input(
                    action = MainActivityIntentRouter.ACTION_VIEW,
                    dataUri = "https://music.youtube.com/watch?v=test",
                ),
            ),
        )
        assertEquals(
            MainActivityIntentRouter.Route.OpenDeepLink("https://music.youtube.com/watch?v=test"),
            route(
                MainActivityIntentRouter.Input(
                    action = MainActivityIntentRouter.ACTION_SEND,
                    sharedText = "Listen: https://music.youtube.com/watch?v=test.",
                ),
            ),
        )
        assertEquals(
            MainActivityIntentRouter.Route.None,
            route(MainActivityIntentRouter.Input(action = MainActivityIntentRouter.ACTION_SEND)),
        )
        assertEquals(
            MainActivityIntentRouter.Route.None,
            route(
                MainActivityIntentRouter.Input(
                    action = MainActivityIntentRouter.ACTION_SEND,
                    sharedText = "This message has no URL",
                ),
            ),
        )
    }

    @Test
    fun `null view uri is ignored`() {
        assertEquals(
            MainActivityIntentRouter.Route.None,
            route(MainActivityIntentRouter.Input(action = MainActivityIntentRouter.ACTION_VIEW)),
        )
    }

    @Test
    fun `shortcuts open their corresponding tab`() {
        assertEquals(
            MainActivityIntentRouter.Route.OpenSearch,
            route(MainActivityIntentRouter.Input(action = MainActivityIntentRouter.ACTION_SEARCH)),
        )
        assertEquals(
            MainActivityIntentRouter.Route.OpenLibrary,
            route(MainActivityIntentRouter.Input(action = MainActivityIntentRouter.ACTION_LIBRARY)),
        )
    }

    private fun route(input: MainActivityIntentRouter.Input): MainActivityIntentRouter.Route =
        MainActivityIntentRouter.route(
            input = input,
            updateAction = updateAction,
            recognitionAction = recognitionAction,
        )
}
