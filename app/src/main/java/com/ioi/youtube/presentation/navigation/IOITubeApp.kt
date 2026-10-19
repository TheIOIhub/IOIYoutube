package com.ioi.youtube.presentation.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.ioi.youtube.presentation.home.HomeRoute
import com.ioi.youtube.presentation.player.PlayerRoute
import com.ioi.youtube.presentation.search.SearchRoute
import com.ioi.youtube.presentation.util.findActivity

@Composable
fun IOITubeApp(navController: NavHostController = rememberNavController()) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val showBottomBar = currentRoute == Routes.HOME || currentRoute == Routes.SEARCH

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Екран вiдео — вільний поворот, решта застосунку — портрет.
    LaunchedEffect(currentRoute, activity) {
        activity?.requestedOrientation = orientationForRoute(currentRoute)
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
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) {
                HomeRoute(
                    onVideoClick = { videoId ->
                        navController.navigate(Routes.player(videoId))
                    },
                )
            }
            composable(Routes.SEARCH) {
                SearchRoute(
                    onVideoClick = { videoId ->
                        navController.navigate(Routes.player(videoId))
                    },
                )
            }
            composable(Routes.PLAYER) {
                PlayerRoute(
                    onVideoClick = { videoId ->
                        navController.navigate(Routes.player(videoId))
                    },
                )
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
