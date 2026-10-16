package com.ammiratv.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ammiratv.core.model.Playlist
import com.ammiratv.core.model.PlaylistType
import com.ammiratv.core.model.daysUntilExpiry
import com.ammiratv.core.ui.component.dpadFocusable
import com.ammiratv.core.ui.theme.DarkBackground
import com.ammiratv.core.ui.theme.DarkCard
import com.ammiratv.core.ui.theme.AmmiraTeal
import com.ammiratv.core.ui.theme.StatusOffline
import com.ammiratv.core.ui.theme.StatusOnline
import com.ammiratv.core.ui.theme.StatusWarning
import com.ammiratv.core.ui.theme.TextPrimary
import com.ammiratv.core.ui.theme.TextSecondary

/** Days left at or below which the expiry line turns orange. */
private const val EXPIRY_WARNING_DAYS = 7

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun SourcesScreen(
    onBackClick: () -> Unit,
    onAddSource: () -> Unit,
    viewModel: SourcesViewModel = hiltViewModel(),
    modifier: Modifier = Modifier
) {
    val playlists by viewModel.playlists.collectAsState()
    val activeId by viewModel.activePlaylistId.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val reimporting by viewModel.reimporting.collectAsState()
    val errors by viewModel.refreshErrors.collectAsState()

    LaunchedEffect(Unit) { viewModel.refreshProviderInfo() }
    BackHandler(onBack = onBackClick)
    val compact = com.ammiratv.core.ui.device.LocalWidthClass.current == com.ammiratv.core.ui.device.WidthClass.COMPACT

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = if (compact) 16.dp else 24.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Sorgenti",
                    fontSize = if (compact) 24.sp else 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Button(
                    onClick = onAddSource,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmmiraTeal,
                        contentColor = Color(0xFFFFFFFF)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(10.dp), onClick = onAddSource)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (compact) "Aggiungi" else "Aggiungi sorgente", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (playlists.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nessuna sorgente. Aggiungi la tua playlist M3U o il tuo account Xtream.",
                        color = TextSecondary,
                        fontSize = 16.sp
                    )
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(items = playlists, key = { it.id }) { playlist ->
                        SourceCard(
                            playlist = playlist,
                            isActive = playlist.id == activeId,
                            isRefreshing = playlist.id in refreshing,
                            isReimporting = playlist.id in reimporting,
                            errorMessage = errors[playlist.id],
                            compact = compact,
                            onReimport = { viewModel.reimport(playlist) },
                            onUse = { viewModel.setActive(playlist.id) },
                            onDelete = { viewModel.delete(playlist.id) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SourceCard(
    playlist: Playlist,
    isActive: Boolean,
    isRefreshing: Boolean,
    isReimporting: Boolean,
    errorMessage: String?,
    compact: Boolean,
    onReimport: () -> Unit,
    onUse: () -> Unit,
    onDelete: () -> Unit
) {
    // Deleting needs a second click: an accidental OK on the remote must not wipe a source.
    var confirmDelete by remember { mutableStateOf(false) }

    // Phones: details on top, the buttons on their own line below (side by side they crushed
    // the text into a thin column and overlapped it). TVs keep the single wide row.
    val card: @Composable (@Composable () -> Unit, @Composable () -> Unit) -> Unit = { info, buttons ->
        if (compact) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkCard, RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                info()
                buttons()
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkCard, RoundedCornerShape(14.dp))
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Box(Modifier.weight(1f)) { info() }
                buttons()
            }
        }
    }

    card({
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = playlist.name,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isActive) {
                    Spacer(modifier = Modifier.width(10.dp))
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Attiva",
                        tint = AmmiraTeal,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (playlist.type) {
                    PlaylistType.M3U -> "Playlist M3U"
                    PlaylistType.XTREAM -> "Xtream Codes"
                    PlaylistType.STALKER -> "Stalker / MAG"
                },
                color = TextSecondary,
                fontSize = 13.sp
            )

            if (playlist.type == PlaylistType.XTREAM) {
                Spacer(modifier = Modifier.height(8.dp))
                ProviderStatusLine(playlist = playlist, isRefreshing = isRefreshing)
            }

            if (isReimporting) {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Aggiornamento canali in corso…", color = AmmiraTeal, fontSize = 13.sp)
            } else if (errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(errorMessage, color = StatusOffline, fontSize = 13.sp)
            }
            if (playlist.alternateUrls.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Server di riserva: ${playlist.alternateUrls.size}",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }
    }, {
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isReimporting && playlist.url.startsWith("http", ignoreCase = true)) {
                Button(
                    onClick = onReimport,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2A2A30),
                        contentColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onReimport)
                ) {
                    Text("Aggiorna")
                }
            }

            if (!isActive) {
                Button(
                    onClick = onUse,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmmiraTeal,
                        contentColor = Color(0xFFFFFFFF)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp), onClick = onUse)
                ) {
                    Text("Usa", fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = {
                    if (confirmDelete) onDelete() else confirmDelete = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (confirmDelete) StatusOffline else Color(0xFF2A2A30),
                    contentColor = TextPrimary
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.dpadFocusable(shape = RoundedCornerShape(8.dp)) {
                    if (confirmDelete) onDelete() else confirmDelete = true
                }
            ) {
                Text(if (confirmDelete) "Conferma?" else "Elimina")
            }
        }
    })
}

/** "Scade tra 12 giorni  •  Connessioni 1/2" with colour cues for low time / full slots. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ProviderStatusLine(playlist: Playlist, isRefreshing: Boolean) {
    val days = playlist.daysUntilExpiry()
    val (expiryText, expiryColor) = when {
        days == null -> "Nessuna scadenza" to TextSecondary
        days < 0 -> "Scaduto" to StatusOffline
        days == 0 -> "Scade oggi" to StatusOffline
        days == 1 -> "Scade domani" to StatusWarning
        days <= EXPIRY_WARNING_DAYS -> "Scade tra $days giorni" to StatusWarning
        else -> "Scade tra $days giorni" to StatusOnline
    }

    val max = playlist.maxConnections
    val connectionsColor = if (max != null && playlist.activeConnections >= max) StatusWarning else TextSecondary

    androidx.compose.foundation.layout.FlowRow(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(text = expiryText, color = expiryColor, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (max != null) {
            Text(
                text = "Connessioni ${playlist.activeConnections}/$max",
                color = connectionsColor,
                fontSize = 14.sp
            )
        }
        if (isRefreshing) {
            Text(text = "aggiornamento…", color = TextSecondary, fontSize = 12.sp)
        }
    }
}
