package com.auriqo.music.utils.potoken

import android.webkit.CookieManager
import com.auriqo.music.utils.cipher.CipherDeobfuscator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

class PoTokenGenerator {
    private val TAG = "PoTokenGenerator"

    private val webViewSupported by lazy { runCatching { CookieManager.getInstance() }.isSuccess }
    private var webViewBadImpl = false 

    private val webPoTokenGenLock = Mutex()
    private var webPoTokenSessionId: String? = null
    private var webPoTokenStreamingPot: String? = null
    private var webPoTokenGenerator: PoTokenWebView? = null

    /** Drops only the session-bound token state after a CDN rejects a stream. */
    suspend fun invalidate() = webPoTokenGenLock.withLock {
        withContext(Dispatchers.Main) {
            webPoTokenGenerator?.close()
        }
        webPoTokenGenerator = null
        webPoTokenStreamingPot = null
        webPoTokenSessionId = null
    }

    suspend fun getWebClientPoToken(videoId: String, sessionId: String): PoTokenResult? {
        Timber.tag(TAG).d("getWebClientPoToken called: videoId=$videoId, session present")
        Timber.tag(TAG).d("WebView state: supported=$webViewSupported, badImpl=$webViewBadImpl")
        if (!webViewSupported || webViewBadImpl) {
            Timber.tag(TAG).d("WebView not available: supported=$webViewSupported, badImpl=$webViewBadImpl")
            return null
        }

        return try {
            getWebClientPoToken(videoId, sessionId, forceRecreate = false)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "poToken generation exception: ${e.javaClass.simpleName}: ${e.message}")
            when (e) {
                is BadWebViewException -> {
                    Timber.tag(TAG).e(e, "Could not obtain poToken because WebView is broken")
                    webViewBadImpl = true
                    null
                }
                else -> throw e 
            }
        }
    }

    
    private suspend fun getWebClientPoToken(
        videoId: String,
        sessionId: String,
        forceRecreate: Boolean,
    ): PoTokenResult = webPoTokenGenLock.withLock {
        Timber.tag(TAG).d("Web poToken requested: videoId=$videoId, session present")
        var recreateGenerator = forceRecreate
        var result: PoTokenResult? = null

        while (result == null) {
            val shouldRecreate =
                recreateGenerator || webPoTokenGenerator == null || webPoTokenGenerator!!.isExpired ||
                    webPoTokenGenerator!!.isDead || webPoTokenSessionId != sessionId

            if (shouldRecreate) {
                Timber.tag(TAG).d("Creating new PoTokenWebView (forceRecreate=$recreateGenerator)")
                withContext(Dispatchers.Main) {
                    webPoTokenGenerator?.close()
                }
                webPoTokenGenerator = null
                webPoTokenStreamingPot = null
                webPoTokenSessionId = null

                val newGenerator = PoTokenWebView.getNewPoTokenGenerator(CipherDeobfuscator.appContext)
                val newStreamingPot = try {
                    newGenerator.generatePoToken(sessionId)
                } catch (e: Exception) {
                    withContext(NonCancellable + Dispatchers.Main) { newGenerator.close() }
                    throw e
                }
                webPoTokenSessionId = sessionId
                webPoTokenGenerator = newGenerator
                webPoTokenStreamingPot = newStreamingPot
                Timber.tag(TAG).d("Streaming poToken generated")
            }

            val poTokenGenerator = requireNotNull(webPoTokenGenerator)
            val streamingPot = requireNotNull(webPoTokenStreamingPot)
            try {
                val playerPot = poTokenGenerator.generatePoToken(videoId)
                Timber.tag(TAG).d("poTokens generated successfully")
                result = PoTokenResult(playerPot, streamingPot)
            } catch (e: CancellationException) {
                throw e
            } catch (throwable: Throwable) {
                if (shouldRecreate) {
                    throw throwable
                }
                Timber.tag(TAG).e(throwable, "Failed to obtain poToken, retrying with a new WebView")
                recreateGenerator = true
            }
        }
        requireNotNull(result)
    }
}
