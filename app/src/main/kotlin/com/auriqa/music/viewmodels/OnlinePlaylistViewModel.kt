

package com.auriqo.music.viewmodels

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.music.innertube.YouTube
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import com.music.innertube.models.filterVideoSongs
import com.auriqo.music.constants.HideVideoSongsKey
import com.auriqo.music.constants.YouTubeDataApiKey
import com.auriqo.music.constants.YouTubeAttributionAccessTokenKey
import com.auriqo.music.constants.YouTubeAttributionWorkerUrlKey
import com.auriqo.music.api.PlaylistAttribution
import com.auriqo.music.api.YouTubeDataApi
import com.auriqo.music.db.MusicDatabase
import com.auriqo.music.utils.dataStore
import com.auriqo.music.utils.read
import com.auriqo.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnlinePlaylistViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    database: MusicDatabase
) : ViewModel() {
    private val playlistId = savedStateHandle.get<String>("playlistId")!!

    val playlist = MutableStateFlow<PlaylistItem?>(null)
    val playlistSongs = MutableStateFlow<List<SongItem>>(emptyList())
    val relatedItems = MutableStateFlow<List<YTItem>>(emptyList())
    val attributions = MutableStateFlow<Map<String, PlaylistAttribution>>(emptyMap())

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore = _isLoadingMore.asStateFlow()

    val dbPlaylist = database.playlistByBrowseId(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    var continuation: String? = null
        private set

    private var proactiveLoadJob: Job? = null
    private var playlistIsCollaborative = false
    private var localAttributions = emptyMap<String, PlaylistAttribution>()
    private var remoteAttributions = emptyMap<String, PlaylistAttribution>()

    init {
        fetchInitialPlaylistData()
    }

    private fun fetchInitialPlaylistData() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            continuation = null
            proactiveLoadJob?.cancel() 
            localAttributions = emptyMap()
            remoteAttributions = emptyMap()
            attributions.value = emptyMap()

            YouTube.playlist(playlistId)
                .onSuccess { playlistPage ->
                    playlist.value = playlistPage.playlist
                    playlistSongs.value = applySongFilters(playlistPage.songs)
                    relatedItems.value = playlistPage.related ?: emptyList()
                    playlistIsCollaborative = playlistPage.isCollaborative
                    addLocalAttributions(playlistPage.songs)
                    val workerUrl = context.dataStore.read(
                        YouTubeAttributionWorkerUrlKey,
                        "https://auriqo-youtube-attribution.berruetx.workers.dev",
                    )
                    val accessToken = context.dataStore.read(YouTubeAttributionAccessTokenKey, "")
                    val apiKey = context.dataStore.read(YouTubeDataApiKey, "")
                    val shouldLoadRemoteAttributions = localAttributions.isEmpty() ||
                        accessToken.isNotBlank() || apiKey.isNotBlank()
                    if (shouldLoadRemoteAttributions && workerUrl.isNotBlank() && accessToken.isNotBlank()) {
                        YouTubeDataApi.workerPlaylistAttributions(workerUrl, playlistId, accessToken)
                            .onSuccess { setRemoteAttributions(it) }
                            .onFailure {
                                if (apiKey.isNotBlank()) {
                                    YouTubeDataApi.playlistAttributions(apiKey, playlistId)
                                        .onSuccess { setRemoteAttributions(it) }
                                } else {
                                    reportException(it)
                                }
                            }
                    } else if (shouldLoadRemoteAttributions && apiKey.isNotBlank()) {
                        YouTubeDataApi.playlistAttributions(apiKey, playlistId)
                            .onSuccess { setRemoteAttributions(it) }
                            .onFailure { reportException(it) }
                    }
                    continuation = playlistPage.songsContinuation
                    _isLoading.value = false
                    if (continuation != null) {
                        startProactiveBackgroundLoading()
                    }
                }.onFailure { throwable ->
                    _error.value = throwable.message?.takeIf { it.isNotBlank() }
                        ?: throwable::class.java.simpleName
                        ?: "Failed to load playlist"
                    _isLoading.value = false
                    reportException(throwable)
                }
        }
    }

    private fun startProactiveBackgroundLoading() {
        proactiveLoadJob?.cancel() 
        proactiveLoadJob = viewModelScope.launch(Dispatchers.IO) {
            var currentProactiveToken = continuation
            while (currentProactiveToken != null && isActive) {
                
                if (_isLoadingMore.value) {
                    
                    
                    break 
                }

                YouTube.playlistContinuation(currentProactiveToken, playlistIsCollaborative)
                    .onSuccess { playlistContinuationPage ->
                        val currentSongs = playlistSongs.value.toMutableList()
                        currentSongs.addAll(playlistContinuationPage.songs)
                        playlistSongs.value = applySongFilters(currentSongs)
                        addLocalAttributions(playlistContinuationPage.songs)
                        currentProactiveToken = playlistContinuationPage.continuation
                        
                        this@OnlinePlaylistViewModel.continuation = currentProactiveToken 
                    }.onFailure { throwable ->
                        reportException(throwable)
                        currentProactiveToken = null 
                    }
            }
            
        }
    }

    fun loadMoreSongs() {
        if (_isLoadingMore.value) return 
        
        val tokenForManualLoad = continuation ?: return 

        proactiveLoadJob?.cancel() 
        _isLoadingMore.value = true

        viewModelScope.launch(Dispatchers.IO) {
            YouTube.playlistContinuation(tokenForManualLoad, playlistIsCollaborative)
                .onSuccess { playlistContinuationPage ->
                    val currentSongs = playlistSongs.value.toMutableList()
                    currentSongs.addAll(playlistContinuationPage.songs)
                    playlistSongs.value = applySongFilters(currentSongs)
                    addLocalAttributions(playlistContinuationPage.songs)
                    continuation = playlistContinuationPage.continuation
                }.onFailure { throwable ->
                    reportException(throwable)
                }.also {
                    _isLoadingMore.value = false
                    
                    if (continuation != null && isActive) {
                        startProactiveBackgroundLoading()
                    }
                }
        }
    }

    fun retry() {
        proactiveLoadJob?.cancel()
        fetchInitialPlaylistData() 
    }

    private suspend fun applySongFilters(songs: List<SongItem>): List<SongItem> {
        val hideVideoSongs = context.dataStore.read(HideVideoSongsKey, false)
        val uniqueSongs = songs.distinctBy { it.id }
        if (!hideVideoSongs) return uniqueSongs

        val filtered = uniqueSongs.filterVideoSongs(true)
        // If filtering hides everything, keep original list to avoid false "empty playlist" UX.
        return if (filtered.isEmpty() && uniqueSongs.isNotEmpty()) uniqueSongs else filtered
    }

    private fun addLocalAttributions(songs: List<SongItem>) {
        val discovered = songs.mapNotNull { song ->
            song.playlistContributor?.let { contributor ->
                val contributorId = contributor.id?.takeIf { it.isNotBlank() } ?: contributor.name
                song.id to PlaylistAttribution(
                    channelId = contributorId,
                    channelTitle = contributor.name,
                    addedAt = null,
                    avatarUrl = null,
                )
            }
        }.toMap()
        if (discovered.isEmpty()) return
        localAttributions = localAttributions + discovered
        refreshAttributions()
    }

    private fun setRemoteAttributions(attributions: Map<String, PlaylistAttribution>) {
        remoteAttributions = attributions
        refreshAttributions()
    }

    private fun refreshAttributions() {
        val merged = remoteAttributions.toMutableMap()
        localAttributions.forEach { (songId, local) ->
            val remote = merged[songId]
            merged[songId] = if (remote == null) {
                local
            } else {
                local.copy(
                    addedAt = remote.addedAt ?: local.addedAt,
                    avatarUrl = local.avatarUrl ?: remote.avatarUrl,
                )
            }
        }
        attributions.value = merged
    }

    override fun onCleared() {
        super.onCleared()
        proactiveLoadJob?.cancel()
    }
}
