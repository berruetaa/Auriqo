package com.auriqo.music.debug

import android.app.Application
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.auriqo.music.playback.PlayerConnection
import okhttp3.EventListener

interface DebugRuntimeContract {
    val available: Boolean
    fun initialize(application: Application)
    fun onPlayerConnectionChanged(connection: PlayerConnection?)
    fun registerNavigation(
        builder: NavGraphBuilder,
        navController: NavHostController,
        scrollBehavior: TopAppBarScrollBehavior,
    )
    @Composable fun Overlay(navController: NavController)
    @Composable fun AboutEntry(navController: NavController)
    fun networkEventListenerFactory(): EventListener.Factory? = null
    fun <T> withNetworkTrace(traceId: String?, block: () -> T): T = block()
    suspend fun <T> withNetworkTraceSuspend(traceId: String?, block: suspend () -> T): T = block()
    fun consumeFault(point: DebugFaultPoint): DebugFaultSpec? = null
}

enum class DebugFaultPoint { PLAYER_RESPONSE, DATASOURCE_OPEN }

data class DebugFaultSpec(
    val kind: Kind,
    val valueMs: Long = 0L,
    val httpStatus: Int? = null,
) {
    enum class Kind {
        HTTP_STATUS,
        RESOLUTION_TIMEOUT,
        DATASOURCE_TIMEOUT,
        OFFLINE,
        DELAY,
        EXPIRE_STREAM,
        INVALIDATE_EXTRACTOR,
        SIGNATURE_FAILURE,
        N_TRANSFORM_FAILURE,
        POTOKEN_FAILURE,
        FORMAT_FAILURE,
    }
}

object DebugRuntime {
    lateinit var instance: DebugRuntimeContract
        private set

    fun currentOrNull(): DebugRuntimeContract? =
        if (::instance.isInitialized) instance else null

    fun install(runtime: DebugRuntimeContract) {
        instance = runtime
    }
}
