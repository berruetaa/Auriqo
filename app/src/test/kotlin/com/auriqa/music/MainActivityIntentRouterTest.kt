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
            route(MainActivityIntentRouter.Input(action = updateAction, hasData = true)),
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
            MainActivityIntentRouter.Route.AssistantSearch("Daft Punk"),
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
            MainActivityIntentRouter.Route.DeepLink,
            route(MainActivityIntentRouter.Input(action = MainActivityIntentRouter.ACTION_VIEW, hasData = true)),
        )
        assertEquals(
            MainActivityIntentRouter.Route.DeepLink,
            route(
                MainActivityIntentRouter.Input(
                    action = MainActivityIntentRouter.ACTION_SEND,
                    sharedText = "https://music.youtube.com/watch?v=test",
                ),
            ),
        )
        assertEquals(
            MainActivityIntentRouter.Route.None,
            route(MainActivityIntentRouter.Input(action = MainActivityIntentRouter.ACTION_SEND)),
        )
    }

    private fun route(input: MainActivityIntentRouter.Input): MainActivityIntentRouter.Route =
        MainActivityIntentRouter.route(
            input = input,
            updateAction = updateAction,
            recognitionAction = recognitionAction,
        )
}
