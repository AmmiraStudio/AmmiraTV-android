package com.ammiratv

import android.Manifest
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.os.Build
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import com.ammiratv.core.ui.theme.DarkBackground
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ammiratv.core.common.PipController
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ammiratv.core.ui.theme.AmmiraTvTheme
import com.ammiratv.navigation.AmmiraTvNavGraph
import dagger.hilt.android.AndroidEntryPoint

/**
 * Single Activity for the entire app.
 * Uses Jetpack Compose for UI rendering on both TV and mobile.
 * @AndroidEntryPoint enables Hilt injection in this Activity.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @javax.inject.Inject
    lateinit var preferences: com.ammiratv.core.datastore.AmmiraTvPreferences

    @javax.inject.Inject
    lateinit var playerEngine: com.ammiratv.core.player.PlayerEngine

    // Mirrors the "Riproduzione in background" setting so onStop never has to wait for the disk
    @Volatile private var backgroundPlayback = true
    private var suspendedByLeaving = false

    override fun onStop() {
        super.onStop()
        // Picture-in-Picture keeps the video on screen: that is not "background"
        if (!backgroundPlayback && !isInPictureInPictureMode && !isChangingConfigurations) {
            playerEngine.suspendInBackground()
            suspendedByLeaving = true
        }
    }

    override fun onStart() {
        super.onStart()
        if (suspendedByLeaving) {
            suspendedByLeaving = false
            playerEngine.resumeFromBackground()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            preferences.backgroundPlayback.collect { backgroundPlayback = it }
        }
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()
        setContent {
            val fontScale by preferences.fontScale.collectAsState(initial = 1f)
            // Saveable: a rotation or recreation must not replay the intro
            // The logo animation is skipped on weak devices: composing the app underneath while it
            // plays is what makes the first seconds stutter there.
            val weakDevice = remember {
                (getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager)?.isLowRamDevice == true
            }
            var showSplash by androidx.compose.runtime.saveable.rememberSaveable {
                androidx.compose.runtime.mutableStateOf(!weakDevice)
            }
            AmmiraTvTheme(fontScale = fontScale) {
                // The last run ended in a crash: show what happened so it can be reported
                var crashText by androidx.compose.runtime.remember {
                    androidx.compose.runtime.mutableStateOf(CrashLogger.read(this@MainActivity))
                }
                crashText?.let { text ->
                    com.ammiratv.core.ui.component.LongTextDialog(
                        title = "L'app si e' chiusa in modo anomalo l'ultima volta",
                        text = text,
                        onDismiss = {
                            CrashLogger.clear(this@MainActivity)
                            crashText = null
                        }
                    )
                }
                Box(Modifier.fillMaxSize()) {
                    // Edge-to-edge is on, so every screen would otherwise draw under the status and
                    // navigation bars (clock over the title, untappable top buttons). Padding the
                    // root once fixes all of them; the insets count as consumed for children, so
                    // screens that already pad themselves do not get a second gap.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(DarkBackground)
                            .windowInsetsPadding(WindowInsets.systemBars)
                    ) {
                        // The app composes underneath while the logo plays
                        AmmiraTvNavGraph()
                    }
                    if (showSplash) AmmiraSplash(onFinished = { showSplash = false })
                }
            }
        }
    }

    // Android 13+ needs a runtime grant, otherwise reminders and the recording notification are
    // silently dropped. A denial is fine: everything else keeps working without notifications.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        com.ammiratv.core.ui.device.PlaybackChrome.inPictureInPicture = isInPictureInPictureMode
    }

    // Home/recents while a stream is playing -> keep it visible in a floating window (API 26+)
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (PipController.canEnterPip && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder()
                        .setAspectRatio(android.util.Rational(16, 9))
                        .build()
                )
            } catch (_: IllegalStateException) {
                // Device without PiP support: ignore
            }
        }
    }
}
