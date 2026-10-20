package com.ioi.youtube.presentation.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun IOITubeBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        NavigationBarItem(
            selected = currentRoute == Routes.HOME,
            onClick = { onNavigate(Routes.HOME) },
            icon = { Icon(Icons.Filled.Home, contentDescription = "Головна") },
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SHORTS,
            onClick = { onNavigate(Routes.SHORTS) },
            icon = { Icon(Icons.Filled.SmartDisplay, contentDescription = "Shorts") },
        )
        NavigationBarItem(
            selected = currentRoute == Routes.SEARCH,
            onClick = { onNavigate(Routes.SEARCH) },
            icon = { Icon(Icons.Filled.Search, contentDescription = "Пошук") },
        )
    }
}
