package com.auriqo.music.utils.cipher

import android.content.Context
import android.net.Uri
import com.music.innertube.NewPipeExtractor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Resolves YouTube's cipher parameters through one native Rhino runtime.
 *
 * PoToken still uses a WebView because Botguard is a browser integrity challenge. Signature and
 * n-parameter execution does not need that browser: the player bundle is loaded once into Rhino,
 * the configured closures are retained, and every playback request reuses them synchronously.
 */
object CipherDeobfuscator {
    private const val TAG = "echomusic_CipherDeobfusc"

    lateinit var appContext: Context
        private set

    private val resolverMutex = Mutex()
    private var nativeRuntime: NativePlayerJsRuntime? = null
    private var nativePlayerHash: String? = null
    private var nativeConfigEpoch = -1

    fun initialize(context: Context) {
        appContext = context.applicationContext
        PlayerConfigStore.initialize(context)
        PlayerConfigStore.scheduleStartupRefresh()
    }

    suspend fun signatureTimestamp(videoId: String? = null): Int? = resolverMutex.withLock {
        val result = PlayerJsFetcher.getPlayerJs(videoId = videoId)
        if (result != null) {
            val (playerJs, hash) = result
            FunctionNameExtractor.extractSignatureTimestamp(playerJs, hash)?.let { return@withLock it }
        }

        NewPipeExtractor.getSignatureTimestamp(videoId.orEmpty())
            .onFailure { error ->
                if (error !is CancellationException) {
                    Timber.tag(TAG).w(error, "Native player signature timestamp unavailable")
                }
            }
            .getOrNull()
    }

    suspend fun deobfuscateStreamUrl(signatureCipher: String, videoId: String): String? =
        resolverMutex.withLock {
            val params = CipherQueryParser.parse(signatureCipher)
            val obfuscatedSig = params["s"]
            val sigParam = params["sp"] ?: "signature"
            val baseUrl = params["url"]

            if (obfuscatedSig == null || baseUrl == null) {
                Timber.tag(TAG).e(
                    "Could not parse signatureCipher params: " +
                        "s=${obfuscatedSig != null}, url=${baseUrl != null}",
                )
                return@withLock null
            }

            Timber.tag(TAG).d(
                "Deobfuscating cipher natively for $videoId: " +
                    "sig=${obfuscatedSig.take(20)}..., sp=$sigParam",
            )

            val deobfuscatedSig = getNativeRuntime(videoId)?.let { runtime ->
                runCatching {
                    withContext(Dispatchers.Default) {
                        runtime.deobfuscateSignature(obfuscatedSig)
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).e(error, "Native player signature execution failed")
                }.getOrNull()
            } ?: NewPipeExtractor.deobfuscateSignature(videoId, obfuscatedSig)
                .onFailure { error ->
                    if (error !is CancellationException) {
                        Timber.tag(TAG).e(error, "NewPipe signature fallback failed")
                    }
                }
                .getOrNull()

            if (deobfuscatedSig.isNullOrEmpty()) return@withLock null

            val separator = if ("?" in baseUrl) "&" else "?"
            val finalUrl = "$baseUrl${separator}${sigParam}=${Uri.encode(deobfuscatedSig)}"
            Timber.tag(TAG).d("Native cipher deobfuscation succeeded for $videoId")
            finalUrl
        }

    suspend fun transformNParamInUrl(url: String, videoId: String? = null): String =
        resolverMutex.withLock {
            if (!Regex("[?&]n=([^&]+)").containsMatchIn(url)) return@withLock url

            val nativeResult = getNativeRuntime(videoId.orEmpty())?.let { runtime ->
                runCatching {
                    withContext(Dispatchers.Default) { runtime.transformN(Uri.decode(findN(url))) }
                }.onFailure { error ->
                    Timber.tag(TAG).e(error, "Native player n-transform failed")
                }.getOrNull()
            }
            if (!nativeResult.isNullOrEmpty()) {
                return@withLock url.replaceFirst(
                    Regex("([?&])n=[^&]+"),
                    "\$1n=${Uri.encode(nativeResult)}",
                )
            }

            NewPipeExtractor.transformNParam(videoId.orEmpty(), url)
                .onFailure { error ->
                    if (error !is CancellationException) {
                        Timber.tag(TAG).e(error, "NewPipe n-transform fallback failed")
                    }
                }
                .getOrElse { url }
        }

    suspend fun onStreamRejected(): Boolean = resolverMutex.withLock {
        nativeRuntime = null
        nativePlayerHash = null
        nativeConfigEpoch = -1
        PlayerJsFetcher.invalidateCache()
        NewPipeExtractor.clearPlayerJavaScriptCache()
        PlayerConfigStore.refreshAfterStreamRejection()
    }

    private suspend fun getNativeRuntime(videoId: String): NativePlayerJsRuntime? {
        val (playerJs, playerHash) = PlayerJsFetcher.getPlayerJs(videoId = videoId) ?: return null
        val configEpoch = PlayerConfigStore.configEpoch
        if (
            nativeRuntime != null &&
            nativePlayerHash == playerHash &&
            nativeConfigEpoch == configEpoch
        ) {
            return nativeRuntime
        }

        val config = FunctionNameExtractor.getHardcodedConfig(playerHash)
        if (config == null) {
            Timber.tag(TAG).w("No native player config for hash=$playerHash; using NewPipe fallback")
            nativeRuntime = null
            nativePlayerHash = playerHash
            nativeConfigEpoch = configEpoch
            return null
        }

        return try {
            Timber.tag(TAG).d("Creating native player runtime: hash=$playerHash, chars=${playerJs.length}")
            NativePlayerJsRuntime.create(playerJs, config).also {
                nativeRuntime = it
                nativePlayerHash = playerHash
                nativeConfigEpoch = configEpoch
                Timber.tag(TAG).d("Native player runtime ready: hash=$playerHash")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Native player runtime creation failed")
            nativeRuntime = null
            null
        }
    }

    private fun findN(url: String): String =
        Regex("[?&]n=([^&]+)").find(url)?.groupValues?.get(1).orEmpty()

}
