package com.auriqo.music.utils.cipher

import org.junit.Assert.assertEquals
import org.junit.Test

class CipherQueryFixtureTest {
    @Test
    fun signatureCipherQuery_preservesEncodedUrlAndDecodesFields() {
        val query = fixture("signature-cipher-query.txt")

        val params = CipherQueryParser.parse(query)

        assertEquals(
            "https://cdn.example.invalid/videoplayback?expire=1700000000&n=sanitized&foo=bar",
            params["url"],
        )
        assertEquals("sig", params["sp"])
        assertEquals("SANITIZED_SIGNATURE", params["s"])
    }

    @Test
    fun signatureCipherQuery_ignoresMalformedPairs() {
        assertEquals(
            mapOf("s" to "signature"),
            CipherQueryParser.parse("broken&s=signature&=missing"),
        )
    }

    private fun fixture(name: String): String =
        requireNotNull(javaClass.getResource("/fixtures/cipher/$name")) {
            "Missing cipher fixture: $name"
        }.readText().trim()
}
