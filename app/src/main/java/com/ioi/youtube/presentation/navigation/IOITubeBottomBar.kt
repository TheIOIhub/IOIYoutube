package com.ioi.youtube.presentation.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.unit.dp

/**
 * Компактна смуга знизу: 56dp у висоту, іконка 24dp у пігулці 48dp.
 *
 * Material3 1.3.1 не дає керувати висотою та відступами NavigationBar
 * (у версії немає параметрів itemSpacing / iconContainerSize / ContainerHeight),
 * тому смугу складаємо вручну — інакше іконки сиділи б у пігулці вищою за них,
 * а відступи зверху/знизу на телефоні здавалися великими.
 *
 * Пігулка 48dp — це водночас мінімальна площа дотику за Material guidelines;
 * 56dp смуги лишає по 4dp поля зверху й знизу, тому іконки не «пливуть».
 * Системний відступ навігаційної смуги додається ззовні, щоб жест-бар
 * не перекривав кнопки (MainActivity працює в edge-to-edge).
 */
private val TabHeight = 56.dp
private val IconSize = 24.dp
private val ItemPadding = 4.dp
private val IconRadius = 18.dp

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
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(TabHeight)
            .padding(horizontal = 4.dp),
    ) {
        Tabs.forEach { (route, label, icon) ->
            val selected = currentRoute == route
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(ItemPadding)
                    .clip(RoundedCornerShape(IconRadius))
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        RoundedCornerShape(IconRadius),
                    )
                    .clickable(onClick = { onNavigate(route) }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    modifier = Modifier.size(IconSize),
                )
            }
        }
    }
}
