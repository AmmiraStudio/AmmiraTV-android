package com.ammiratv.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.ammiratv.core.ui.device.LocalDeviceProfile
import com.ammiratv.core.ui.device.LocalWidthClass
import com.ammiratv.core.ui.device.PlaybackChrome
import com.ammiratv.core.ui.device.WidthClass
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkSurface
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextSecondary

private data class TopLevel(val route: String, val label: String, val icon: ImageVector)

private val TOP_LEVEL = listOf(
    TopLevel(AmmiraTvDestinations.LIVE, "Live", Icons.Default.LiveTv),
    TopLevel(AmmiraTvDestinations.EPG, "Guida", Icons.Default.CalendarToday),
    TopLevel(AmmiraTvDestinations.VOD, "Film", Icons.Default.Movie),
    TopLevel(AmmiraTvDestinations.SEARCH, "Cerca", Icons.Default.Search),
    TopLevel(AmmiraTvDestinations.SETTINGS, "Altro", Icons.Default.Settings)
)

/**
 * App shell that picks the navigation pattern for the device:
 *  - phone (narrow window): bottom bar;
 *  - tablet / wide window: side rail;
 *  - TV and Fire TV: nothing here, the Live screen's focusable tiles are the menu.
 * The bar is hidden while a full-screen player is showing.
 */
@Composable
fun AdaptiveNavigation(
    navController: NavHostController,
    content: @Composable (Modifier) -> Unit
) {
    val device = LocalDeviceProfile.current
    val width = LocalWidthClass.current
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val onTopLevel = TOP_LEVEL.any { it.route == route }
    val show = !device.isTv && onTopLevel && !PlaybackChrome.fullscreenPlayer && !PlaybackChrome.inPictureInPicture

    fun go(target: String) {
        if (target == route) return
        navController.navigate(target) {
            // Always one level above Live, so Back from any section returns to Live
            popUpTo(AmmiraTvDestinations.LIVE) { inclusive = false }
            launchSingleTop = true
        }
    }

    if (!show) {
        content(Modifier.fillMaxSize())
        return
    }

    val colors = NavigationBarItemDefaults.colors(
        selectedIconColor = androidx.compose.ui.graphics.Color.White,
        selectedTextColor = AmmiraTeal,
        indicatorColor = AmmiraTeal,
        unselectedIconColor = TextSecondary,
        unselectedTextColor = TextSecondary
    )

    if (width == WidthClass.COMPACT) {
        Column(Modifier.fillMaxSize()) {
            content(Modifier.weight(1f))
            NavigationBar(containerColor = DarkSurface, windowInsets = WindowInsets(0, 0, 0, 0)) {
                TOP_LEVEL.forEach { item ->
                    NavigationBarItem(
                        selected = item.route == route,
                        onClick = { go(item.route) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1, fontSize = 11.sp) },
                        colors = colors
                    )
                }
            }
        }
    } else {
        val railColors = NavigationRailItemDefaults.colors(
            selectedIconColor = androidx.compose.ui.graphics.Color.White,
            selectedTextColor = AmmiraTeal,
            indicatorColor = AmmiraTeal,
            unselectedIconColor = TextSecondary,
            unselectedTextColor = TextSecondary
        )
        Row(Modifier.fillMaxSize()) {
            NavigationRail(containerColor = DarkSurface, windowInsets = WindowInsets(0, 0, 0, 0)) {
                TOP_LEVEL.forEach { item ->
                    NavigationRailItem(
                        selected = item.route == route,
                        onClick = { go(item.route) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, maxLines = 1, fontSize = 11.sp) },
                        colors = railColors
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxSize()) { content(Modifier.fillMaxSize()) }
        }
    }
}
