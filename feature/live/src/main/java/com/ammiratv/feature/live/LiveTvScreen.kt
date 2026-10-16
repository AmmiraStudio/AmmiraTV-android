package com.ammiratv.feature.live

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import com.ammiratv.core.ui.R as UiR
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ammiratv.core.ui.component.PinDialog
import com.ammiratv.core.model.Channel
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ammiratv.core.ui.component.CategoryPill
import com.ammiratv.core.ui.component.ChannelItemView
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.AmmiraPurple
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary
import com.ammiratv.domain.usecase.EpgSyncState

import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed

@Composable
fun LiveTvScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToEpg: () -> Unit = {},
    onNavigateToSearch: () -> Unit = {},
    onNavigateToProfiles: () -> Unit = {},
    onNavigateToVod: () -> Unit = {},
    onNavigateToSources: () -> Unit = {},
    /** Channel another screen (EPG, search) asked us to play; null/<=0 means none. */
    pendingChannelId: Long? = null,
    onPendingChannelConsumed: () -> Unit = {},
    viewModel: LiveTvViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val playerState by viewModel.playerState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val channels by viewModel.channels.collectAsState()
    val nowAndNext by viewModel.nowAndNext.collectAsState()
    val epgSyncState by viewModel.epgSyncState.collectAsState()
    val playlists by viewModel.playlists.collectAsState()
    val quickQuery by viewModel.quickQuery.collectAsState()
    val quickResults by viewModel.quickResults.collectAsState()
    val activeProfile by viewModel.activeProfile.collectAsState()
    val sleepTimerMinutes by viewModel.sleepTimerMinutes.collectAsState()
    val stillWatching by viewModel.stillWatchingPrompt.collectAsState()
    val numberEntry by viewModel.numberEntry.collectAsState()
    val numberingEnabled by viewModel.numberingEnabled.collectAsState()
    val isTvDevice = com.ammiratv.core.ui.device.LocalDeviceProfile.current.isTv
    // A remote needs something focused from the start, or the first press lands "somewhere"
    val firstFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(isTvDevice, uiState.playingChannel == null) {
        if (isTvDevice && uiState.playingChannel == null) runCatching { firstFocus.requestFocus() }
    }

    LaunchedEffect(pendingChannelId) {
        if (pendingChannelId != null && pendingChannelId > 0) {
            viewModel.playChannelById(pendingChannelId)
            onPendingChannelConsumed()
        }
    }

    // When a channel is playing, show the PlayerScreen with fast zapping
    if (uiState.playingChannel != null) {
        com.ammiratv.core.ui.device.FullscreenPlayerEffect()
        PlayerScreen(
            channel = uiState.playingChannel!!,
            playerEngine = viewModel.playerEngine,
            playerState = playerState,
            isOverlayVisible = uiState.isOverlayVisible,
            epgInfo = nowAndNext[uiState.playingChannel?.tvgId],
            failoverMessage = uiState.failoverMessage,
            isAudioMenuVisible = uiState.isAudioMenuVisible,
            isSubtitleMenuVisible = uiState.isSubtitleMenuVisible,
            onZapNext = { viewModel.zapNext() },
            onZapPrevious = { viewModel.zapPrevious() },
            onToggleOverlay = { viewModel.toggleOverlay() },
            onHideOverlay = { viewModel.hideOverlay() },
            onBackClick = { viewModel.stopPlayback() },
            onCycleAspectRatio = { viewModel.cycleAspectRatio() },
            onTogglePlayPause = { viewModel.togglePlayPause() },
            onSeekRelative = { offset -> viewModel.seekRelative(offset) },
            onJumpToLive = { viewModel.jumpToLive() },
            onToggleAudioMenu = { viewModel.toggleAudioMenu() },
            onToggleSubtitleMenu = { viewModel.toggleSubtitleMenu() },
            onSelectAudioTrack = { index -> viewModel.setAudioTrack(index) },
            onSelectSubtitleTrack = { index -> viewModel.setSubtitleTrack(index) },
            onDismissTrackMenus = { viewModel.dismissTrackMenus() },
            onClearFailoverMessage = { viewModel.clearFailoverMessage() },
            isQuickPanelOpen = uiState.isQuickPanelOpen,
            playlists = playlists,
            activePlaylistId = uiState.playlistId,
            quickQuery = quickQuery,
            quickResults = quickResults,
            onOpenQuickPanel = { viewModel.openQuickPanel() },
            onCloseQuickPanel = { viewModel.closeQuickPanel() },
            onQuickQueryChange = { viewModel.setQuickQuery(it) },
            onSwitchPlaylist = { viewModel.switchPlaylist(it) },
            onPlayChannel = { viewModel.playChannel(it) },
            onRecord = { viewModel.recordCurrentChannel() },
            sleepTimerMinutes = sleepTimerMinutes,
            onCycleSleepTimer = { viewModel.cycleSleepTimer() },
            numberEntry = numberEntry,
            onDigit = { viewModel.onDigit(it) },
            onConfirmNumber = { viewModel.confirmNumberEntry() },
            onOverlayInteraction = { viewModel.keepOverlayAlive() },
            onToggleFill = { viewModel.toggleFill() },
            modifier = modifier
        )

        if (stillWatching) {
            AlertDialog(
                onDismissRequest = { viewModel.confirmStillWatching() },
                confirmButton = {
                    TextButton(onClick = { viewModel.confirmStillWatching() }) { Text("Sì, continua") }
                },
                title = { Text("Stai ancora guardando?") },
                text = { Text("La riproduzione si fermerà tra un minuto se non rispondi.") }
            )
        }
        return
    }

    // PIN gate: settings screens of a child profile need the parental PIN.
    var pendingProtectedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pinError by remember { mutableStateOf<String?>(null) }

    fun guarded(action: () -> Unit) {
        if (viewModel.requiresPinForSettings()) {
            pinError = null
            pendingProtectedAction = action
        } else {
            action()
        }
    }

    val openSettings: () -> Unit = { guarded(onNavigateToSettings) }
    val openSources: () -> Unit = { guarded(onNavigateToSources) }
    val openProfiles: () -> Unit = { guarded(onNavigateToProfiles) }

    pendingProtectedAction?.let { action ->
        PinDialog(
            title = "Inserisci il PIN genitori",
            errorMessage = pinError,
            onComplete = { pin ->
                if (viewModel.verifyPin(pin)) {
                    pendingProtectedAction = null
                    action()
                } else {
                    pinError = "PIN errato"
                }
            },
            onDismiss = { pendingProtectedAction = null }
        )
    }

    if (uiState.isLimitReached) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissLimitReached() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissLimitReached() }) { Text("OK") }
            },
            title = { Text("Tempo di visione terminato") },
            text = { Text("Il limite giornaliero di questo profilo è stato raggiunto. Riprova domani.") }
        )
    }

    // Channel Browsing UI
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding() // edge-to-edge: keep the title clear of the clock
                .padding(horizontal = if (isTvDevice) 24.dp else 16.dp, vertical = if (isTvDevice) 20.dp else 12.dp)
        ) {
            // Top bar: logo + wordmark, profile and settings as small round buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = UiR.drawable.app_logo),
                    contentDescription = "AmmiraTv Logo",
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    com.ammiratv.core.ui.component.ScrollingText(
                        text = "AMMIRATV",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        style = androidx.compose.ui.text.TextStyle(letterSpacing = 3.sp),
                        color = TextPrimary
                    )
                    (epgSyncState as? EpgSyncState.Syncing)?.let { syncing ->
                        Text(
                            text = if (syncing.programs > 0) "Aggiornamento guida: ${syncing.programs}" else "Aggiornamento guida…",
                            fontSize = 11.sp,
                            color = AmmiraTeal
                        )
                    }
                }
                RoundIconButton(Icons.Default.Person, activeProfile?.name ?: "Profilo", openProfiles)
                Spacer(modifier = Modifier.width(10.dp))
                RoundIconButton(Icons.Default.Settings, "Impostazioni", openSettings)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // TV: four big focusable tiles are the menu. Phones and tablets get a bottom bar / side
            // rail from the app shell instead, so the tiles would only duplicate it.
            if (isTvDevice) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    HomeTile(
                        "LIVE", Icons.Default.LiveTv, active = true, onClick = {},
                        modifier = Modifier.weight(1f).focusRequester(firstFocus)
                    )
                    HomeTile("GUIDA TV", Icons.Default.CalendarToday, active = false, onClick = onNavigateToEpg, modifier = Modifier.weight(1f))
                    HomeTile("FILM E SERIE", Icons.Default.Movie, active = false, onClick = onNavigateToVod, modifier = Modifier.weight(1f))
                    HomeTile("CERCA", Icons.Default.Search, active = false, onClick = onNavigateToSearch, modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Categories horizontal bar
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                item {
                    CategoryPill(
                        name = "Tutti i canali",
                        isSelected = uiState.selectedCategory == null && !uiState.isFavoritesSelected,
                        onClick = { viewModel.selectCategory(null) }
                    )
                }
                item {
                    CategoryPill(
                        name = "⭐ Preferiti",
                        isSelected = uiState.isFavoritesSelected,
                        onClick = { viewModel.selectFavorites() }
                    )
                }
                itemsIndexed(categories) { _, category ->
                    CategoryPill(
                        name = category.name,
                        isSelected = uiState.selectedCategory?.id == category.id,
                        onClick = { viewModel.selectCategory(category) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Channel List or Empty State
            if (channels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LiveTv,
                            contentDescription = null,
                            tint = Color(0xFF4A4A52),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (uiState.playlistId == 0L) "Nessuna sorgente configurata" else "Nessun canale in questa categoria",
                            color = TextSecondary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = openSources,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = AmmiraTeal,
                                contentColor = Color(0xFFFFFFFF)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(10.dp), onClick = openSources)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Aggiungi Playlist M3U o Xtream", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
                // Guide data only for what is on screen (+ margin), however long the list is
                LaunchedEffect(channels, gridState) {
                    androidx.compose.runtime.snapshotFlow {
                        val visible = gridState.layoutInfo.visibleItemsInfo
                        val first = visible.firstOrNull()?.index ?: 0
                        val last = visible.lastOrNull()?.index ?: 0
                        first to last
                    }.collect { (first, last) ->
                        if (channels.isNotEmpty()) {
                            val from = (first - 10).coerceAtLeast(0)
                            val to = (last + 20).coerceAtMost(channels.size - 1)
                            viewModel.onVisibleGuideIds(
                                (from..to).mapNotNull { channels.getOrNull(it)?.tvgId }
                            )
                        }
                    }
                }
                // One column on a phone, more on tablets and wide windows
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    state = gridState,
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(
                        minSize = if (com.ammiratv.core.ui.device.LocalDeviceProfile.current.lowRam) 560.dp else 340.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItemsIndexed(
                        items = channels,
                        key = { _, channel -> channel.id }
                    ) { index, channel ->
                        ChannelItemView(
                            channel = channel,
                            // Numbering on: the real TV number, blank when the channel has none
                            channelNumber = if (numberingEnabled) channel.channelNumber else index + 1,
                            epgInfo = nowAndNext[channel.tvgId],
                            onChannelClick = { viewModel.playChannel(it) },
                            onFavoriteToggle = { viewModel.toggleFavorite(it) }
                        )
                    }
                }
            }
        }
    }
}

/** Big rounded navigation tile of the home screen; the active one is filled with the accent gradient. */
@Composable
private fun HomeTile(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    val fill = if (active) {
        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(AmmiraTeal, AmmiraPurple))
    } else {
        androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(DarkCard, DarkCard))
    }
    Box(
        modifier = modifier
            .height(76.dp)
            .dpadFocusable(shape = shape, focusedScale = 1.06f, onClick = onClick)
            .clip(shape)
            .background(fill)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = label,
                color = androidx.compose.ui.graphics.Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .dpadFocusable(shape = androidx.compose.foundation.shape.CircleShape, focusedScale = 1.1f, onClick = onClick)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(DarkCard),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.size(22.dp)
        )
    }
}