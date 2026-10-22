package com.ioi.youtube.presentation.navigation

import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ioi.youtube.presentation.home.HomeRoute
import com.ioi.youtube.presentation.player.MiniPlayer
import com.ioi.youtube.presentation.player.PlaybackViewModel
import com.ioi.youtube.presentation.player.PlayerRoute
import com.ioi.youtube.presentation.search.SearchRoute
import com.ioi.youtube.presentation.shorts.ShortsRoute
import com.ioi.youtube.presentation.util.findActivity

@Composable
fun IOITubeApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.HOME ||
        currentRoute == Routes.SEARCH ||
        currentRoute?.startsWith(Routes.SHORTS) == true

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val playback: PlaybackViewModel = hiltViewModel()

    // Екран вiдео — вільний поворот, решта застосунку — портрет.
    LaunchedEffect(currentRoute, activity) {
        activity?.requestedOrientation = orientationForRoute(currentRoute)
    }

    // Shorts має власне вiдтворення; довге вiдео не повинно грати пiд ним.
    LaunchedEffect(currentRoute) {
        if (currentRoute?.startsWith(Routes.SHORTS) == true) {
            playback.stop()
        }
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                IOITubeBottomBar(
                    currentRoute = currentRoute,
                    onNavigate = { route -> navController.navigateTopLevel(route) },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(Routes.HOME) {
                    HomeRoute(
                        onVideoClick = { videoId ->
                            playback.expand()
                            navController.navigate(Routes.player(videoId))
                        },
                        onShortClick = { videoId ->
                            navController.navigate(Routes.shorts(videoId))
                        },
                    )
                }
                composable(Routes.SEARCH) {
                    SearchRoute(
                        onVideoClick = { videoId ->
                            playback.expand()
                            navController.navigate(Routes.player(videoId))
                        },
                    )
                }
                composable(
                    route = Routes.SHORTS_PATTERN,
                    arguments = listOf(
                        navArgument(Routes.ARG_VIDEO_ID) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    ShortsRoute()
                }
                composable(
                    route = Routes.PLAYER,
                    exitTransition = { ExitTransition.None },
                    popExitTransition = { ExitTransition.None },
                ) {
                    PlayerRoute(
                        playback = playback,
                        onVideoClick = { videoId ->
                            playback.expand()
                            navController.navigate(Routes.player(videoId)) {
                                popUpTo(Routes.PLAYER) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }

            val showMiniPlayer = playback.minimized &&
                playback.canMinimize &&
                (currentRoute == Routes.HOME || currentRoute == Routes.SEARCH)

            if (showMiniPlayer) {
                val videoId = playback.videoId
                if (videoId != null) {
                    MiniPlayer(
                        player = playback.holder.player,
                        onExpand = {
                            playback.expand()
                            navController.navigate(Routes.player(videoId))
                        },
                        onClose = playback::stop,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

private fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
