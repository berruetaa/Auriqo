package com.auriqo.music.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackClientPolicyTest {
    @Test
    fun interactivePrimaryDefersCdnValidationToMedia3() {
        assertTrue(
            YTPlayerUtils.shouldDeferPrimaryValidationToMedia3(
                clientIndex = -1,
                validatePrimaryCandidate = false,
            ),
        )
    }

    @Test
    fun downloadPrimaryIsValidatedBeforeReturning() {
        assertFalse(
            YTPlayerUtils.shouldDeferPrimaryValidationToMedia3(
                clientIndex = -1,
                validatePrimaryCandidate = true,
            ),
        )
    }

    @Test
    fun fallbackClientsNeverUseThePrimaryFastPath() {
        assertFalse(
            YTPlayerUtils.shouldDeferPrimaryValidationToMedia3(
                clientIndex = 0,
                validatePrimaryCandidate = false,
            ),
        )
    }
}
