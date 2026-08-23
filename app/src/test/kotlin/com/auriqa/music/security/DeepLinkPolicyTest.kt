package com.auriqo.music.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepLinkPolicyTest {
    @Test
    fun acceptsDeclaredWebAndCustomHosts() {
        assertTrue(DeepLinkPolicy.isSupported("https://music.youtube.com/watch?v=test"))
        assertTrue(DeepLinkPolicy.isSupported("https://share.echomusic.fun/video"))
        assertTrue(DeepLinkPolicy.isSupported("https://echomusic-listen-together.onrender.com/listen?code=ROOM"))
        assertTrue(DeepLinkPolicy.isSupported("echomusic://listen?code=ROOM"))
    }

    @Test
    fun rejectsUntrustedHostsSchemesAndUserinfo() {
        assertFalse(DeepLinkPolicy.isSupported("https://evil.example/watch?v=test"))
        assertFalse(DeepLinkPolicy.isSupported("https://youtube.com.evil.example/watch?v=test"))
        assertFalse(DeepLinkPolicy.isSupported("https://user:pass@music.youtube.com/watch?v=test"))
        assertFalse(DeepLinkPolicy.isSupported("javascript://youtube.com/watch?v=test"))
    }
}
