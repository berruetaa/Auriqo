package com.auriqo.music.playback

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioManager.AUDIOFOCUS_GAIN
import android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
import android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
import android.media.AudioManager.AUDIOFOCUS_LOSS
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
import android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK
import android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
import android.media.AudioManager.OnAudioFocusChangeListener
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Android boundary for audio focus.
 *
 * [AudioFocusStateMachine] decides what a focus event means. This class owns the AudioManager
 * request and translates the resulting actions into player callbacks supplied by MusicService.
 */
@RequiresApi(26)
class AudioFocusController(
    context: Context,
    private val scope: CoroutineScope,
    private val isPlaying: () -> Boolean,
    private val isMuted: () -> Boolean,
    private val volume: () -> Float,
    private val pause: () -> Unit,
    private val resume: () -> Unit,
    private val setVolume: (Float) -> Unit,
    private val canResume: () -> Boolean,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val stateMachine = AudioFocusStateMachine()
    private var resumeJob: Job? = null

    private val focusRequest = AudioFocusRequest.Builder(AUDIOFOCUS_GAIN)
        .setAudioAttributes(
            android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
        )
        .setOnAudioFocusChangeListener(OnAudioFocusChangeListener(::onAudioFocusChange))
        .setAcceptsDelayedFocusGain(true)
        .build()

    val hasAudioFocus: Boolean
        get() = stateMachine.state.hasFocus

    fun request(): Boolean {
        if (hasAudioFocus) return true

        val granted = audioManager.requestAudioFocus(focusRequest) == AUDIOFOCUS_REQUEST_GRANTED
        if (granted) {
            applyTransition(
                stateMachine.onEvent(
                    AudioFocusStateMachine.Event.GAIN,
                    isPlaying = isPlaying(),
                ),
            )
        }
        return hasAudioFocus
    }

    fun abandon() {
        cancelResume()
        audioManager.abandonAudioFocusRequest(focusRequest)
        stateMachine.onFocusAbandoned()
    }

    fun release() {
        abandon()
    }

    private fun onAudioFocusChange(focusChange: Int) {
        val event = when (focusChange) {
            AUDIOFOCUS_GAIN -> AudioFocusStateMachine.Event.GAIN
            AUDIOFOCUS_GAIN_TRANSIENT -> AudioFocusStateMachine.Event.GAIN_TRANSIENT
            AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> AudioFocusStateMachine.Event.GAIN_TRANSIENT_MAY_DUCK
            AUDIOFOCUS_LOSS -> AudioFocusStateMachine.Event.LOSS
            AUDIOFOCUS_LOSS_TRANSIENT -> AudioFocusStateMachine.Event.LOSS_TRANSIENT
            AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> AudioFocusStateMachine.Event.LOSS_TRANSIENT_CAN_DUCK
            else -> return
        }

        applyTransition(stateMachine.onEvent(event, isPlaying = isPlaying()))
    }

    private fun applyTransition(transition: AudioFocusStateMachine.Transition) {
        transition.actions.forEach { action ->
            when (action) {
                AudioFocusStateMachine.Action.PAUSE -> {
                    cancelResume()
                    pause()
                }

                AudioFocusStateMachine.Action.RESUME -> scheduleResume()

                AudioFocusStateMachine.Action.DUCK -> {
                    if (isPlaying()) setVolume(if (isMuted()) 0f else volume() * DUCK_FACTOR)
                }

                AudioFocusStateMachine.Action.RESTORE_VOLUME -> {
                    setVolume(if (isMuted()) 0f else volume())
                }

                AudioFocusStateMachine.Action.ABANDON_FOCUS -> abandon()
            }
        }
    }

    private fun scheduleResume() {
        cancelResume()
        resumeJob = scope.launch {
            delay(RESUME_DELAY_MS)
            if (stateMachine.state.hasFocus && !isPlaying() && canResume()) {
                resume()
            }
        }
    }

    private fun cancelResume() {
        resumeJob?.cancel()
        resumeJob = null
    }

    private companion object {
        const val DUCK_FACTOR = 0.2f
        const val RESUME_DELAY_MS = 300L
    }
}
