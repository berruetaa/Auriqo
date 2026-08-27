

package com.auriqo.music.utils

import android.net.ConnectivityManager
import android.net.Uri
import android.util.Log
import com.music.innertube.NewPipeExtractor
import com.music.innertube.YouTube
import com.music.innertube.models.YouTubeClient
import com.auriqo.music.utils.BotDetectionMitigator
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import com.auriqo.music.constants.AudioQuality
import com.auriqo.music.utils.cipher.CipherDeobfuscator
import com.auriqo.music.utils.YTPlayerUtils.MAIN_CLIENT
import com.auriqo.music.utils.YTPlayerUtils.STREAM_FALLBACK_CLIENTS
import com.auriqo.music.utils.YTPlayerUtils.validateStatus
import com.auriqo.music.utils.potoken.PoTokenGenerator
import com.auriqo.music.utils.potoken.PoTokenResult
import com.auriqo.music.utils.PlaybackLogLevel
import com.auriqo.music.utils.PlaybackLogManager
import com.auriqo.music.playback.diagnostics.PlaybackDiagnostics
import com.auriqo.music.playback.diagnostics.PlaybackFailureHint
import com.auriqo.music.playback.diagnostics.PlaybackRedactor
import com.auriqo.music.playback.diagnostics.PlaybackResolutionException
import com.auriqo.music.debug.DebugRuntime
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.io.IOException
import kotlinx.coroutines.flow.first

object YTPlayerUtils {
    private const val logTag = "YTPlayerUtils"
    private const val TAG = "YTPlayerUtils"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
        .dns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                val addresses = Dns.SYSTEM.lookup(hostname)
                return when (YouTube.ipVersion) {
                    IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                    IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                    IpVersion.AUTO -> addresses
                }
            }
        })
        .proxySelector(object : ProxySelector() {
            override fun select(uri: URI?): List<Proxy> = listOfNotNull(YouTube.proxy ?: Proxy.NO_PROXY)
            override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) {
                Timber.tag(TAG).e(
                    "Proxy connection failed host=${uri?.host ?: "unknown"} " +
                        "type=${ioe?.javaClass?.simpleName ?: "unknown"}",
                )
            }
        })
        .proxyAuthenticator { _, response ->
            YouTube.proxyAuth?.let { auth ->
                response.request.newBuilder()
                    .header("Proxy-Authorization", auth)
                    .build()
            } ?: response.request
        }
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .apply {
            DebugRuntime.currentOrNull()?.networkEventListenerFactory()?.let(::eventListenerFactory)
        }
        .build()
    }

    private val poTokenGenerator = PoTokenGenerator()
    private val RAW_N_PARAMETER = Regex("[?&]n=[^&]+")

    
    private val MAIN_CLIENT: YouTubeClient = VISIONOS

    
    private val METADATA_CLIENT: YouTubeClient = WEB_REMIX

    // Keep fallback policy capability-driven. Auriqo can generate Web BotGuard/GVS tokens,
    // but it does not implement Android DroidGuard or iOSGuard attestation.
    private val STREAM_FALLBACK_CLIENTS: Array<YouTubeClient> = arrayOf(
        WEB_REMIX,
    )
    data class PlaybackData(
        val audioConfig: PlayerResponse.PlayerConfig.AudioConfig?,
        val videoDetails: PlayerResponse.VideoDetails?,
        val playbackTracking: PlayerResponse.PlaybackTracking?,
        val format: PlayerResponse.StreamingData.Format,
        val streamUrl: String,
        val streamExpiresInSeconds: Int,
    )

    internal enum class StreamUrlSource {
        RawPlayer,
        NewPipe,
    }

    private data class ResolvedStreamUrl(
        val url: String,
        val source: StreamUrlSource,
    )

    internal fun shouldApplyNTransform(source: StreamUrlSource, url: String): Boolean =
        source == StreamUrlSource.RawPlayer && RAW_N_PARAMETER.containsMatchIn(url)
    
    suspend fun playerResponseForPlayback(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context? = null,
        knownArtist: String? = null,
        knownTitle: String? = null,
        knownDurationMs: Long? = null,
        isDownload: Boolean = false,
        excludedItags: Set<Int> = emptySet(),
    ): Result<PlaybackData> {
        val trace = PlaybackDiagnostics.currentFor(videoId)
        trace?.playerResponseStart()
        val firstAttempt = resolvePlaybackData(
            videoId,
            playlistId,
            audioQuality,
            connectivityManager,
            context,
            knownArtist,
            knownTitle,
            excludedItags,
        )
        val result = if (firstAttempt.isFailure && YouTube.cookie == null) {
            Timber.tag(TAG).w("Playback failed for guest. Rotating session and retrying...")
            PlaybackLogManager.log(PlaybackLogLevel.BOT, "Playback failed for guest", "Triggering bot detection mitigation (rotating guest session)")
            BotDetectionMitigator.rotateGuestSession()
            val retryResult = resolvePlaybackData(
                videoId,
                playlistId,
                audioQuality,
                connectivityManager,
                context,
                knownArtist,
                knownTitle,
                excludedItags,
            )
            retryResult.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
            retryResult
        } else {
            firstAttempt
        }
        result.onSuccess { BotDetectionMitigator.notifyPlaybackSuccess() }
        trace?.playerResponseEnd(
            status = (result.exceptionOrNull() as? PlaybackResolutionException)?.playabilityStatus
                ?: if (result.isSuccess) "OK" else null,
            success = result.isSuccess,
        )
        return result
    }

    private suspend fun resolvePlaybackData(
        videoId: String,
        playlistId: String? = null,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        context: android.content.Context? = null,
        knownArtist: String? = null,
        knownTitle: String? = null,
        excludedItags: Set<Int> = emptySet(),
    ): Result<PlaybackData> = runCatching {
        Timber.tag(logTag).d("Fetching player response for videoId: $videoId, playlistId: $playlistId")
        PlaybackLogManager.log(PlaybackLogLevel.INFO, "Resolving playback data", "Video: $videoId")
        
        
        val isUploadedTrack = playlistId == "MLPT" || playlistId?.contains("MLPT") == true

        val isLoggedIn = YouTube.cookie != null
        Timber.tag(logTag).d("Session authentication status: ${if (isLoggedIn) "Logged in" else "Not logged in"}")

        
        val signatureTimestamp = getSignatureTimestampOrNull(videoId)
        Timber.tag(logTag).d("Signature timestamp: ${signatureTimestamp.timestamp}")

        
        var poToken: PoTokenResult? = null
        val sessionId = if (isLoggedIn) YouTube.dataSyncId else YouTube.visitorData
        if (MAIN_CLIENT.useWebPoTokens && sessionId != null) {
            Timber.tag(logTag).d("Generating PoToken for MAIN_CLIENT with sessionId")
            try {
                poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                if (poToken != null) {
                    Timber.tag(logTag).d("PoToken generated successfully")
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(logTag).e("PoToken generation failed type=${e::class.java.simpleName}")
            }
        }

        
        Timber.tag(logTag).d("Attempting to get player response using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying ${MAIN_CLIENT.clientName} (Main)")
        var mainPlayerResponse = YouTube.player(videoId, playlistId, MAIN_CLIENT, signatureTimestamp.timestamp, poToken?.playerRequestPoToken).getOrThrow()

        
        
        
        var metadataResponse: PlayerResponse? = null
        if (isLoggedIn) {
            Timber.tag(logTag).d("Fetching metadata from METADATA_CLIENT (WEB_REMIX) for authenticated tracking")
            try {
                
                var metaPoToken: PoTokenResult? = null
                val metaSessionId = YouTube.dataSyncId
                if (METADATA_CLIENT.useWebPoTokens && metaSessionId != null) {
                    try {
                        metaPoToken = poTokenGenerator.getWebClientPoToken(videoId, metaSessionId)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(logTag).e("Metadata PoToken generation failed type=${e::class.java.simpleName}")
                    }
                }
                metadataResponse = YouTube.player(
                    videoId, playlistId, METADATA_CLIENT,
                    signatureTimestamp.timestamp, metaPoToken?.playerRequestPoToken
                ).getOrNull()
                Timber.tag(logTag).d("Metadata response obtained: ${metadataResponse?.playabilityStatus?.status}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.tag(logTag).e("Failed to fetch metadata from METADATA_CLIENT type=${e::class.java.simpleName}")
            }
        }

        
        var usedAgeRestrictedClient: YouTubeClient? = null
        val wasOriginallyAgeRestricted: Boolean

        
        
        
        
        
        val mainStatus = mainPlayerResponse.playabilityStatus.status
        val isAgeRestrictedFromResponse = mainStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED"
        ) || (mainStatus == "LOGIN_REQUIRED" && mainPlayerResponse.playabilityStatus.reason?.contains("age", ignoreCase = true) == true)
        wasOriginallyAgeRestricted = isAgeRestrictedFromResponse

        if (isAgeRestrictedFromResponse && isLoggedIn) {
            
            Timber.tag(logTag).d("Age-restricted detected, using WEB_CREATOR")
            Log.i(TAG, "Age-restricted: using WEB_CREATOR for videoId=$videoId")
            val creatorResponse = YouTube.player(videoId, playlistId, WEB_CREATOR, null, null)
                .onFailure {
                    // Distinguish thrown request/parse failures from genuine playability
                    // rejections (both otherwise surface as a null response downstream).
                    Timber.tag(logTag).e("player() request FAILED for WEB_CREATOR type=${it::class.java.simpleName}")
                }.getOrNull()
            if (creatorResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("WEB_CREATOR works for age-restricted content")
                mainPlayerResponse = creatorResponse
                usedAgeRestrictedClient = WEB_CREATOR
            }
        }

        
        if (mainPlayerResponse == null) {
            throw PlaybackResolutionException(
                message = "Failed to get player response",
                hint = PlaybackFailureHint.PLAYER_RESPONSE_FAILED,
            )
        }

        
        
        val audioConfig = metadataResponse?.playerConfig?.audioConfig ?: mainPlayerResponse.playerConfig?.audioConfig
        val videoDetails = metadataResponse?.videoDetails ?: mainPlayerResponse.videoDetails
        val playbackTracking = metadataResponse?.playbackTracking ?: mainPlayerResponse.playbackTracking
        var format: PlayerResponse.StreamingData.Format? = null
        var streamUrl: String? = null
        var streamExpiresInSeconds: Int? = null
        var streamPlayerResponse: PlayerResponse? = null
        var retryMainPlayerResponse: PlayerResponse? = if (usedAgeRestrictedClient != null) mainPlayerResponse else null

        
        val currentStatus = mainPlayerResponse.playabilityStatus.status
        var isAgeRestricted = currentStatus in listOf(
            "AGE_CHECK_REQUIRED",
            "AGE_VERIFICATION_REQUIRED",
            "CONTENT_CHECK_REQUIRED",
            "UNPLAYABLE",
            "LOGIN_REQUIRED"
        )

        if (isAgeRestricted) {
            Timber.tag(logTag).d("Content needs fallback (status: $currentStatus)")
            android.util.Log.i("YTPlayerUtils", "Unplayable content detected: videoId=$videoId, status=$currentStatus")
        }
        
        val isPrivateTrack = mainPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

        
        
        
        val startIndex = when {
            isPrivateTrack -> 0
            isAgeRestricted -> 0
            else -> -1
        }

        for (clientIndex in (startIndex until STREAM_FALLBACK_CLIENTS.size)) {
            
            format = null
            streamUrl = null
            streamExpiresInSeconds = null

            
            val client: YouTubeClient
            if (clientIndex == -1) {
                
                client = MAIN_CLIENT
                streamPlayerResponse = retryMainPlayerResponse ?: mainPlayerResponse
                Timber.tag(logTag).d("Trying stream from MAIN_CLIENT: ${client.clientName}")
            } else {
                
                client = STREAM_FALLBACK_CLIENTS[clientIndex]
                Timber.tag(logTag).d("Trying fallback client ${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}: ${client.clientName}")
                PlaybackLogManager.log(PlaybackLogLevel.DEBUG, "Trying fallback [${clientIndex + 1}/${STREAM_FALLBACK_CLIENTS.size}]", client.clientName)

                if (client.loginRequired && !isLoggedIn && YouTube.cookie == null) {
                    
                    Timber.tag(logTag).d("Skipping client ${client.clientName} - requires login but user is not logged in")
                    continue
                }

                
                if (client.useWebPoTokens && poToken == null && sessionId != null) {
                    Timber.tag(logTag).d("Lazily generating PoToken for fallback web client: ${client.clientName}")
                    try {
                        poToken = poTokenGenerator.getWebClientPoToken(videoId, sessionId)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        PlaybackDiagnostics.currentFor(videoId)?.breadcrumb("POTOKEN_FAILED", e::class.simpleName)
                        Timber.tag(logTag).e("Lazy PoToken generation failed type=${e::class.java.simpleName}")
                    }
                }

                Timber.tag(logTag).d("Fetching player response for fallback client: ${client.clientName}")
                
                val clientPoToken = if (client.useWebPoTokens) poToken?.playerRequestPoToken else null
                
                val clientSigTimestamp = if (wasOriginallyAgeRestricted) null else signatureTimestamp.timestamp
                streamPlayerResponse =
                    YouTube.player(videoId, playlistId, client, clientSigTimestamp, clientPoToken)
                        .onFailure {
                            Timber.tag(logTag).e("player() request FAILED for ${client.clientName} type=${it::class.java.simpleName}")
                        }.getOrNull()
            }

            
            if (streamPlayerResponse?.playabilityStatus?.status == "OK") {
                Timber.tag(logTag).d("Player response status OK for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                PlaybackLogManager.log(PlaybackLogLevel.INFO, "Player response OK", if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName)

                
                val hasDirectUrls = streamPlayerResponse.streamingData?.adaptiveFormats
                    ?.any { !it.url.isNullOrEmpty() } == true
                val hasSignatureCipher = streamPlayerResponse.streamingData?.adaptiveFormats
                    ?.any { !it.signatureCipher.isNullOrEmpty() || !it.cipher.isNullOrEmpty() } == true

                Timber.tag(logTag).d("URL check: hasDirectUrls=$hasDirectUrls, hasSignatureCipher=$hasSignatureCipher")

                
                val responseToUse = streamPlayerResponse

                format =
                    findFormat(
                        responseToUse,
                        audioQuality,
                        connectivityManager,
                        excludedItags,
                    )

                if (format == null) {
                    Timber.tag(logTag).d("No suitable format found for client: ${if (clientIndex == -1) MAIN_CLIENT.clientName else STREAM_FALLBACK_CLIENTS[clientIndex].clientName}")
                    continue
                }

                Timber.tag(logTag).d("Format found: ${format.mimeType}, bitrate: ${format.bitrate}")

                val resolvedStream = findResolvedStreamUrlOrNull(
                    format,
                    videoId,
                    responseToUse,
                    skipNewPipe = wasOriginallyAgeRestricted,
                )
                if (resolvedStream == null) {
                    Timber.tag(logTag).d("Stream URL not found for format")
                    continue
                }
                val rawStreamUrl = resolvedStream.url
                streamUrl = rawStreamUrl

                
                val currentClient = if (clientIndex == -1) {
                    usedAgeRestrictedClient ?: MAIN_CLIENT
                } else {
                    STREAM_FALLBACK_CLIENTS[clientIndex]
                }
                val needsNTransform = shouldApplyNTransform(resolvedStream.source, resolvedStream.url)

                
                val isPrivatelyOwnedTrack = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                
                if (needsNTransform) {
                    val trace = PlaybackDiagnostics.currentFor(videoId)
                    trace?.cipherStart("n_transform")
                    try {
                        Timber.tag(logTag).d("Applying n-transform to stream URL for ${currentClient.clientName}")
                            val transformed = CipherDeobfuscator.transformNParamInUrl(rawStreamUrl, videoId)
                        if (transformed != rawStreamUrl) {
                            streamUrl = transformed
                            Timber.tag(logTag).d("N-transform applied successfully")
                        }
                        trace?.cipherEnd("n_transform", success = true)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        trace?.cipherEnd("n_transform", success = false)
                        throw e
                    } catch (e: Exception) {
                        trace?.cipherEnd("n_transform", success = false)
                        trace?.breadcrumb("N_TRANSFORM_FAILED", e::class.simpleName)
                        Timber.tag(logTag).e("N-transform failed type=${e::class.java.simpleName}")
                    }
                }

                
                
                val streamingPoToken = poToken?.streamingDataPoToken
                    ?.takeIf { currentClient.useWebPoTokens }
                if (streamingPoToken != null) {
                    Timber.tag(logTag).d("Appending pot= parameter to stream URL")
                    streamUrl = appendStreamingPoToken(streamUrl!!, streamingPoToken)
                }

                streamExpiresInSeconds = streamPlayerResponse.streamingData?.expiresInSeconds
                if (streamExpiresInSeconds == null) {
                    Timber.tag(logTag).d("Stream expiration time not found")
                    continue
                }

                Timber.tag(logTag).d("Stream expires in: $streamExpiresInSeconds seconds")

                
                val urlHost = try { java.net.URL(streamUrl).host } catch (e: Exception) { "unknown" }
                Timber.tag(logTag).d("Stream URL host: $urlHost, pot length: ${poToken?.streamingDataPoToken?.length ?: 0}")

                
                val isPrivatelyOwned = streamPlayerResponse.videoDetails?.musicVideoType == "MUSIC_VIDEO_TYPE_PRIVATELY_OWNED_TRACK"

                if (isPrivatelyOwned) {
                    Timber.tag(logTag).d("Skipping validation for privately owned track: ${currentClient.clientName}")
                    PlaybackDiagnostics.currentFor(videoId)?.breadcrumb(
                        "STREAM_CLIENT_SELECTED",
                        "${currentClient.clientName}/${currentClient.clientVersion} private=true",
                    )
                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId, private=true")
                    break
                }

                if (clientIndex == -1) {
                    // The normal path should pay for one request: Media3's real GET is the
                    // authoritative stream check. A speculative HEAD here duplicated DNS/TLS
                    // and delayed cold start; rejected URLs are classified and recovered by the
                    // player boundary below. Fallback clients still use validation to choose a
                    // viable candidate before spending another resolution attempt.
                    PlaybackDiagnostics.currentFor(videoId)?.breadcrumb("STREAM_VALIDATION_SKIPPED", "main_client")
                    PlaybackDiagnostics.currentFor(videoId)?.breadcrumb(
                        "STREAM_CLIENT_SELECTED",
                        "${currentClient.clientName}/${currentClient.clientVersion} validation=media3",
                    )
                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId (validation deferred to Media3)")
                    break
                }

                if (validateStatus(streamUrl!!, currentClient)) {
                    
                    PlaybackDiagnostics.currentFor(videoId)?.breadcrumb(
                        "STREAM_CLIENT_SELECTED",
                        "${currentClient.clientName}/${currentClient.clientVersion} validation=range_get",
                    )
                    Timber.tag(logTag).d("Stream validated successfully with client: ${currentClient.clientName}")
                    PlaybackLogManager.log(PlaybackLogLevel.INFO, "Stream validated", currentClient.clientName)
                    
                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId")
                    break
                } else {
                    Timber.tag(logTag).d("Stream validation failed for client: ${currentClient.clientName}")

                    
                    if (needsNTransform) {
                        var nTransformWorked = false

                        
                        try {
                            val nTransformed = CipherDeobfuscator.transformNParamInUrl(rawStreamUrl, videoId)
                            if (nTransformed != rawStreamUrl) {
                                val fallbackStreamUrl =
                                    appendStreamingPoToken(nTransformed, streamingPoToken)
                                Timber.tag(logTag).d("CipherDeobfuscator n-transform applied, re-validating...")
                                if (validateStatus(fallbackStreamUrl, currentClient)) {
                                    Timber.tag(logTag).d("N-transformed URL VALIDATED OK!")
                                    streamUrl = fallbackStreamUrl
                                    nTransformWorked = true
                                    Log.i(TAG, "Playback: client=${currentClient.clientName}, videoId=$videoId (cipher n-transform)")
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            PlaybackDiagnostics.currentFor(videoId)?.breadcrumb("N_TRANSFORM_FAILED", e::class.simpleName)
                            Timber.tag(logTag).e("CipherDeobfuscator n-transform error type=${e::class.java.simpleName}")
                        }

                        if (nTransformWorked) break
                    }
                }
            } else {
                val status = streamPlayerResponse?.playabilityStatus?.status ?: "Unknown"
                val reason = streamPlayerResponse?.playabilityStatus?.reason ?: "No reason"
                Timber.tag(logTag).d("Player response status not OK: $status, reason: $reason")
                PlaybackLogManager.log(PlaybackLogLevel.WARNING, "Client failed: ${client.clientName}", "$status: $reason")
                
                
                Timber.tag(logTag).d("Player response status not OK: ${streamPlayerResponse?.playabilityStatus?.status}, reason: ${streamPlayerResponse?.playabilityStatus?.reason}")
            }
        }

        if (streamPlayerResponse == null) {
            Timber.tag(logTag).e("Bad stream player response - all clients failed")
            throw PlaybackResolutionException(
                message = "Bad stream player response",
                hint = PlaybackFailureHint.PLAYER_RESPONSE_FAILED,
            )
        }

        if (streamPlayerResponse.playabilityStatus.status != "OK") {
            val errorReason = streamPlayerResponse.playabilityStatus.reason
            Timber.tag(logTag).e("Playability status not OK: $errorReason")
            throw PlaybackResolutionException(
                message = errorReason ?: "Playability status not OK",
                playabilityStatus = streamPlayerResponse.playabilityStatus.status,
                playabilityReason = errorReason,
            )
        }

        if (streamExpiresInSeconds == null) {
            Timber.tag(logTag).e("Missing stream expire time")
            throw PlaybackResolutionException(
                message = "Missing stream expire time",
                hint = PlaybackFailureHint.STREAM_URL_EXPIRED,
            )
        }

        if (format == null) {
            Timber.tag(logTag).e("Could not find format")
            throw PlaybackResolutionException(
                message = "Could not find format",
                hint = PlaybackFailureHint.FORMAT_NOT_FOUND,
            )
        }

        if (streamUrl == null) {
            Timber.tag(logTag).e("Could not find stream url")
            throw PlaybackResolutionException(
                message = "Could not find stream url",
                hint = if (!format.signatureCipher.isNullOrEmpty() || !format.cipher.isNullOrEmpty()) {
                    PlaybackFailureHint.SIGNATURE_DECIPHER_FAILED
                } else {
                    PlaybackFailureHint.STREAM_URL_EXPIRED
                },
            )
        }

        Timber.tag(logTag).d("Successfully obtained playback data with format: ${format.mimeType}, bitrate: ${format.bitrate}")
        PlaybackData(
            audioConfig,
            videoDetails,
            playbackTracking,
            format,
            streamUrl,
            streamExpiresInSeconds,
        )
    }.onFailure { e ->
        if (e is kotlinx.coroutines.CancellationException) throw e
        Timber.tag(logTag).e("Playback resolution failed type=${e::class.java.simpleName}")
        PlaybackLogManager.log(
            PlaybackLogLevel.ERROR,
            "Playback failed",
            "${e::class.simpleName}: ${PlaybackRedactor.sanitizeText(e.message.orEmpty())}",
        )
    }
    
    suspend fun playerResponseForMetadata(
        videoId: String,
        playlistId: String? = null,
    ): Result<PlayerResponse> {
        Timber.tag(logTag).d("Fetching metadata-only player response for videoId: $videoId using MAIN_CLIENT: ${MAIN_CLIENT.clientName}")
        return YouTube.player(videoId, playlistId, client = WEB_REMIX) 
            .onSuccess { Timber.tag(logTag).d("Successfully fetched metadata") }
            .onFailure { Timber.tag(logTag).e("Failed to fetch metadata type=${it::class.java.simpleName}") }
    }

    private fun findFormat(
        playerResponse: PlayerResponse,
        audioQuality: AudioQuality,
        connectivityManager: ConnectivityManager,
        excludedItags: Set<Int> = emptySet(),
    ): PlayerResponse.StreamingData.Format? {
        Timber.tag(logTag).d("Finding format with audioQuality: $audioQuality, network metered: ${connectivityManager.isActiveNetworkMetered}")

        val format = playerResponse.streamingData?.adaptiveFormats
            ?.filter { it.isAudio && it.isOriginal }
            ?.filterNot { it.itag in excludedItags }
            ?.maxByOrNull {
                it.bitrate * when (audioQuality) {
                    AudioQuality.OPUS -> 1
                } + (if (it.mimeType.startsWith("audio/webm")) 10240 else 0) 
            }

        if (format != null) {
            Timber.tag(logTag).d("Selected format: ${format.mimeType}, bitrate: ${format.bitrate}")
        } else {
            Timber.tag(logTag).d("No suitable audio format found")
        }

        return format
    }
    
    private fun validateStatus(url: String, client: YouTubeClient): Boolean {
        Timber.tag(logTag).d("Validating stream URL status")
        try {
            val requestBuilder = okhttp3.Request.Builder()
                .get()
                .url(url)
                .header("Range", "bytes=0-0")
                .header("User-Agent", client.userAgent)

            
            YouTube.cookie?.let { cookie ->
                requestBuilder.addHeader("Cookie", cookie)
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                val isSuccessful = response.isSuccessful
                Timber.tag(logTag).d("Stream URL validation result: ${if (isSuccessful) "Success" else "Failed"} (${response.code})")
                return isSuccessful
            }
        } catch (e: Exception) {
            Timber.tag(logTag).e(
                "Stream URL validation failed type=${e::class.java.simpleName} " +
                    "message=${PlaybackRedactor.sanitizeText(e.message.orEmpty())}",
            )
        }
        return false
    }

    private fun appendStreamingPoToken(url: String, streamingPoToken: String?): String {
        if (streamingPoToken == null) return url
        val separator = if ("?" in url) "&" else "?"
        return "${url}${separator}pot=${Uri.encode(streamingPoToken)}"
    }

    data class SignatureTimestampResult(
        val timestamp: Int?,
        val isAgeRestricted: Boolean
    )

    private suspend fun getSignatureTimestampOrNull(videoId: String): SignatureTimestampResult {
        Timber.tag(logTag).d("Getting signature timestamp for videoId: $videoId")
        val cipherTimestamp = try {
            CipherDeobfuscator.signatureTimestamp(videoId)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(logTag).w(e, "Could not read signature timestamp from cipher player")
            null
        }
        if (cipherTimestamp != null) {
            Timber.tag(logTag).d("Signature timestamp from cipher player: $cipherTimestamp")
            return SignatureTimestampResult(cipherTimestamp, isAgeRestricted = false)
        }
        val result = NewPipeExtractor.getSignatureTimestamp(videoId)
        return result.fold(
            onSuccess = { timestamp ->
                Timber.tag(logTag).d("Signature timestamp from NewPipe fallback: $timestamp")
                SignatureTimestampResult(timestamp, isAgeRestricted = false)
            },
            onFailure = { error ->
                if (error is kotlinx.coroutines.CancellationException) {
                    throw error
                }
                val isAgeRestricted = error.message?.contains("age-restricted", ignoreCase = true) == true ||
                    error.cause?.message?.contains("age-restricted", ignoreCase = true) == true
                if (isAgeRestricted) {
                    Timber.tag(logTag).d("Age-restricted content detected from NewPipe")
                    Timber.tag(logTag).i("Age-restricted detected early via NewPipe: videoId=%s", videoId)
                } else {
                    Timber.tag(logTag).e("Failed to get signature timestamp type=${error::class.java.simpleName}")
                }
                SignatureTimestampResult(null, isAgeRestricted)
            }
        )
    }

    suspend fun findUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false,
    ): String? = findResolvedStreamUrlOrNull(
        format,
        videoId,
        playerResponse,
        skipNewPipe,
    )?.url

    private suspend fun findResolvedStreamUrlOrNull(
        format: PlayerResponse.StreamingData.Format,
        videoId: String,
        playerResponse: PlayerResponse,
        skipNewPipe: Boolean = false,
    ): ResolvedStreamUrl? {
        Timber.tag(logTag).d("Finding stream URL for format: ${format.mimeType}, videoId: $videoId, skipNewPipe: $skipNewPipe")

        
        format.url?.takeIf { it.isNotEmpty() }?.let { directUrl ->
            Timber.tag(logTag).d("Using URL from format directly")
            return ResolvedStreamUrl(directUrl, StreamUrlSource.RawPlayer)
        }

        
        val signatureCipher = format.signatureCipher ?: format.cipher
        if (!signatureCipher.isNullOrEmpty()) {
            Timber.tag(logTag).d("Format has signatureCipher, using custom deobfuscation")
            val trace = PlaybackDiagnostics.currentFor(videoId)
            trace?.cipherStart("signature")
            val customDeobfuscatedUrl = try {
                CipherDeobfuscator.deobfuscateStreamUrl(signatureCipher, videoId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                trace?.cipherEnd("signature", success = false)
                throw e
            } catch (e: Exception) {
                trace?.breadcrumb("SIGNATURE_DECIPHER_FAILED", e::class.simpleName)
                null
            }
            trace?.cipherEnd("signature", success = customDeobfuscatedUrl != null)
            if (customDeobfuscatedUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained via custom cipher deobfuscation")
                return ResolvedStreamUrl(customDeobfuscatedUrl, StreamUrlSource.RawPlayer)
            }
            Timber.tag(logTag).d("Custom cipher deobfuscation failed")
        }

        
        if (skipNewPipe) {
            Timber.tag(logTag).d("Skipping NewPipe methods for age-restricted content")
            return null
        }

        
        val deobfuscatedUrl = NewPipeExtractor.getStreamUrl(format, videoId)
        if (deobfuscatedUrl != null) {
            Timber.tag(logTag).d("Stream URL obtained via NewPipe deobfuscation")
            return ResolvedStreamUrl(deobfuscatedUrl, StreamUrlSource.NewPipe)
        }

        PlaybackDiagnostics.currentFor(videoId)?.breadcrumb("STREAM_URL_NOT_FOUND", "itag=${format.itag}")

        
        Timber.tag(logTag).d("Trying StreamInfo fallback for URL")
        val streamUrls = YouTube.getNewPipeStreamUrls(videoId)
        if (streamUrls.isNotEmpty()) {
            val streamUrl = streamUrls.find { it.first == format.itag }?.second
            if (streamUrl != null) {
                Timber.tag(logTag).d("Stream URL obtained from StreamInfo")
                return ResolvedStreamUrl(streamUrl, StreamUrlSource.NewPipe)
            }

            
            val audioStream = streamUrls.find { urlPair ->
                playerResponse.streamingData?.adaptiveFormats?.any {
                    it.itag == urlPair.first && it.isAudio
                } == true
            }?.second

            if (audioStream != null) {
                Timber.tag(logTag).d("Audio stream URL obtained from StreamInfo (different itag)")
                return ResolvedStreamUrl(audioStream, StreamUrlSource.NewPipe)
            }
        }

        Timber.tag(logTag).e("Failed to get stream URL")
        return null
    }

    /**
     * Clears the state that can make an otherwise syntactically valid stream URL fail at the CDN.
     * The caller already owns a one-shot recovery gate, so this never retries on its own.
     */
    suspend fun refreshAfterStreamRejection(videoId: String) {
        Timber.tag(logTag).d("Refreshing stream resolver state for videoId: $videoId")
        poTokenGenerator.invalidate()
        CipherDeobfuscator.onStreamRejected()
    }
}
