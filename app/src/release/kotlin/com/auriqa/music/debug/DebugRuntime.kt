package com.auriqo.music.debug

import android.app.Application
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.auriqo.music.playback.PlayerConnection

/** Release boundary: no debug UI, persistence, network collector or fault hooks. */
private object ReleaseDebugRuntime : DebugRuntimeContract {
    override val available: Boolean = false
    override fun initialize(application: Application) = Unit
    override fun onPlayerConnectionChanged(connection: PlayerConnection?) = Unit
    override fun registerNavigation(
        builder: NavGraphBuilder,
        navController: NavHostController,
        scrollBehavior: TopAppBarScrollBehavior,
    ) = Unit
    @Composable override fun Overlay(navController: NavController) = Unit
    @Composable override fun AboutEntry(navController: NavController) = Unit
}

fun createDebugRuntime(): DebugRuntimeContract = ReleaseDebugRuntime
