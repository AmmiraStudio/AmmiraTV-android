package com.ammiratv.core.ui.theme

import com.ammiratv.core.ui.device.LocalDeviceProfile
import com.ammiratv.core.ui.device.LocalWidthClass
import com.ammiratv.core.ui.device.rememberDeviceProfile
import com.ammiratv.core.ui.device.rememberWidthClass
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * CompositionLocal to check if the app is running on a TV.
 * Access via LocalIsTv.current from any Composable.
 */
val LocalIsTv = compositionLocalOf { false }

private val DarkColorScheme = darkColorScheme(
    primary = AmmiraTeal,
    onPrimary = DarkBackground,
    primaryContainer = AmmiraTealDark,
    onPrimaryContainer = AmmiraTealLight,
    secondary = AmmiraPurple,
    onSecondary = DarkBackground,
    secondaryContainer = AmmiraPurpleDark,
    onSecondaryContainer = AmmiraPurpleLight,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    error = StatusOffline,
    onError = TextPrimary
)

/**
 * AmmiraTv theme.
 * Always dark (IPTV apps are designed for dark viewing environments).
 * Provides LocalIsTv so any composable can adapt to TV vs mobile.
 */
@Composable
fun AmmiraTvTheme(
    fontScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val profile = rememberDeviceProfile()
    val widthClass = rememberWidthClass()

    // Multiply the system font scale so the user's own accessibility setting is kept
    val baseDensity = androidx.compose.ui.platform.LocalDensity.current
    val density = androidx.compose.ui.unit.Density(baseDensity.density, baseDensity.fontScale * fontScale)

    CompositionLocalProvider(
        LocalIsTv provides profile.isTv,
        LocalDeviceProfile provides profile,
        LocalWidthClass provides widthClass,
        androidx.compose.ui.platform.LocalDensity provides density
    ) {
        MaterialTheme(
            colorScheme = DarkColorScheme,
            typography = AmmiraTvTypography,
            content = content
        )
    }
}
