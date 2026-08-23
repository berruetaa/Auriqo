package com.auriqo.music

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.compose.material3.TopAppBarScrollBehavior
import com.auriqo.music.ui.component.backdrop.backdrops.LayerBackdrop
import com.auriqo.music.ui.component.backdrop.backdrops.layerBackdrop
import com.auriqo.music.ui.screens.Screens
import com.auriqo.music.ui.screens.navigationBuilder

/** Owns the route graph and its transitions; [MainActivity] only supplies the shell dependencies. */
@Composable
internal fun AppNavigation(
    navController: NavHostController,
    startDestination: String,
    navigationItems: List<Screens>,
    scrollBehavior: TopAppBarScrollBehavior,
    appBackdrop: LayerBackdrop,
    activity: MainActivity,
    snackbarHostState: SnackbarHostState,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = {
            val currentRouteIndex = navigationItems.indexOfFirst {
                it.route == targetState.destination.route
            }
            val previousRouteIndex = navigationItems.indexOfFirst {
                it.route == initialState.destination.route
            }

            if (currentRouteIndex == -1 || currentRouteIndex > previousRouteIndex)
                slideInHorizontally { it / 8 } + fadeIn(tween(200))
            else
                slideInHorizontally { -it / 8 } + fadeIn(tween(200))
        },
        exitTransition = {
            val currentRouteIndex = navigationItems.indexOfFirst {
                it.route == initialState.destination.route
            }
            val targetRouteIndex = navigationItems.indexOfFirst {
                it.route == targetState.destination.route
            }

            if (targetRouteIndex == -1 || targetRouteIndex > currentRouteIndex)
                slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
            else
                slideOutHorizontally { it / 8 } + fadeOut(tween(200))
        },
        popEnterTransition = {
            val currentRouteIndex = navigationItems.indexOfFirst {
                it.route == targetState.destination.route
            }
            val previousRouteIndex = navigationItems.indexOfFirst {
                it.route == initialState.destination.route
            }

            if (previousRouteIndex != -1 && previousRouteIndex < currentRouteIndex)
                slideInHorizontally { it / 8 } + fadeIn(tween(200))
            else
                slideInHorizontally { -it / 8 } + fadeIn(tween(200))
        },
        popExitTransition = {
            val currentRouteIndex = navigationItems.indexOfFirst {
                it.route == initialState.destination.route
            }
            val targetRouteIndex = navigationItems.indexOfFirst {
                it.route == targetState.destination.route
            }

            if (currentRouteIndex != -1 && currentRouteIndex < targetRouteIndex)
                slideOutHorizontally { -it / 8 } + fadeOut(tween(200))
            else
                slideOutHorizontally { it / 8 } + fadeOut(tween(200))
        },
        modifier = Modifier
            .layerBackdrop(appBackdrop)
            .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        navigationBuilder(
            navController = navController,
            scrollBehavior = scrollBehavior,
            activity = activity,
            snackbarHostState = snackbarHostState,
        )
    }
}
