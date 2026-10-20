package com.ioi.youtube.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ioi.youtube.presentation.navigation.Routes

/**
 * Компактна смуга знизу: 56dp у висоту, іконка 24dp в контейнері 48dp.
 *
 * Material3 1.3.1 не дає керувати висотою та відступами NavigationBar
 * (у версії немає параметрів itemSpacing / iconContainerSize / ContainerHeight),
 * тому смугу складаємо вручну — інакше іконки сиділи б у пігулці вищою за них,
 * а відступи зверху/знизу на телефоні здавалися великими.
 */
private val TabHeight = 56.dp
private val IconSize = 24.dp
private val IconPadding = 12.dp
private val ItemPadding = 8.dp
private val CornerRadius = 14.dp
private val IconRadius = 18.dp
/** (TabHeight - 2*ItemPadding - IconSize) / 2.dp — вертикальний відступ іконки в смугі. */
private val IconVerticalOffset = 8.dp

private val Tabs = listOf(
    Triple(Routes.HOME, "Головна", Icons.Filled.Home),
    Triple(Routes.SHORTS, "Shorts", Icons.Filled.SmartDisplay),
    Triple(Routes.SEARCH, "Пошук", Icons.Filled.Search),
)

@Composable
fun IOITubeBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TabHeight)
            .padding(horizontal = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clip(RoundedCornerShape(CornerRadius))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(CornerRadius)),
    ) {
        Tabs.forEach { (route, label, icon) ->
            val selected = currentRoute == route
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(ItemPadding)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(IconRadius),
                    )
                    .clickable(onClick = { onNavigate(route) }),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .padding(IconPadding)
                        .padding(PaddingValues(vertical = IconVerticalOffset)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, contentDescription = label)
                }
            }
        }
    }
}
