package com.auriqo.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioFocusStateMachineTest {
    @Test
    fun `transient loss pauses and gain resumes`() {
        val machine = AudioFocusStateMachine()

        val loss = machine.onEvent(
            AudioFocusStateMachine.Event.LOSS_TRANSIENT,
            isPlaying = true,
        )
        assertEquals(listOf(AudioFocusStateMachine.Action.PAUSE), loss.actions)
        assertFalse(loss.state.hasFocus)
        assertTrue(loss.state.resumeOnGain)

        val gain = machine.onEvent(
            AudioFocusStateMachine.Event.GAIN,
            isPlaying = false,
        )
        assertEquals(
            listOf(
                AudioFocusStateMachine.Action.RESUME,
                AudioFocusStateMachine.Action.RESTORE_VOLUME,
            ),
            gain.actions,
        )
        assertTrue(gain.state.hasFocus)
        assertFalse(gain.state.resumeOnGain)
    }

    @Test
    fun `duck loss does not pause playback`() {
        val machine = AudioFocusStateMachine()

        val transition = machine.onEvent(
            AudioFocusStateMachine.Event.LOSS_TRANSIENT_CAN_DUCK,
            isPlaying = true,
        )

        assertEquals(listOf(AudioFocusStateMachine.Action.DUCK), transition.actions)
        assertTrue(transition.state.hasFocus)
        assertFalse(transition.state.resumeOnGain)
    }

    @Test
    fun `permanent loss pauses and abandons focus`() {
        val machine = AudioFocusStateMachine(initialState = AudioFocusStateMachine.State(hasFocus = true))

        val transition = machine.onEvent(
            AudioFocusStateMachine.Event.LOSS,
            isPlaying = true,
        )

        assertEquals(
            listOf(
                AudioFocusStateMachine.Action.PAUSE,
                AudioFocusStateMachine.Action.ABANDON_FOCUS,
            ),
            transition.actions,
        )
        assertFalse(transition.state.hasFocus)
        assertFalse(transition.state.resumeOnGain)
    }

    @Test
    fun `gain while already playing only restores volume and clears pending resume`() {
        val machine = AudioFocusStateMachine(
            initialState = AudioFocusStateMachine.State(resumeOnGain = true),
        )

        val transition = machine.onEvent(
            AudioFocusStateMachine.Event.GAIN_TRANSIENT,
            isPlaying = true,
        )

        assertEquals(listOf(AudioFocusStateMachine.Action.RESTORE_VOLUME), transition.actions)
        assertFalse(transition.state.resumeOnGain)
    }

    @Test
    fun `gain after permanent loss never resumes`() {
        val machine = AudioFocusStateMachine()

        machine.onEvent(AudioFocusStateMachine.Event.LOSS, isPlaying = true)

        val gain = machine.onEvent(AudioFocusStateMachine.Event.GAIN, isPlaying = false)

        assertEquals(listOf(AudioFocusStateMachine.Action.RESTORE_VOLUME), gain.actions)
    }

    @Test
    fun `loss while paused does not create a resume action`() {
        val machine = AudioFocusStateMachine()

        val loss = machine.onEvent(
            AudioFocusStateMachine.Event.LOSS_TRANSIENT,
            isPlaying = false,
        )
        val gain = machine.onEvent(AudioFocusStateMachine.Event.GAIN, isPlaying = false)

        assertTrue(loss.actions.isEmpty())
        assertEquals(listOf(AudioFocusStateMachine.Action.RESTORE_VOLUME), gain.actions)
    }

    @Test
    fun `repeated gain emits resume only once`() {
        val machine = AudioFocusStateMachine()

        machine.onEvent(AudioFocusStateMachine.Event.LOSS_TRANSIENT, isPlaying = true)
        val firstGain = machine.onEvent(AudioFocusStateMachine.Event.GAIN, isPlaying = false)
        val secondGain = machine.onEvent(AudioFocusStateMachine.Event.GAIN, isPlaying = false)

        assertEquals(
            listOf(
                AudioFocusStateMachine.Action.RESUME,
                AudioFocusStateMachine.Action.RESTORE_VOLUME,
            ),
            firstGain.actions,
        )
        assertEquals(listOf(AudioFocusStateMachine.Action.RESTORE_VOLUME), secondGain.actions)
    }
}
