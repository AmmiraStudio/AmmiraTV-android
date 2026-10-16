package com.ammiratv.feature.live

import android.view.KeyEvent
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import com.ammiratv.core.ui.component.ExpandableText
import com.ammiratv.core.ui.component.ScrollingText
import com.ammiratv.core.ui.device.LocalDeviceProfile
import com.ammiratv.core.ui.device.LocalWidthClass
import com.ammiratv.core.ui.device.WidthClass
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ammiratv.core.common.ChannelNameCleaner
import com.ammiratv.core.model.AspectRatio
import com.ammiratv.core.model.Channel
import com.ammiratv.core.model.ChannelEpgInfo
import com.ammiratv.core.model.PlayerState
import com.ammiratv.core.model.Playlist
import com.ammiratv.domain.usecase.UniversalSearchResult
import com.ammiratv.core.player.PlayerEngine
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.DarkSurfaceVariant
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: Channel,
    playerEngine: PlayerEngine,
    playerState: PlayerState,
    isOverlayVisible: Boolean,
    epgInfo: ChannelEpgInfo? = null,
    failoverMessage: String? = null,
    isAudioMenuVisible: Boolean = false,
    isSubtitleMenuVisible: Boolean = false,
    onZapNext: () -> Unit,
    onZapPrevious: () -> Unit,
    onToggleOverlay: () -> Unit,
    onHideOverlay: () -> Unit,
    onBackClick: () -> Unit,
    onCycleAspectRatio: () -> Unit = {},
    onTogglePlayPause: () -> Unit = {},
    onSeekRelative: (Long) -> Unit = {},
    onJumpToLive: () -> Unit = {},
    onToggleAudioMenu: () -> Unit = {},
    onToggleSubtitleMenu: () -> Unit = {},
    onSelectAudioTrack: (Int) -> Unit = {},
    onSelectSubtitleTrack: (Int) -> Unit = {},
    onDismissTrackMenus: () -> Unit = {},
    onClearFailoverMessage: () -> Unit = {},
    isQuickPanelOpen: Boolean = false,
    playlists: List<Playlist> = emptyList(),
    activePlaylistId: Long = 0L,
    quickQuery: String = "",
    quickResults: UniversalSearchResult = UniversalSearchResult(),
    onOpenQuickPanel: () -> Unit = {},
    onCloseQuickPanel: () -> Unit = {},
    onQuickQueryChange: (String) -> Unit = {},
    onSwitchPlaylist: (Long) -> Unit = {},
    onPlayChannel: (Channel) -> Unit = {},
    onRecord: () -> Unit = {},
    sleepTimerMinutes: Int? = null,
    onCycleSleepTimer: () -> Unit = {},
    /** Number being typed with the remote, if any. */
    numberEntry: NumberEntryState? = null,
    onDigit: (Int) -> Unit = {},
    onConfirmNumber: () -> Unit = {},
    /** Any key while the controls bar is showing: restart its hide timer. */
    onOverlayInteraction: () -> Unit = {},
    /** TV: make the picture fill the whole screen (no black bars) / back to the original shape. */
    onToggleFill: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val device = LocalDeviceProfile.current
    val config = LocalConfiguration.current
    // Phone held upright: video on top, programme info and controls underneath
    val portrait = !device.isTv && config.screenHeightDp > config.screenWidthDp
    val compact = LocalWidthClass.current == WidthClass.COMPACT
    val videoHeight = (config.screenWidthDp * 9f / 16f).dp
    val rotateScreen = com.ammiratv.core.ui.device.rememberOrientationToggle()
    com.ammiratv.core.ui.device.OrientationResetEffect()
    // Phones / tablets: rotate. TV / Fire TV: the picture is always full screen already, so the
    // button makes it fill the screen instead (no side bars).
    val toggleFullscreen: () -> Unit = { if (device.isTv) onToggleFill() else rotateScreen() }
    var showNumpad by remember { mutableStateOf(false) }

    // A TV left on a movie must not dim or sleep: keep the screen awake while the player is shown
    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    // Immersive while watching; the bars come back when the player is left
    androidx.compose.runtime.DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars()) }
    }

    // Allow the Activity to shrink into Picture-in-Picture when the user leaves while watching
    androidx.compose.runtime.DisposableEffect(Unit) {
        com.ammiratv.core.common.PipController.canEnterPip = true
        onDispose { com.ammiratv.core.common.PipController.canEnterPip = false }
    }

    // With the controls bar showing, the D-pad belongs to it: focus goes to its first button.
    // When the bar (or the panel) goes away, focus returns to the video so zapping works again.
    val controlsFocus = remember { FocusRequester() }
    LaunchedEffect(isQuickPanelOpen, isOverlayVisible) {
        runCatching {
            if (isQuickPanelOpen) return@runCatching
            if (isOverlayVisible) controlsFocus.requestFocus() else focusRequester.requestFocus()
        }
    }

    BackHandler {
        if (isQuickPanelOpen) {
            onCloseQuickPanel()
        } else if (isAudioMenuVisible || isSubtitleMenuVisible) {
            onDismissTrackMenus()
        } else if (isOverlayVisible) {
            onHideOverlay()
        } else {
            onBackClick()
        }
    }

    val resizeMode = when (playerState.aspectRatio) {
        AspectRatio.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        AspectRatio.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        AspectRatio.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        AspectRatio.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .focusRequester(focusRequester)
            .focusable()
            // Touch: tap shows the controls (or the search panel when they are always visible),
            // swipe up / down changes channel
            .pointerInput(portrait, isQuickPanelOpen) {
                detectTapGestures(onTap = {
                    // Held upright a tap goes full screen; in landscape it shows / hides the controls
                    if (!isQuickPanelOpen) { if (portrait) toggleFullscreen() else onToggleOverlay() }
                })
            }
            .pointerInput(isQuickPanelOpen) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { _, dy -> total += dy },
                    onDragEnd = {
                        if (!isQuickPanelOpen) {
                            if (total < -120f) onZapNext() else if (total > 120f) onZapPrevious()
                        }
                    }
                )
            }
            .onPreviewKeyEvent { keyEvent ->
                if (isQuickPanelOpen) {
                    // The panel owns the D-pad (text field, chips, results); only BACK is ours.
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                        keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK
                    ) {
                        onCloseQuickPanel()
                        true
                    } else false
                } else if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                    // While the controls bar is up, arrows and OK are for its buttons (focus
                    // navigation / click); only Back and the digit keys stay with the player.
                    val code = keyEvent.nativeKeyEvent.keyCode
                    // Left / right / OK move along the bar and press its buttons. Up / down are NOT
                    // needed there (it is a single row), so they keep changing channel: no need to
                    // leave the controls first.
                    val navigationKey = code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT ||
                        code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    if (isOverlayVisible) onOverlayInteraction()
                    if (isOverlayVisible && navigationKey) return@onPreviewKeyEvent false
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            onZapPrevious()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            onZapNext()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (!isOverlayVisible) {
                                onSeekRelative(-15_000L) // Rewind 15s
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!isOverlayVisible) {
                                onSeekRelative(15_000L) // Forward 15s
                                true
                            } else false
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                            onTogglePlayPause()
                            true
                        }
                        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> {
                            onDigit(keyEvent.nativeKeyEvent.keyCode - KeyEvent.KEYCODE_0)
                            true
                        }
                        in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> {
                            onDigit(keyEvent.nativeKeyEvent.keyCode - KeyEvent.KEYCODE_NUMPAD_0)
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                            if (numberEntry != null && !numberEntry.notFound) {
                                // OK while typing a number tunes it immediately
                                onConfirmNumber()
                            } else {
                                // One OK shows the controls bar over the full-screen video
                                onToggleOverlay()
                            }
                            true
                        }
                        // Menu key (Amazon / Android TV remotes): search, source and sleep timer
                        KeyEvent.KEYCODE_MENU -> {
                            onOpenQuickPanel()
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            onSeekRelative(-15_000L)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            onSeekRelative(15_000L)
                            true
                        }
                        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                            onTogglePlayPause()
                            true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            if (isAudioMenuVisible || isSubtitleMenuVisible) {
                                onDismissTrackMenus()
                                true
                            } else if (isOverlayVisible) {
                                onHideOverlay()
                                true
                            } else {
                                onBackClick()
                                true
                            }
                        }
                        else -> false
                    }
                } else false
            }
    ) {
        val videoModifier = if (portrait) {
            Modifier.align(Alignment.TopCenter).fillMaxWidth().aspectRatio(16f / 9f)
        } else Modifier.fillMaxSize()

        // Media3 Video Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    this.resizeMode = resizeMode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = playerEngine.getPlayerView() as? Player
                }
            },
            update = { view ->
                view.player = playerEngine.getPlayerView() as? Player
                view.resizeMode = resizeMode
            },
            modifier = videoModifier
        )

        // Phone held upright: a visible full-screen button on the picture itself
        if (portrait && !com.ammiratv.core.ui.device.PlaybackChrome.inPictureInPicture) {
            androidx.compose.material3.IconButton(
                onClick = toggleFullscreen,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = videoHeight - 52.dp, end = 6.dp)
                    .background(Color(0x88000000), RoundedCornerShape(24.dp))
            ) {
                Icon(Icons.Default.Fullscreen, contentDescription = "Schermo intero", tint = Color.White)
            }
        }

        // Buffering Indicator
        if (playerState.isBuffering) {
            Box(
                modifier = videoModifier,
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = AmmiraTeal,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(56.dp)
                    )
                    if (playerState.isReconnecting) {
                        Spacer(Modifier.height(10.dp))
                        Text("Riconnessione in corso…", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }

        // Error Banner
        if (playerState.isError && failoverMessage == null) {
            Box(
                modifier = videoModifier.background(Color(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = Color(0xFFFF5252),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Errore di riproduzione dello stream",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ExpandableText(
                        text = playerState.errorMessage ?: "Impossibile aprire il flusso video",
                        title = "Errore di riproduzione",
                        collapsedLines = 3,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Automatic Failover Top Banner Notification
        AnimatedVisibility(
            visible = failoverMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xE61A1A1F))
                    .border(1.dp, AmmiraTeal, RoundedCornerShape(12.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = null,
                        tint = AmmiraTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = failoverMessage ?: "",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Quick Info & Controls Overlay Banner (Press OK or D-pad Center)
        AnimatedVisibility(
            visible = (isOverlayVisible || portrait) && !com.ammiratv.core.ui.device.PlaybackChrome.inPictureInPicture,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = if (portrait) {
                Modifier.align(Alignment.TopStart).padding(top = videoHeight).fillMaxSize()
            } else Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (portrait) Modifier.fillMaxSize().verticalScroll(rememberScrollState()) else Modifier)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xF20E0E10), Color(0xFF0B0B0D))
                        )
                    )
                    .padding(horizontal = if (compact) 14.dp else 28.dp, vertical = if (compact) 12.dp else 20.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Channel & Current Program Info Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = onBackClick,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Indietro",
                                tint = Color.White
                            )
                        }

                        Spacer(modifier = Modifier.width(if (compact) 8.dp else 16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ScrollingText(
                                    text = (channel.channelNumber?.let { "$it  " } ?: "") + channel.cleanName,
                                    forceScroll = true,
                                    color = TextPrimary,
                                    fontSize = if (compact) 18.sp else 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                val quality = ChannelNameCleaner.extractQuality(channel.name)
                                if (quality != null) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(AmmiraTeal, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = quality,
                                            color = Color(0xFFFFFFFF),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                if (!channel.groupTitle.isNullOrBlank() && !compact) {
                                    Spacer(modifier = Modifier.width(12.dp))
                                    ScrollingText(
                                        text = "•   ${channel.groupTitle}",
                                        forceScroll = true,
                                        color = TextSecondary,
                                        fontSize = 14.sp,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                }
                            }

                            val curProg = epgInfo?.currentProgram
                            if (curProg != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${formatEpgTime(curProg.startTime)} - ${formatEpgTime(curProg.endTime)}",
                                        color = AmmiraTeal,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    ScrollingText(
                                        text = curProg.title,
                                        forceScroll = true,
                                        color = Color.White,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                LinearProgressIndicator(
                                    progress = { epgInfo.progressPercent },
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = AmmiraTeal,
                                    trackColor = Color(0xFF30303A)
                                )

                                val nextProg = epgInfo.nextProgram
                                if (nextProg != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    ScrollingText(
                                        text = "A seguire (${formatEpgTime(nextProg.startTime)}): ${nextProg.title}",
                                        forceScroll = true,
                                        color = TextSecondary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        // Remote D-pad hint: only meaningful with a remote
                        if (device.isTv) {
                            Text(
                                text = "▲ ▼ Canale  •  ◄ ► Timeshift",
                                color = Color(0xFF888888),
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Player Action Controls Bar (Play/Pause, Seek, Aspect Ratio, Audio, Subtitles, Jump to Live)
                    // What the device is actually decoding: helps to tell "no picture" causes apart
                    playerState.videoInfo?.let {
                        Text(
                            text = it,
                            color = TextSecondary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(bottom = 6.dp, start = if (compact) 0.dp else 56.dp)
                        )
                    }
                    // Scrolls sideways when the buttons do not fit (phones); D-pad focus follows
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = if (compact) 0.dp else 56.dp)
                    ) {
                        PlayerActionButton(
                            icon = if (playerState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            label = if (playerState.isPlaying) "Pausa" else "Riproduci",
                            modifier = Modifier.focusRequester(controlsFocus),
                            onClick = onTogglePlayPause
                        )

                        PlayerActionButton(
                            icon = Icons.Default.FastRewind,
                            label = "-30s",
                            onClick = { onSeekRelative(-30_000L) }
                        )

                        PlayerActionButton(
                            icon = Icons.Default.FastForward,
                            label = "+30s",
                            onClick = { onSeekRelative(30_000L) }
                        )

                        PlayerActionButton(
                            icon = Icons.Default.FiberManualRecord,
                            label = "Diretta",
                            iconTint = Color(0xFFFF4D4D),
                            onClick = onJumpToLive
                        )

                        PlayerActionButton(
                            icon = Icons.Default.FiberManualRecord,
                            label = "Registra",
                            iconTint = Color(0xFFFF4D4D),
                            onClick = onRecord
                        )

                        PlayerActionButton(
                            icon = Icons.Default.AspectRatio,
                            label = playerState.aspectRatio.name,
                            onClick = onCycleAspectRatio
                        )

                        // Remotes without number keys (many Fire TV ones) use this on-screen pad
                        PlayerActionButton(
                            icon = Icons.Default.Dialpad,
                            label = "Numeri",
                            onClick = { showNumpad = true }
                        )

                        PlayerActionButton(
                            icon = Icons.Default.Fullscreen,
                            label = if (device.isTv) "Schermo intero" else if (portrait) "Schermo intero" else "Riduci",
                            onClick = toggleFullscreen
                        )

                        PlayerActionButton(
                            icon = Icons.Default.Search,
                            label = "Cerca / Timer",
                            onClick = onOpenQuickPanel
                        )

                        PlayerActionButton(
                            icon = Icons.Default.Audiotrack,
                            label = "Audio",
                            onClick = onToggleAudioMenu
                        )

                        PlayerActionButton(
                            icon = Icons.Default.Subtitles,
                            label = "Sottotitoli",
                            onClick = onToggleSubtitleMenu
                        )
                    }
                }
            }
        }

        // Number being typed: big digits with the channel it will tune (or "not found")
        AnimatedVisibility(
            visible = numberEntry != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
        ) {
            val entry = numberEntry
            if (entry != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .background(Color(0xE60E0E10), RoundedCornerShape(16.dp))
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                ) {
                    Text(
                        text = entry.digits,
                        color = if (entry.notFound) Color(0xFFFF5252) else AmmiraTeal,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black
                    )
                    val line = when {
                        entry.notFound -> "Canale non disponibile"
                        entry.previewName != null -> entry.previewName
                        else -> null
                    }
                    if (line != null) {
                        ScrollingText(
                            text = line,
                            forceScroll = true,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.widthIn(max = 280.dp)
                        )
                    }
                }
            }
        }

        // OK panel: search + current programme + source chips
        AnimatedVisibility(
            visible = isQuickPanelOpen,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            QuickPanel(
                channel = channel,
                epgInfo = epgInfo,
                playlists = playlists,
                activePlaylistId = activePlaylistId,
                query = quickQuery,
                results = quickResults,
                onQueryChange = onQuickQueryChange,
                onSwitchPlaylist = onSwitchPlaylist,
                onPlayChannel = onPlayChannel,
                sleepTimerMinutes = sleepTimerMinutes,
                onCycleSleepTimer = onCycleSleepTimer,
                onShowControls = {
                    onCloseQuickPanel()
                    onToggleOverlay()
                }
            )
        }

        if (showNumpad) {
            NumberPadDialog(
                onDigit = onDigit,
                onConfirm = {
                    onConfirmNumber()
                    showNumpad = false
                },
                onDismiss = { showNumpad = false }
            )
        }

        // Audio Track Selector Dialog
        if (isAudioMenuVisible) {
            TrackSelectionDialog(
                title = "Seleziona Traccia Audio",
                tracks = playerState.audioTracks.map { track ->
                    TrackDisplayItem(
                        index = track.index,
                        title = "${track.name} ${track.language?.let { "($it)" } ?: ""}",
                        isSelected = track.isSelected
                    )
                },
                onSelectTrack = onSelectAudioTrack,
                onDismiss = onDismissTrackMenus
            )
        }

        // Subtitle Track Selector Dialog
        if (isSubtitleMenuVisible) {
            val subtitleItems = mutableListOf(
                TrackDisplayItem(index = -1, title = "Disattiva sottotitoli", isSelected = playerState.subtitleTracks.none { it.isSelected })
            ).apply {
                addAll(playerState.subtitleTracks.map { track ->
                    TrackDisplayItem(
                        index = track.index,
                        title = "${track.name} ${track.language?.let { "($it)" } ?: ""}",
                        isSelected = track.isSelected
                    )
                })
            }

            TrackSelectionDialog(
                title = "Seleziona Sottotitoli",
                tracks = subtitleItems,
                onSelectTrack = onSelectSubtitleTrack,
                onDismiss = onDismissTrackMenus
            )
        }
    }
}

@Composable
private fun PlayerActionButton(
    icon: ImageVector,
    label: String,
    iconTint: Color = AmmiraTeal,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = DarkCard,
            contentColor = TextPrimary
        ),
        shape = RoundedCornerShape(8.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        modifier = modifier
            .height(40.dp)
            .dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onClick)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** On-screen number pad: lets remotes and phones without number keys tune a channel by number. */
@Composable
private fun NumberPadDialog(onDigit: (Int) -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        val first = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceVariant)
                .padding(20.dp)
        ) {
            Text("Numero del canale", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            val rows = listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9))
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { d ->
                        PadKey(
                            label = d.toString(),
                            modifier = if (d == 1) Modifier.focusRequester(first) else Modifier,
                            onClick = { onDigit(d) }
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PadKey(label = "Chiudi", onClick = onDismiss)
                PadKey(label = "0", onClick = { onDigit(0) })
                PadKey(label = "OK", highlighted = true, onClick = onConfirm)
            }
        }
    }
}

@Composable
private fun PadKey(label: String, modifier: Modifier = Modifier, highlighted: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(width = 72.dp, height = 56.dp)
            .dpadFocusable(shape = shape, focusedScale = 1.05f, onClick = onClick)
            .background(if (highlighted) AmmiraTeal else DarkCard, shape)
    ) {
        Text(label, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

data class TrackDisplayItem(
    val index: Int,
    val title: String,
    val isSelected: Boolean
)

@Composable
private fun TrackSelectionDialog(
    title: String,
    tracks: List<TrackDisplayItem>,
    onSelectTrack: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(DarkSurfaceVariant)
                .padding(20.dp)
        ) {
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (tracks.isEmpty()) {
                    Text(
                        text = "Nessuna traccia disponibile per questo stream",
                        color = TextSecondary,
                        fontSize = 14.sp
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(tracks) { item ->
                            Button(
                                onClick = { onSelectTrack(item.index) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (item.isSelected) AmmiraTeal else DarkCard,
                                    contentColor = if (item.isSelected) Color(0xFFFFFFFF) else TextPrimary
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = { onSelectTrack(item.index) })
                            ) {
                                Text(
                                    text = item.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (item.isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatEpgTime(millis: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(millis))
}
