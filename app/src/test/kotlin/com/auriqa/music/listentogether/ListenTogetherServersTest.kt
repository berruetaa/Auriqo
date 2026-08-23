package com.auriqo.music.listentogether

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenTogetherServersTest {
    @Test
    fun allowsSecureWebSocketServers() {
        assertTrue(ListenTogetherServers.isAllowedServerUrl("wss://sync.example.test/ws"))
    }

    @Test
    fun allowsCleartextOnlyForLocalDevelopmentHosts() {
        assertTrue(ListenTogetherServers.isAllowedServerUrl("ws://localhost:8080/ws"))
        assertTrue(ListenTogetherServers.isAllowedServerUrl("ws://10.0.2.2:8080/ws"))
        assertFalse(ListenTogetherServers.isAllowedServerUrl("ws://sync.example.test/ws"))
    }

    @Test
    fun rejectsCredentialsFragmentsAndUnexpectedSchemes() {
        assertFalse(ListenTogetherServers.isAllowedServerUrl("http://sync.example.test/ws"))
        assertFalse(ListenTogetherServers.isAllowedServerUrl("wss://user:pass@sync.example.test/ws"))
        assertFalse(ListenTogetherServers.isAllowedServerUrl("wss://sync.example.test/ws#fragment"))
    }
}
