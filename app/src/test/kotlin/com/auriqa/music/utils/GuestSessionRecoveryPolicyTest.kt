package com.auriqo.music.utils

import com.auriqo.music.playback.diagnostics.PlaybackFailureHint
import com.auriqo.music.playback.diagnostics.PlaybackResolutionException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuestSessionRecoveryPolicyTest {
    @Test
    fun cdn403EvidenceCanRotateGuestSession() {
        assertTrue(
            YTPlayerUtils.shouldRotateGuestSessionAfterFailure(
                PlaybackResolutionException(
                    message = "CDN validation failed with HTTP 403",
                    hint = PlaybackFailureHint.STREAM_URL_EXPIRED,
                ),
            ),
        )
    }

    @Test
    fun ageConfirmationIsNotBotEvidence() {
        assertFalse(
            YTPlayerUtils.shouldRotateGuestSessionAfterFailure(
                PlaybackResolutionException(
                    message = "Sign in to confirm your age",
                    playabilityStatus = "LOGIN_REQUIRED",
                    playabilityReason = "Sign in to confirm your age",
                ),
            ),
        )
    }

    @Test
    fun transportFailuresNeverRotateIdentity() {
        assertFalse(YTPlayerUtils.shouldRotateGuestSessionAfterFailure(SocketTimeoutException("timed out")))
        assertFalse(YTPlayerUtils.shouldRotateGuestSessionAfterFailure(UnknownHostException("youtube.com")))
    }

    @Test
    fun supersededResolutionDoesNotRotateIdentity() {
        assertFalse(
            YTPlayerUtils.shouldRotateGuestSessionAfterFailure(
                PlaybackResolutionException(
                    message = "context changed",
                    hint = PlaybackFailureHint.SUPERSEDED_RESOLUTION,
                ),
            ),
        )
    }

    @Test
    fun explicitBotChallengeRotatesGuestSession() {
        assertTrue(
            YTPlayerUtils.shouldRotateGuestSessionAfterFailure(
                PlaybackResolutionException(
                    message = "Sign in to confirm you're not a bot",
                    hint = PlaybackFailureHint.PLAYER_RESPONSE_FAILED,
                ),
            ),
        )
    }
}
