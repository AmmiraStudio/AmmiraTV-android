package com.ammiratv.feature.vod

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import java.util.Locale
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.input.pointer.pointerInput

@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(
    onBackClick: () -> Unit,
    viewModel: VodPlayerViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    val player by viewModel.playerState.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    com.ammiratv.core.ui.device.FullscreenPlayerEffect()
    com.ammiratv.core.ui.device.OrientationResetEffect()
    val deviceIsTv = com.ammiratv.core.ui.device.LocalDeviceProfile.current.isTv
    val rotate = com.ammiratv.core.ui.device.rememberOrientationToggle()
    // On a TV the picture is already full screen: the button does nothing visible there
    val rotateScreen: () -> Unit = { if (!deviceIsTv) rotate() }

    // Immersive while watching; the bars come back when the player is left
    val hostView = androidx.compose.ui.platform.LocalView.current
    DisposableEffect(hostView) {
        hostView.keepScreenOn = true
        val window = (hostView.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, hostView) }
        controller?.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            hostView.keepScreenOn = false
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    // Always persist the position and stop the shared engine, however the screen is left.
    DisposableEffect(Unit) {
        onDispose { viewModel.onLeave() }
    }

    BackHandler {
        when {
            state.audioMenu || state.subtitleMenu -> viewModel.dismissMenus()
            state.controlsVisible -> onBackClick()
            else -> viewModel.showControls() // first Back reveals controls, second leaves
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focusRequester)
            .focusable()
            // Touch: a tap brings the controls up
            .pointerInput(Unit) { detectTapGestures(onTap = { viewModel.showControls() }) }
            .onPreviewKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                // While a track menu is open it owns the D-pad
                if (state.audioMenu || state.subtitleMenu) return@onPreviewKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        viewModel.seekRelative(-10_000); true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        viewModel.seekRelative(10_000); true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_SPACE -> {
                        viewModel.togglePlayPause(); true
                    }
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                        viewModel.showControls(); true
                    }
                    else -> false
                }
            }
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.player = viewModel.engine.getPlayerView() as? Player
                }
            },
            update = { it.player = viewModel.engine.getPlayerView() as? Player },
            modifier = Modifier.fillMaxSize()
        )

        if (player.isBuffering || !state.isReady) {
            CircularProgressIndicator(color = AmmiraTeal, modifier = Modifier.align(Alignment.Center).size(56.dp))
        }

        if (player.isError) {
            Text(
                text = player.errorMessage ?: "Impossibile riprodurre questo contenuto",
                color = Color(0xFFFF5252),
                modifier = Modifier.align(Alignment.Center).padding(24.dp)
            )
        }
        state.errorMessage?.let {
            Text(it, color = Color(0xFFFF5252), modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }

        if (state.controlsVisible) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xF2000000))))
                    .padding(horizontal = 32.dp, vertical = 20.dp)
            ) {
                com.ammiratv.core.ui.component.ScrollingText(
                    state.title, forceScroll = true, color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold
                )
                state.subtitle?.let {
                    com.ammiratv.core.ui.component.ScrollingText(it, forceScroll = true, color = TextSecondary, fontSize = 14.sp)
                }

                Spacer(Modifier.height(12.dp))

                val fraction = if (player.durationMs > 0) {
                    (player.currentPositionMs.toFloat() / player.durationMs).coerceIn(0f, 1f)
                } else 0f
                // Tapping the bar seeks (touch); the D-pad uses left/right
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .pointerInput(player.durationMs) {
                            detectTapGestures { offset ->
                                if (player.durationMs > 0) viewModel.seekToFraction(offset.x / size.width)
                            }
                        },
                    color = AmmiraTeal,
                    trackColor = Color(0xFF44444D)
                )
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(player.currentPositionMs), color = TextSecondary, fontSize = 12.sp)
                    Text(formatTime(player.durationMs), color = TextSecondary, fontSize = 12.sp)
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState())
                ) {
                    ControlButton(if (player.isPlaying) "Pausa" else "Riproduci", viewModel::togglePlayPause)
                    ControlButton("-30s") { viewModel.seekRelative(-30_000) }
                    ControlButton("+30s") { viewModel.seekRelative(30_000) }
                    if (!deviceIsTv) ControlButton("Schermo intero") { rotateScreen() }
                    ControlButton("Audio", viewModel::toggleAudioMenu)
                    ControlButton("Sottotitoli", viewModel::toggleSubtitleMenu)
                }
            }
        }

        if (state.audioMenu) {
            TrackDialog(
                title = "Traccia audio",
                options = player.audioTracks.map { Triple(it.index, "${it.name} ${it.language?.let { l -> "($l)" } ?: ""}", it.isSelected) },
                onSelect = viewModel::setAudioTrack,
                onDismiss = viewModel::dismissMenus
            )
        }
        if (state.subtitleMenu) {
            TrackDialog(
                title = "Sottotitoli",
                options = listOf(Triple(-1, "Disattiva", player.subtitleTracks.none { it.isSelected })) +
                    player.subtitleTracks.map { Triple(it.index, "${it.name} ${it.language?.let { l -> "($l)" } ?: ""}", it.isSelected) },
                onSelect = viewModel::setSubtitleTrack,
                onDismiss = viewModel::dismissMenus
            )
        }
    }
}

@Composable
private fun ControlButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = DarkCard, contentColor = TextPrimary),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) { Text(text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun TrackDialog(
    title: String,
    options: List<Triple<Int, String, Boolean>>,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (options.isEmpty()) Text("Nessuna traccia disponibile")
                options.forEach { (index, label, selected) ->
                    Button(
                        onClick = { onSelect(index) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (selected) AmmiraTeal else DarkCard,
                            contentColor = if (selected) Color(0xFFFFFFFF) else TextPrimary
                        ),
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .fillMaxWidth()
                            .dpadFocusable(shape = RoundedCornerShape(8.dp)) { onSelect(index) }
                    ) { Text(label.trim(), fontSize = 14.sp) }
                }
            }
        }
    )
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
