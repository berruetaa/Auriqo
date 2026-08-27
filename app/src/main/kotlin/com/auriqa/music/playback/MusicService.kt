

@file:Suppress("DEPRECATION")

package com.auriqo.music.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.SQLException
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Binder
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import com.music.innertube.models.WatchEndpoint
import com.auriqo.music.MainActivity
import com.auriqo.music.R
import com.auriqo.music.constants.AudioNormalizationKey
import com.auriqo.music.constants.AudioOffload
import com.auriqo.music.constants.AudioQualityKey
import com.auriqo.music.constants.AutoDownloadOnLikeKey
import com.auriqo.music.constants.AutoLoadMoreKey
import com.auriqo.music.constants.AutoSkipNextOnErrorKey
import com.auriqo.music.constants.AutomixCrossfadeKey
import com.auriqo.music.constants.CrossfadeDurationKey
import com.auriqo.music.constants.CrossfadeEnabledKey
import com.auriqo.music.constants.CrossfadeGaplessKey
import com.auriqo.music.constants.DisableLoadMoreWhenRepeatAllKey
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.auriqo.music.constants.DiscordActivityNameKey
import com.auriqo.music.constants.DiscordActivityTypeKey
import com.auriqo.music.constants.DiscordTokenKey
import com.auriqo.music.constants.EnableDiscordRPCKey
import com.auriqo.music.constants.EnableLastFMScrobblingKey
import com.auriqo.music.constants.HideExplicitKey
import com.auriqo.music.constants.HideVideoSongsKey
import com.auriqo.music.constants.HistoryDuration
import com.auriqo.music.constants.LastFMSessionKey
import com.auriqo.music.constants.LastFMUseNowPlaying
import com.auriqo.music.constants.LastFMUseSendLikes
import com.auriqo.music.constants.MediaSessionConstants.CommandToggleLike
import com.auriqo.music.constants.MediaSessionConstants.CommandToggleStartRadio
import com.auriqo.music.constants.PauseListenHistoryKey
import com.auriqo.music.constants.PauseOnMute
import com.auriqo.music.constants.PersistentQueueKey
import com.auriqo.music.constants.PersistentShuffleAcrossQueuesKey
import com.auriqo.music.constants.PlayerVolumeKey

import com.auriqo.music.constants.RememberShuffleAndRepeatKey
import com.auriqo.music.constants.RepeatModeKey
import com.auriqo.music.constants.ResumeOnBluetoothConnectKey
import com.auriqo.music.constants.ScrobbleDelayPercentKey
import com.auriqo.music.constants.ScrobbleDelaySecondsKey
import com.auriqo.music.constants.ScrobbleMinSongDurationKey
import com.auriqo.music.constants.ShowLyricsKey
import com.auriqo.music.constants.ShuffleModeKey
import com.auriqo.music.constants.ShufflePlaylistFirstKey
import com.auriqo.music.constants.PreloadLyricsEnabledKey
import com.auriqo.music.constants.PreloadNextSongEnabledKey
import com.auriqo.music.constants.PreloadNextSongLimitKey
import com.auriqo.music.constants.PreventDuplicateTracksInQueueKey
import com.auriqo.music.constants.SimilarContent
import com.auriqo.music.constants.SkipSilenceInstantKey
import com.auriqo.music.constants.SkipSilenceKey
import com.auriqo.music.constants.IpVersionKey
import com.music.innertube.models.IpVersion
import okhttp3.Dns
import java.net.InetAddress
import java.net.Inet4Address
import java.net.Inet6Address
import com.auriqo.music.db.MusicDatabase
import com.auriqo.music.db.entities.Event
import com.auriqo.music.db.entities.FormatEntity
import com.auriqo.music.db.entities.LyricsEntity
import com.auriqo.music.db.entities.RelatedSongMap
import com.auriqo.music.db.entities.Song
import com.auriqo.music.di.DownloadCache
import com.auriqo.music.di.PlayerCache
import com.auriqo.music.eq.EqualizerService
import com.auriqo.music.eq.audio.AutomixDuckAudioProcessor
import com.auriqo.music.eq.audio.CustomEqualizerAudioProcessor
import com.auriqo.music.eq.data.EQProfileRepository
import com.auriqo.music.extensions.SilentHandler
import com.auriqo.music.extensions.collect
import com.auriqo.music.extensions.collectLatest
import com.auriqo.music.extensions.currentMetadata
import com.auriqo.music.extensions.findNextMediaItemById
import com.auriqo.music.extensions.mediaItems
import com.auriqo.music.extensions.metadata
import com.auriqo.music.extensions.setOffloadEnabled
import com.auriqo.music.extensions.toEnum
import com.auriqo.music.extensions.toMediaItem
import com.auriqo.music.extensions.toPersistQueue
import com.auriqo.music.extensions.toQueue
import com.auriqo.music.echomusic.updater.downloadmanager.AuriqoNotificationProvider
import com.auriqo.music.lyrics.LyricsHelper
import com.auriqo.music.models.PersistPlayerState
import com.auriqo.music.models.PersistQueue
import com.auriqo.music.models.toMediaMetadata
import com.auriqo.music.db.entities.BeatInfoEntity
import com.auriqo.music.debug.DebugFaultPoint
import com.auriqo.music.debug.DebugFaultSpec
import com.auriqo.music.debug.DebugRuntime
import com.auriqo.music.playback.audio.BeatAnalyzer
import com.auriqo.music.playback.audio.SilenceDetectorAudioProcessor
import com.auriqo.music.playback.diagnostics.Media3PlaybackDiagnostics
import com.auriqo.music.playback.diagnostics.PlaybackCauseChainExtractor
import com.auriqo.music.playback.diagnostics.PlaybackDiagnostics
import com.auriqo.music.playback.diagnostics.PlaybackFailure
import com.auriqo.music.playback.diagnostics.PlaybackFailureClassifier
import com.auriqo.music.playback.diagnostics.PlaybackFailureStage
import com.auriqo.music.playback.diagnostics.PlaybackRedactor
import com.auriqo.music.playback.diagnostics.PlaybackResolutionException
import com.auriqo.music.playback.diagnostics.PlaybackTraceRecorder
import com.auriqo.music.playback.diagnostics.PlaybackTracingDataSource
import com.auriqo.music.playback.queues.EmptyQueue
import com.auriqo.music.playback.queues.Queue
import com.auriqo.music.playback.queues.YouTubeQueue
import com.auriqo.music.playback.queues.filterExplicit
import com.auriqo.music.playback.queues.filterVideoSongs
import com.auriqo.music.utils.CoilBitmapLoader
import com.auriqo.music.ui.screens.settings.DiscordPresenceManager
import com.auriqo.music.utils.NetworkConnectivityObserver
import com.auriqo.music.utils.ScrobbleManager
import com.auriqo.music.utils.SyncUtils
import com.auriqo.music.utils.YTPlayerUtils
import com.auriqo.music.utils.dataStore
import com.auriqo.music.utils.snapshot
import com.auriqo.music.utils.reportException
import com.auriqo.music.widget.AuriqoWidgetManager
import com.auriqo.music.widget.MusicWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import com.auriqo.music.utils.isLocalMediaId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

private const val INSTANT_SILENCE_SKIP_STEP_MS = 15_000L
private const val INSTANT_SILENCE_SKIP_SETTLE_MS = 350L
private const val MAX_SUPERSEDED_STREAM_RESOLUTION_RETRIES = 1

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var syncUtils: SyncUtils

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    @Inject
    lateinit var equalizerService: EqualizerService

    @Inject
    lateinit var eqProfileRepository: EQProfileRepository

    @Inject
    lateinit var widgetManager: AuriqoWidgetManager

    @Inject
    lateinit var listenTogetherManager: com.auriqo.music.listentogether.ListenTogetherManager
    

    private lateinit var audioManager: AudioManager
    // Wi-Fi Lock: Prevents modern Wi-Fi 6/7 routers from putting the Wi-Fi chip into
    // low-power sleep mode while music is actively streaming in the background.
    // Without this, the router's power-saving protocol (Target Wake Time) causes
    // packet delays, leading to audio buffering or playback stopping after the screen turns off.
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private lateinit var audioFocusController: AudioFocusController
    private var wasPlayingBeforeVolumeMute = false
    private var isPausedByVolumeMute = false
    var preferredDeviceId: Int? = null 
        private set

    private var crossfadeEnabled = false
    private var crossfadeDuration = 5000f
    private var crossfadeGapless = true
    private var crossfadeTriggerJob: Job? = null

    private var automixEnabled = false
    private var activeAutomixPlan: AutomixPlan? = null

    /** A secondary player buffered ahead of the trigger so the blend doesn't cold-start. */
    private data class PrebufferedTransition(
        val player: ExoPlayer,
        val plan: AutomixPlan?,
        val targetMediaId: String,
    )
    private var prebuffered: PrebufferedTransition? = null
    private val analysisDataSourceFactory by lazy { createDataSourceFactory() }
    private val beatAnalysisJobs = java.util.Collections.synchronizedMap(mutableMapOf<String, BeatAnalysisHandle>())
    private val immediateBeatAnalysisMutex = kotlinx.coroutines.sync.Mutex()
    private val lookaheadBeatAnalysisMutex = kotlinx.coroutines.sync.Mutex()

    private enum class BeatAnalysisPriority {
        IMMEDIATE,
        LOOKAHEAD,
    }

    private data class BeatAnalysisHandle(
        val priority: BeatAnalysisPriority,
        val job: Job,
    )

    // Single generous budget for both priorities: a lookahead fetch can be promoted to
    // immediate mid-download (see maybeAnalyzeBeat), so it must not have been started on a
    // shorter deadline that expires right when the track finally needs its beat data.
    private fun beatAnalysisTimeoutMs(priority: BeatAnalysisPriority): Long = 45_000L

    private data class AutomixPair(
        val currentId: String,
        val nextId: String,
    )

    /** Beat-aligned transition computed from cached BeatInfoEntity of both tracks. */
    private data class AutomixPlan(
        val currentId: String,
        val nextId: String,
        val triggerTimeMs: Long,
        val incomingStartMs: Long,
        val tempoRatio: Float,
        /** Harmonic correction on the incoming track's pitch, capped at ±3 semitones. */
        val pitchRatio: Float = 1f,
        /** DJ blend length: 16 beats of the outgoing track, clamped to sane bounds. */
        val overlapMs: Long,
    )

    private data class AutomixPlanResult(
        val plan: AutomixPlan?,
        val pairAnalyzed: Boolean,
    )

    /** Live state of the automix engine, surfaced in the player debug overlay. */
    data class AutomixDebugInfo(
        val status: String,
        val outBpm: Float? = null,
        val outConfidence: Float? = null,
        val outMixOutMs: Long? = null,
        val inBpm: Float? = null,
        val inConfidence: Float? = null,
        val inMixInMs: Long? = null,
        val triggerTimeMs: Long? = null,
        val incomingStartMs: Long? = null,
        val tempoRatio: Float? = null,
    )

    val automixDebugInfo = MutableStateFlow<AutomixDebugInfo?>(null)

    private val secondaryPlayerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            val mediaId = secondaryPlayer?.currentMediaItem?.mediaId
            val existingTrace = PlaybackDiagnostics.currentFor(mediaId)
            val trace = existingTrace
                ?: mediaId?.let { PlaybackDiagnostics.startResolution(it, "secondary_player") }
                ?: PlaybackDiagnostics.start(null, "secondary_player")
            Media3PlaybackDiagnostics.findHttpDetails(error)?.let(trace::httpStatus)
            val causes = PlaybackCauseChainExtractor.extract(error)
                .joinToString(" <- ") { cause ->
                    "${cause.className}:${cause.message.orEmpty()}"
                }
            trace.breadcrumb(
                "SECONDARY_PLAYER_ERROR",
                "media3=${Media3PlaybackDiagnostics.errorCodeName(error.errorCode)}(${error.errorCode}) " +
                    "type=${error::class.java.simpleName} causes=$causes",
            )
            if (existingTrace == null && mediaId != null) {
                PlaybackDiagnostics.finishResolution(mediaId, trace)
            }
            secondaryPlayer?.stop()
            secondaryPlayer?.clearMediaItems()
            secondaryPlayer = null
        }
    }

    private var scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private lateinit var wearSync: com.auriqo.music.wearsync.WearSyncManager

    private val binder = MusicBinder()

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)
    private var lastNetworkConnected: Boolean? = null
    private var streamNetworkGeneration = 0L

    private lateinit var audioQuality: com.auriqo.music.constants.AudioQuality
    private lateinit var ipVersion: IpVersion

    private var currentQueue: Queue = EmptyQueue
    var queueTitle: String? = null

    val currentMediaMetadata = MutableStateFlow<com.auriqo.music.models.MediaMetadata?>(null)
    private val currentSong by lazy {
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.stateIn(scope, SharingStarted.Lazily, null)
    }
    val currentSongLiked by lazy {
        currentSong
            .map { it?.song?.liked == true }
            .stateIn(scope, SharingStarted.Eagerly, false)
    }
    private val currentFormat by lazy {
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }
    }

    lateinit var playerVolume: MutableStateFlow<Float>
    val isMuted = MutableStateFlow(false)

    private fun restorePlayerVolume(volume: Float): Float =
        if (volume.isNaN() || volume <= 0f) 1f else volume.coerceAtMost(1f)

    fun toggleMute() {
        val newMutedState = !isMuted.value
        isMuted.value = newMutedState
        
        player.volume = if (newMutedState) 0f else playerVolume.value
    }

    fun setMuted(muted: Boolean) {
        isMuted.value = muted
        
        
        player.volume = if (muted) 0f else playerVolume.value
    }

    /**
     * Adjusts the output controlled by the Wear rotary crown. Local playback
     * follows the phone's media stream; Cast playback follows the receiver's
     * own volume instead.
     */
    fun adjustMediaVolume(direction: Int) {
        val normalizedDirection = direction.coerceIn(-1, 1)
        if (normalizedDirection == 0) return

        runCatching {
            val cast = castConnectionHandler
            if (cast?.isCasting?.value == true) {
                cast.setVolume(cast.castVolume.value + normalizedDirection * 0.05f)
            } else {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (normalizedDirection > 0) {
                        AudioManager.ADJUST_RAISE
                    } else {
                        AudioManager.ADJUST_LOWER
                    },
                    0,
                )
            }
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Unable to adjust media volume from Wear")
        }
    }

    fun setPreferredAudioDevice(deviceId: Int?) { 
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val deviceInfo = devices.find { it.id == deviceId }
            player.setPreferredAudioDevice(deviceInfo)
            preferredDeviceId = deviceId
        }
    }


    lateinit var sleepTimer: SleepTimer

    @Inject
    @PlayerCache
    lateinit var playerCache: SimpleCache

    @Inject
    @DownloadCache
    lateinit var downloadCache: SimpleCache

    lateinit var player: ExoPlayer
        private set
    @Volatile
    private var playbackMetadataSnapshot: Map<String, com.auriqo.music.models.MediaMetadata> = emptyMap()
    private var secondaryPlayer: ExoPlayer? = null
    private var fadingPlayer: ExoPlayer? = null
    val isCrossfading = MutableStateFlow(false)
    val isAutomixing = MutableStateFlow(false)
    private var crossfadeJob: Job? = null

    private lateinit var mediaSession: MediaLibrarySession

    
    private val playerInitialized = MutableStateFlow(false)
    val isPlayerReady: kotlinx.coroutines.flow.StateFlow<Boolean> = playerInitialized.asStateFlow()

    
    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow = _playerFlow.asStateFlow()

    private val playerSilenceProcessors = HashMap<Player, SilenceDetectorAudioProcessor>()
    private val playerDuckProcessors = HashMap<Player, AutomixDuckAudioProcessor>()


    private val instantSilenceSkipEnabled = MutableStateFlow(false)

    private var isAudioEffectSessionOpened = false
    private var loudnessEnhancer: LoudnessEnhancer? = null
    // Holds the outgoing track's enhancer alive through the crossfade so its normalization
    // isn't stripped mid-fade (which would make a heavily-cut track jump louder as it fades).
    private var fadingLoudnessEnhancer: LoudnessEnhancer? = null
    private var lastPresenceToken: String? = null


    private var lastPlaybackSpeed = 1.0f
    private var discordUpdateJob: kotlinx.coroutines.Job? = null

    private var scrobbleManager: ScrobbleManager? = null

    private var listenBrainzEnabled = false
    private var listenBrainzToken = ""
    private var listenBrainzCurrentStartTs: Long = 0L
    private var listenBrainzCurrentMediaId: String? = null

    // Cached playback preferences kept in sync with DataStore.
    private var cachedRepeatMode: Int = REPEAT_MODE_OFF
    private var cachedShuffleEnabled: Boolean = false
    private var cachedPreloadEnabled: Boolean = true
    private var cachedPreloadLimit: Int = 1
    private var cachedPreloadLyrics: Boolean = true

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())

    
    private var originalQueueSize: Int = 0

    private var consecutivePlaybackErr = 0
    private var retryJob: Job? = null
    private var retryCount = 0
    private var silenceSkipJob: Job? = null

    private val streamRecovery = StreamRecoveryCoordinator()
    private var streamRecoveryJob: Job? = null
    private val _terminalPlaybackFailure = MutableStateFlow<PlaybackFailure?>(null)
    val terminalPlaybackFailure: kotlinx.coroutines.flow.StateFlow<PlaybackFailure?> = _terminalPlaybackFailure.asStateFlow()
    private var lastPlaybackFailure: PlaybackFailure? = null

    /** Redacted playback state for the debug source set; never contains stream URLs. */
    internal fun debugStreamSnapshot(): StreamRecoveryCoordinator.DebugSnapshot = streamRecovery.debugSnapshot()

    internal fun debugPlaybackGeneration(): Long = streamRecovery.playbackGeneration()

    internal fun debugAudioFocusSnapshot(): AudioFocusController.DebugSnapshot? =
        if (::audioFocusController.isInitialized) audioFocusController.debugSnapshot else null

    /** Reused by every player and cache data source; new TCP/TLS pools are never made per song. */
    private val playbackHttpClient by lazy {
        val builder = OkHttpClient
            .Builder()
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val addresses = Dns.SYSTEM.lookup(hostname)
                    return when (this@MusicService.ipVersion) {
                        IpVersion.IPV4 -> addresses.filter { it is Inet4Address }.ifEmpty { addresses }
                        IpVersion.IPV6 -> addresses.filter { it is Inet6Address }.ifEmpty { addresses }
                        IpVersion.AUTO -> addresses
                    }
                }
            })
            .proxy(YouTube.proxy)
            .proxyAuthenticator { _, response ->
                YouTube.proxyAuth?.let { auth ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", auth)
                        .build()
                } ?: response.request
            }
        DebugRuntime.instance.networkEventListenerFactory()?.let(builder::eventListenerFactory)
        builder.build()
    }

    
    private val bypassCacheForQualityChange = mutableSetOf<String>()
    private val selectedFormatItags = java.util.concurrent.ConcurrentHashMap<String, Int>()
    private val excludedFormatItags = java.util.concurrent.ConcurrentHashMap<String, MutableSet<Int>>()

    
    private var currentMediaIdRetryCount = mutableMapOf<String, Int>()
    private val MAX_RETRY_PER_SONG = 3
    private val RETRY_DELAY_MS = 1000L

    
    var castConnectionHandler: CastConnectionHandler? = null
        private set

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (!player.isPlaying) {
                        scope.launch(Dispatchers.IO) {
                            DiscordPresenceManager.stop()
                        }
                    }
                }
                Intent.ACTION_SCREEN_ON -> {
                    if (player.isPlaying) {
                        scope.launch {
                            currentSong.value?.let { song ->
                                ensurePresenceManager()
                            }
                        }
                    }
                }
            }
        }
    }

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            val hasBluetooth = addedDevices?.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            } == true

            if (hasBluetooth) {
                if (dataStore.snapshot(ResumeOnBluetoothConnectKey, false)) {
                    if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                        player.play()
                    }
                }
            }
        }
    }

    override fun startForegroundService(service: Intent): android.content.ComponentName? {
        return try {
            super.startForegroundService(service)
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is android.app.ForegroundServiceStartNotAllowedException) {
                Timber.e(e, "Suppressed ForegroundServiceStartNotAllowedException in MusicService")
                null
            } else {
                throw e
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wearSync = com.auriqo.music.wearsync.WearSyncProvider.create(this)
        wearSync.start(scope)

        
        // Workaround for ForegroundServiceStartNotAllowedException
        setListener(object : Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                Timber.tag(TAG).e("ForegroundServiceStartNotAllowedException caught by MediaSessionService listener")
                reportException(Exception("ForegroundServiceStartNotAllowedException caught by MediaSessionService listener"))
            }
        })
        
        playerInitialized.value = false

        scrobbleManager = ScrobbleManager(scope)

        scope.launch {
            dataStore.data.map { it[EnableLastFMScrobblingKey] ?: false }.distinctUntilChanged().collect {
                scrobbleManager?.enableScrobbling = it
            }
        }
        scope.launch {
            dataStore.data.map { it[LastFMUseNowPlaying] ?: false }.distinctUntilChanged().collect {
                scrobbleManager?.useNowPlaying = it
            }
        }
        scope.launch {
            dataStore.data.map { it[LastFMUseSendLikes] ?: false }.distinctUntilChanged().collect {
                scrobbleManager?.useSendLikes = it
            }
        }
        scope.launch {
            dataStore.data.map { it[LastFMSessionKey] }.distinctUntilChanged().collect { sessionKey ->
                com.auriqa.music.utils.lastfm.LastFM.setSessionKey(sessionKey)
            }
        }        
        

        try {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.music_player),
                    NotificationManager.IMPORTANCE_LOW
                )
            )
            val pending = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )
            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.music_player))
                .setContentText("")
                .setSmallIcon(R.drawable.auriqo_notification_mark)
                .setContentIntent(pending)
                .setOngoing(true)
                .build()
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create foreground notification")
            reportException(e)
        }

        setMediaNotificationProvider(
            AuriqoNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.drawable.auriqo_notification_mark)
                },
        )
        player = createExoPlayer()
        player.addListener(this@MusicService)
        sleepTimer = SleepTimer(scope, player)
        player.addListener(sleepTimer)
        playerInitialized.value = true
        Timber.tag(TAG).d("Player successfully initialized")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        player.repeatMode = dataStore.snapshot(RepeatModeKey, REPEAT_MODE_OFF)

        
        if (dataStore.snapshot(RememberShuffleAndRepeatKey, true)) {
            player.shuffleModeEnabled = dataStore.snapshot(ShuffleModeKey, false)
        }
        updateNotification()

        
        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        val screenStateFilter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenStateReceiver, screenStateFilter)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)

        audioQuality = dataStore.snapshot(AudioQualityKey).toEnum(com.auriqo.music.constants.AudioQuality.OPUS)
        ipVersion = dataStore.snapshot(IpVersionKey).toEnum(IpVersion.AUTO)
        playerVolume = MutableStateFlow(restorePlayerVolume(dataStore.snapshot(PlayerVolumeKey, 1f)))

        audioFocusController = AudioFocusController(
            context = this,
            scope = scope,
            isPlaying = { player.isPlaying },
            isMuted = { isMuted.value },
            volume = { playerVolume.value },
            pause = { player.pause() },
            resume = {
                if (castConnectionHandler?.isCasting?.value != true) player.play()
            },
            setVolume = { player.volume = it },
            canResume = {
                playerInitialized.value && castConnectionHandler?.isCasting?.value != true
            },
        )

        
        initializeCast()

        
        scope.launch {
            eqProfileRepository.activeProfile.collect { profile ->
                if (profile != null) {
                    val result = equalizerService.applyProfile(profile)
                    if (result.isSuccess && player.playbackState == Player.STATE_READY && player.isPlaying) {
                        
                        
                        
                        player.seekTo(player.currentPosition)
                    }
                } else {
                    equalizerService.disable()
                    if (player.playbackState == Player.STATE_READY && player.isPlaying) {
                        player.seekTo(player.currentPosition)
                    }
                }
            }
        }

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                if (lastNetworkConnected != null && lastNetworkConnected != isConnected) {
                    streamNetworkGeneration++
                    preloadJob?.cancel()
                    // Route changes can invalidate signed CDN URLs. Discard look-ahead
                    // resolutions while retaining the current item until it actually needs a
                    // re-open; downloaded bytes remain in their separate cache.
                    streamRecovery.retainOnly(player.currentMediaItem?.mediaId)
                }
                lastNetworkConnected = isConnected
                isNetworkConnected.value = isConnected
                PlaybackDiagnostics.current()?.networkChanged(
                    connected = isConnected,
                    networkType = playbackNetworkType(),
                )
                if (isConnected && waitingForNetworkConnection.value) {
                    // A stream URL can be bound to the previous route/IP. Keep downloaded and
                    // byte-cache data intact, but force remote URL resolution for this network.
                    streamRecovery.retainOnly(null)
                    triggerRetry()
                }
                
                if (isConnected && player.isPlaying) {
                    val mediaId = player.currentMetadata?.id
                    if (mediaId != null) {
                        database.song(mediaId).first()?.let { song ->
                            ensurePresenceManager()
                        }
                    }
                }
            }
        }

        
        scope.launch {
            dataStore.data
                .map { 
                    val listenBrainz = it[com.auriqo.music.constants.ListenBrainzEnabledKey] ?: false
                    val dataSaver = it[com.auriqo.music.constants.DataSaverEnabledKey] ?: false
                    if (dataSaver) false else listenBrainz
                }
                .distinctUntilChanged()
                .collect { listenBrainzEnabled = it }
        }

        scope.launch {
            dataStore.data
                .map { it[com.auriqo.music.constants.ListenBrainzTokenKey] ?: "" }
                .distinctUntilChanged()
                .collect { listenBrainzToken = it }
        }

        var isFirstQualityEmit = true
        scope.launch {
            dataStore.data
                .map { 
                    val qualityStr = it[AudioQualityKey]
                    val quality = qualityStr?.let { value ->
                        com.auriqo.music.constants.AudioQuality.entries.find { enumVal -> enumVal.name == value }
                    } ?: com.auriqo.music.constants.AudioQuality.OPUS
                    val dataSaver = it[com.auriqo.music.constants.DataSaverEnabledKey] ?: false
                    if (dataSaver) com.auriqo.music.constants.AudioQuality.OPUS else quality
                }
                .distinctUntilChanged()
                .collect { newQuality ->
                    val oldQuality = audioQuality
                    audioQuality = newQuality

                    
                    if (isFirstQualityEmit) {
                        isFirstQualityEmit = false
                        Timber.tag("MusicService").i("QUALITY INIT: $newQuality")
                        return@collect
                    }

                    Timber.tag("MusicService").i("QUALITY CHANGED: $oldQuality -> $newQuality")

                    Timber.tag("MusicService").i("QUALITY CHANGED: $oldQuality -> $newQuality. Will take effect starting from the next song.")

                    // Clear upcoming stream resolutions so they fetch the new quality, while
                    // keeping only the current item's short-lived URL.
                    val currentMediaId = player.currentMediaItem?.mediaId
                    streamRecovery.retainOnly(currentMediaId)

                    // Re-trigger prefetch to fetch the next songs in the new quality
                    preloadUpcomingItems()
                }
        }

        
        scope.launch {
            dataStore.data
                .map { it[IpVersionKey]?.toEnum(IpVersion.AUTO) ?: IpVersion.AUTO }
                .distinctUntilChanged()
                .collect { newIpVersion ->
                    val oldIpVersion = ipVersion
                    ipVersion = newIpVersion

                    if (isFirstQualityEmit) return@collect

                    Timber.tag("MusicService").i("IP VERSION CHANGED: $oldIpVersion -> $newIpVersion")

                    
                    val mediaId = player.currentMediaItem?.mediaId ?: return@collect
                    val currentPosition = player.currentPosition
                    val currentIndex = player.currentMediaItemIndex
                    val wasPlaying = player.isPlaying

                    
                    streamRecovery.invalidateStream(mediaId)

                    
                    player.stop()
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()
                    if (wasPlaying) {
                        player.play()
                    }
                }
        }

        combine(playerVolume, isMuted) { volume, muted ->
            if (muted) 0f else volume
        }.collectLatest(scope) {
            player.volume = it
        }



        currentSong.debounce(1000).collect(scope) { song ->
            updateNotification()
            updateWidgetUI(player.isPlaying)
        }

        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { 
                val showLyrics = it[ShowLyricsKey] ?: false
                val dataSaver = it[com.auriqo.music.constants.DataSaverEnabledKey] ?: false
                if (dataSaver) false else showLyrics
            }.distinctUntilChanged(),
        ) { mediaMetadata, showLyrics ->
            mediaMetadata to showLyrics
        }.collectLatest(scope) { (mediaMetadata, showLyrics) ->
            if (showLyrics && mediaMetadata != null && database.lyrics(mediaMetadata.id)
                    .first() == null
            ) {
                val lyricsWithProvider = lyricsHelper.getLyrics(mediaMetadata)
                database.query {
                    upsert(
                        LyricsEntity(
                            id = mediaMetadata.id,
                            lyrics = lyricsWithProvider.lyrics,
                            provider = lyricsWithProvider.provider,
                        ),
                    )
                }
            }
        }

        dataStore.data
            .map { (it[SkipSilenceKey] ?: false) to (it[SkipSilenceInstantKey] ?: false) }
            .distinctUntilChanged()
            .collectLatest(scope) { (skipSilence, instantSkip) ->
                player.skipSilenceEnabled = skipSilence
                secondaryPlayer?.skipSilenceEnabled = skipSilence

                val enableInstant = skipSilence && instantSkip
                instantSilenceSkipEnabled.value = enableInstant

                playerSilenceProcessors.values.forEach { processor ->
                    processor.instantModeEnabled = enableInstant
                    if (!enableInstant) {
                        processor.resetTracking()
                    }
                }

                if (!enableInstant) {
                    silenceSkipJob?.cancel()
                }
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) { (format, normalizeAudio) -> setupLoudnessEnhancer()}

        combine(
            dataStore.data.map { it[AudioOffload] ?: false },
            dataStore.data.map { it[CrossfadeEnabledKey] ?: false }
        ) { offloadPref, crossfadeEnabled ->
             
             if (crossfadeEnabled) false else offloadPref
        }.distinctUntilChanged()
        .collectLatest(scope) { useOffload ->
             player.setOffloadEnabled(useOffload)
             secondaryPlayer?.setOffloadEnabled(useOffload)
        }



        combine(
            dataStore.data.map { prefs ->
                Triple(
                    prefs[CrossfadeEnabledKey] ?: false,
                    prefs[CrossfadeDurationKey] ?: 5f,
                    prefs[CrossfadeGaplessKey] ?: true
                )
            },
            listenTogetherManager.roomState
        ) { (enabled, duration, gapless), roomState ->
            
            Triple(enabled && roomState == null, duration, gapless)
        }
            .distinctUntilChanged()
            .collect(scope) { (enabled, duration, gapless) ->
                crossfadeEnabled = enabled
                crossfadeDuration = duration * 1000f
                crossfadeGapless = gapless
                if (enabled) {
                    prepareAutomixForCurrentPair()
                    scheduleCrossfade()
                } else {
                    crossfadeTriggerJob?.cancel()
                    crossfadeTriggerJob = null
                    automixDebugInfo.value = null
                }
            }

        dataStore.data
            .map { it[AutomixCrossfadeKey] ?: false }
            .distinctUntilChanged()
            .collect(scope) {
                automixEnabled = it
                if (it) {
                    prepareAutomixForCurrentPair()
                    scheduleCrossfade()
                } else {
                    crossfadeTriggerJob?.cancel()
                    crossfadeTriggerJob = null
                    activeAutomixPlan = null
                    automixDebugInfo.value = null
                    scheduleCrossfade()
                }
            }

        // Keep cached preferences in sync so Player.Listener callbacks can read
        // them without blocking the main thread.
        dataStore.data
            .map { it[RepeatModeKey] ?: REPEAT_MODE_OFF }
            .distinctUntilChanged()
            .collect(scope) { cachedRepeatMode = it }

        dataStore.data
            .map { it[ShuffleModeKey] ?: false }
            .distinctUntilChanged()
            .collect(scope) { cachedShuffleEnabled = it }

        dataStore.data
            .map { 
                val preload = it[PreloadNextSongEnabledKey] ?: true
                val dataSaver = it[com.auriqo.music.constants.DataSaverEnabledKey] ?: false
                if (dataSaver) false else preload
            }
            .distinctUntilChanged()
            .collect(scope) { cachedPreloadEnabled = it }

        dataStore.data
            .map { it[PreloadNextSongLimitKey] ?: 1 }
            .distinctUntilChanged()
            .collect(scope) { cachedPreloadLimit = it }

        dataStore.data
            .map { it[PreloadLyricsEnabledKey] ?: true }
            .distinctUntilChanged()
            .collect(scope) { cachedPreloadLyrics = it }


        if (dataStore.snapshot(PersistentQueueKey, true)) {
            val queueFile = filesDir.resolve(PERSISTENT_QUEUE_FILE)
            if (queueFile.exists()) {
                runCatching {
                    queueFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistQueue
                        }
                    }
                }.onSuccess { queue ->
                    runCatching {
                        
                        val restoredQueue = queue.toQueue()
                        
                        scope.launch {
                            playerInitialized.first { it }
                            if (isActive) {
                                playQueue(
                                    queue = restoredQueue,
                                    playWhenReady = false,
                                )
                            }
                        }
                    }.onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to restore persisted queue, clearing data")
                        clearPersistedQueueFiles()
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read persisted queue, clearing data")
                    clearPersistedQueueFiles()
                }
            }

            val automixFile = filesDir.resolve(PERSISTENT_AUTOMIX_FILE)
            if (automixFile.exists()) {
                runCatching {
                    automixFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistQueue
                        }
                    }
                }.onSuccess { queue ->
                    runCatching {
                        automixItems.value = queue.items.map { it.toMediaItem() }
                    }.onFailure { error ->
                        Timber.tag(TAG).w(error, "Failed to restore automix queue, clearing data")
                        clearPersistedQueueFiles()
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read automix queue, clearing data")
                    clearPersistedQueueFiles()
                }
            }

            
            val playerStateFile = filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE)
            if (playerStateFile.exists()) {
                runCatching {
                    playerStateFile.inputStream().use { fis ->
                        ObjectInputStream(fis).use { oos ->
                            oos.readObject() as PersistPlayerState
                        }
                    }
                }.onSuccess { playerState ->
                    
                    scope.launch {
                        delay(1000) 
                        
                        
                        
                        playerVolume.value = restorePlayerVolume(playerState.volume)

                        
                        if (playerState.currentMediaItemIndex < player.mediaItemCount) {
                            player.seekTo(playerState.currentMediaItemIndex, playerState.currentPosition)
                        }
                    }
                }.onFailure { error ->
                    Timber.tag(TAG).w(error, "Failed to read player state, clearing data")
                    clearPersistedQueueFiles()
                }
            }
        }

        
        scope.launch {
            while (isActive) {
                delay(30.seconds)
                if (dataStore.snapshot(PersistentQueueKey, true)) {
                    saveQueueToDisk()
                }
            }
        }

        
        scope.launch {
            while (isActive) {
                delay(10.seconds)
                if (dataStore.snapshot(PersistentQueueKey, true) && player.isPlaying) {
                    saveQueueToDisk()
                }
            }
        }
    }

    private fun createExoPlayer(): ExoPlayer {
        val eqProcessor = CustomEqualizerAudioProcessor()
        equalizerService.addAudioProcessor(eqProcessor)

        val duckProcessor = AutomixDuckAudioProcessor()

        val silenceProcessor = SilenceDetectorAudioProcessor { handleLongSilenceDetected() }

        
        val skipSilence = dataStore.snapshot(SkipSilenceKey, false)
        val instantSkip = dataStore.snapshot(SkipSilenceInstantKey, false)
        silenceProcessor.instantModeEnabled = skipSilence && instantSkip

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(createMediaSourceFactory())
            .setRenderersFactory(createRenderersFactory(eqProcessor, silenceProcessor, duckProcessor))
            .setLoadControl(
                DefaultLoadControl.Builder()
                    // The next track is resolved/warmed ahead of time, so startup can wait for
                    // a small amount of audio instead of the previous 750 ms gate. Rebuffering
                    // remains conservative to avoid trading instant start for audible stalls.
                    .setBufferDurationsMs(30_000, 50_000, 250, 1_000)
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build()
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .setDeviceVolumeControlEnabled(true)
            .build()

        playerSilenceProcessors[player] = silenceProcessor
        playerDuckProcessors[player] = duckProcessor

        player.apply {
                val offload = dataStore.snapshot(AudioOffload, false)
                val crossfade = dataStore.snapshot(CrossfadeEnabledKey, false)
                setOffloadEnabled(if (crossfade) false else offload)
                skipSilenceEnabled = dataStore.snapshot(SkipSilenceKey, false)
                addAnalyticsListener(PlaybackStatsListener(false, this@MusicService))

                
            }
        _playerFlow.value = player
        return player
    }

    /**
     * Acquires a high-performance Wi-Fi lock when playback starts.
     *
     * WIFI_MODE_FULL_HIGH_PERF tells the system to keep the Wi-Fi chip fully
     * active with minimal latency — disabling power-saving sleep cycles.
     * This is called every time [player.isPlaying] becomes true.
     */
    private fun acquireWifiLock() {
        if (wifiLock == null) {
            val wifiManager = applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
            wifiLock = wifiManager?.createWifiLock(
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "echo_music:wifi_lock"
            )
        }
        if (wifiLock?.isHeld == false) {
            wifiLock?.acquire()
            Timber.tag(TAG).d("Wi-Fi lock acquired")
        }
    }

    /** Starts a new logical trace and generation; a failed URL or exhausted budget is never reused. */
    fun retryCurrentPlayback() {
        if (!playerInitialized.value) return
        val mediaId = player.currentMediaItem?.mediaId ?: return
        val index = player.currentMediaItemIndex
        if (index == C.INDEX_UNSET || index !in 0 until player.mediaItemCount) return

        streamRecoveryJob?.cancel()
        retryJob?.cancel()
        preloadJob?.cancel()
        waitingForNetworkConnection.value = false
        _terminalPlaybackFailure.value = null
        lastPlaybackFailure = null
        excludedFormatItags.clear()
        selectedFormatItags.clear()
        val trace = PlaybackDiagnostics.start(mediaId, "manual_retry")
        trace.breadcrumb("USER_RETRY")
        excludedFormatItags.remove(mediaId)
        if (!mediaId.isLocalMediaId()) {
            streamRecovery.invalidateStream(mediaId)
        }
        streamRecovery.beginPlayback(mediaId, force = true)

        val position = player.currentPosition.coerceAtLeast(0L)
        val playWhenReady = player.playWhenReady
        player.seekTo(index, position)
        player.prepare()
        player.playWhenReady = playWhenReady
    }

    /**
     * Releases the Wi-Fi lock when playback is paused, stopped, or the service is destroyed.
     *
     * Releasing the lock allows the device to return to normal Wi-Fi power-saving
     * behaviour, preserving battery when music is not playing.
     */
    private fun releaseWifiLock() {
        if (wifiLock?.isHeld == true) {
            wifiLock?.release()
            Timber.tag(TAG).d("Wi-Fi lock released")
        }
    }

    private fun clearPersistedQueueFiles() {
        runCatching { filesDir.resolve(PERSISTENT_QUEUE_FILE).delete() }
        runCatching { filesDir.resolve(PERSISTENT_AUTOMIX_FILE).delete() }
        runCatching { filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).delete() }
    }

    fun hasAudioFocusForPlayback(): Boolean {
        return ::audioFocusController.isInitialized && audioFocusController.hasAudioFocus
    }

    private fun waitOnNetworkError() {
        if (waitingForNetworkConnection.value) return

        
        if (retryCount >= MAX_RETRY_COUNT) {
            finishNetworkRecoveryBudget()
            return
        }

        waitingForNetworkConnection.value = true
        PlaybackDiagnostics.current()?.recoveryStart(
            attempt = retryCount + 1,
            maxAttempts = MAX_RETRY_COUNT,
            reason = "NETWORK_OFFLINE",
        )

        
        retryJob?.cancel()
        retryJob = scope.launch {
            while (isActive && waitingForNetworkConnection.value) {
                val delayMs = minOf(3000L * (1 shl retryCount.coerceAtMost(4)), 30000L)
                Timber.tag(TAG).d("Waiting ${delayMs}ms before retry attempt ${retryCount + 1}/$MAX_RETRY_COUNT")
                delay(delayMs)

                retryCount++
                if (isNetworkConnected.value) {
                    triggerRetry()
                    return@launch
                }
                if (retryCount >= MAX_RETRY_COUNT) {
                    finishNetworkRecoveryBudget()
                    return@launch
                }
            }
        }
    }

    private fun finishNetworkRecoveryBudget() {
        Timber.tag(TAG).w("Max retry count ($MAX_RETRY_COUNT) reached, stopping playback")
        waitingForNetworkConnection.value = false
        PlaybackDiagnostics.current()?.recoveryEnd(
            attempt = retryCount,
            success = false,
            result = "network_retry_budget_exhausted",
        )
        handleFinalFailure(
            lastPlaybackFailure?.copy(
                terminal = true,
                attempt = retryCount,
                maxAttempts = MAX_RETRY_COUNT,
                recoveryActions = listOf(
                    com.auriqo.music.playback.diagnostics.PlaybackRecoveryAction(
                        action = "wait_for_network",
                        result = "budget_exhausted",
                        attempt = retryCount,
                    ),
                ),
            ),
        )
        retryCount = 0
    }

    private fun triggerRetry() {
        val wasWaiting = waitingForNetworkConnection.value
        waitingForNetworkConnection.value = false
        retryJob?.cancel()
        if (wasWaiting) {
            PlaybackDiagnostics.current()?.recoveryEnd(
                attempt = retryCount.coerceAtLeast(1),
                success = true,
                result = "network_reconnected",
            )
        }

        if (player.currentMediaItem != null) {
            
            
            if (retryCount > 3) {
                Timber.tag(TAG).d("Retry count > 3, attempting to refresh stream URL")
                val currentPosition = player.currentPosition
                player.seekTo(player.currentMediaItemIndex, currentPosition)
            }
            player.prepare()
            
            
        }
    }

    private fun skipOnError() {
        
        consecutivePlaybackErr += 2
        val nextWindowIndex = player.nextMediaItemIndex

        if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
            player.seekTo(nextWindowIndex, C.TIME_UNSET)
            player.prepare()
            
            if (castConnectionHandler?.isCasting?.value != true) {
                player.play()
            }
            return
        }

        player.pause()
        consecutivePlaybackErr = 0
    }

    private fun stopOnError() {
        player.pause()
    }

    private fun updateNotification() {
        val liked = currentSong.value?.song?.liked == true
        val nextRepeatMode =
            when (player.repeatMode) {
                REPEAT_MODE_OFF -> REPEAT_MODE_ALL
                REPEAT_MODE_ALL -> REPEAT_MODE_ONE
                REPEAT_MODE_ONE -> REPEAT_MODE_OFF
                else -> REPEAT_MODE_OFF
            }
        val buttons =
            listOf(
                CommandButton.Builder(
                    if (liked) CommandButton.ICON_HEART_FILLED else CommandButton.ICON_HEART_UNFILLED,
                )
                    .setDisplayName(getString(if (liked) R.string.action_remove_like else R.string.action_like))
                    .setCustomIconResId(if (liked) R.drawable.ic_heart else R.drawable.ic_heart_outline)
                    .setSessionCommand(CommandToggleLike)
                    .setEnabled(currentSong.value != null)
                    .build(),
                CommandButton.Builder(
                    if (player.shuffleModeEnabled) CommandButton.ICON_SHUFFLE_ON else CommandButton.ICON_SHUFFLE_OFF,
                )
                    .setDisplayName(
                        getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on),
                    )
                    .setCustomIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                    .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
                    .build(),
                CommandButton.Builder(
                    when (player.repeatMode) {
                        REPEAT_MODE_ONE -> CommandButton.ICON_REPEAT_ONE
                        REPEAT_MODE_ALL -> CommandButton.ICON_REPEAT_ALL
                        else -> CommandButton.ICON_REPEAT_OFF
                    },
                )
                    .setDisplayName(
                        getString(
                            when (player.repeatMode) {
                                REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                else -> R.string.repeat_mode_off
                            },
                        ),
                    )
                    .setCustomIconResId(
                        when (player.repeatMode) {
                            REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                            REPEAT_MODE_ALL -> R.drawable.repeat_on
                            else -> R.drawable.repeat
                        },
                    )
                    .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE, nextRepeatMode)
                    .build(),
                CommandButton.Builder(CommandButton.ICON_RADIO)
                    .setDisplayName(getString(R.string.start_radio))
                    .setCustomIconResId(R.drawable.radio)
                    .setSessionCommand(CommandToggleStartRadio)
                    .setEnabled(currentSong.value != null)
                    .build(),
            )
        mediaSession.setMediaButtonPreferences(buttons)
    }

    private suspend fun recoverSong(
        mediaId: String,
        playbackData: YTPlayerUtils.PlaybackData? = null,
        isOfflinePlayback: Boolean = false
    ) {
        val song = database.song(mediaId).first()
        val mediaMetadata = withContext(Dispatchers.Main) {
            player.findNextMediaItemById(mediaId)?.metadata
        } ?: return
        val duration = song?.song?.duration?.takeIf { it != -1 }
            ?: mediaMetadata.duration.takeIf { it != -1 }
            ?: if (isOfflinePlayback) -1 else (playbackData?.videoDetails ?: YTPlayerUtils.playerResponseForMetadata(mediaId)
                .getOrNull()?.videoDetails)?.lengthSeconds?.toInt()
            ?: -1
        database.query {
            if (song == null) insert(mediaMetadata.copy(duration = duration))
            else {
                var updatedSong = song.song
                if (song.song.duration == -1) {
                    updatedSong = updatedSong.copy(duration = duration)
                }
                
                if (song.song.isVideo != mediaMetadata.isVideoSong) {
                    updatedSong = updatedSong.copy(isVideo = mediaMetadata.isVideoSong)
                }
                if (updatedSong != song.song) {
                    update(updatedSong)
                }
            }
        }
        if (!isOfflinePlayback && !database.hasRelatedSongs(mediaId)) {
            val relatedEndpoint =
                YouTube.next(WatchEndpoint(videoId = mediaId)).getOrNull()?.relatedEndpoint
                    ?: return
            val relatedPage = YouTube.related(relatedEndpoint).getOrNull() ?: return
            database.query {
                relatedPage.songs
                    .map(SongItem::toMediaMetadata)
                    .onEach(::insert)
                    .map {
                        RelatedSongMap(
                            songId = mediaId,
                            relatedSongId = it.id
                        )
                    }
                    .forEach(::insert)
            }
        }
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        if (!scope.isActive) scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

        
        if (!playerInitialized.value) {
            Timber.tag(TAG).w("playQueue called before player initialization, queuing request")
            scope.launch {
                playerInitialized.first { it }
                playQueue(queue, playWhenReady)
            }
            return
        }

        // A user-selected queue is a new playback generation, even if it starts with the same id.
        streamRecoveryJob?.cancel()
        retryJob?.cancel()
        preloadJob?.cancel()
        waitingForNetworkConnection.value = false
        _terminalPlaybackFailure.value = null
        lastPlaybackFailure = null
        val requestTrace = PlaybackDiagnostics.startUserRequest(
            mediaId = queue.preloadItem?.id,
            source = "user_tap",
        )
        // Queue replacement invalidates all in-flight resolver tokens for discarded items while
        // preserving a valid first item when the caller supplied one.
        streamRecovery.retainOnly(queue.preloadItem?.id)
        streamRecovery.beginPlayback(null, force = true)
        currentQueue = queue
        queueTitle = null
        val persistShuffleAcrossQueues = dataStore.snapshot(PersistentShuffleAcrossQueuesKey, false)
        val previousShuffleEnabled = player.shuffleModeEnabled
        if (!persistShuffleAcrossQueues) {
            player.shuffleModeEnabled = false
        }
        
        originalQueueSize = 0
        if (queue.preloadItem != null) {
            player.setMediaItem(queue.preloadItem!!.toMediaItem())
            requestTrace.recordMediaItemCreated(queue.preloadItem!!.id, 0)
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        scope.launch(SilentHandler) {
            val initialStatus =
                withContext(Dispatchers.IO) {
                    queue.getInitialStatus()
                        .filterExplicit(dataStore.snapshot(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.snapshot(HideVideoSongsKey, false) || dataStore.snapshot(com.auriqo.music.constants.DataSaverEnabledKey, false))
                }
            if (queue.preloadItem != null && player.playbackState == STATE_IDLE) return@launch
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return@launch
            
            originalQueueSize = initialStatus.items.size
            if (queue.preloadItem != null) {
                val safeIndex = initialStatus.mediaItemIndex.coerceIn(0, (initialStatus.items.size - 1).coerceAtLeast(0))
                player.addMediaItems(
                    0,
                    initialStatus.items.subList(0, safeIndex)
                )
                player.addMediaItems(
                    initialStatus.items.subList(
                        (safeIndex + 1).coerceAtMost(initialStatus.items.size),
                        initialStatus.items.size
                    )
                )
                resyncCastQueueIfCasting()
            } else {
                val safeIndex = initialStatus.mediaItemIndex.coerceIn(0, (initialStatus.items.size - 1).coerceAtLeast(0))
                player.setMediaItems(
                    initialStatus.items,
                    safeIndex,
                    initialStatus.position,
                )
                player.prepare()
                player.playWhenReady = playWhenReady
            }

            requestTrace.recordMediaItemCreated(
                mediaId = player.currentMediaItem?.mediaId,
                queueIndex = player.currentMediaItemIndex,
            )

            
            if (player.shuffleModeEnabled) {
                val shufflePlaylistFirst = dataStore.snapshot(ShufflePlaylistFirstKey, false)
                applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
            }
        }
    }

    /**
     * Re-load the Cast queue from the local player when casting.
     * Called after the full radio queue has been loaded into the player.
     */
    private fun resyncCastQueueIfCasting() {
        if (castConnectionHandler?.isCasting?.value == true) {
            castConnectionHandler?.loadCurrentMedia()
        }
    }

    fun startRadioSeamlessly() {
        
        if (!playerInitialized.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player initialization")
            return
        }

        val currentMediaMetadata = player.currentMetadata ?: return

        val currentIndex = player.currentMediaItemIndex
        val currentMediaId = currentMediaMetadata.id

        scope.launch(SilentHandler) {
            
            val radioQueue = YouTubeQueue(
                endpoint = WatchEndpoint(
                    videoId = currentMediaId
                )
            )

            try {
                val initialStatus = withContext(Dispatchers.IO) {
                    radioQueue.getInitialStatus()
                        .filterExplicit(dataStore.snapshot(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.snapshot(HideVideoSongsKey, false) || dataStore.snapshot(com.auriqo.music.constants.DataSaverEnabledKey, false))
                }

                if (initialStatus.title != null) {
                    queueTitle = initialStatus.title
                }

                
                val radioItems = initialStatus.items.filter { item ->
                    item.mediaId != currentMediaId
                }

                if (radioItems.isNotEmpty()) {
                    val itemCount = player.mediaItemCount

                    if (itemCount > currentIndex + 1) {
                        player.removeMediaItems(currentIndex + 1, itemCount)
                    }

                    player.addMediaItems(currentIndex + 1, radioItems)
                    if (player.shuffleModeEnabled) {
                        val shufflePlaylistFirst = dataStore.snapshot(ShufflePlaylistFirstKey, false)
                        applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                    }
                }

                currentQueue = radioQueue
            } catch (e: Exception) {
                
                try {
                    val nextResult = withContext(Dispatchers.IO) {
                        YouTube.next(WatchEndpoint(videoId = currentMediaId)).getOrNull()
                    }
                    nextResult?.relatedEndpoint?.let { relatedEndpoint ->
                        val relatedPage = withContext(Dispatchers.IO) {
                            YouTube.related(relatedEndpoint).getOrNull()
                        }
                        relatedPage?.songs?.let { songs ->
                            val radioItems = songs
                                .filter { it.id != currentMediaId }
                                .map { it.toMediaItem() }
                                .filterExplicit(dataStore.snapshot(HideExplicitKey, false))
                                .filterVideoSongs(dataStore.snapshot(HideVideoSongsKey, false) || dataStore.snapshot(com.auriqo.music.constants.DataSaverEnabledKey, false))

                            if (radioItems.isNotEmpty()) {
                                val itemCount = player.mediaItemCount
                                if (itemCount > currentIndex + 1) {
                                    player.removeMediaItems(currentIndex + 1, itemCount)
                                }
                                player.addMediaItems(currentIndex + 1, radioItems)
                                if (player.shuffleModeEnabled) {
                                    val shufflePlaylistFirst = dataStore.snapshot(ShufflePlaylistFirstKey, false)
                                    applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
                                }
                            }
                        }
                    }
                } catch (_: Exception) {
                    
                }
            }
        }
    }

    fun getAutomixAlbum(albumId: String) {
        scope.launch(SilentHandler) {
            YouTube
                .album(albumId)
                .onSuccess {
                    getAutomix(it.album.playlistId)
                }
        }
    }

    fun getAutomix(playlistId: String) {
        if (dataStore.snapshot(SimilarContent, true) &&
            !(dataStore.snapshot(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)) {
            scope.launch(SilentHandler) {
                try {
                    
                    YouTube.next(WatchEndpoint(playlistId = playlistId))
                        .onSuccess { firstResult ->
                            YouTube.next(WatchEndpoint(playlistId = firstResult.endpoint.playlistId))
                                .onSuccess { secondResult ->
                                    automixItems.value = secondResult.items.map { song ->
                                        song.toMediaItem()
                                    }
                                }
                                .onFailure {
                                    
                                    if (firstResult.items.isNotEmpty()) {
                                        automixItems.value = firstResult.items.map { song ->
                                            song.toMediaItem()
                                        }
                                    }
                                }
                        }
                        .onFailure {
                            
                            val currentSong = player.currentMetadata
                            if (currentSong != null) {
                                
                                YouTube.next(WatchEndpoint(
                                    videoId = currentSong.id
                                )).onSuccess { radioResult ->
                                    val filteredItems = radioResult.items
                                        .filter { it.id != currentSong.id }
                                        .map { it.toMediaItem() }
                                    if (filteredItems.isNotEmpty()) {
                                        automixItems.value = filteredItems
                                    }
                                }.onFailure {
                                    
                                    YouTube.next(WatchEndpoint(videoId = currentSong.id)).getOrNull()?.relatedEndpoint?.let { relatedEndpoint ->
                                        YouTube.related(relatedEndpoint).onSuccess { relatedPage ->
                                            val relatedItems = relatedPage.songs
                                                .filter { it.id != currentSong.id }
                                                .map { it.toMediaItem() }
                                            if (relatedItems.isNotEmpty()) {
                                                automixItems.value = relatedItems

                                            }
                                        }
                                    }
                                }
                            }
                        }
                } catch (_: Exception) {
                    
                }
            }
        }
    }

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        addToQueue(listOf(item))
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        automixItems.value =
            automixItems.value.toMutableList().apply {
                removeAt(position)
            }
        playNext(listOf(item))
    }

    fun clearAutomix() {
        automixItems.value = emptyList()
    }

    fun playNext(items: List<MediaItem>) {
        val isCasting = castConnectionHandler?.isCasting?.value == true
        Timber.d("CastFlow.playNext: items=${items.size}, isCasting=$isCasting, playerItemCount=${player.mediaItemCount}")
        
        if ((player.mediaItemCount == 0 || player.playbackState == STATE_IDLE) && !isCasting) {
            Timber.d("CastFlow.playNext: empty queue, setting items directly")
            player.setMediaItems(items)
            player.prepare()
            player.play()
            return
        }

        
        if (dataStore.snapshot(PreventDuplicateTracksInQueueKey, false)) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }

            
            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
        }

        val insertIndex = player.currentMediaItemIndex + 1
        val shuffleEnabled = player.shuffleModeEnabled

        player.addMediaItems(insertIndex, items)
        player.prepare()

        // Sync new items to Cast queue after current item
        if (isCasting) {
            Timber.d("CastFlow.playNext: dispatching insertItemsAfterCurrent to Cast")
            scope.launch {
                castConnectionHandler?.insertItemsAfterCurrent(items)
            }
        }

        if (shuffleEnabled) {
            
            val timeline = player.currentTimeline
            if (!timeline.isEmpty) {
                val size = timeline.windowCount
                val currentIndex = player.currentMediaItemIndex

                
                val newIndices = (insertIndex until (insertIndex + items.size)).toSet()

                
                val orderAfter = mutableListOf<Int>()
                var idx = currentIndex
                while (true) {
                    idx = timeline.getNextWindowIndex(idx, Player.REPEAT_MODE_OFF, true)
                    if (idx == C.INDEX_UNSET) break
                    if (idx != currentIndex) orderAfter.add(idx)
                }

                val prevList = mutableListOf<Int>()
                var pIdx = currentIndex
                while (true) {
                    pIdx = timeline.getPreviousWindowIndex(pIdx, Player.REPEAT_MODE_OFF, true)
                    if (pIdx == C.INDEX_UNSET) break
                    if (pIdx != currentIndex) prevList.add(pIdx)
                }
                prevList.reverse() 

                val existingOrder = (prevList + orderAfter).filter { it != currentIndex && it !in newIndices }

                
                val nextBlock = (insertIndex until (insertIndex + items.size)).toList()
                val finalOrder = IntArray(size)
                var pos = 0
                finalOrder[pos++] = currentIndex
                nextBlock.forEach { if (it in 0 until size) finalOrder[pos++] = it }
                existingOrder.forEach { if (pos < size) finalOrder[pos++] = it }

                
                if (pos < size) {
                    for (i in 0 until size) {
                        if (!finalOrder.contains(i)) {
                            finalOrder[pos++] = i
                            if (pos == size) break
                        }
                    }
                }

                player.setShuffleOrder(DefaultShuffleOrder(finalOrder, System.currentTimeMillis()))
            }
        }
    }

    fun addToQueue(items: List<MediaItem>) {
        val isCasting = castConnectionHandler?.isCasting?.value == true
        Timber.d("CastFlow.addToQueue: items=${items.size}, isCasting=$isCasting, playerItemCount=${player.mediaItemCount}")
        
        if (dataStore.snapshot(PreventDuplicateTracksInQueueKey, false)) {
            val itemIds = items.map { it.mediaId }.toSet()
            val indicesToRemove = mutableListOf<Int>()
            val currentIndex = player.currentMediaItemIndex

            for (i in 0 until player.mediaItemCount) {
                if (i != currentIndex && player.getMediaItemAt(i).mediaId in itemIds) {
                    indicesToRemove.add(i)
                }
            }

            
            indicesToRemove.sortedDescending().forEach { index ->
                player.removeMediaItem(index)
            }
        }

        val countBeforeAppend = player.mediaItemCount

        // Suppress onTimelineChanged Cast sync — we handle it directly below
        player.addMediaItems(items)

        // Sync new items to end of Cast queue
        if (isCasting) {
            Timber.d("CastFlow.addToQueue: dispatching appendItemsToCastQueue to Cast")
            scope.launch {
                castConnectionHandler?.appendItemsToCastQueue(items)
            }
        }

        if (player.shuffleModeEnabled) {
            appendToShuffleOrder(countBeforeAppend)
        }
        player.prepare()
    }

    fun toggleLibrary() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let {
                val isInLibrary = it.song.inLibrary != null
                val token = if (isInLibrary) it.song.libraryRemoveToken else it.song.libraryAddToken

                
                token?.let { feedbackToken ->
                    YouTube.feedback(listOf(feedbackToken))
                }

                YouTube.toggleSongLibrary(it.song.id, !isInLibrary)
                
                database.query {
                    update(it.song.toggleLibrary())
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleLike() {
        scope.launch {
            val songToToggle = currentSong.first()
            songToToggle?.let {
                val song = it.song.toggleLike()
                database.query {
                    update(song)
                    syncUtils.likeSong(song)

                    
                    if (dataStore.snapshot(AutoDownloadOnLikeKey, false) && song.liked) {
                        
                        val downloadRequest =
                            androidx.media3.exoplayer.offline.DownloadRequest
                                .Builder(song.id, song.id.toUri())
                                .setCustomCacheKey(song.id)
                                .setData(song.title.toByteArray())
                                .build()
                        androidx.media3.exoplayer.offline.DownloadService.sendAddDownload(
                            this@MusicService,
                            ExoDownloadService::class.java,
                            downloadRequest,
                            false
                        )
                    }
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    private fun setupLoudnessEnhancer() {
        val audioSessionId = player.audioSessionId

        if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
            Timber.tag(TAG).w("setupLoudnessEnhancer: invalid audioSessionId ($audioSessionId), cannot create effect yet")
            return
        }

        
        if (loudnessEnhancer == null) {
            try {
                loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                Timber.tag(TAG).d("LoudnessEnhancer created for sessionId=$audioSessionId")
            } catch (e: Exception) {
                reportException(e)
                loudnessEnhancer = null
                return
            }
        }

        scope.launch {
            try {
                val currentMediaId = withContext(Dispatchers.Main) {
                    player.currentMediaItem?.mediaId
                }

                val normalizeAudio = withContext(Dispatchers.IO) {
                    dataStore.data.map { it[AudioNormalizationKey] ?: true }.first()
                }

                if (normalizeAudio && currentMediaId != null) {
                    val format = withContext(Dispatchers.IO) {
                        database.format(currentMediaId).first()
                    }

                    Timber.tag(TAG).d("Audio normalization enabled: $normalizeAudio")
                    Timber.tag(TAG).d("Format loudnessDb: ${format?.loudnessDb}, perceptualLoudnessDb: ${format?.perceptualLoudnessDb}")

                    
                    val loudness = format?.loudnessDb ?: format?.perceptualLoudnessDb

                    withContext(Dispatchers.Main) {
                        if (loudness != null) {
                            val loudnessDb = loudness.toFloat()
                            val targetGain = (-loudnessDb * 100).toInt()
                            val clampedGain = targetGain.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)

                            Timber.tag(TAG).d("Calculated raw normalization gain: $targetGain mB (from loudness: $loudnessDb)")

                            try {
                                loudnessEnhancer?.setTargetGain(clampedGain)
                                loudnessEnhancer?.enabled = true
                                Timber.tag(TAG).i("LoudnessEnhancer gain applied: $clampedGain mB")
                            } catch (e: Exception) {
                                Timber.tag(TAG).e(e, "Failed to apply loudness enhancement")
                                reportException(e)
                                releaseLoudnessEnhancer()
                            }
                        } else {
                            loudnessEnhancer?.enabled = false
                            Timber.tag(TAG).w("Normalization enabled but no loudness data available - no normalization applied")
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        loudnessEnhancer?.enabled = false
                        Timber.tag(TAG).d("setupLoudnessEnhancer: normalization disabled or mediaId unavailable")
                    }
                }
            } catch (e: Exception) {
                reportException(e)
                releaseLoudnessEnhancer()
            }
        }
    }

    private fun releaseLoudnessEnhancer() {
        try {
            loudnessEnhancer?.release()
            Timber.tag(TAG).d("LoudnessEnhancer released")
        } catch (e: Exception) {
            reportException(e)
            Timber.tag(TAG).e(e, "Error releasing LoudnessEnhancer: ${e.message}")
        } finally {
            loudnessEnhancer = null
        }
    }

    private fun openAudioEffectSession() {
        if (isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = true
        setupLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
        )
    }

    private fun closeAudioEffectSession() {
        if (!isAudioEffectSessionOpened) return
        isAudioEffectSessionOpened = false
        releaseLoudnessEnhancer()
        sendBroadcast(
            Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            },
        )
    }

    private var previousMediaItemIndex = C.INDEX_UNSET

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        val startsNewPlaybackGeneration =
            reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT ||
                player.currentMediaItemIndex != previousMediaItemIndex
        if (startsNewPlaybackGeneration) {
            streamRecoveryJob?.cancel()
            retryJob?.cancel()
            waitingForNetworkConnection.value = false
            _terminalPlaybackFailure.value = null
            lastPlaybackFailure = null
        }
        val trace = PlaybackDiagnostics.transitionTo(
            mediaId = mediaItem?.mediaId,
            force = startsNewPlaybackGeneration,
        )
        if (startsNewPlaybackGeneration) {
            trace.recordMediaItemCreated(mediaItem?.mediaId, player.currentMediaItemIndex)
            mediaItem?.mediaId?.let(excludedFormatItags::remove)
        }
        streamRecovery.beginPlayback(
            mediaItem?.mediaId,
            force = startsNewPlaybackGeneration,
        )
        // Stale plan belongs to the previous track; planner re-arms when the new one is READY.
        if (!isCrossfading.value) automixDebugInfo.value = null
        prepareAutomixForCurrentPair()

        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            if (cachedRepeatMode == REPEAT_MODE_ONE &&
                previousMediaItemIndex != C.INDEX_UNSET &&
                previousMediaItemIndex != player.currentMediaItemIndex) {

                player.seekTo(previousMediaItemIndex, 0)
            }
        }
        previousMediaItemIndex = player.currentMediaItemIndex

        lastPlaybackSpeed = -1.0f 

        preloadUpcomingItems()
        setupLoudnessEnhancer()

        discordUpdateJob?.cancel()

        scrobbleManager?.onSongStop()
        checkAndSubmitListenBrainzFinished()

        if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
            scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
            player.currentMediaItem?.mediaId?.let { mediaId ->
                if (listenBrainzCurrentMediaId != mediaId) {
                    listenBrainzCurrentMediaId = mediaId
                    listenBrainzCurrentStartTs = System.currentTimeMillis()
                }
                checkAndSubmitListenBrainzPlayingNow(mediaId)
            }
        }

        
        
        if (castConnectionHandler?.isCasting?.value == true &&
            castConnectionHandler?.isSyncingFromCast != true &&
            mediaItem != null) {
            val metadata = mediaItem.metadata
            if (metadata != null) {
                Timber.d("CastFlow.onMediaItemTransition: mediaId=${metadata.id}, reason=$reason")
                val navigated = castConnectionHandler?.navigateToMediaIfInQueue(metadata.id) ?: false
                Timber.d("CastFlow.onMediaItemTransition: navigated=$navigated")
                if (!navigated) {
                    Timber.d("CastFlow.onMediaItemTransition: item not in Cast queue, calling loadMedia")
                    castConnectionHandler?.loadMedia(metadata)
                }
            }
        }

        
        if (dataStore.snapshot(AutoLoadMoreKey, true) &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            player.mediaItemCount - player.currentMediaItemIndex <= 5 &&
            currentQueue.hasNextPage() &&
            !(dataStore.snapshot(DisableLoadMoreWhenRepeatAllKey, false) && player.repeatMode == REPEAT_MODE_ALL)
        ) {
            scope.launch(SilentHandler) {
                val mediaItems = withContext(Dispatchers.IO) {
                    currentQueue.nextPage()
                        .filterExplicit(dataStore.snapshot(HideExplicitKey, false))
                        .filterVideoSongs(dataStore.snapshot(HideVideoSongsKey, false) || dataStore.snapshot(com.auriqo.music.constants.DataSaverEnabledKey, false))
                }
                if (player.playbackState != STATE_IDLE && mediaItems.isNotEmpty()) {
                    val countBeforeAppend = player.mediaItemCount
                    player.addMediaItems(mediaItems)
                    if (player.shuffleModeEnabled) {
                        appendToShuffleOrder(countBeforeAppend)
                    }
                }
            }
        }

        
        if (dataStore.snapshot(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        val trace = PlaybackDiagnostics.currentFor(player.currentMediaItem?.mediaId)
        when (playbackState) {
            Player.STATE_BUFFERING -> trace?.buffering()
            Player.STATE_READY -> trace?.ready()
            Player.STATE_ENDED, Player.STATE_IDLE -> Unit
        }

        
        if (playbackState == Player.STATE_ENDED) {
            if (cachedRepeatMode == REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                player.seekTo(0, 0)
                player.prepare()
                player.play()
            }
        }

        
        if (dataStore.snapshot(PersistentQueueKey, true) && !isSilenceSkipping) {
            saveQueueToDisk()
        }

        if (playbackState == Player.STATE_READY) {
            consecutivePlaybackErr = 0
            retryCount = 0
            waitingForNetworkConnection.value = false
            retryJob?.cancel()

            
            player.currentMediaItem?.mediaId?.let { mediaId ->
                resetRetryCount(mediaId)
                Timber.tag(TAG).d("Playback successful for $mediaId, reset retry count")
            }
            scheduleCrossfade()
            prepareAutomixForCurrentPair()
        }

        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            scrobbleManager?.onSongStop()
            checkAndSubmitListenBrainzFinished()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        
        if (playWhenReady && castConnectionHandler?.isCasting?.value == true) {
            player.pause()
            return
        }

        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            if (playWhenReady) {
                isPausedByVolumeMute = false
            }

            if (!playWhenReady && !isPausedByVolumeMute) {
                wasPlayingBeforeVolumeMute = false
            }
        }

        if (playWhenReady) {
            setupLoudnessEnhancer()
        }
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED,
                Player.EVENT_MEDIA_ITEM_TRANSITION,
                EVENT_TIMELINE_CHANGED,
                EVENT_POSITION_DISCONTINUITY
            )
        ) {
            prepareAutomixForCurrentPair()
            scheduleCrossfade()
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady) {
                val focusGranted = audioFocusController.request()
                if (focusGranted) {
                    openAudioEffectSession()
                }
            } else {
                closeAudioEffectSession()
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }
        if (events.contains(EVENT_TIMELINE_CHANGED)) {
            playbackMetadataSnapshot = player.mediaItems
                .mapNotNull { item -> item.metadata?.let { item.mediaId to it } }
                .toMap()
            preloadUpcomingItems()
        }

        
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) {
            updateWidgetUI(player.isPlaying)
            if (player.isPlaying) {
                if (player.playbackState == Player.STATE_READY) {
                    // Media3 has no public audio-sink "first sample written" callback. READY +
                    // isPlaying is the closest stable signal: renderers are enabled and the
                    // playback position is advancing, unlike READY alone.
                    PlaybackDiagnostics.currentFor(player.currentMediaItem?.mediaId)?.firstAudio()
                }
                startWidgetUpdates()
                acquireWifiLock()
            } else {
                stopWidgetUpdates()
                releaseWifiLock()
            }
            if (!player.isPlaying && !events.containsAny(Player.EVENT_POSITION_DISCONTINUITY, Player.EVENT_MEDIA_ITEM_TRANSITION)) {
                scope.launch {
                    DiscordPresenceManager.stop()
                }
            }
        }

        
        if (events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_IS_PLAYING_CHANGED) && player.isPlaying) {
            val mediaId = player.currentMetadata?.id
            if (mediaId != null) {
                scope.launch {
                    
                    database.song(mediaId).first()?.let { song ->
                        ensurePresenceManager()
                    }
                }
            }
        }

        
        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            scrobbleManager?.onPlayerStateChanged(player.isPlaying, player.currentMetadata, duration = player.duration)
            
            if (player.isPlaying) {
                player.currentMediaItem?.mediaId?.let { mediaId ->
                    if (listenBrainzCurrentMediaId != mediaId) {
                        checkAndSubmitListenBrainzFinished()
                        listenBrainzCurrentMediaId = mediaId
                        listenBrainzCurrentStartTs = System.currentTimeMillis()
                        scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
                    }
                    checkAndSubmitListenBrainzPlayingNow(mediaId)
                }
            }
        }

    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        updateNotification()
        if (shuffleModeEnabled) {
            
            if (player.mediaItemCount == 0) return

            val shufflePlaylistFirst = dataStore.snapshot(ShufflePlaylistFirstKey, false)
            val currentIndex = player.currentMediaItemIndex
            val totalCount = player.mediaItemCount

            applyShuffleOrder(currentIndex, totalCount, shufflePlaylistFirst)
        }

        
        if (dataStore.snapshot(RememberShuffleAndRepeatKey, true)) {
            scope.launch {
                dataStore.edit { settings ->
                    settings[ShuffleModeKey] = shuffleModeEnabled
                }
            }
        }

        
        if (dataStore.snapshot(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }

        
        if (dataStore.snapshot(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
    }

    
    private fun applyShuffleOrder(
        currentIndex: Int,
        totalCount: Int,
        shufflePlaylistFirst: Boolean
    ) {
        if (totalCount == 0) return

        if (shufflePlaylistFirst && originalQueueSize > 0 && originalQueueSize < totalCount) {
            
            val originalIndices = (0 until originalQueueSize).filter { it != currentIndex }.toMutableList()
            val addedIndices = (originalQueueSize until totalCount).filter { it != currentIndex }.toMutableList()

            originalIndices.shuffle()
            addedIndices.shuffle()

            val shuffledIndices = IntArray(totalCount)
            var pos = 0
            shuffledIndices[pos++] = currentIndex

            if (currentIndex < originalQueueSize) {
                originalIndices.forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            } else {
                (0 until originalQueueSize).shuffled().forEach { shuffledIndices[pos++] = it }
                addedIndices.forEach { shuffledIndices[pos++] = it }
            }
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        } else {
            val shuffledIndices = IntArray(totalCount) { it }
            shuffledIndices.shuffle()
            
            val currentItemIndexInShuffled = shuffledIndices.indexOf(currentIndex)
            if (currentItemIndexInShuffled != -1) { 
                val temp = shuffledIndices[0]
                shuffledIndices[0] = shuffledIndices[currentItemIndexInShuffled]
                shuffledIndices[currentItemIndexInShuffled] = temp
            }
            player.setShuffleOrder(DefaultShuffleOrder(shuffledIndices, System.currentTimeMillis()))
        }
    }

    /**
     * Shuffles the items that were just appended to the end of the queue and schedules them
     * after everything that was already queued, keeping the existing shuffle order untouched.
     *
     * Rebuilding the whole order with [applyShuffleOrder] would discard a manual reorder made
     * from the queue screen and would schedule already played songs again, so it must only be
     * used when the queue itself is replaced, not when items are appended to it.
     *
     * @param countBeforeAppend number of items the queue had before the new items were added.
     */
    private fun appendToShuffleOrder(countBeforeAppend: Int) {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return

        val totalCount = timeline.windowCount
        if (countBeforeAppend < 0 || countBeforeAppend >= totalCount) return

        // Walk the current shuffle order and keep only the items that were already queued.
        val existingOrder = ArrayList<Int>(countBeforeAppend)
        var index = timeline.getFirstWindowIndex(true)
        while (index != C.INDEX_UNSET) {
            if (index < countBeforeAppend) existingOrder.add(index)
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
        }

        // Bail out on an unexpected timeline rather than building an invalid shuffle order.
        if (existingOrder.size != countBeforeAppend) return

        val appendedOrder = (countBeforeAppend until totalCount).shuffled()
        val finalOrder = (existingOrder + appendedOrder).toIntArray()

        player.setShuffleOrder(DefaultShuffleOrder(finalOrder, System.currentTimeMillis()))
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)
        if (playbackParameters.speed != lastPlaybackSpeed) {
            lastPlaybackSpeed = playbackParameters.speed
            discordUpdateJob?.cancel()

            
            discordUpdateJob = scope.launch {
                delay(1000)
                if (player.playWhenReady && player.playbackState == Player.STATE_READY) {
                    currentSong.value?.let { song ->
                        ensurePresenceManager()
                    }
                }
            }
        }
    }

    
    private fun getHttpResponseCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    private fun streamKey(
        mediaId: String,
        quality: com.auriqo.music.constants.AudioQuality,
    ): StreamRecoveryCoordinator.StreamKey = StreamRecoveryCoordinator.StreamKey(
        mediaId = mediaId,
        quality = quality.name,
        networkGeneration = streamNetworkGeneration,
    )

    private fun playbackNetworkType(): String {
        val network = connectivityManager.activeNetwork ?: return "offline"
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return "unknown"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "bluetooth"
            else -> "other"
        }
    }

    private fun traceForPlaybackError(mediaId: String?): PlaybackTraceRecorder {
        return PlaybackDiagnostics.currentFor(mediaId)
            ?: PlaybackDiagnostics.start(mediaId, "player_error").also {
                it.breadcrumb("TRACE_STARTED_AT_ERROR")
            }
    }

    private fun diagnosePlaybackError(
        error: PlaybackException,
        mediaId: String?,
        trace: PlaybackTraceRecorder,
        terminalOverride: Boolean? = false,
        attempt: Int = 1,
        maxAttempts: Int = 1,
        stage: PlaybackFailureStage = PlaybackFailureStage.PLAYER_STATE,
    ): PlaybackFailure {
        return PlaybackFailureClassifier.classify(
            Media3PlaybackDiagnostics.toFailureInput(
                error = error,
                traceId = trace.traceId,
                mediaId = mediaId,
                stage = stage,
                attempt = attempt,
                maxAttempts = maxAttempts,
                streamGeneration = streamRecovery.playbackGeneration(),
                cacheStatus = if (mediaId?.isLocalMediaId() == true) "local" else "remote",
                quality = if (::audioQuality.isInitialized) audioQuality.name else null,
                queueIndex = player.currentMediaItemIndex.takeIf { it != C.INDEX_UNSET },
                networkType = playbackNetworkType(),
                terminalOverride = terminalOverride,
            ),
        )
    }

    private fun isRejectedStreamFailure(error: PlaybackException): Boolean =
        error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
            when (getHttpResponseCode(error)) {
                403, 404, 410, 416, 429 -> true
                in 500..599 -> true
                else -> false
            }

    private fun isPageReloadError(error: PlaybackException): Boolean {
        val errorMessage = error.message?.lowercase() ?: ""
        val causeMessage = error.cause?.message?.lowercase() ?: ""
        val innerCauseMessage = error.cause?.cause?.message?.lowercase() ?: ""

        val reloadKeywords = listOf(
            "page needs to be reloaded",
            "pagina deve essere ricaricata",
            "la pagina deve essere ricaricata",
            "page must be reloaded",
            "reload",
            "ricaricata"
        )

        return reloadKeywords.any { keyword ->
            errorMessage.contains(keyword) ||
            causeMessage.contains(keyword) ||
            innerCauseMessage.contains(keyword)
        }
    }

    private fun hasNetworkTransportFailure(error: PlaybackException): Boolean {
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
            (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED
        ) {
            return true
        }

        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is java.net.ConnectException ||
                cause is java.net.UnknownHostException ||
                cause is java.net.SocketTimeoutException ||
                cause is java.net.SocketException ||
                cause is java.io.InterruptedIOException
            ) {
                return true
            }
            cause = cause.cause
        }
        return false
    }

    private fun isNetworkRelatedError(error: PlaybackException): Boolean =
        streamFailureKind(error) == StreamRecoveryCoordinator.FailureKind.Permanent &&
            hasNetworkTransportFailure(error)

    
    private fun isAudioRendererError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED ||
                (error.cause as? PlaybackException)?.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK
    }

    private fun isCacheOrStreamCorruptionError(error: PlaybackException): Boolean {
        return error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED
    }

    private fun streamFailureKind(
        error: PlaybackException,
        mediaId: String? = null,
    ): StreamRecoveryCoordinator.FailureKind {
        val hint = Media3PlaybackDiagnostics.hintFor(error)
        if (mediaId != null && !mediaId.isLocalMediaId() &&
            selectedFormatItags.containsKey(mediaId) &&
            hint in setOf(
                com.auriqo.music.playback.diagnostics.PlaybackFailureHint.DECODER_INIT_FAILED,
                com.auriqo.music.playback.diagnostics.PlaybackFailureHint.DECODING_FAILED,
            )
        ) {
            return StreamRecoveryCoordinator.FailureKind.AlternateFormat
        }

        when (hint) {
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.TIMEOUT,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.CONNECTION_FAILED,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.OFFLINE ->
                return StreamRecoveryCoordinator.FailureKind.Permanent

            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.CACHE_CORRUPTED,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.CACHE_POSITION_OUT_OF_RANGE,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.CONTAINER_MALFORMED,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.CONTAINER_UNSUPPORTED ->
                return StreamRecoveryCoordinator.FailureKind.CacheOrStreamCorruption

            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.PLAYER_RESPONSE_FAILED,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.PLAYER_JS_NOT_FOUND,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.SIGNATURE_FUNCTION_NOT_FOUND,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.SIGNATURE_DECIPHER_FAILED,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.N_TRANSFORM_NOT_FOUND,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.N_TRANSFORM_FAILED,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.POTOKEN_FAILED,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.STREAM_URL_EXPIRED,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.FORMAT_NOT_FOUND,
            com.auriqo.music.playback.diagnostics.PlaybackFailureHint.CONTENT_TYPE_INVALID ->
                return StreamRecoveryCoordinator.FailureKind.ReloadRequired

            else -> Unit
        }

        return when {
            isRejectedStreamFailure(error) ->
                if (getHttpResponseCode(error) == 429) {
                    StreamRecoveryCoordinator.FailureKind.RateLimited
                } else {
                    StreamRecoveryCoordinator.FailureKind.RejectedStream
                }
            isPageReloadError(error) -> StreamRecoveryCoordinator.FailureKind.ReloadRequired
            hasNetworkTransportFailure(error) -> StreamRecoveryCoordinator.FailureKind.Permanent
            error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ->
                StreamRecoveryCoordinator.FailureKind.UnclassifiedStreamIo
            isCacheOrStreamCorruptionError(error) ->
                StreamRecoveryCoordinator.FailureKind.CacheOrStreamCorruption
            else -> StreamRecoveryCoordinator.FailureKind.Permanent
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        
        if (!playerInitialized.value) {
            Timber.tag("PlaybackTrace").e(
                "PLAYER_ERROR_BEFORE_INIT type=${error::class.java.simpleName} " +
                    "media3=${Media3PlaybackDiagnostics.errorCodeName(error.errorCode)}(${error.errorCode})",
            )
            return
        }

        val mediaId = player.currentMediaItem?.mediaId
        val trace = traceForPlaybackError(mediaId)
        val diagnosticFailure = diagnosePlaybackError(error, mediaId, trace)
        lastPlaybackFailure = diagnosticFailure
        _terminalPlaybackFailure.value = null
        Media3PlaybackDiagnostics.findHttpDetails(error)?.let(trace::httpStatus)
        trace.breadcrumb("CLASSIFIED", diagnosticFailure.exactCode.name)
        if (mediaId != null && !mediaId.isLocalMediaId() &&
            streamFailureKind(error, mediaId) == StreamRecoveryCoordinator.FailureKind.AlternateFormat
        ) {
            selectedFormatItags[mediaId]?.let { itag ->
                excludedFormatItags.computeIfAbsent(mediaId) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
                    .add(itag)
                trace.formatFallback(itag, null, "decoder_failure")
            }
        }
        Timber.tag("PlaybackTrace").w(
            "[%s] PLAYER_ERROR mediaId=%s media3=%s(%d) type=%s message=%s",
            trace.traceId,
            PlaybackRedactor.sanitizeScalar(mediaId),
            Media3PlaybackDiagnostics.errorCodeName(error.errorCode),
            error.errorCode,
            error::class.java.simpleName,
            PlaybackRedactor.sanitizeText(error.message.orEmpty()),
        )
        when {
            isAudioRendererError(error) -> {
                Timber.tag(TAG).d("AudioTrack error detected (${error.errorCode}), performing safe recovery")
                handleAudioRendererError(mediaId)
                return
            }
            !isNetworkConnected.value &&
                (mediaId == null || !mediaId.isLocalMediaId()) &&
                !isRejectedStreamFailure(error) -> {
                Timber.tag(TAG).d("Network disconnected, waiting for connection")
                waitOnNetworkError()
                return
            }
            (mediaId == null || !mediaId.isLocalMediaId()) && isNetworkRelatedError(error) -> {
                Timber.tag(TAG).d("Network-related error detected, waiting for a bounded reconnect retry")
                waitOnNetworkError()
                return
            }
            mediaId != null &&
                !mediaId.isLocalMediaId() &&
                handleStreamFailure(mediaId, error, diagnosticFailure, trace) -> {
                return
            }
            mediaId != null &&
                mediaId.isLocalMediaId() &&
                handleLocalSourceFailure(mediaId, error, diagnosticFailure, trace) -> {
                return
            }
        }

        handleFinalFailure(diagnosticFailure)
    }

    /** Clears only the volatile cache keyed by the failed stream's media id. */
    private suspend fun invalidateVolatileStreamCache(mediaId: String) = withContext(Dispatchers.IO) {
        try {
            playerCache.removeResource(mediaId)
            Timber.tag(TAG).d("Cleared volatile player cache for $mediaId")
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to clear volatile player cache for $mediaId")
        }
    }

    /** Executes the shared player-state part of one coordinator-approved recovery. */
    private fun launchOneShotPlaybackRecovery(
        decision: StreamRecoveryCoordinator.RecoveryDecision.Recover,
        beforePrepare: suspend () -> Unit = {},
        trace: PlaybackTraceRecorder? = null,
    ): Job = scope.launch {
        val snapshot = decision.snapshot
        var recoveryEventRecorded = false
        try {
            beforePrepare()

            if (!playerInitialized.value ||
                !streamRecovery.isCurrentRecovery(decision.token) ||
                player.currentMediaItem?.mediaId != snapshot.mediaId ||
                player.currentMediaItemIndex != snapshot.queueIndex ||
                snapshot.queueIndex !in 0 until player.mediaItemCount ||
                player.getMediaItemAt(snapshot.queueIndex).mediaId != snapshot.mediaId
            ) {
                trace?.recoveryEnd(1, success = false, result = "superseded")
                recoveryEventRecorded = true
                return@launch
            }

            player.seekTo(snapshot.queueIndex, snapshot.positionMs)
            // If prepare reports an error synchronously, it must be classified as the second
            // terminal failure instead of being hidden behind the in-progress gate.
            streamRecovery.completeRecovery(decision.token)
            player.prepare()
            player.playWhenReady = snapshot.playWhenReady
            Timber.tag(TAG).d(
                "Reprepared ${snapshot.mediaId} at ${snapshot.positionMs}ms " +
                    "(playWhenReady=${snapshot.playWhenReady})",
            )
            trace?.recoveryEnd(1, success = true, result = "reprepare_requested")
            recoveryEventRecorded = true
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (!recoveryEventRecorded) {
                trace?.recoveryEnd(1, success = false, result = "cancelled")
            }
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Playback recovery failed for ${snapshot.mediaId}")
            if (!recoveryEventRecorded) {
                trace?.recoveryEnd(1, success = false, result = "reprepare_failed")
            }
            if (player.currentMediaItem?.mediaId == snapshot.mediaId) {
                handleFinalFailure(lastPlaybackFailure)
            }
        } finally {
            streamRecovery.completeRecovery(decision.token)
        }
    }

    /**
     * Central adapter from a Media3 error to one bounded stream recovery. URL cache invalidation
     * happens inside [streamRecovery]; this method never touches downloadCache or local media.
     */
    private fun handleStreamFailure(
        mediaId: String,
        error: PlaybackException,
        diagnosticFailure: PlaybackFailure? = null,
        trace: PlaybackTraceRecorder? = null,
    ): Boolean {
        val failureKind = streamFailureKind(error, mediaId)
        if (failureKind == StreamRecoveryCoordinator.FailureKind.Permanent) return false

        val queueIndex = player.currentMediaItemIndex
        if (queueIndex == C.INDEX_UNSET || queueIndex !in 0 until player.mediaItemCount) {
            return false
        }

        val snapshot = StreamRecoveryCoordinator.PlaybackSnapshot(
            mediaId = mediaId,
            queueIndex = queueIndex,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            playWhenReady = player.playWhenReady,
        )

        return when (val decision = streamRecovery.onFailure(snapshot, failureKind)) {
            is StreamRecoveryCoordinator.RecoveryDecision.Recover -> {
                retryJob?.cancel()
                waitingForNetworkConnection.value = false
                trace?.recoveryStart(
                    attempt = 1,
                    maxAttempts = 1,
                    reason = diagnosticFailure?.exactCode?.name ?: failureKind.name,
                )
                streamRecoveryJob = launchOneShotPlaybackRecovery(
                    decision = decision,
                    beforePrepare = {
                    if (decision.failure == StreamRecoveryCoordinator.FailureKind.RateLimited) {
                        delay(RATE_LIMIT_BACKOFF_MS)
                    }
                    invalidateVolatileStreamCache(mediaId)
                    if (decision.failure.refreshExtractorState) {
                        try {
                            withContext(Dispatchers.IO) {
                                YTPlayerUtils.refreshAfterStreamRejection(mediaId)
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.tag(TAG).w(
                                e,
                                "Extractor refresh failed; retrying with the invalidated URL cache",
                            )
                        }
                    }
                    },
                    trace = trace,
                )
                true
            }

            StreamRecoveryCoordinator.RecoveryDecision.RecoveryInProgress -> {
                trace?.breadcrumb("RECOVERY_DEDUPLICATED", mediaId)
                Timber.tag(TAG).d("Ignoring duplicate stream failure for $mediaId while recovery is in progress")
                true
            }

            StreamRecoveryCoordinator.RecoveryDecision.Exhausted -> {
                scope.launch { invalidateVolatileStreamCache(mediaId) }
                Timber.tag(TAG).w("Fresh stream also failed for $mediaId; not retrying again")
                val exhaustedFailure = (diagnosticFailure ?: lastPlaybackFailure)?.copy(
                    terminal = true,
                    attempt = 2,
                    maxAttempts = 1,
                    technicalMessage = "${diagnosticFailure?.technicalMessage ?: "stream failure"} recovery=exhausted",
                    recoveryActions = listOf(
                        com.auriqo.music.playback.diagnostics.PlaybackRecoveryAction(
                            action = "stream_re-resolve",
                            result = "exhausted",
                            attempt = 1,
                        ),
                    ),
                )
                handleFinalFailure(exhaustedFailure)
                true
            }

            StreamRecoveryCoordinator.RecoveryDecision.NotRecoverable -> false
        }
    }

    /**
     * Local URIs never go through stream resolution, but Media3 can still need a bounded
     * reprepare after a transient parser/range failure. Keep this separate from CDN recovery so
     * it neither clears download data nor wakes the YouTube extractor.
     */
    private fun handleLocalSourceFailure(
        mediaId: String,
        error: PlaybackException,
        diagnosticFailure: PlaybackFailure? = null,
        trace: PlaybackTraceRecorder? = null,
    ): Boolean {
        if (!isCacheOrStreamCorruptionError(error)) {
            return false
        }

        val queueIndex = player.currentMediaItemIndex
        if (queueIndex == C.INDEX_UNSET || queueIndex !in 0 until player.mediaItemCount) {
            return false
        }

        val snapshot = StreamRecoveryCoordinator.PlaybackSnapshot(
            mediaId = mediaId,
            queueIndex = queueIndex,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            playWhenReady = player.playWhenReady,
        )
        return when (
            val decision = streamRecovery.onFailure(
                snapshot,
                StreamRecoveryCoordinator.FailureKind.LocalSourceCorruption,
            )
        ) {
            is StreamRecoveryCoordinator.RecoveryDecision.Recover -> {
                retryJob?.cancel()
                streamRecoveryJob?.cancel()
                trace?.recoveryStart(
                    attempt = 1,
                    maxAttempts = 1,
                    reason = diagnosticFailure?.exactCode?.name ?: "local_source_recovery",
                )
                streamRecoveryJob = launchOneShotPlaybackRecovery(decision, trace = trace)
                true
            }

            StreamRecoveryCoordinator.RecoveryDecision.RecoveryInProgress -> true

            StreamRecoveryCoordinator.RecoveryDecision.Exhausted -> {
                handleFinalFailure(
                    diagnosticFailure?.copy(
                        terminal = true,
                        attempt = 2,
                        maxAttempts = 1,
                    ),
                )
                true
            }

            StreamRecoveryCoordinator.RecoveryDecision.NotRecoverable -> false
        }
    }

    
    private fun hasExceededRetryLimit(mediaId: String): Boolean {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        return currentRetries >= MAX_RETRY_PER_SONG
    }

    
    private fun incrementRetryCount(mediaId: String) {
        val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
        currentMediaIdRetryCount[mediaId] = currentRetries + 1
        Timber.tag(TAG).d("Retry count for $mediaId: ${currentRetries + 1}/$MAX_RETRY_PER_SONG")
    }

    
    private fun resetRetryCount(mediaId: String) {
        currentMediaIdRetryCount.remove(mediaId)
    }

    
    private fun handleAudioRendererError(mediaId: String?) {
        if (mediaId == null) {
            handleFinalFailure()
            return
        }

        if (hasExceededRetryLimit(mediaId)) {
            PlaybackDiagnostics.currentFor(mediaId)?.breadcrumb("AUDIO_RECOVERY_EXHAUSTED", MAX_RETRY_PER_SONG.toString())
            handleFinalFailure()
            return
        }

        incrementRetryCount(mediaId)
        val trace = PlaybackDiagnostics.currentFor(mediaId)
        trace?.recoveryStart(
            attempt = currentMediaIdRetryCount[mediaId] ?: 1,
            maxAttempts = MAX_RETRY_PER_SONG,
            reason = "AUDIO_TRACK",
        )

        retryJob?.cancel()
        retryJob = scope.launch {
            try {
                
                val wasPlaying = player.playWhenReady
                player.pause()
                Timber.tag(TAG).d("Paused playback due to AudioTrack error")

                
                
                delay(RETRY_DELAY_MS * 3) 

                
                if (!playerInitialized.value) {
                    Timber.tag(TAG).w("Player no longer initialized, aborting AudioTrack recovery")
                    trace?.recoveryEnd(
                        attempt = currentMediaIdRetryCount[mediaId] ?: 1,
                        success = false,
                        result = "player_released",
                    )
                    return@launch
                }

                val currentIndex = player.currentMediaItemIndex
                if (currentIndex != C.INDEX_UNSET) {
                    
                    val currentPosition = player.currentPosition
                    player.seekTo(currentIndex, currentPosition)
                    player.prepare()

                    Timber.tag(TAG).d("Retrying playback for $mediaId after AudioTrack error")

                    
                    if (wasPlaying) {
                        delay(500) 
                        if (hasAudioFocusForPlayback() && playerInitialized.value) {
                            if (castConnectionHandler?.isCasting?.value != true) {
                                player.play()
                            }
                        }
                    }
                    trace?.recoveryEnd(
                        attempt = currentMediaIdRetryCount[mediaId] ?: 1,
                        success = true,
                        result = "audio_renderer_reprepare",
                    )
                } else {
                    Timber.tag(TAG).w("Invalid media item index during AudioTrack recovery")
                    trace?.recoveryEnd(
                        attempt = currentMediaIdRetryCount[mediaId] ?: 1,
                        success = false,
                        result = "invalid_media_index",
                    )
                    handleFinalFailure()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                trace?.recoveryEnd(
                    attempt = currentMediaIdRetryCount[mediaId] ?: 1,
                    success = false,
                    result = "cancelled",
                )
                throw e
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Error during AudioTrack error recovery")
                trace?.recoveryEnd(
                    attempt = currentMediaIdRetryCount[mediaId] ?: 1,
                    success = false,
                    result = "reprepare_failed",
                )
                handleFinalFailure()
            }
        }
    }

    private fun handleFinalFailure(failure: PlaybackFailure? = lastPlaybackFailure) {
        val terminalFailure = failure?.copy(terminal = true) ?: return
        lastPlaybackFailure = terminalFailure
        _terminalPlaybackFailure.value = terminalFailure
        PlaybackDiagnostics.currentFor(terminalFailure.mediaId)?.terminalFailure(terminalFailure)
        if (dataStore.snapshot(AutoSkipNextOnErrorKey, false)) {
            Timber.tag(TAG).d("All recovery attempts exhausted, auto-skipping to next track")
            skipOnError()
        } else {
            Timber.tag(TAG).d("All recovery attempts exhausted, stopping playback")
            stopOnError()
        }
    }

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
        super.onDeviceVolumeChanged(volume, muted)
        val pauseOnMute = dataStore.snapshot(PauseOnMute, false)

        if ((volume == 0 || muted) && pauseOnMute) {
            if (player.isPlaying) {
                wasPlayingBeforeVolumeMute = true
                isPausedByVolumeMute = true
                player.pause()
            }
        } else if (volume > 0 && !muted && pauseOnMute) {
            if (wasPlayingBeforeVolumeMute && !player.isPlaying && castConnectionHandler?.isCasting?.value != true) {
                wasPlayingBeforeVolumeMute = false
                isPausedByVolumeMute = false
                player.play()
            }
        }
    }

    private fun createCacheDataSource(): CacheDataSource.Factory =
        CacheDataSource
            .Factory()
            .setCache(downloadCache)
            .setUpstreamDataSourceFactory(
                CacheDataSource
                    .Factory()
                    .setCache(playerCache)
                    .setUpstreamDataSourceFactory(
                        OkHttpDataSource.Factory(playbackHttpClient)
                    )
            ).setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

    
    private var isSilenceSkipping = false

    private fun handleLongSilenceDetected() {
        if (!instantSilenceSkipEnabled.value) return
        if (silenceSkipJob?.isActive == true) return

        silenceSkipJob = scope.launch {
            
            delay(200)
            performInstantSilenceSkip()
        }
    }

    private suspend fun performInstantSilenceSkip() {
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
        if (duration <= INSTANT_SILENCE_SKIP_STEP_MS) return

        isSilenceSkipping = true
        try {
            var hops = 0
            val silenceProcessor = playerSilenceProcessors[player] ?: return
            while (coroutineContext.isActive && instantSilenceSkipEnabled.value && silenceProcessor.isCurrentlySilent()) {
                val current = player.currentPosition
                val target = (current + INSTANT_SILENCE_SKIP_STEP_MS).coerceAtMost(duration - 500)

                if (target <= current) break

                
                silenceProcessor.resetTracking()
                player.seekTo(target)
                hops++

                if (hops >= 80 || target >= duration - 500) break

                delay(INSTANT_SILENCE_SKIP_SETTLE_MS)
            }
            if (hops > 0) {
                Timber.tag(TAG).d("Silence skip: jumped $hops times")
            }
        } finally {
            isSilenceSkipping = false
        }
    }

    private fun updateListenBrainz(title: String, artistNames: String, releaseName: String, durationMs: Long, isFinished: Boolean, startMs: Long = 0, endMs: Long = 0, positionMs: Long = 0) {
        val cleanToken = listenBrainzToken.trim()
        if (!listenBrainzEnabled || cleanToken.isBlank()) return
        scope.launch {
            if (isFinished) {
                com.auriqa.music.ui.screens.settings.ListenBrainzManager.submitFinished(
                    context = this@MusicService,
                    token = cleanToken,
                    title = title,
                    artistNames = artistNames,
                    releaseName = releaseName,
                    durationMs = durationMs,
                    startMs = startMs,
                    endMs = endMs
                )
            } else {
                com.auriqa.music.ui.screens.settings.ListenBrainzManager.submitPlayingNow(
                    context = this@MusicService,
                    token = cleanToken,
                    title = title,
                    artistNames = artistNames,
                    releaseName = releaseName,
                    durationMs = durationMs,
                    positionMs = positionMs
                )
            }
        }
    }

    private suspend fun currentPresenceSong(): Song? {
        val mediaId = withContext(Dispatchers.Main.immediate) {
            player.currentMediaItem?.mediaId
        } ?: return null
        return database.song(mediaId).firstOrNull()
    }

    private fun ensurePresenceManager() {
        if (DiscordPresenceManager.lastRpcStartTime != null && lastPresenceToken != null) {
            if (dataStore.snapshot(EnableDiscordRPCKey, true) && dataStore.snapshot(DiscordTokenKey, "").isNotBlank()) {
                DiscordPresenceManager.restart()
            }
            return
        }

        scope.launch {
            if (!dataStore.snapshot(EnableDiscordRPCKey, true)) {
                if (DiscordPresenceManager.lastRpcStartTime != null) {
                    try { DiscordPresenceManager.stop() } catch (_: Exception) {}
                    lastPresenceToken = null
                }
                return@launch
            }

            val key = dataStore.snapshot(DiscordTokenKey, "")
            if (key.isBlank()) {
                if (DiscordPresenceManager.lastRpcStartTime != null) {
                    try { DiscordPresenceManager.stop() } catch (_: Exception) {}
                    lastPresenceToken = null
                }
                return@launch
            }

            if (DiscordPresenceManager.lastRpcStartTime != null && lastPresenceToken == key) {
                return@launch
            }

            try {
                DiscordPresenceManager.stop()
                DiscordPresenceManager.start(
                    context = this@MusicService,
                    token = key,
                    songProvider = { currentPresenceSong() },
                    positionProvider = { player.currentPosition },
                    isPausedProvider = { !player.isPlaying }
                )
                lastPresenceToken = key
            } catch (ex: Exception) {
                Timber.tag(TAG).e(ex, "Failed to start presence manager")
            }
        }
    }

    private fun resolvePlaybackDataForStream(
        mediaId: String,
        quality: com.auriqo.music.constants.AudioQuality,
    ): YTPlayerUtils.PlaybackData {
        val queueMetadata = lookupPlaybackMetadata(
            playbackMetadataSnapshot.asSequence().map { it.key to it.value },
            mediaId,
        )
        val knownArtist = queueMetadata?.artists
            ?.joinToString { it.name }
            ?.replace(" - Topic", "")
        val knownTitle = queueMetadata?.title
        val knownDuration = queueMetadata?.duration?.let { if (it > 0) it * 1000L else null }

        // ResolvingDataSource.Factory exposes a synchronous resolver. This is an intentional
        // adapter on Media3's loading thread; keep Room and player reads out of this boundary and
        // never call it from the service or UI thread directly.
        return run {
            val playbackData = runBlocking(Dispatchers.IO) {
                applyDebugResolverFaultIfRequested()
                DebugRuntime.instance.withNetworkTraceSuspend(PlaybackDiagnostics.currentFor(mediaId)?.traceId) {
                    YTPlayerUtils.playerResponseForPlayback(
                        mediaId,
                        audioQuality = quality,
                        connectivityManager = connectivityManager,
                        context = this@MusicService,
                        knownArtist = knownArtist,
                        knownTitle = knownTitle,
                        knownDurationMs = knownDuration,
                        excludedItags = excludedFormatItags[mediaId]?.toSet().orEmpty(),
                    )
                }
            }.getOrElse { throwable ->
                when (throwable) {
                    is PlaybackException -> throw throwable
                    is PlaybackResolutionException -> throw PlaybackException(
                        throwable.message ?: getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    )

                    is java.net.ConnectException, is java.net.UnknownHostException -> {
                        throw PlaybackException(
                            getString(R.string.error_no_internet),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
                        )
                    }

                    is java.net.SocketTimeoutException -> {
                        throw PlaybackException(
                            getString(R.string.error_timeout),
                            throwable,
                            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
                        )
                    }

                    else -> throw PlaybackException(
                        getString(R.string.error_unknown),
                        throwable,
                        PlaybackException.ERROR_CODE_REMOTE_ERROR,
                    )
                }
            }
            requireNotNull(playbackData) { getString(R.string.error_unknown) }
        }
    }

    /**
     * Debug-only fault boundary. It is deliberately centralized here so production playback does
     * not acquire chaos branches in every resolver/cipher call site.
     */
    private fun applyDebugResolverFaultIfRequested() {
        val fault = DebugRuntime.instance.consumeFault(DebugFaultPoint.PLAYER_RESPONSE) ?: return
        if (fault.valueMs > 0L) android.os.SystemClock.sleep(fault.valueMs)
        when (fault.kind) {
            DebugFaultSpec.Kind.DELAY -> Unit
            DebugFaultSpec.Kind.RESOLUTION_TIMEOUT -> throw java.net.SocketTimeoutException(
                "Debug chaos: player response timeout",
            )
            DebugFaultSpec.Kind.OFFLINE -> throw java.net.UnknownHostException(
                "Debug chaos: offline",
            )
            DebugFaultSpec.Kind.EXPIRE_STREAM -> throw PlaybackResolutionException(
                "Debug chaos: expired stream",
                hint = com.auriqo.music.playback.diagnostics.PlaybackFailureHint.STREAM_URL_EXPIRED,
            )
            DebugFaultSpec.Kind.INVALIDATE_EXTRACTOR -> throw PlaybackResolutionException(
                "Debug chaos: extractor invalidated",
                hint = com.auriqo.music.playback.diagnostics.PlaybackFailureHint.PLAYER_JS_NOT_FOUND,
            )
            DebugFaultSpec.Kind.SIGNATURE_FAILURE -> throw PlaybackResolutionException(
                "Debug chaos: signature failure",
                hint = com.auriqo.music.playback.diagnostics.PlaybackFailureHint.SIGNATURE_DECIPHER_FAILED,
            )
            DebugFaultSpec.Kind.N_TRANSFORM_FAILURE -> throw PlaybackResolutionException(
                "Debug chaos: n transform failure",
                hint = com.auriqo.music.playback.diagnostics.PlaybackFailureHint.N_TRANSFORM_FAILED,
            )
            DebugFaultSpec.Kind.POTOKEN_FAILURE -> throw PlaybackResolutionException(
                "Debug chaos: PoToken failure",
                hint = com.auriqo.music.playback.diagnostics.PlaybackFailureHint.POTOKEN_FAILED,
            )
            DebugFaultSpec.Kind.FORMAT_FAILURE -> throw PlaybackResolutionException(
                "Debug chaos: selected format failure",
                hint = com.auriqo.music.playback.diagnostics.PlaybackFailureHint.FORMAT_NOT_FOUND,
            )
            DebugFaultSpec.Kind.HTTP_STATUS,
            DebugFaultSpec.Kind.DATASOURCE_TIMEOUT -> Unit
        }
    }

    private fun scheduleFormatPersistence(format: FormatEntity) {
        scope.launch(Dispatchers.IO) {
            runCatching { database.upsert(format) }
                .onFailure { reportException(it) }
        }
    }

    private fun createDataSourceFactory(): DataSource.Factory {
        return ResolvingDataSource.Factory(
            DefaultDataSource.Factory(
                this,
                PlaybackTracingDataSource.Factory(
                    createCacheDataSource(),
                    PlaybackDiagnostics::currentFor,
                ),
            )
        ) { dataSpec ->
            val mediaId = dataSpec.key ?: error("No media id")
            if (mediaId.isLocalMediaId()) {
                val localUri = android.net.Uri.parse(mediaId)
                try {
                    contentResolver.openFileDescriptor(localUri, "r")?.close()
                } catch (e: java.io.FileNotFoundException) {
                    throw androidx.media3.common.PlaybackException("Local file deleted", e, androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
                }
                return@Factory dataSpec
            }


            
            var shouldBypassCache = bypassCacheForQualityChange.contains(mediaId)

            val cachedLength = androidx.media3.datasource.cache.ContentMetadata.getContentLength(downloadCache.getContentMetadata(mediaId))
                .takeIf { it != androidx.media3.common.C.LENGTH_UNSET.toLong() } ?: -1L
            val isFullyDownloaded = cachedLength > 0 && downloadCache.isCached(mediaId, 0, cachedLength)

            val activeQualityInCache = streamRecovery.activeQuality(mediaId)?.let {
                runCatching { com.auriqo.music.constants.AudioQuality.valueOf(it) }.getOrNull()
            }
            val lockedQuality = activeQualityInCache ?: audioQuality
            val streamKey = streamKey(mediaId, lockedQuality)
            val trace = PlaybackDiagnostics.currentFor(mediaId)
            trace?.resolutionRequested(mediaId, lockedQuality.name)


            if (!shouldBypassCache) {
                if (isFullyDownloaded) {
                    trace?.breadcrumb("DOWNLOAD_CACHE_HIT", "full")
                    trace?.breadcrumb("CACHE_ORIGIN", "download")
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                    return@Factory dataSpec
                }

                if (downloadCache.isCached(
                        mediaId,
                        dataSpec.position,
                        if (dataSpec.length >= 0) dataSpec.length else 1
                )
                ) {
                    streamRecovery.cachedStream(streamKey)?.let { cached ->
                        cached.itag?.let { selectedFormatItags[mediaId] = it }
                        trace?.resolutionCacheHit(cached.expiresAtMs - System.currentTimeMillis())
                        trace?.streamSelected(cached.itag, cached.mimeType, cached.bitrate)
                        trace?.breadcrumb("CACHE_ORIGIN", "download")
                        scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                        return@Factory dataSpec.withUri(cached.url.toUri())
                    }
                    // Fall through to fetch real URL since it's only partially downloaded
                }

                if (playerCache.isCached(mediaId, dataSpec.position, CHUNK_LENGTH)) {
                    streamRecovery.cachedStream(streamKey)?.let { cached ->
                        cached.itag?.let { selectedFormatItags[mediaId] = it }
                        trace?.resolutionCacheHit(cached.expiresAtMs - System.currentTimeMillis())
                        trace?.streamSelected(cached.itag, cached.mimeType, cached.bitrate)
                        trace?.breadcrumb("CACHE_ORIGIN", "player_preload")
                        trace?.breadcrumb("FIRST_BYTES_WARMED", CHUNK_LENGTH.toString())
                        scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                        return@Factory dataSpec.withUri(cached.url.toUri())
                    }
                    Timber.tag(TAG).w("Ghost cache entry for $mediaId, re-fetching")
                    playerCache.removeResource(mediaId)
                }

                streamRecovery.cachedStream(streamKey)?.let { cached ->
                    cached.itag?.let { selectedFormatItags[mediaId] = it }
                    trace?.resolutionCacheHit(cached.expiresAtMs - System.currentTimeMillis())
                    trace?.streamSelected(cached.itag, cached.mimeType, cached.bitrate)
                    trace?.breadcrumb("CACHE_ORIGIN", "stream_resolution")
                    scope.launch(Dispatchers.IO) { recoverSong(mediaId, isOfflinePlayback = true) }
                    return@Factory dataSpec.withUri(cached.url.toUri())
                }
            } else {
                Timber.tag("MusicService").i("BYPASSING CACHE for $mediaId due to quality change")
            }

            trace?.resolutionCacheMiss(if (shouldBypassCache) "quality_bypass" else "not_cached")
            Timber.tag("MusicService").i("FETCHING STREAM: $mediaId | quality=$lockedQuality")
            var resolutionToken = streamRecovery.resolutionToken(mediaId)
            var resolvedPlayback: YTPlayerUtils.PlaybackData? = null
            for (attempt in 0..MAX_SUPERSEDED_STREAM_RESOLUTION_RETRIES) {
                val candidate = resolvePlaybackDataForStream(mediaId, lockedQuality)
                when (
                    streamRecovery.cacheStream(
                        key = streamKey,
                        url = candidate.streamUrl,
                        expiresAtMs = System.currentTimeMillis() +
                            (candidate.streamExpiresInSeconds * 1000L),
                        token = resolutionToken,
                        resolvedAtMs = System.currentTimeMillis(),
                        itag = candidate.format.itag,
                        mimeType = candidate.format.mimeType,
                        bitrate = candidate.format.bitrate,
                    )
                ) {
                    StreamRecoveryCoordinator.CacheWriteResult.Stored -> {
                        selectedFormatItags[mediaId] = candidate.format.itag
                        val excluded = excludedFormatItags[mediaId].orEmpty()
                        if (excluded.isNotEmpty()) {
                            trace?.formatFallback(
                                fromItag = excluded.firstOrNull(),
                                toItag = candidate.format.itag,
                                reason = "decoder_or_format_failure",
                            )
                        }
                        trace?.streamSelected(
                            candidate.format.itag,
                            candidate.format.mimeType,
                            candidate.format.bitrate,
                        )
                        resolvedPlayback = candidate
                        break
                    }

                    StreamRecoveryCoordinator.CacheWriteResult.Superseded -> {
                        Timber.tag(TAG).d(
                            "Discarded superseded stream resolution for $mediaId; " +
                                "resolving once for the current generation (attempt ${attempt + 1})",
                        )
                        resolutionToken = streamRecovery.resolutionToken(mediaId)
                    }

                    StreamRecoveryCoordinator.CacheWriteResult.Expired -> {
                        throw PlaybackException(
                            "Resolved stream expired before it could be used",
                            null,
                            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
                        )
                    }
                }
            }

            val nonNullPlayback = resolvedPlayback ?: throw PlaybackException(
                "Stream resolution was superseded repeatedly",
                null,
                PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            )
            run {
                val format = nonNullPlayback.format
                
                var targetCacheKey = mediaId
                
                if (shouldBypassCache) {
                    Timber.tag(TAG).i("Bypassed cache. Using custom cache key to prevent intercept.")
                    targetCacheKey = "${mediaId}_diff"
                }

                val loudnessDb = nonNullPlayback.audioConfig?.loudnessDb
                val perceptualLoudnessDb = nonNullPlayback.audioConfig?.perceptualLoudnessDb

                Timber.tag(TAG).d("Storing format for $mediaId with loudnessDb: $loudnessDb, perceptualLoudnessDb: $perceptualLoudnessDb")
                if (loudnessDb == null && perceptualLoudnessDb == null) {
                    Timber.tag(TAG).w("No loudness data available from YouTube for video: $mediaId")
                }

                if (!isFullyDownloaded || targetCacheKey == mediaId) {
                    scheduleFormatPersistence(
                        FormatEntity(
                            id = mediaId,
                            itag = format.itag,
                            mimeType = format.mimeType.split(";")[0],
                            codecs = format.mimeType.substringAfter("codecs=", "\"\"")
                                .substringBefore(";")
                                .removeSurrounding("\"")
                                .takeIf { it.isNotEmpty() }
                                ?: "unknown",
                            bitrate = format.bitrate,
                            sampleRate = format.audioSampleRate,
                            contentLength = format.contentLength ?: 0L,
                            loudnessDb = loudnessDb,
                            perceptualLoudnessDb = perceptualLoudnessDb,
                            playbackUrl = nonNullPlayback.playbackTracking?.videostatsPlaybackUrl?.baseUrl,
                        ),
                    )
                }
                scope.launch(Dispatchers.IO) { recoverSong(mediaId, nonNullPlayback) }

                
                if (bypassCacheForQualityChange.remove(mediaId)) {
                    Timber.tag("MusicService").d("Cleared bypass cache flag for $mediaId after fresh fetch")
                }

                val streamUrl = nonNullPlayback.streamUrl
                
                return@Factory dataSpec.buildUpon().setKey(targetCacheKey).setUri(streamUrl.toUri()).build()
            }
        }
    }

    private fun createMediaSourceFactory() =
        DefaultMediaSourceFactory(
            createDataSourceFactory(),
            androidx.media3.extractor.DefaultExtractorsFactory()
        )

    private fun createRenderersFactory(
        eqProcessor: CustomEqualizerAudioProcessor,
        silenceProcessor: SilenceDetectorAudioProcessor,
        duckProcessor: AutomixDuckAudioProcessor,
    ) =
        object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ) = DefaultAudioSink
                .Builder(this@MusicService)
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessorChain(
                    DefaultAudioSink.DefaultAudioProcessorChain(

                        arrayOf(
                            eqProcessor,
                            duckProcessor,
                            silenceProcessor,
                        ),
                        SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
                        SonicAudioProcessor(),
                    ),
                ).build()
        }

    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val historyDurationMs = dataStore.snapshot(HistoryDuration)?.times(1000f) ?: 30000f

        if (playbackStats.totalPlayTimeMs >= historyDurationMs &&
            !dataStore.snapshot(PauseListenHistoryKey, false)
        ) {
            database.query {
                incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                try {
                    insert(
                        Event(
                            songId = mediaItem.mediaId,
                            timestamp = LocalDateTime.now(),
                            playTime = playbackStats.totalPlayTimeMs,
                        ),
                    )
                } catch (_: SQLException) {
                }
            }
        }

        if (playbackStats.totalPlayTimeMs >= historyDurationMs) {
            scope.launch(Dispatchers.IO) {
                val playbackUrl = database.format(mediaItem.mediaId).first()?.playbackUrl
                    ?: YTPlayerUtils.playerResponseForMetadata(mediaItem.mediaId, null)
                        .getOrNull()?.playbackTracking?.videostatsPlaybackUrl?.baseUrl
                playbackUrl?.let {
                    YouTube.registerPlayback(null, playbackUrl)
                        .onFailure {
                            reportException(it)
                        }
                }
            }
        }
    }

    private fun saveQueueToDisk() {
        if (player.mediaItemCount == 0) {
            Timber.tag(TAG).d("Skipping queue save - no media items")
            return
        }

        try {
            
            val persistQueue = currentQueue.toPersistQueue(
                title = queueTitle,
                items = player.mediaItems.mapNotNull { it.metadata },
                mediaItemIndex = player.currentMediaItemIndex,
                position = player.currentPosition
            )

            val persistAutomix =
                PersistQueue(
                    title = "automix",
                    items = automixItems.value.mapNotNull { it.metadata },
                    mediaItemIndex = 0,
                    position = 0,
                )

            
            val persistPlayerState = PersistPlayerState(
                playWhenReady = player.playWhenReady,
                repeatMode = player.repeatMode,
                shuffleModeEnabled = player.shuffleModeEnabled,
                volume = restorePlayerVolume(playerVolume.value),
                currentPosition = player.currentPosition,
                currentMediaItemIndex = player.currentMediaItemIndex,
                playbackState = player.playbackState
            )

            runCatching {
                filesDir.resolve(PERSISTENT_QUEUE_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistQueue)
                    }
                }
                Timber.tag(TAG).d("Queue saved successfully")
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to save queue")
                reportException(it)
            }

            runCatching {
            filesDir.resolve(PERSISTENT_AUTOMIX_FILE).outputStream().use { fos ->
                ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistAutomix)
                    }
                }
                Timber.tag(TAG).d("Automix saved successfully")
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to save automix")
                reportException(it)
            }

            runCatching {
                filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).outputStream().use { fos ->
                    ObjectOutputStream(fos).use { oos ->
                        oos.writeObject(persistPlayerState)
                    }
                }
                Timber.tag(TAG).d("Player state saved successfully")
            }.onFailure {
                Timber.tag(TAG).e(it, "Failed to save player state")
                reportException(it)
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during queue save operation")
            reportException(e)
        }
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        wearSync.stop()
        streamRecoveryJob?.cancel()
        retryJob?.cancel()
        streamRecovery.beginPlayback(null, force = true)
        releasePrebuffered()

        try {
            unregisterReceiver(screenStateReceiver)
        } catch (e: Exception) {
            
        }
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        castConnectionHandler?.release()
        if (dataStore.snapshot(PersistentQueueKey, true)) {
            saveQueueToDisk()
        }
        DiscordPresenceManager.stop()
        connectivityObserver.unregister()
        releaseWifiLock()
        audioFocusController.release()
        releaseLoudnessEnhancer()
        try {
            fadingLoudnessEnhancer?.release()
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Failed releasing fading enhancer on destroy")
        } finally {
            fadingLoudnessEnhancer = null
        }
        mediaSession.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        playerSilenceProcessors.remove(player)
        
        
        
        player.release()
        discordUpdateJob?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        // Keep background playback alive when the user dismisses the UI while a song is
        // actually playing. If playback is paused/stopped, however, there is no reason to
        // retain the foreground service or its MediaSession notification.
        if (::player.isInitialized && !player.isPlaying) {
            Timber.tag(TAG).d("App task removed while playback is inactive; stopping service")

            // Stop the playback engine first so Media3 cannot promote the service again and
            // recreate the notification after it has been dismissed.
            player.stop()

            // Remove both the foreground-service notification and any notification last
            // published by Media3's notification provider.
            stopForeground(STOP_FOREGROUND_REMOVE)
            getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)

            // onDestroy() releases the MediaLibrarySession, player, audio focus and other
            // resources. Releasing the session there also removes Android's media controls.
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MusicWidgetReceiver.ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_LIKE -> {
                toggleLike()
            }
            MusicWidgetReceiver.ACTION_NEXT -> {
                player.seekToNext()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_PREVIOUS -> {
                player.seekToPrevious()
                updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_UPDATE_WIDGET -> {
                updateWidgetUI(player.isPlaying)
            }
            "com.auriqo.music.ACTION_CLEAR_SONG_CACHE" -> {
                val songId = intent.getStringExtra("songId")
                if (songId != null) {
                    streamRecovery.invalidateStream(songId)
                }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    
    private fun updateWidgetUI(isPlaying: Boolean) {
        scope.launch {
            try {
                val songData = currentSong.value
                val song = songData?.song
                val songTitle = song?.title ?: getString(R.string.no_song_playing)
                val artistName = songData?.artists?.joinToString(", ") { it.name } ?: getString(R.string.tap_to_open)
                val isLiked = songData?.song?.liked == true

                widgetManager.updateWidgets(
                    title = songTitle,
                    artist = artistName,
                    artworkUri = song?.thumbnailUrl,
                    isPlaying = isPlaying,
                    isLiked = isLiked,
                    duration = if (player.duration != C.TIME_UNSET) player.duration else 0,
                    currentPosition = player.currentPosition
                )
            } catch (e: Exception) {
                
            }
        }
    }

    private var widgetUpdateJob: Job? = null

    private fun startWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = scope.launch {
            while (isActive) {
                if (player.isPlaying) {
                    updateWidgetUI(true)
                }
                delay(200)
            }
        }
    }

    private fun stopWidgetUpdates() {
        widgetUpdateJob?.cancel()
        widgetUpdateJob = null
    }

    private fun shareSong() {
        val songData = currentSong.value
        val songId = songData?.song?.id ?: return

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "https://www.youtube.com/watch?v=$songId")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(shareIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    
    suspend fun getStreamUrl(mediaId: String): String? {
        return withContext(Dispatchers.IO) {
            try {
                val playbackData = YTPlayerUtils.playerResponseForPlayback(
                    videoId = mediaId,
                    audioQuality = audioQuality,
                    connectivityManager = connectivityManager,
                ).getOrNull()
                playbackData?.streamUrl
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to get stream URL for Cast")
                null
            }
        }
    }

    
    private fun initializeCast() {
        if (dataStore.snapshot(com.auriqo.music.constants.EnableGoogleCastKey, true)) {
            try {
                castConnectionHandler = CastConnectionHandler(this, scope, this)
                if (castConnectionHandler?.initialize() != true) {
                    castConnectionHandler?.release()
                    castConnectionHandler = null
                    timber.log.Timber.w("Google Cast not available on this device")
                } else {
                    timber.log.Timber.d("Google Cast initialized")
                }
            } catch (e: RuntimeException) {
                timber.log.Timber.e(e, "Google Play Services not available for Cast")
                castConnectionHandler?.release()
                castConnectionHandler = null
            } catch (e: Exception) {
                timber.log.Timber.e(e, "Failed to initialize Google Cast")
                castConnectionHandler?.release()
                castConnectionHandler = null
            }
        }
    }


    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) {
            prepareAutomixForCurrentPair()
            scheduleCrossfade()
        }
    }

    private fun currentAutomixPair(): AutomixPair? {
        val currentId = player.currentMediaItem?.mediaId ?: return null
        val repeatOne = cachedRepeatMode == REPEAT_MODE_ONE
        val nextId = if (repeatOne) {
            currentId
        } else {
            val nextIndex = player.nextMediaItemIndex
            if (nextIndex == C.INDEX_UNSET) return null
            player.getMediaItemAt(nextIndex).mediaId
        }
        return AutomixPair(currentId, nextId)
    }

    private fun prepareAutomixForCurrentPair() {
        if (!automixEnabled || !crossfadeEnabled || isCrossfading.value) return
        val pair = currentAutomixPair() ?: return
        maybeAnalyzeBeat(pair.currentId, BeatAnalysisPriority.IMMEDIATE)
        if (pair.nextId != pair.currentId) {
            maybeAnalyzeBeat(pair.nextId, BeatAnalysisPriority.IMMEDIATE)
        }
    }

    private fun isAutomixPlanCurrent(plan: AutomixPlan): Boolean {
        val pair = currentAutomixPair() ?: return false
        return pair.currentId == plan.currentId && pair.nextId == plan.nextId
    }

    private fun scheduleCrossfade() {
        crossfadeTriggerJob?.cancel()
        crossfadeTriggerJob = null
        releasePrebuffered()
        if (!crossfadeEnabled || player.duration == C.TIME_UNSET || player.duration <= crossfadeDuration) return
        if (crossfadeGapless && isNextItemGapless()) return
        if (!player.hasNextMediaItem() && player.repeatMode != REPEAT_MODE_ONE) return

        val baseTriggerTime = player.duration - crossfadeDuration.toLong()
        if (baseTriggerTime - player.currentPosition <= 0) return

        val targetMediaId = player.currentMediaItem?.mediaId
        val trackDuration = player.duration

        crossfadeTriggerJob = scope.launch {
            if (!automixEnabled) automixDebugInfo.value = null
            // Plan first: it enqueues analysis for current+next, which must not wait
            // behind slower far-queue lookahead fetches.
            val planResult = if (automixEnabled) {
                computeAutomixPlan(baseTriggerTime, trackDuration)
            } else {
                AutomixPlanResult(plan = null, pairAnalyzed = false)
            }
            val plan = planResult.plan
            if (automixEnabled && planResult.pairAnalyzed) analyzeUpcomingTracks()
            val triggerTime = plan?.triggerTimeMs ?: baseTriggerTime
            if (triggerTime - player.currentPosition <= 0) return@launch

            // Poll playback position instead of a wall-clock delay: position freezes on
            // pause, so the trigger can't misfire while paused and get lost.
            var prebufferStarted = false
            while (isActive) {
                if (player.currentMediaItem?.mediaId != targetMediaId) return@launch
                val remaining = triggerTime - player.currentPosition
                if (remaining <= 0) break
                if (!prebufferStarted && remaining <= PREBUFFER_LEAD_MS) {
                    prebufferStarted = true
                    prebufferSecondaryPlayer(plan)
                }
                delay(minOf(remaining, 250L))
            }
            if (isActive && player.isPlaying && player.currentMediaItem?.mediaId == targetMediaId && !sleepTimer.pauseWhenSongEnd) {
                if (plan != null && !isAutomixPlanCurrent(plan)) {
                    scheduleCrossfade()
                    return@launch
                }
                startCrossfade(plan)
            }
        }
    }

    /**
     * Builds a beat-aligned transition from cached beat analysis of the outgoing and
     * incoming tracks. Returns null (plain crossfade fallback) when either track lacks
     * usable analysis; kicks off lazy analysis in that case so a later transition can align.
     */
    private suspend fun computeAutomixPlan(baseTriggerTime: Long, trackDuration: Long): AutomixPlanResult {
        val pair = currentAutomixPair() ?: return AutomixPlanResult(plan = null, pairAnalyzed = false)
        val currentId = pair.currentId
        val nextId = pair.nextId

        val (outBeat, inBeat) = withContext(Dispatchers.IO) {
            database.beatInfo(currentId) to database.beatInfo(nextId)
        }
        if (outBeat == null) maybeAnalyzeBeat(currentId, BeatAnalysisPriority.IMMEDIATE)
        if (inBeat == null && nextId != currentId) maybeAnalyzeBeat(nextId, BeatAnalysisPriority.IMMEDIATE)
        val partialDebug = AutomixDebugInfo(
            status = "",
            outBpm = outBeat?.bpm, outConfidence = outBeat?.confidence, outMixOutMs = outBeat?.mixOutPointMs,
            inBpm = inBeat?.bpm, inConfidence = inBeat?.confidence, inMixInMs = inBeat?.mixInPointMs,
        )
        if (outBeat == null || inBeat == null) {
            Timber.tag(TAG).d(
                "Automix fallback: beat info missing (current=%s next=%s)",
                outBeat != null, inBeat != null
            )
            automixDebugInfo.value = partialDebug.copy(
                status = "fallback: analysis pending (" +
                    (if (outBeat == null) "current" else "") +
                    (if (outBeat == null && inBeat == null) "+" else "") +
                    (if (inBeat == null) "next" else "") + ")"
            )
            return AutomixPlanResult(plan = null, pairAnalyzed = false)
        }
        if (outBeat.confidence < 0.3f || inBeat.confidence < 0.3f || outBeat.bpm <= 0f || inBeat.bpm <= 0f) {
            Timber.tag(TAG).d(
                "Automix fallback: low confidence (out=%.2f/%.0fbpm in=%.2f/%.0fbpm)",
                outBeat.confidence, outBeat.bpm, inBeat.confidence, inBeat.bpm
            )
            automixDebugInfo.value = partialDebug.copy(status = "fallback: low confidence")
            return AutomixPlanResult(plan = null, pairAnalyzed = true)
        }

        val periodMs = (60_000f / outBeat.bpm).toDouble()

        // DJ blend: 16 beats of the outgoing track (4 bars), 6-16s bounds.
        val overlapMs = (16 * periodMs).toLong().coerceIn(6_000L, 16_000L)

        // Dynamic mix-out: start the transition where the song's body ends (outro begins)
        // rather than a fixed distance from the end. Sentinel <= 0 means "no outro found".
        val latestTrigger = trackDuration - overlapMs
        val mixOut = outBeat.mixOutPointMs?.takeIf { it > 0 }
        val effectiveTrigger = mixOut?.coerceAtMost(latestTrigger) ?: latestTrigger

        // Snap the fade start onto an 8-beat phrase boundary of the outgoing track's grid.
        // Anchor past the current position so re-planning late (pause/seek near the end)
        // still lands on the next musical boundary instead of giving up.
        val phraseMs = periodMs * 8
        val anchor = maxOf(effectiveTrigger, player.currentPosition + 1000)
        val k = ((anchor - outBeat.firstBeatOffsetMs) / phraseMs).toLong()
        var triggerTime = (outBeat.firstBeatOffsetMs + k * phraseMs).toLong()
        if (triggerTime < anchor) triggerTime = (outBeat.firstBeatOffsetMs + (k + 1) * phraseMs).toLong()
        // Phrase-snapping can push triggerTime past latestTrigger by up to ~1 phrase.
        // The outgoing player keeps its own playlist and keeps advancing in real time
        // during the fade, so the full overlap must fit before its natural end or it
        // auto-advances on its own mid-fade — playing the next track a second time (or
        // wrapping to track 1 on repeat-all). Rather than discarding the whole plan for
        // a few seconds of overshoot, shrink the overlap to whatever room is actually
        // left; only fall back if that leaves too little room to blend at all.
        val roomMs = trackDuration - 500 - triggerTime
        val effectiveOverlapMs = overlapMs.coerceAtMost(roomMs)
        if (effectiveOverlapMs < 3000L || triggerTime >= trackDuration - 3000) {
            Timber.tag(TAG).d("Automix fallback: trigger %d out of range (pos=%d dur=%d overlap=%d)", triggerTime, player.currentPosition, trackDuration, overlapMs)
            automixDebugInfo.value = partialDebug.copy(status = "fallback: trigger out of range")
            return AutomixPlanResult(plan = null, pairAnalyzed = true)
        }

        // Fold octave errors, then cap pitch-preserving stretch at ±8%.
        var tempoRatio = outBeat.bpm / inBeat.bpm
        while (tempoRatio > 1.5f) tempoRatio /= 2f
        while (tempoRatio < 0.667f) tempoRatio *= 2f
        if (tempoRatio !in 0.92f..1.08f) tempoRatio = 1f

        // Harmonic correction: compare keys via their relative-major pitch class (a minor
        // key's relative major sits 3 semitones up), then pitch-shift the incoming track
        // the minimal circular distance to align. Skip when either key is unknown, when
        // they already match, or when the shift would be large enough to sound worse than
        // the clash it's fixing (>3 semitones).
        var pitchRatio = 1f
        val outKeyClass = outBeat.keyPitchClass
        val inKeyClass = inBeat.keyPitchClass
        if (outKeyClass != null && inKeyClass != null) {
            val outEffective = if (outBeat.keyIsMinor == true) (outKeyClass + 3) % 12 else outKeyClass
            val inEffective = if (inBeat.keyIsMinor == true) (inKeyClass + 3) % 12 else inKeyClass
            var semitoneShift = (outEffective - inEffective) % 12
            if (semitoneShift > 6) semitoneShift -= 12
            if (semitoneShift < -6) semitoneShift += 12
            if (semitoneShift != 0 && kotlin.math.abs(semitoneShift) <= 3) {
                pitchRatio = Math.pow(2.0, semitoneShift / 12.0).toFloat()
            }
        }

        // Dynamic mix-in: skip the incoming track's intro, snapped onto its own 8-beat grid.
        val inPeriodMs = (60_000f / inBeat.bpm).toDouble()
        val rawStart = inBeat.mixInPointMs?.takeIf { it > 0 } ?: inBeat.firstBeatOffsetMs
        val inPhraseMs = inPeriodMs * 8
        val inK = kotlin.math.ceil((rawStart - inBeat.firstBeatOffsetMs) / inPhraseMs).toLong().coerceAtLeast(0)
        val incomingStart = (inBeat.firstBeatOffsetMs + inK * inPhraseMs).toLong()

        val plan = AutomixPlan(
            currentId = currentId,
            nextId = nextId,
            triggerTimeMs = triggerTime,
            incomingStartMs = incomingStart,
            tempoRatio = tempoRatio,
            pitchRatio = pitchRatio,
            overlapMs = effectiveOverlapMs,
        )
        Timber.tag(TAG).d(
            "Automix plan: trigger=%dms incomingStart=%dms tempoRatio=%.3f pitchRatio=%.3f overlap=%dms",
            plan.triggerTimeMs, plan.incomingStartMs, plan.tempoRatio, plan.pitchRatio, plan.overlapMs
        )
        automixDebugInfo.value = partialDebug.copy(
            status = "plan ready",
            triggerTimeMs = plan.triggerTimeMs,
            incomingStartMs = plan.incomingStartMs,
            tempoRatio = plan.tempoRatio,
        )
        return AutomixPlanResult(plan = plan, pairAnalyzed = true)
    }

    /**
     * Queue lookahead: analyze the next few upcoming tracks while the current one plays,
     * so beat data is ready by the time their transition is planned.
     */
    private fun analyzeUpcomingTracks() {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return
        // Skip the immediate next item: the transition planner already enqueues it
        // (with priority over this far-queue lookahead).
        var index = timeline.getNextWindowIndex(
            player.currentMediaItemIndex, REPEAT_MODE_OFF, player.shuffleModeEnabled
        )
        if (index == C.INDEX_UNSET) return
        repeat(2) {
            index = timeline.getNextWindowIndex(index, REPEAT_MODE_OFF, player.shuffleModeEnabled)
            if (index == C.INDEX_UNSET) return
            maybeAnalyzeBeat(player.getMediaItemAt(index).mediaId, BeatAnalysisPriority.LOOKAHEAD)
        }
    }

    /**
     * Lazy per-track beat analysis; fetches audio through the playback data-source chain
     * (cache-first, network otherwise) and stores the result permanently. Serialized so
     * lookahead doesn't stack up parallel downloads.
     */
    private fun maybeAnalyzeBeat(
        mediaId: String,
        priority: BeatAnalysisPriority = BeatAnalysisPriority.IMMEDIATE,
    ) {
        synchronized(beatAnalysisJobs) {
            val existing = beatAnalysisJobs[mediaId]
            if (existing != null) {
                // A fetch is already running for this track. Never cancel-and-restart it:
                // that throws away the bytes already downloaded (often megabytes) right when
                // the track is about to be needed. Promote its priority in place instead so
                // the in-flight download finishes and its result is reused.
                if (priority == BeatAnalysisPriority.IMMEDIATE &&
                    existing.priority == BeatAnalysisPriority.LOOKAHEAD
                ) {
                    Timber.tag(TAG).d("Beat analysis priority promoted in place for %s", mediaId)
                    beatAnalysisJobs[mediaId] = existing.copy(priority = BeatAnalysisPriority.IMMEDIATE)
                }
                return
            }

            val job = scope.launch(Dispatchers.IO) {
                val mutex = when (priority) {
                    BeatAnalysisPriority.IMMEDIATE -> immediateBeatAnalysisMutex
                    BeatAnalysisPriority.LOOKAHEAD -> lookaheadBeatAnalysisMutex
                }
                mutex.withLock {
                    try {
                        runBeatAnalysis(mediaId, priority)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Timber.tag(TAG).d("Beat analysis cancelled for %s (%s)", mediaId, priority)
                        throw e
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Beat analysis failed for $mediaId")
                    } finally {
                        synchronized(beatAnalysisJobs) {
                            val current = beatAnalysisJobs[mediaId]
                            if (current?.job == coroutineContext[Job]) {
                                beatAnalysisJobs.remove(mediaId)
                            }
                        }
                    }
                }
            }
            beatAnalysisJobs[mediaId] = BeatAnalysisHandle(priority, job)
        }
    }

    private suspend fun runBeatAnalysis(mediaId: String, priority: BeatAnalysisPriority) {
        val existing = database.beatInfo(mediaId)
        // Skip when analyzed with mix points (null mixOut = pre-mix-point row, rescan once).
        if (existing != null && !(existing.bpm > 0f && existing.mixOutPointMs == null)) return
        Timber.tag(TAG).d("Beat analysis starting for %s (%s)", mediaId, priority)

        val result: BeatAnalyzer.Result?
        val dataComplete: Boolean
        val startedAt = android.os.SystemClock.elapsedRealtime()
        val analysisContext = coroutineContext
        fun timedOutOrCancelled(): Boolean =
            !analysisContext.isActive ||
                android.os.SystemClock.elapsedRealtime() - startedAt > beatAnalysisTimeoutMs(priority)
        if (mediaId.isLocalMediaId()) {
            result = BeatAnalyzer.analyzeUri(
                this@MusicService,
                android.net.Uri.parse(mediaId),
                shouldCancel = ::timedOutOrCancelled,
            )
            dataComplete = true
        } else {
            val fetched = BeatAnalyzer.analyzeStream(
                analysisDataSourceFactory,
                mediaId,
                cacheDir,
                shouldCancel = { !analysisContext.isActive || timedOutOrCancelled() },
            )
                ?: run {
                    Timber.tag(TAG).d("Beat analysis skipped for %s: fetch failed", mediaId)
                    return // retry on a later transition
                }
            result = fetched.result
            dataComplete = fetched.complete
        }
        Timber.tag(TAG).d(
            "Beat analysis done for %s: %s", mediaId,
            result?.let { "bpm=%.1f conf=%.2f mixIn=%s mixOut=%s".format(it.bpm, it.confidence, it.mixInPointMs, it.mixOutPointMs) } ?: "failed (complete=$dataComplete)"
        )
        if (result == null && !dataComplete) return // partial data; retry when fully cached

        val entity = result?.let {
            BeatInfoEntity(
                mediaId, it.bpm, it.firstBeatOffsetMs, it.confidence,
                mixInPointMs = it.mixInPointMs ?: -1L, // -1 sentinel: scanned, none found
                mixOutPointMs = it.mixOutPointMs ?: -1L,
                keyPitchClass = it.keyPitchClass,
                keyIsMinor = it.keyIsMinor,
            )
        } ?: BeatInfoEntity(mediaId, 0f, 0L, 0f, mixInPointMs = -1L, mixOutPointMs = -1L)
        withContext(Dispatchers.IO) { database.upsert(entity) }

        // Fresh data may unlock a beat-aligned plan for the ongoing transition:
        // re-arm the scheduler if this track is the current or next item.
        withContext(Dispatchers.Main) {
            val currentId = player.currentMediaItem?.mediaId
            val nextIndex = player.nextMediaItemIndex
            val nextId = if (nextIndex != C.INDEX_UNSET) player.getMediaItemAt(nextIndex).mediaId else null
            if (mediaId == currentId || mediaId == nextId) scheduleCrossfade()
        }
    }

    private fun isNextItemGapless(): Boolean {
        val current = player.currentMediaItem?.mediaMetadata ?: return false
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return false
        val next = player.getMediaItemAt(nextIndex).mediaMetadata
        return current.albumTitle != null && current.albumTitle == next.albumTitle
    }

    private fun releasePrebuffered() {
        val pb = prebuffered ?: return
        prebuffered = null
        playerDuckProcessors.remove(pb.player)
        playerSilenceProcessors.remove(pb.player)
        try {
            pb.player.removeListener(secondaryPlayerListener)
            pb.player.stop()
            pb.player.clearMediaItems()
            pb.player.release()
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Failed to release prebuffered crossfade player")
        }
    }

    /**
     * Builds and prepares the secondary player ahead of the actual trigger, muted and not
     * yet playing, so the blend doesn't have to cold-start a fresh decode/buffer right when
     * it needs to be audible. Adopted by [startCrossfade] if it's still valid by then.
     */
    private fun prebufferSecondaryPlayer(plan: AutomixPlan?) {
        if (isCrossfading.value || secondaryPlayer != null || prebuffered != null) return

        val savedRepeatMode = cachedRepeatMode
        val savedShuffleEnabled = cachedShuffleEnabled
        val targetIndex = if (savedRepeatMode == REPEAT_MODE_ONE) {
            player.currentMediaItemIndex
        } else {
            player.nextMediaItemIndex
        }
        if (targetIndex == C.INDEX_UNSET) return
        val targetMediaId = player.getMediaItemAt(targetIndex).mediaId

        val secPlayer = createExoPlayer()
        secPlayer.addListener(secondaryPlayerListener)

        val itemCount = player.mediaItemCount
        val items = mutableListOf<MediaItem>()
        for (i in 0 until itemCount) items.add(player.getMediaItemAt(i))
        secPlayer.setMediaItems(items)

        secPlayer.seekTo(targetIndex, plan?.incomingStartMs ?: 0)
        if (plan != null) {
            val base = try { player.playbackParameters } catch (e: Exception) { PlaybackParameters.DEFAULT }
            if (base != PlaybackParameters.DEFAULT) secPlayer.playbackParameters = base
        }
        secPlayer.volume = 0f
        secPlayer.repeatMode = savedRepeatMode
        secPlayer.shuffleModeEnabled = savedShuffleEnabled
        secPlayer.prepare() // playWhenReady left false: buffers ahead without playing.

        prebuffered = PrebufferedTransition(secPlayer, plan, targetMediaId)
    }

    private fun startCrossfade(plan: AutomixPlan? = null) {
        if (isCrossfading.value) return

        val savedRepeatMode = cachedRepeatMode
        val savedShuffleEnabled = cachedShuffleEnabled

        val targetIndex = if (savedRepeatMode == REPEAT_MODE_ONE) {
            player.currentMediaItemIndex
        } else {
            player.nextMediaItemIndex
        }
        if (targetIndex == C.INDEX_UNSET) return
        val targetMediaId = player.getMediaItemAt(targetIndex).mediaId

        activeAutomixPlan = plan

        val pb = prebuffered
        val secPlayer: ExoPlayer
        if (pb != null && pb.targetMediaId == targetMediaId) {
            // Already buffered ahead of time — adopt it instead of cold-starting a new one.
            secPlayer = pb.player
            activeAutomixPlan = pb.plan
            prebuffered = null
        } else {
            releasePrebuffered() // stale — buffered for a track that's no longer next.

            secPlayer = createExoPlayer()
            secPlayer.addListener(secondaryPlayerListener)

            val itemCount = player.mediaItemCount
            val items = mutableListOf<MediaItem>()
            for (i in 0 until itemCount) items.add(player.getMediaItemAt(i))
            secPlayer.setMediaItems(items)

            // Beat-aligned: start the incoming track on its first downbeat.
            secPlayer.seekTo(targetIndex, plan?.incomingStartMs ?: 0)
            if (plan != null) {
                val base = try { player.playbackParameters } catch (e: Exception) { PlaybackParameters.DEFAULT }
                if (base != PlaybackParameters.DEFAULT) secPlayer.playbackParameters = base
            }
            secPlayer.volume = 0f
            secPlayer.repeatMode = savedRepeatMode
            secPlayer.shuffleModeEnabled = savedShuffleEnabled
            secPlayer.prepare()
        }

        secondaryPlayer = secPlayer
        secPlayer.playWhenReady = true

        performCrossfadeSwap()

        if (savedShuffleEnabled) {
            val shufflePlaylistFirst = dataStore.snapshot(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
    }

    private fun performCrossfadeSwap() {
        isCrossfading.value = true
        isAutomixing.value = activeAutomixPlan != null
        if (activeAutomixPlan != null) {
            automixDebugInfo.value = automixDebugInfo.value?.copy(status = "automixing now")
        }
        val nextPlayer = secondaryPlayer ?: return
        val currentPlayer = player

        fadingPlayer = currentPlayer
        player = nextPlayer
        _playerFlow.value = player
        secondaryPlayer = null

        // The outgoing player keeps its full playlist and keeps advancing in real time
        // while it fades out. If it reaches its own natural end before cleanupCrossfade
        // stops it (trigger-time math off, or the fade loop lagging behind due to a
        // scheduling hiccup), it would auto-advance on its own — playing the next track
        // a second time, or wrapping to track 1 on repeat-all. Truncate its playlist so
        // it has nowhere to advance to; worst case it just stops.
        try {
            val idx = currentPlayer.currentMediaItemIndex
            if (idx != C.INDEX_UNSET && idx + 1 < currentPlayer.mediaItemCount) {
                currentPlayer.removeMediaItems(idx + 1, currentPlayer.mediaItemCount)
            }
            currentPlayer.repeatMode = REPEAT_MODE_OFF
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Failed to truncate fading player's playlist")
        }

        fadingPlayer?.removeListener(this)
        fadingPlayer?.removeListener(sleepTimer)

        
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isCrossfading.value && fadingPlayer != null) {
                    try {
                        if (isPlaying) {
                            fadingPlayer?.play()
                        } else {
                            fadingPlayer?.pause()
                        }
                    } catch (e: Exception) {
                        Timber.tag(TAG).e(e, "Error syncing fadingPlayer play state")
                    }
                } else {
                    player.removeListener(this)
                }
            }
        })

        nextPlayer.removeListener(secondaryPlayerListener)
        nextPlayer.addListener(this)
        nextPlayer.addListener(sleepTimer)

        sleepTimer.player = player

        try {
            (mediaSession as MediaSession).player = player
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to swap player in MediaSession")
        }

        // The crossfade swap moves playback to a brand-new ExoPlayer with its own
        // audio session id, but this player's listener was attached after the
        // seek/prepare already happened, so no EVENT_MEDIA_ITEM_TRANSITION fires for
        // it. Without this, the LoudnessEnhancer and system-EQ session stay bound to
        // the outgoing (soon-to-be-released) session, so the incoming track plays
        // without normalization/EQ.
        currentMediaMetadata.value = player.currentMetadata
        val oldSessionId = fadingPlayer?.audioSessionId
        // Keep the current enhancer (still bound to the outgoing session) alive and attached
        // through the fade instead of releasing it, so the outgoing track stays normalized
        // while it fades out. A fresh enhancer for the incoming session is created below.
        // cleanupCrossfade releases this once the fade is done.
        try {
            fadingLoudnessEnhancer?.release()
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Failed releasing stale fading enhancer")
        }
        fadingLoudnessEnhancer = loudnessEnhancer
        loudnessEnhancer = null
        if (isAudioEffectSessionOpened) {
            if (oldSessionId != null && oldSessionId != C.AUDIO_SESSION_ID_UNSET && oldSessionId > 0) {
                sendBroadcast(
                    Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                        putExtra(AudioEffect.EXTRA_AUDIO_SESSION, oldSessionId)
                        putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    },
                )
            }
            isAudioEffectSessionOpened = false
            openAudioEffectSession()
        } else {
            setupLoudnessEnhancer()
        }

        crossfadeJob = scope.launch {
            val djPlan = activeAutomixPlan
            val duration = djPlan?.overlapMs ?: crossfadeDuration.toLong()
            // Fine-grained ramp: aim for ~15ms per volume step so each gain increment is
            // below the threshold of audibility. Coarse steps (the old 100ms) make the fade
            // a stepped "zipper"/click; at 15ms the ramp sounds continuous. Volume writes are
            // near-free, so the extra steps cost nothing meaningful.
            val steps = (duration / 15L).toInt().coerceIn(50, 800)
            val stepTime = duration / steps
            val startVolume = try { fadingPlayer?.volume ?: 1f } catch (e: Exception) { 1f }

            // Bass-swap ducking (DJ blend only): cut the outgoing track's low end as it
            // drops and hold the incoming track's low end back until it takes over, so
            // two full basslines don't sum into mud during the overlap.
            val outDuck = fadingPlayer?.let { playerDuckProcessors[it] }
            val inDuck = playerDuckProcessors[player]

            // Equal-power curve: sin/cos gains keep combined signal energy ~constant
            // through the blend, so linearly summing two tracks doesn't dip in
            // perceived loudness at the midpoint the way linear/smoothstep gain does.
            fun equalPowerIn(edge0: Float, edge1: Float, x: Float): Float {
                val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
                return kotlin.math.sin(t * (Math.PI / 2.0).toFloat())
            }
            fun equalPowerOut(edge0: Float, edge1: Float, x: Float): Float {
                val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
                return kotlin.math.cos(t * (Math.PI / 2.0).toFloat())
            }

            try {
                for (i in 0..steps) {
                    if (!isActive) break

                    while (!player.isPlaying && isActive) {
                        delay(100)
                    }

                    val progress = i / steps.toFloat()
                    // Fade-out then fade-in with a gentle dip: the outgoing track drops away
                    // over the first ~60% of the blend, the incoming rises over the last ~60%,
                    // so they overlap only through the middle where both sit well below full.
                    // Old track leaves, new one arrives — no sudden level match, no boost.
                    // Both curves are cosine/sine eased, so the ramp stays click-free.
                    val fadeOut = equalPowerOut(0f, 0.6f, progress)
                    val fadeIn = equalPowerIn(0.4f, 1f, progress)

                    try {
                        player.volume = startVolume * fadeIn
                        fadingPlayer?.volume = startVolume * fadeOut
                    } catch (e: Exception) { break }

                    if (djPlan != null) {
                        // Outgoing bass cuts through the same 0.45-1.0 window it fades
                        // out in; incoming bass fills back in through 0-0.55.
                        outDuck?.setMix(equalPowerIn(0.45f, 1f, progress))
                        inDuck?.setMix(1f - equalPowerIn(0f, 0.55f, progress))
                    }

                    delay(stepTime)
                }
            } finally {
                try {
                    fadingPlayer?.volume = 0f
                    player.volume = startVolume
                } catch (e: Exception) {
                    Timber.tag(TAG).d(e, "Crossfade volume reset skipped, player likely released")
                }
                outDuck?.resetGain()
                inDuck?.resetGain()
                cleanupCrossfade()
                activeAutomixPlan = null
            }
        }
    }

    private fun cleanupCrossfade() {
        try {
            fadingLoudnessEnhancer?.release()
        } catch (e: Exception) {
            Timber.tag(TAG).d(e, "Failed releasing fading enhancer")
        } finally {
            fadingLoudnessEnhancer = null
        }
        fadingPlayer?.let { playerDuckProcessors.remove(it) }
        fadingPlayer?.stop()
        fadingPlayer?.clearMediaItems()
        fadingPlayer?.release()
        fadingPlayer = null
        isCrossfading.value = false
        isAutomixing.value = false
        sleepTimer.notifySongTransition()
    }

    companion object {
        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val YOUTUBE_PLAYLIST = "youtube_playlist"
        const val SEARCH = "search"
        const val SHUFFLE_ACTION = "__shuffle__"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val CHUNK_LENGTH = 512 * 1024L
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        /** How far ahead of the crossfade trigger to start buffering the incoming track. */
        const val PREBUFFER_LEAD_MS = 3000L
        const val MAX_PRELOAD_TRACKS = 3
        const val PRELOAD_CONCURRENCY = 2
        const val PRELOAD_FIRST_BYTES = 64 * 1024
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val MAX_CONSECUTIVE_ERR = 5
        const val MAX_RETRY_COUNT = 10
        
        private const val MAX_GAIN_MB = 300 
        private const val MIN_GAIN_MB = -1500 

        private const val TAG = "MusicService"
        private const val RATE_LIMIT_BACKOFF_MS = 2_000L

        @Volatile
        var isRunning = false
            private set
    }

    private var preloadJob: kotlinx.coroutines.Job? = null

    private fun preloadUpcomingItems() {
        val preloadEnabled = cachedPreloadEnabled
        if (!preloadEnabled) return

        val preloadLimit = cachedPreloadLimit
        val preloadLyrics = cachedPreloadLyrics
        val preloadQuality = audioQuality

        val currentIndex = player.currentMediaItemIndex
        if (currentIndex == androidx.media3.common.C.INDEX_UNSET) return

        val limit = kotlin.math.min(
            preloadLimit.coerceIn(1, MAX_PRELOAD_TRACKS),
            player.mediaItemCount - currentIndex - 1,
        )
        if (limit <= 0) return

        val preloadPlan = PlaybackPreloadPlanner(MAX_PRELOAD_TRACKS)
        (1..limit).forEach { offset ->
            preloadPlan.offer(
                mediaId = player.getMediaItemAt(currentIndex + offset).mediaId,
                priority = offset - 1,
            )
        }
        val upcomingMediaIds = preloadPlan.snapshot()

        preloadJob?.cancel()
        preloadJob = scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            coroutineScope {
                val dispatcher = kotlinx.coroutines.Dispatchers.IO.limitedParallelism(PRELOAD_CONCURRENCY)
                upcomingMediaIds.map { candidate ->
                    async(dispatcher) {
                        preloadUpcomingItem(
                            mediaId = candidate.mediaId,
                            quality = preloadQuality,
                            preloadLyrics = preloadLyrics,
                            priority = candidate.priority,
                        )
                    }
                }.awaitAll()
            }
        }
    }

    private suspend fun preloadUpcomingItem(
        mediaId: String,
        quality: com.auriqo.music.constants.AudioQuality,
        preloadLyrics: Boolean,
        priority: Int,
    ) {
        if (mediaId.isLocalMediaId()) return
        val trace = PlaybackDiagnostics.startResolution(mediaId, "queue_lookahead_p$priority")
        try {
            val streamKey = streamKey(mediaId, quality)
            val isFullyDownloaded = downloadCache.getCachedSpans(mediaId).isNotEmpty()
            val cached = streamRecovery.cachedStream(streamKey)
            if (isFullyDownloaded) {
                trace.breadcrumb("DOWNLOAD_CACHE_HIT", "preload")
            } else if (cached != null) {
                cached.itag?.let { selectedFormatItags[mediaId] = it }
                trace.resolutionCacheHit(cached.expiresAtMs - System.currentTimeMillis())
                trace.streamSelected(cached.itag, cached.mimeType, cached.bitrate)
                warmupNextTrackBytes(mediaId, cached, priority, trace)
            } else {
                trace.resolutionRequested(mediaId, quality.name)
                trace.resolutionCacheMiss("preload")
                Timber.tag(TAG).d("Preloading stream priority=$priority")
                val resolutionToken = streamRecovery.resolutionToken(mediaId)
                val dbSong = database.song(mediaId).firstOrNull()
                val knownArtist = dbSong?.artists
                    ?.joinToString(separator = ", ") { artist -> artist.name }
                    ?.replace(" - Topic", "")

                val playbackData = YTPlayerUtils.playerResponseForPlayback(
                    videoId = mediaId,
                    audioQuality = quality,
                    connectivityManager = connectivityManager,
                    context = this@MusicService,
                    knownArtist = knownArtist,
                    knownTitle = dbSong?.song?.title,
                    knownDurationMs = dbSong?.song?.duration?.let { if (it > 0) it * 1000L else null },
                )

                playbackData.getOrNull()?.let { playback ->
                    when (
                        streamRecovery.cacheStream(
                            key = streamKey,
                            url = playback.streamUrl,
                            expiresAtMs = System.currentTimeMillis() + playback.streamExpiresInSeconds * 1000L,
                            token = resolutionToken,
                            resolvedAtMs = System.currentTimeMillis(),
                            itag = playback.format.itag,
                            mimeType = playback.format.mimeType,
                            bitrate = playback.format.bitrate,
                        )
                    ) {
                        StreamRecoveryCoordinator.CacheWriteResult.Stored -> {
                            selectedFormatItags[mediaId] = playback.format.itag
                            trace.streamSelected(playback.format.itag, playback.format.mimeType, playback.format.bitrate)
                            trace.breadcrumb("PRELOAD_STORED", "priority=$priority")
                            streamRecovery.cachedStream(streamKey)?.let { cached ->
                                warmupNextTrackBytes(mediaId, cached, priority, trace)
                            }
                        }

                        StreamRecoveryCoordinator.CacheWriteResult.Superseded -> {
                            trace.breadcrumb("PRELOAD_DISCARDED", "superseded")
                        }

                        StreamRecoveryCoordinator.CacheWriteResult.Expired -> {
                            trace.breadcrumb("PRELOAD_DISCARDED", "expired")
                        }
                    }
                }
            }
        } catch (error: kotlinx.coroutines.CancellationException) {
            trace.breadcrumb("PRELOAD_CANCELLED")
            throw error
        } catch (error: Exception) {
            trace.breadcrumb("PRELOAD_FAILED", error::class.simpleName)
            Timber.tag(TAG).w("Preload failed type=${error::class.java.simpleName}")
        } finally {
            if (preloadLyrics) {
                preloadLyricsFor(mediaId)
            }
            PlaybackDiagnostics.finishResolution(mediaId, trace)
        }
    }

    private suspend fun preloadLyricsFor(mediaId: String) {
        val dbLyrics = database.lyrics(mediaId).firstOrNull()
        if (dbLyrics != null) return
        val dbSong = database.song(mediaId).firstOrNull() ?: return
        kotlin.runCatching {
            val metadata = com.auriqo.music.models.MediaMetadata(
                id = dbSong.song.id,
                title = dbSong.song.title,
                artists = dbSong.artists.map { artist -> com.auriqo.music.models.MediaMetadata.Artist(artist.id, artist.name) },
                duration = dbSong.song.duration,
                thumbnailUrl = dbSong.thumbnailUrl,
            )
            val lyricsResult = lyricsHelper.getLyrics(metadata)
            database.query {
                upsert(com.auriqo.music.db.entities.LyricsEntity(id = mediaId, lyrics = lyricsResult.lyrics))
            }
            Timber.tag(TAG).d("Preloaded lyrics")
        }
    }

    /** Warms only the next track's first 64 KiB; it never downloads the song or runs on metered data. */
    private suspend fun warmupNextTrackBytes(
        mediaId: String,
        cached: StreamRecoveryCoordinator.CachedStream,
        priority: Int,
        trace: PlaybackTraceRecorder,
    ) {
        if (
            priority != 0 ||
            connectivityManager.isActiveNetworkMetered ||
            dataStore.snapshot(com.auriqo.music.constants.DataSaverEnabledKey, false)
        ) return
        val source = createCacheDataSource().createDataSource()
        try {
            val spec = DataSpec.Builder()
                .setUri(cached.url.toUri())
                .setKey(mediaId)
                .setPosition(0L)
                .setLength(PRELOAD_FIRST_BYTES.toLong())
                .build()
            var total = 0
            source.open(spec)
            val buffer = ByteArray(16 * 1024)
            while (total < PRELOAD_FIRST_BYTES) {
                val read = source.read(buffer, 0, minOf(buffer.size, PRELOAD_FIRST_BYTES - total))
                if (read == C.RESULT_END_OF_INPUT) break
                if (read <= 0) break
                total += read
            }
            trace.breadcrumb("FIRST_BYTES_WARMED", total.toString())
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            Media3PlaybackDiagnostics.findHttpDetails(error)?.let(trace::httpStatus)
            trace.breadcrumb("FIRST_BYTES_WARMUP_FAILED", error::class.simpleName)
            streamRecovery.invalidateStream(mediaId)
        } finally {
            runCatching { source.close() }
        }
    }

    private fun checkAndSubmitListenBrainzFinished() {
        listenBrainzCurrentMediaId?.let { mediaId ->
            val startTs = listenBrainzCurrentStartTs
            if (startTs > 0) {
                scope.launch {
                    val mediaMetadata = player.mediaItems.find { it.mediaId == mediaId }?.metadata
                    val dbSong = if (mediaMetadata == null) database.song(mediaId).firstOrNull() else null
                    
                    val title = mediaMetadata?.title ?: dbSong?.song?.title ?: return@launch
                    val artistNames = mediaMetadata?.artists?.joinToString(" & ") { it.name } 
                        ?: dbSong?.artists?.joinToString(" & ") { it.name } ?: ""
                    val releaseName = mediaMetadata?.album?.title ?: dbSong?.album?.title ?: ""
                    val durationMs = mediaMetadata?.duration?.takeIf { it != -1 }?.times(1000L) 
                        ?: dbSong?.song?.duration?.takeIf { it != -1 }?.times(1000L) ?: 0L

                    updateListenBrainz(title, artistNames, releaseName, durationMs, isFinished = true, startMs = startTs, endMs = System.currentTimeMillis())
                }
            }
        }
        listenBrainzCurrentStartTs = 0L
        listenBrainzCurrentMediaId = null
    }

    private fun checkAndSubmitListenBrainzPlayingNow(mediaId: String) {
        scope.launch {
            val mediaMetadata = player.mediaItems.find { it.mediaId == mediaId }?.metadata
            val dbSong = if (mediaMetadata == null) database.song(mediaId).firstOrNull() else null
            
            val title = mediaMetadata?.title ?: dbSong?.song?.title ?: return@launch
            val artistNames = mediaMetadata?.artists?.joinToString(" & ") { it.name } 
                ?: dbSong?.artists?.joinToString(" & ") { it.name } ?: ""
            val releaseName = mediaMetadata?.album?.title ?: dbSong?.album?.title ?: ""
            val durationMs = mediaMetadata?.duration?.takeIf { it != -1 }?.times(1000L) 
                ?: dbSong?.song?.duration?.takeIf { it != -1 }?.times(1000L) ?: 0L

            updateListenBrainz(title, artistNames, releaseName, durationMs, isFinished = false)
        }
    }
}
