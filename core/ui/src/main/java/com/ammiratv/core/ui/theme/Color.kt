package com.ammiratv.core.ui.theme

import androidx.compose.ui.graphics.Color

// AmmiraTv palette: neutral near-black with a hot ruby-to-coral accent. No blues, no yellows.
// Designed for readability at 3+ metres (TV viewing distance).

// Primary: hot ruby
val AmmiraRuby = Color(0xFFFF2D55)
val AmmiraRubyDark = Color(0xFFD01438)
val AmmiraRubyLight = Color(0xFFFF6B88)

// Secondary: coral-orange, the warm end of the accent gradient
val AmmiraCoral = Color(0xFFFF7A3D)
val AmmiraCoralDark = Color(0xFFD9591B)
val AmmiraCoralLight = Color(0xFFFFA070)

// Names kept so existing screens keep compiling; they now resolve to the new palette.
val AmmiraTeal = AmmiraRuby
val AmmiraTealDark = AmmiraRubyDark
val AmmiraTealLight = AmmiraRubyLight
val AmmiraPurple = AmmiraCoral
val AmmiraPurpleDark = AmmiraCoralDark
val AmmiraPurpleLight = AmmiraCoralLight

// Backgrounds: neutral blacks, slightly lifted for cards
val DarkBackground = Color(0xFF0A0A0C)
val DarkSurface = Color(0xFF141418)
val DarkSurfaceVariant = Color(0xFF1D1D23)
val DarkCard = Color(0xFF19191E)

// Text
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB5B5BD)
val TextTertiary = Color(0xFF77777F)

// Status
val StatusOnline = Color(0xFF4CAF50)
val StatusOffline = Color(0xFFEF5350)
val StatusWarning = Color(0xFFFF9800)
val StatusBuffering = Color(0xFFFFC107)

// Focus: bright ruby border for D-pad navigation on TV
val FocusBorder = AmmiraRuby
val FocusBorderSecondary = AmmiraCoral
