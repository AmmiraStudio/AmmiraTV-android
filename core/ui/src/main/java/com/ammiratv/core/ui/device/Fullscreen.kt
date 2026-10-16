package com.ammiratv.core.ui.device

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Full-screen button action for phones and tablets: turns the device to landscape (video fills
 * the screen) and back to portrait. The system bars are already hidden while a player is shown.
 * On a TV the video is always full screen, so callers use their own action there.
 */
@Composable
fun rememberOrientationToggle(): () -> Unit {
    val activity = LocalContext.current.findActivity()
    val config = LocalConfiguration.current
    val portrait = config.screenHeightDp > config.screenWidthDp
    return remember(activity, portrait) {
        {
            activity?.requestedOrientation = if (portrait) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            }
        }
    }
}

/** Gives the orientation back to the system when the player is left. */
@Composable
fun OrientationResetEffect() {
    val activity = LocalContext.current.findActivity()
    DisposableEffect(activity) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
}