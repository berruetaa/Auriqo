package com.auriqo.music.utils.cipher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FunctionNameExtractorTest {
    @Test
    fun signatureTimestampUsesTheAnchoredPlayerLiteral() {
        assertEquals(
            20476,
            FunctionNameExtractor.extractSignatureTimestamp(fixture("signature-timestamp-20476.js")),
        )
    }

    @Test
    fun signatureTimestampFallbackDoesNotMatchTheEndOfAnotherIdentifier() {
        assertNull(
            FunctionNameExtractor.extractSignatureTimestamp(
                fixture("signature-timestamp-false-positive.js"),
                knownHash = "not-a-player-hash",
            ),
        )
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/player/$name")) {
            "Missing player fixture: $name"
        }.readText()
}
