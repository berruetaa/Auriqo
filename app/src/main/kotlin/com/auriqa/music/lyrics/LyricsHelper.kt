package com.auriqo.music.lyrics

import android.content.Context
import android.util.LruCache
import com.auriqo.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.auriqo.music.models.MediaMetadata
import com.auriqo.music.utils.NetworkConnectivityObserver
import com.auriqo.music.utils.dataStore
import com.auriqo.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) {
    private suspend fun resolveLyricsProviders(): List<LyricsProvider> {
        val preferences = context.dataStore.data.first()
        val order = LyricsProviderRegistry.resolveProviderOrder(preferences)

        return LyricsProviderRegistry.getOrderedEnabledProviders(
            order = order,
            preferences = preferences,
            context = context,
        )
    }

    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
    private var currentLyricsJob: Job? = null

    private suspend fun <T> runCancellableLyricsRequest(
        block: suspend CoroutineScope.() -> T,
    ): T {
        currentLyricsJob?.cancelAndJoin()
        return coroutineScope {
            val requestJob = async(
                context = SupervisorJob(coroutineContext[Job]),
                start = CoroutineStart.LAZY,
                block = block,
            )
            currentLyricsJob = requestJob
            try {
                requestJob.start()
                requestJob.await()
            } finally {
                if (currentLyricsJob === requestJob) {
                    currentLyricsJob = null
                }
            }
        }
    }

    suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider =
        runCancellableLyricsRequest {
            val cached = cache.get(mediaMetadata.id)?.firstOrNull()
            if (cached != null) {
                return@runCancellableLyricsRequest LyricsWithProvider(cached.lyrics, cached.providerName)
            }

            val isNetworkAvailable = try {
                networkConnectivity.isCurrentlyConnected()
            } catch (e: Exception) {
                true
            }

            if (!isNetworkAvailable) {
                return@runCancellableLyricsRequest LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
            }

            val providers = resolveLyricsProviders()
            if (providers.isEmpty()) {
                return@runCancellableLyricsRequest LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
            }

            coroutineScope {
                val channel = Channel<LyricsWithProvider?>(providers.size)
                providers.forEach { provider ->
                    launch {
                        try {
                            val result = provider.getLyrics(
                                mediaMetadata.id,
                                mediaMetadata.title,
                                mediaMetadata.artists.joinToString { it.name },
                                mediaMetadata.duration,
                                mediaMetadata.album?.title,
                            )
                            result.onSuccess { lyrics ->
                                if (lyrics != LYRICS_NOT_FOUND && lyrics.isNotBlank()) {
                                    channel.send(LyricsWithProvider(lyrics, provider.name))
                                } else {
                                    channel.send(null)
                                }
                            }.onFailure {
                                reportException(it)
                                channel.send(null)
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            reportException(e)
                            channel.send(null)
                        }
                    }
                }

                var responses = 0
                val receivedUnsynced = mutableMapOf<String, LyricsWithProvider>()

                while (responses < providers.size) {
                    val result = channel.receive()
                    responses++
                    if (result != null) {
                        val isSynced = result.lyrics.trimStart().startsWith("[")
                        if (isSynced) {
                            coroutineContext.cancelChildren()
                            return@coroutineScope result
                        }
                        receivedUnsynced[result.provider] = result
                    }
                }

                providers
                    .firstNotNullOfOrNull { receivedUnsynced[it.name] }
                    ?: LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
            }
        }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String? = null,
        callback: (LyricsResult) -> Unit,
    ) {
        runCancellableLyricsRequest {
            val cacheKey = "$songArtists-$songTitle".replace(" ", "")
            cache.get(cacheKey)?.let { results ->
                results.forEach {
                    callback(it)
                }
                return@runCancellableLyricsRequest
            }

            val isNetworkAvailable = try {
                networkConnectivity.isCurrentlyConnected()
            } catch (e: Exception) {
                true
            }

            if (!isNetworkAvailable) {
                return@runCancellableLyricsRequest
            }

            val allResult = java.util.concurrent.CopyOnWriteArrayList<LyricsResult>()
            val providers = resolveLyricsProviders()
            val jobs = providers.map { provider ->
                launch {
                    try {
                        provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { lyrics ->
                            val result = LyricsResult(provider.name, lyrics)
                            allResult += result
                            callback(result)
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                    }
                }
            }
            jobs.forEach { it.join() }
            cache.put(cacheKey, allResult.toList())
        }
    }

    fun cancelCurrentLyricsJob() {
        currentLyricsJob?.cancel()
        currentLyricsJob = null
    }

    companion object {
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)

data class LyricsWithProvider(
    val lyrics: String,
    val provider: String,
)
