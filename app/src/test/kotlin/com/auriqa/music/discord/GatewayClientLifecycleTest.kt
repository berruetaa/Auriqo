package com.auriqa.music.discord

import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class GatewayClientLifecycleTest {
    @Test
    fun disconnectIsIdempotentAndPreventsReuse() {
        val client = GatewayClient()

        assertFalse(client.sendPresenceUpdate(JSONObject()))

        client.disconnect()
        client.disconnect()

        assertFalse(client.sendPresenceUpdate(JSONObject()))
        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { client.connect("token") }
        }
    }
}
