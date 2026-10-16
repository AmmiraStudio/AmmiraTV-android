package com.ammiratv.core.ui.device

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Tells the app shell whether a full-screen player is on screen, so the navigation bar / rail hides
 * while watching and comes back afterwards. A counter, not a flag: two players overlapping during
 * a screen transition must not leave the bar stuck hidden.
 */
object PlaybackChrome {
    private var users by mutableIntStateOf(0)

    val fullscreenPlayer: Boolean get() = users > 0

    /** Set by the Activity: the app is shown in the small Picture-in-Picture window. */
    var inPictureInPicture by androidx.compose.runtime.mutableStateOf(false)

    internal fun enter() { users++ }
    internal fun leave() { users = (users - 1).coerceAtLeast(0) }
}

/** Call from every full-screen player composable. */
@Composable
fun FullscreenPlayerEffect() {
    DisposableEffect(Unit) {
        PlaybackChrome.enter()
        onDispose { PlaybackChrome.leave() }
    }
}
