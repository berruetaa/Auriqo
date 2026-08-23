package com.auriqo.music.playback

/**
 * Pure audio-focus decision engine.
 *
 * Android's AudioManager remains an I/O boundary owned by MusicService/AudioFocusController. This
 * class owns only state transitions so focus behavior can be regression-tested without a service,
 * player, device, or framework runtime.
 */
class AudioFocusStateMachine(
    initialState: State = State(),
) {
    data class State(
        val hasFocus: Boolean = false,
        val resumeOnGain: Boolean = false,
        val lastEvent: Event? = null,
    )

    enum class Event {
        GAIN,
        GAIN_TRANSIENT,
        GAIN_TRANSIENT_MAY_DUCK,
        LOSS,
        LOSS_TRANSIENT,
        LOSS_TRANSIENT_CAN_DUCK,
    }

    enum class Action {
        PAUSE,
        RESUME,
        DUCK,
        RESTORE_VOLUME,
        ABANDON_FOCUS,
    }

    data class Transition(
        val state: State,
        val actions: List<Action>,
    )

    var state: State = initialState
        private set

    fun onEvent(event: Event, isPlaying: Boolean): Transition {
        val transition = when (event) {
            Event.GAIN,
            Event.GAIN_TRANSIENT -> {
                val shouldResume = state.resumeOnGain && !isPlaying
                Transition(
                    state = State(
                        hasFocus = true,
                        resumeOnGain = false,
                        lastEvent = event,
                    ),
                    actions = buildList {
                        if (shouldResume) add(Action.RESUME)
                        add(Action.RESTORE_VOLUME)
                    },
                )
            }

            Event.GAIN_TRANSIENT_MAY_DUCK -> Transition(
                state = state.copy(hasFocus = true, lastEvent = event),
                actions = listOf(Action.RESTORE_VOLUME),
            )

            Event.LOSS -> Transition(
                state = State(
                    hasFocus = false,
                    resumeOnGain = false,
                    lastEvent = event,
                ),
                actions = buildList {
                    if (isPlaying) add(Action.PAUSE)
                    add(Action.ABANDON_FOCUS)
                },
            )

            Event.LOSS_TRANSIENT -> Transition(
                state = State(
                    hasFocus = false,
                    resumeOnGain = isPlaying,
                    lastEvent = event,
                ),
                actions = if (isPlaying) listOf(Action.PAUSE) else emptyList(),
            )

            Event.LOSS_TRANSIENT_CAN_DUCK -> Transition(
                state = state.copy(
                    hasFocus = true,
                    resumeOnGain = false,
                    lastEvent = event,
                ),
                actions = if (isPlaying) listOf(Action.DUCK) else emptyList(),
            )
        }

        state = transition.state
        return transition
    }

    fun onFocusGranted() {
        state = state.copy(hasFocus = true)
    }

    fun onFocusAbandoned() {
        state = state.copy(hasFocus = false)
    }
}
