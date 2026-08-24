package com.auriqo.music.debug

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.compose.material3.TopAppBarScrollBehavior
import com.auriqo.music.playback.PlayerConnection
import com.auriqo.music.ui.screens.settings.DebugLogScreen
import com.auriqo.music.utils.debug.DebugLogTree
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

private object DebugRuntimeServices {
    private val initialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = initialized.asStateFlow()
    val collector = PlaybackDebugCollector()
    val network = DebugNetworkCollector()
    val chaos = DebugChaosController()
    val session = DebugSessionStore()
    val connection = MutableStateFlow<PlayerConnection?>(null)
    val hudEnabled = MutableStateFlow(false)
    val networkTrace = ThreadLocal<String?>()

    fun initialize(application: Application) {
        if (initialized.value) return
        DebugLogTree.install()
        Timber.plant(Timber.DebugTree())
        network.traceIdProvider = { networkTrace.get() }
        session.initialize(application, collector, network)
        hudEnabled.value = DebugPreferenceStore.getBoolean(application, DEBUG_HUD_KEY, false)
        initialized.value = true
    }
}

private object DebugRuntimeImpl : DebugRuntimeContract {
    override val available: Boolean = true

    override fun initialize(application: Application) {
        DebugRuntimeServices.initialize(application)
    }

    override fun onPlayerConnectionChanged(connection: PlayerConnection?) {
        DebugRuntimeServices.connection.value = connection
    }

    override fun registerNavigation(
        builder: NavGraphBuilder,
        navController: NavHostController,
        scrollBehavior: TopAppBarScrollBehavior,
    ) {
        builder.composable(
            route = "settings/debug_center?traceId={traceId}",
            arguments = listOf(navArgument("traceId") { type = NavType.StringType; nullable = true }),
        ) { entry ->
            DebugCenter(navController, entry.arguments?.getString("traceId"))
        }
        builder.composable("settings/debug_logs") {
            DebugLogScreen(navController)
        }
    }

    @Composable
    override fun Overlay(navController: NavController) {
        DebugPerformanceOverlay(
            collector = DebugRuntimeServices.collector,
            navController = navController,
        )
    }

    @Composable
    override fun AboutEntry(navController: NavController) {
        DebugCenterAboutEntry(navController)
    }

    override fun networkEventListenerFactory() = DebugRuntimeServices.network.listenerFactory()

    override fun <T> withNetworkTrace(traceId: String?, block: () -> T): T {
        val previous = DebugRuntimeServices.networkTrace.get()
        DebugRuntimeServices.networkTrace.set(traceId)
        return try {
            block()
        } finally {
            DebugRuntimeServices.networkTrace.set(previous)
        }
    }

    override suspend fun <T> withNetworkTraceSuspend(traceId: String?, block: suspend () -> T): T {
        val previous = DebugRuntimeServices.networkTrace.get()
        DebugRuntimeServices.networkTrace.set(traceId)
        return try {
            block()
        } finally {
            DebugRuntimeServices.networkTrace.set(previous)
        }
    }

    override fun consumeFault(point: DebugFaultPoint): DebugFaultSpec? =
        DebugRuntimeServices.chaos.consume(point)
}

fun createDebugRuntime(): DebugRuntimeContract = DebugRuntimeImpl

internal object DebugRuntimeAccess {
    val collector: PlaybackDebugCollector get() = DebugRuntimeServices.collector
    val network: DebugNetworkCollector get() = DebugRuntimeServices.network
    val chaos: DebugChaosController get() = DebugRuntimeServices.chaos
    val session: DebugSessionStore get() = DebugRuntimeServices.session
    val connection: StateFlow<PlayerConnection?> get() = DebugRuntimeServices.connection.asStateFlow()
    val hudEnabled: StateFlow<Boolean> get() = DebugRuntimeServices.hudEnabled.asStateFlow()
    fun setHudEnabled(context: android.content.Context, enabled: Boolean) {
        DebugPreferenceStore.setBoolean(context, DEBUG_HUD_KEY, enabled)
        DebugRuntimeServices.hudEnabled.value = enabled
    }
}
