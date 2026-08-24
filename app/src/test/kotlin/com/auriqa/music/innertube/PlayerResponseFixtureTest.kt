package com.auriqo.music.innertube

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerResponseFixtureTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun sanitizedFixturesCoverPlayabilityAndFormatContracts() {
        val expectedStatuses = mapOf(
            "response-ok.json" to "OK",
            "response-login-required.json" to "LOGIN_REQUIRED",
            "response-age-restricted.json" to "LOGIN_REQUIRED",
            "response-region-blocked.json" to "UNPLAYABLE",
            "response-unplayable.json" to "UNPLAYABLE",
            "response-signature-cipher.json" to "OK",
        )

        expectedStatuses.forEach { (fixture, expectedStatus) ->
            val response = load(fixture)
            assertEquals(
                fixture,
                expectedStatus,
                response.getValue("playabilityStatus").jsonObject.getValue("status").jsonPrimitive.content,
            )
            assertFalse(fixture, response.toString().contains("visitorDataValue"))
        }

        val ok = load("response-ok.json")
        assertTrue(ok.getValue("streamingData").jsonObject.getValue("adaptiveFormats").jsonArray.isEmpty())

        val cipher = load("response-signature-cipher.json")
            .getValue("streamingData").jsonObject
            .getValue("adaptiveFormats").jsonArray.single().jsonObject
        assertEquals("251", cipher.getValue("itag").jsonPrimitive.content)
        assertTrue(cipher.getValue("signatureCipher").jsonPrimitive.content.contains("placeholder-signature"))
        assertFalse(cipher.getValue("signatureCipher").jsonPrimitive.content.contains("secret"))
    }

    private fun load(name: String) = javaClass.getResourceAsStream("/fixtures/player/$name")!!.use { stream ->
        json.parseToJsonElement(stream.reader().readText()).jsonObject
    }
}
